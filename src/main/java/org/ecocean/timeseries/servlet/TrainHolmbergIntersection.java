package org.ecocean.timeseries.servlet;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.concurrent.ThreadPoolExecutor;

import javax.servlet.ServletConfig;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.http.HttpServlet;

import org.ecocean.servlet.ServletUtilities;


import org.apache.commons.math.stat.descriptive.SummaryStatistics;
import org.ecocean.CommonConfiguration;
import org.ecocean.Encounter;
import org.ecocean.Shepherd;
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
import org.ecocean.grid.msm.MSM;
import org.ecocean.neural.TrainNetwork;

import com.fastdtw.dtw.TimeWarpInfo;
import com.fastdtw.dtw.WarpPath;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonPrimitive;

import weka.classifiers.meta.AdaBoostM1;
import weka.core.Attribute;
import weka.core.FastVector;
import weka.core.Instance;
import weka.core.Instances;
import java.util.Enumeration;
import java.util.Random;
import java.util.Vector;


public class TrainHolmbergIntersection extends HttpServlet {
  
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
    
    
    double intersectionProportion=0.01;
    double bestProportion=intersectionProportion;
    double bestDiff=0;
    
  //create text file so we can also use this training data in the Neuroph UI
    BufferedWriter writer = null;
    

    
    myShepherd.beginDBTransaction();
    
      try {
       
        
        for(;intersectionProportion<=0.2;){
          
          double totalMatchScores=0;
          double totalNonmatchScores=0;
          int numMatchScores=0;
          int numNonmatchScores=0;
        
            Vector encounters=myShepherd.getAllEncountersNoFilterAsVector();
            int numEncs=encounters.size();
            
    
            
            for(int i=0;i<(numEncs-1);i++){
            //for(int i=0;i<1000;i++){
              for(int j=(i+1);j<numEncs;j++){
                
                Encounter enc1=(Encounter)encounters.get(i);
                Encounter enc2=(Encounter)encounters.get(j);
                //make sure both have spots!
                if((enc1.getGenus()!=null)&&(enc2.getGenus()!=null)&&((enc1.getGenus()+enc1.getSpecificEpithet()).equals(enc2.getGenus()+enc2.getSpecificEpithet()))){
                  
                    if(((enc1.getSpots()!=null)&&(enc1.getSpots().size()>0)&&(enc1.getRightSpots()!=null))&&((enc1.getRightSpots().size()>0))&&((enc2.getSpots()!=null)&&(enc2.getSpots().size()>0)&&(enc2.getRightSpots()!=null)&&((enc2.getRightSpots().size()>0)))){
                        try{
                          System.out.println("Learning: "+enc1.getCatalogNumber()+" and "+enc2.getCatalogNumber());
                          
                          //if both have spots, then we need to compare them
                       
                          //first, are they the same animal?
                          //default is 1==no
                          double output=1;
                          if((enc1.getIndividualID()!=null)&&(!enc1.getIndividualID().toLowerCase().equals("unassigned"))){
                            if((enc2.getIndividualID()!=null)&&(!enc2.getIndividualID().toLowerCase().equals("unassigned"))){
                              //train a match
                              if(enc1.getIndividualID().equals(enc2.getIndividualID())){
                                output=0;
                                System.out.println("   Nice match!!!!");
                              }
                            }
                            
                          }
                          
                          
                          
                          
                          
                          EncounterLite el1=new EncounterLite(enc1);
                          EncounterLite el2=new EncounterLite(enc2);
                          
                          //FIRST PASS
                          
                          //HolmbergIntersection
                          Double numIntersections=EncounterLite.getHolmbergIntersectionScore(el1, el2,intersectionProportion);
                          double finalInter=-1;
                          if(numIntersections!=null){finalInter=numIntersections.intValue();}
                         
                         if(output==0){
                           numMatchScores++;
                           totalMatchScores+=numIntersections;
                         }
                         else{
                           numNonmatchScores++;
                           totalNonmatchScores+=numIntersections;
                         }
                          
                          
                          
                        }
                        catch(Exception e){
                          e.printStackTrace();
                        }
          
                      
                        
                      }
              }
             
                }
              }
            double diff=totalMatchScores/numMatchScores-totalNonmatchScores/numNonmatchScores;
            if(diff>bestDiff){
              bestDiff=diff;
              bestProportion=intersectionProportion;
            }
            
            intersectionProportion=intersectionProportion+0.01;
      } //end master for loop of proportions
        
       
        

        
        }
        catch(Exception e){}
      finally{
        myShepherd.rollbackDBTransaction();
        myShepherd.closeDBTransaction();
      }
    
   
   out.println(ServletUtilities.getHeader(request));
   out.println("<p>I recommend the following proportional allowance for the Holmberg Intersection method: "+bestProportion+"</p>");


   out.println("<p><a href=\"http://" + CommonConfiguration.getURLLocation(request) + "/appadmin/scanTaskAdmin.jsp" + "\">Return to Grid Administration" + "</a></p>\n");
   out.println(ServletUtilities.getFooter(context));

  }



 
}
