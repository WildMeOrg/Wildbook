package org.ecocean.timeseries.servlet;

import java.awt.geom.Line2D;
import java.io.BufferedWriter;
import java.io.IOException;
import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.Collections;


import org.ecocean.timeseries.core.*;
import org.ecocean.timeseries.core.distance.*;
import org.ecocean.timeseries.classifier.*;

import javax.servlet.ServletConfig;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.http.HttpServlet;

import org.ecocean.servlet.ServletUtilities;

import org.ecocean.CommonConfiguration;
import org.ecocean.Encounter;
import org.ecocean.Shepherd;
import org.ecocean.SuperSpot;
import org.ecocean.grid.EncounterLite;
import org.ecocean.grid.XComparator;

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
      ArrayList<Integer> labels=new ArrayList<Integer>();

      
    //create text file so we can also use this training data in the Neuroph UI
      BufferedWriter writer = null;
      
      out.println(ServletUtilities.getHeader(request));
      
      myShepherd.beginDBTransaction();
      
        try {
          
        
         
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
              
              //DistanceOperator op  = (DistanceOperator)Class.forName("org.ecocean.timeseries.core.distance.SwaleOperator").newInstance();
              SwaleOperator op=new SwaleOperator();
              
              ArrayList<Trajectory> trajectories=new ArrayList<Trajectory>();
              
      
              System.out.println("Building training patterns for Swale: "+maxPatterns);
              
              int fulfilledPatterns=0;
              int i=0;
              
              while((fulfilledPatterns<maxPatterns)&&(i<numEncs)){
                
                  
              
                  Encounter enc1=(Encounter)encounters.get(i);
                  if( (enc1.getGenus()!=null) && ((enc1.getGenus()+enc1.getSpecificEpithet()).equals(genusSpecies)) ){
                    
                      if(((enc1.getSpots()!=null)&&(enc1.getSpots().size()>0)&&(enc1.getRightSpots()!=null))&&((enc1.getRightSpots().size()>0))){
                        
                        if((enc1.getIndividualID()!=null)){
                          fulfilledPatterns++;
                          System.out.println("..."+i);
                          
                        //initialize our output series
                          ArrayList<Point> theEncDataPoints=new ArrayList<Point>();
                          
                        
                          int label=0;
                          if(!usedIDs.contains(enc1.getIndividualID())){
                            usedIDs.add(enc1.getIndividualID());
                            labels.add(new Integer(usedIDs.size()-1));
                          }    
                          else{
                              usedIDs.add(enc1.getIndividualID());
                              label=usedIDs.indexOf(enc1.getIndividualID());
                              labels.add(new Integer(label));
                          }
                              
                              
                              try{
                             
                  
                                EncounterLite theEnc=new EncounterLite(enc1);
                                ArrayList<SuperSpot> oldSpots=theEnc.getSpots();
                                if(theEnc.getRightSpots()!=null){
                                  oldSpots.addAll(theEnc.getRightSpots());
                                }
                                  Collections.sort(oldSpots, new XComparator());
                                  
                                  //let's prefilter old spots for outliers outside the bounds
                                  if(theEnc.getLeftReferenceSpots()[0].getCentroidX()<theEnc.getLeftReferenceSpots()[2].getCentroidX()){
                                    for(int m=0;m<oldSpots.size();m++){
                                      SuperSpot theSpot=oldSpots.get(m);
                                      if(theSpot.getCentroidX()<=theEnc.getLeftReferenceSpots()[0].getCentroidX()){
                                        oldSpots.remove(m);
                                        m--;
                                      }
                                      if(theSpot.getCentroidX()>=theEnc.getLeftReferenceSpots()[2].getCentroidX()){
                                        oldSpots.remove(m);
                                        m--;
                                      }
                                    }
                                  }
                                  int numOldSpots=oldSpots.size();
                                  
                                  
                                  
                                  
                                  SuperSpot[] oldReferenceSpots=theEnc.getLeftReferenceSpots();
                                  Line2D.Double oldLine=new Line2D.Double(oldReferenceSpots[0].getCentroidX(), oldReferenceSpots[0].getCentroidY(), oldReferenceSpots[2].getCentroidX(), oldReferenceSpots[2].getCentroidY());
                                  double oldLineWidth=Math.abs(oldReferenceSpots[2].getCentroidX()-oldReferenceSpots[0].getCentroidX());
                                  
                                  
                                  for(int m=0;m<numOldSpots;m++){
                                    SuperSpot theSpot=oldSpots.get(m);
                                    java.awt.geom.Point2D.Double thePoint=new java.awt.geom.Point2D.Double(theSpot.getCentroidX(),theSpot.getCentroidY());
                                    double[] myDub={i,(oldLine.ptLineDist(thePoint)/oldLineWidth),i};
                                    
                                    theEncDataPoints.add( new org.ecocean.timeseries.core.Point( myDub ) );
                                    
                                  }
                                
                                  Trajectory oldSeries=new Trajectory(0,theEncDataPoints,op);
                                  trajectories.add(oldSeries);
                                
                                
                              }
                              catch(Exception e){
                                e.printStackTrace();
                              }
                
                            
                          
                        }
                        
                  }
                }
                  i++;
                
                }
              
              
              op.tuneOperator(trajectories, labels, new KNNClassifier("",3));
            
            
            out.println("<p>I recommend the following values for the Swale algorithm when tuned on the "+genusSpecies +" dataset:<ul>");
            
            out.println("<li>Reward: "+op.m_matchreward);
            out.println("<li>Epsilon: "+op.m_threshold);
            out.println("<li>Gap: "+op.m_gappenalty);
            
            
            
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
