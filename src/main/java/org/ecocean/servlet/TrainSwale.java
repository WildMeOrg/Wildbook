package org.ecocean.servlet;

import java.awt.geom.Line2D;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.concurrent.ThreadPoolExecutor;

import fap.core.data.*;
import fap.core.series.*;
import fap.similarities.SwaleSimilarityComputor;
import fap.similarities.SwaleSimilarityTuner;
import fap.custom.*;

import javax.servlet.ServletConfig;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.http.HttpServlet;


import org.apache.commons.math.stat.descriptive.SummaryStatistics;
import org.ecocean.CommonConfiguration;
import org.ecocean.Encounter;
import org.ecocean.Shepherd;
import org.ecocean.SuperSpot;
import org.ecocean.grid.EncounterLite;
import org.ecocean.grid.FlukeMatchComparator;
import org.ecocean.grid.GridManager;
import org.ecocean.grid.GridManagerFactory;
import org.ecocean.grid.I3SMatchObject;
import org.ecocean.grid.MatchObject;
import org.ecocean.grid.ScanTask;
import org.ecocean.grid.ScanTaskCleanupThread;
import org.ecocean.grid.ScanWorkItem;
import org.ecocean.grid.ScanWorkItemResult;
import org.ecocean.grid.SharkGridThreadExecutorService;
import org.ecocean.grid.XComparator;
import org.ecocean.grid.msm.MSM;
import org.ecocean.neural.TrainNetwork;

import com.fastdtw.dtw.TimeWarpInfo;
import com.fastdtw.dtw.WarpPath;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonPrimitive;

import fap.core.data.DataPoint;
import weka.classifiers.meta.AdaBoostM1;
import weka.core.Attribute;
import weka.core.FastVector;
import weka.core.Instance;
import weka.core.Instances;
import java.util.Enumeration;
import java.util.Random;
import java.util.Vector;


public class TrainSwale extends HttpServlet {
  
  public void init(ServletConfig config) throws ServletException {
    super.init(config);
  }

  public void doGet(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
    doPost(request, response);
  }

  public void doPost(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {

    String context=ServletUtilities.getContext(request);
    
    
  //set up for response
    response.setContentType("text/html; charset=UTF-8");
    PrintWriter out = response.getWriter();
    
    String genusSpecies=request.getParameter("genusSpecies").replaceAll(" ", "");
    
    Shepherd myShepherd = new Shepherd(context);
    
    
    ArrayList<String> usedIDs=new ArrayList<String>();
    
    double reward=50;
    double penalty=0;
    double epsilon=0;
    
  //create text file so we can also use this training data in the Neuroph UI
    BufferedWriter writer = null;
    
    out.println(ServletUtilities.getHeader(request));
    
    myShepherd.beginDBTransaction();
    
      try {
        
        SerieList<Serie> myList=new SerieList<Serie>();
       
        
       
             int numScores=0;
      
            Vector encounters=myShepherd.getAllEncountersNoFilterAsVector();
            int numEncs=encounters.size();
            
            int maxPatterns=numEncs;
            if(request.getParameter("maxPatterns")!=null){
              try{
                maxPatterns=Integer.parseInt(request.getParameter("maxPatterns"));
              }
              catch(Exception e){}
            }
            if(numEncs<maxPatterns){maxPatterns=numEncs;}
            
    
            System.out.println("Building training patterns for SWALE: "+maxPatterns);
            
            for(int i=0;i<maxPatterns;i++){
              System.out.println("..."+i);
              
            
                Encounter enc1=(Encounter)encounters.get(i);
                if( (enc1.getGenus()!=null) && ((enc1.getGenus()+enc1.getSpecificEpithet()).equals(genusSpecies)) ){
                  
                    if(((enc1.getSpots()!=null)&&(enc1.getSpots().size()>0)&&(enc1.getRightSpots()!=null))&&((enc1.getRightSpots().size()>0))){
                      
                      if((enc1.getIndividualID()!=null)){
                      
                        double label=0;
                        if(!usedIDs.contains(enc1.getIndividualID())){
                          usedIDs.add(enc1.getIndividualID());
                          label=usedIDs.size()-1;
                        }    
                        else{
                            usedIDs.add(enc1.getIndividualID());
                            label=usedIDs.indexOf(enc1.getIndividualID());
                        }
                            
                            
                            try{
                           
                
                              EncounterLite theEnc=new EncounterLite(enc1);
                              ArrayList<SuperSpot> oldSpots=theEnc.getSpots();
                              oldSpots.addAll(theEnc.getRightSpots());
                                Collections.sort(oldSpots, new XComparator());
                                
                                //let's prefilter old spots for outlies outside the bounds
                                for(int m=0;i<oldSpots.size();m++){
                                  SuperSpot theSpot=oldSpots.get(m);
                                  if(theSpot.getCentroidX()<=theEnc.getLeftReferenceSpots()[0].getCentroidX()){
                                    oldSpots.remove(m);
                                    i--;
                                  }
                                  if(theSpot.getCentroidX()>=theEnc.getLeftReferenceSpots()[2].getCentroidX()){
                                    oldSpots.remove(m);
                                    i--;
                                  }
                                }
                                int numOldSpots=oldSpots.size();
                                
                                
                                
                                
                                SuperSpot[] oldReferenceSpots=theEnc.getLeftReferenceSpots();
                                Line2D.Double oldLine=new Line2D.Double(oldReferenceSpots[0].getCentroidX(), oldReferenceSpots[0].getCentroidY(), oldReferenceSpots[2].getCentroidX(), oldReferenceSpots[2].getCentroidY());
                                double oldLineWidth=Math.abs(oldReferenceSpots[2].getCentroidX()-oldReferenceSpots[0].getCentroidX());
                                
                                //first populate OLD_VALUES - easy
                                SimpleDataPointSerie theEncDataPoints=new SimpleDataPointSerie();
                                
                                for(int m=0;m<numOldSpots;m++){
                                  SuperSpot theSpot=oldSpots.get(m);
                                  java.awt.geom.Point2D.Double thePoint=new java.awt.geom.Point2D.Double(theSpot.getCentroidX(),theSpot.getCentroidY());
                                  theEncDataPoints.addPoint(new DataPoint(m,(oldLine.ptLineDist(thePoint)/oldLineWidth)));
                                  
                                }
                              
                              Serie mySerie=new Serie(theEncDataPoints);
                              mySerie.setLabel(label);
                              myList.add(mySerie);
                              
                              
                            }
                            catch(Exception e){
                              e.printStackTrace();
                            }
              
                          
                        
                      }
                }
              }
             
              
              }
            
            SwaleSimilarityComputor myComp=new SwaleSimilarityComputor();
            WildbookFAPCallback callback=new WildbookFAPCallback();
            SwaleSimilarityTuner myTuner = new SwaleSimilarityTuner(myComp, null);
            myTuner.tune(myList);
            
            
            out.println("<p>I recommend the following values for the Swale algorithm when tuned on the "+genusSpecies +" dataset:<ul>");
            
            out.println("<li>Reward: "+myComp.getReward());
            out.println("<li>Epsilon: "+myComp.getEpsilon());
            out.println("<li>Gap: "+myComp.getGap());
            
            out.println("</ul></p");
                
           

        
        }
        catch(Exception e){
          e.printStackTrace();
          out.println("I hit an error. Check the logs for more information.");
        }
      finally{
        myShepherd.rollbackDBTransaction();
        myShepherd.closeDBTransaction();
      }
    
   
   


   out.println("<p><a href=\"http://" + CommonConfiguration.getURLLocation(request) + "/appadmin/scanTaskAdmin.jsp" + "\">Return to Grid Administration" + "</a></p>\n");
   out.println(ServletUtilities.getFooter(context));

  }



 
}
