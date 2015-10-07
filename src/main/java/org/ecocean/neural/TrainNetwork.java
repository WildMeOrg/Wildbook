/*
 * The Shepherd Project - A Mark-Recapture Framework
 * Copyright (C) 2011 Jason Holmberg
 *
 * This program is free software; you can redistribute it and/or
 * modify it under the terms of the GNU General Public License
 * as published by the Free Software Foundation; either version 2
 * of the License, or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program; if not, write to the Free Software
 * Foundation, Inc., 51 Franklin Street, Fifth Floor, Boston, MA  02110-1301, USA.
 */

package org.ecocean.neural;

import org.ecocean.*;
import org.ecocean.servlet.ServletUtilities;

import javax.servlet.ServletConfig;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.apache.commons.math.stat.descriptive.SummaryStatistics;

import java.io.*;

import org.ecocean.grid.*;

import java.util.Vector;
import java.util.Random;

import com.fastdtw.timeseries.TimeSeriesBase.*;
import com.fastdtw.dtw.*;
import com.fastdtw.util.Distances;
import com.fastdtw.timeseries.TimeSeriesBase.Builder;
import com.fastdtw.timeseries.*;

import org.ecocean.grid.msm.*;

//train weka
import weka.core.Attribute;
import weka.core.FastVector;
import weka.core.Instances;
import weka.core.Instance;
import weka.core.SerializationHelper;
import weka.classifiers.meta.AdaBoostM1;
import weka.classifiers.Evaluation;
import weka.classifiers.Classifier;
import weka.core.converters.ArffSaver;
import weka.core.converters.ArffLoader;


public class TrainNetwork {


  
  public static double round(double value, int places) {
    if (places < 0) throw new IllegalArgumentException();

    long factor = (long) Math.pow(10, places);
    value = value * factor;
    long tmp = Math.round(value);
    return (double) tmp / factor;
}
  
  
  public static SummaryStatistics getMatchedIntersectionPerformance(Shepherd myShepherd, double intersectionProportion){
    SummaryStatistics stats=new SummaryStatistics();
    
    Vector encounters=myShepherd.getAllEncountersNoFilterAsVector();
    int numEncs=encounters.size();
    for(int i=0;i<(numEncs-1);i++){
      for(int j=(i+1);j<numEncs;j++){
        
        Encounter enc1=(Encounter)encounters.get(i);
        Encounter enc2=(Encounter)encounters.get(j);
        if(((enc1.getSpots()!=null)&&(enc1.getSpots().size()>0)&&(enc1.getRightSpots()!=null))&&((enc1.getRightSpots().size()>0))&&((enc2.getSpots()!=null)&&(enc2.getSpots().size()>0)&&(enc2.getRightSpots()!=null)&&((enc2.getRightSpots().size()>0)))){
          
          try{
              
            if((enc1.getIndividualID()!=null)&&(!enc1.getIndividualID().toLowerCase().equals("unassigned"))){
              if((enc2.getIndividualID()!=null)&&(!enc2.getIndividualID().toLowerCase().equals("unassigned"))){
                //train a match
                if(enc1.getIndividualID().equals(enc2.getIndividualID())){
                  
                  EncounterLite el1=new EncounterLite(enc1);
                  EncounterLite el2=new EncounterLite(enc2);
                  
                  //HolmbergIntersection
                  Double numIntersections=EncounterLite.getHolmbergIntersectionScore(el1, el2,intersectionProportion);
                  double finalInter=-1;
                  if(numIntersections!=null){finalInter=numIntersections.intValue();}
                 
                  stats.addValue(finalInter);
                }
              }
              
            }
              
            }
            catch(Exception e){
              e.printStackTrace();
            }
        }
      }
    }
    
    return stats;
  }
  
  
  /*
  public static SummaryStatistics getIntersectionStats(HttpServletRequest request){
    String context="context0";
    context=ServletUtilities.getContext(request);
    Shepherd myShepherd = new Shepherd(context);
    myShepherd.beginDBTransaction();
    //set up for response
  
    SummaryStatistics intersectionStats=new SummaryStatistics();
    
    
      try {
       
        double intersectionProportion=0.2;
        
        // add training data to training set (logical OR function)
        
        Vector encounters=myShepherd.getAllEncountersNoFilterAsVector();
        int numEncs=encounters.size();
        for(int i=0;i<(numEncs-1);i++){
          for(int j=(i+1);j<numEncs;j++){
            
            Encounter enc1=(Encounter)encounters.get(i);
            Encounter enc2=(Encounter)encounters.get(j);
            //make sure both have spots!
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
                      
                      EncounterLite el1=new EncounterLite(enc1);
                      EncounterLite el2=new EncounterLite(enc2);
                      
                      //HolmbergIntersection
                      Double numIntersections=EncounterLite.getHolmbergIntersectionScore(el1, el2,intersectionProportion);
                      double finalInter=-1;
                      if(numIntersections!=null){finalInter=numIntersections.intValue();}
                     
                      intersectionStats.addValue(finalInter);
                      
                      
                    }
                  }
                  
                }
                
                
               
              
                
              
            }
            catch(Exception e){
              e.printStackTrace();
            }

              
              
            }
            
          }
          
          
        }
        



      } 
      catch (Exception le) {
        le.printStackTrace();
        
      }
      finally{
        myShepherd.rollbackDBTransaction();
        myShepherd.closeDBTransaction();
      }
      return intersectionStats;
   }
   
  
  public static SummaryStatistics getI3SStats(HttpServletRequest request){
    double score=0;
     
    String context="context0";
    context=ServletUtilities.getContext(request);
    Shepherd myShepherd = new Shepherd(context);
    myShepherd.beginDBTransaction();
    //set up for response
  
    SummaryStatistics i3sStats=new SummaryStatistics();
    
    
      try {
       
        double intersectionProportion=0.2;
        
        // add training data to training set (logical OR function)
        
        Vector encounters=myShepherd.getAllEncountersNoFilterAsVector();
        int numEncs=encounters.size();
        for(int i=0;i<(numEncs-1);i++){
          for(int j=(i+1);j<numEncs;j++){
            
            Encounter enc1=(Encounter)encounters.get(i);
            Encounter enc2=(Encounter)encounters.get(j);
            //make sure both have spots!
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
                      
                      EncounterLite el1=new EncounterLite(enc1);
                      EncounterLite el2=new EncounterLite(enc2);
                      
                      //HolmbergIntersection
                      //Double numIntersections=EncounterLite.getHolmbergIntersectionScore(el1, el2,intersectionProportion);
                      //double finalInter=-1;
                      //if(numIntersections!=null){finalInter=numIntersections.intValue();}
                     
                      //intersectionStats.addValue(finalInter);
                      
                      //FastDTW
                      //TimeWarpInfo twi=EncounterLite.fastDTW(el1, el2, 30);
                      
                      //java.lang.Double distance = new java.lang.Double(-1);
                      //if(twi!=null){
                      //  WarpPath wp=twi.getPath();
                      //    String myPath=wp.toString();
                      //  distance=new java.lang.Double(twi.getDistance());
                      //}   
                      //dtwStats.addValue(distance);
                      
                      //I3S
                      I3SMatchObject newDScore=EncounterLite.improvedI3SScan(el1, el2);
                      double i3sScore=-1;
                      if(newDScore!=null){i3sScore=newDScore.getI3SMatchValue();}
                      i3sStats.addValue(i3sScore);
                      
                      //Proportion metric
                      //Double proportion=EncounterLite.getFlukeProportion(el1,el2);
                      //proportionStats.addValue(proportion);
                      
                
                      
                      
                    }
                  }
                  
                }
                
                
               
              
                
              
            }
            catch(Exception e){
              e.printStackTrace();
            }

              
              
            }
            
          }
          
          
        }
        



      } 
      catch (Exception le) {
        le.printStackTrace();
        
      }
      finally{
        myShepherd.rollbackDBTransaction();
        myShepherd.closeDBTransaction();
      }
      return i3sStats;
   }
   
  
  

  public static SummaryStatistics getDTWStats(HttpServletRequest request){
    double score=0;
     
    String context="context0";
    context=ServletUtilities.getContext(request);
    Shepherd myShepherd = new Shepherd(context);
    myShepherd.beginDBTransaction();
    //set up for response
  
    SummaryStatistics dtwStats=new SummaryStatistics();
    
    
      try {
       
        
        Vector encounters=myShepherd.getAllEncountersNoFilterAsVector();
        int numEncs=encounters.size();
        for(int i=0;i<(numEncs-1);i++){
          for(int j=(i+1);j<numEncs;j++){
            
            Encounter enc1=(Encounter)encounters.get(i);
            Encounter enc2=(Encounter)encounters.get(j);
            //make sure both have spots!
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
                      
                      EncounterLite el1=new EncounterLite(enc1);
                      EncounterLite el2=new EncounterLite(enc2);
                      
                      //HolmbergIntersection
                      //Double numIntersections=EncounterLite.getHolmbergIntersectionScore(el1, el2,intersectionProportion);
                      //double finalInter=-1;
                      //if(numIntersections!=null){finalInter=numIntersections.intValue();}
                     
                      //intersectionStats.addValue(finalInter);
                      
                      //FastDTW
                      TimeWarpInfo twi=EncounterLite.fastDTW(el1, el2, 30);
                      
                      java.lang.Double distance = new java.lang.Double(-1);
                      if(twi!=null){
                        WarpPath wp=twi.getPath();
                          String myPath=wp.toString();
                        distance=new java.lang.Double(twi.getDistance());
                      }   
                      dtwStats.addValue(distance);
                      
                  
                      
                    }
                  }
                  
                }
                
                
               
              
                
              
            }
            catch(Exception e){
              e.printStackTrace();
            }

              
              
            }
            
          }
          
          
        }
        



      } 
      catch (Exception le) {
        le.printStackTrace();
        
      }
      finally{
        myShepherd.rollbackDBTransaction();
        myShepherd.closeDBTransaction();
      }
      return dtwStats;
   }
  

  public static SummaryStatistics getProportionStats(HttpServletRequest request){
    double score=0;
     
    String context="context0";
    context=ServletUtilities.getContext(request);
    Shepherd myShepherd = new Shepherd(context);
    myShepherd.beginDBTransaction();
    //set up for response
  
    SummaryStatistics proportionStats=new SummaryStatistics();
    
    
      try {
       
        
        Vector encounters=myShepherd.getAllEncountersNoFilterAsVector();
        int numEncs=encounters.size();
        for(int i=0;i<(numEncs-1);i++){
          for(int j=(i+1);j<numEncs;j++){
            
            Encounter enc1=(Encounter)encounters.get(i);
            Encounter enc2=(Encounter)encounters.get(j);
            //make sure both have spots!
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
                      
                      EncounterLite el1=new EncounterLite(enc1);
                      EncounterLite el2=new EncounterLite(enc2);
                  
                      //Proportion metric
                      Double proportion=EncounterLite.getFlukeProportion(el1,el2);
                      proportionStats.addValue(proportion);
                      
                
                      
                      
                    }
                  }
                  
                }
                
                
               
              
                
              
            }
            catch(Exception e){
              e.printStackTrace();
            }

              
              
            }
            
          }
          
          
        }
        



      } 
      catch (Exception le) {
        le.printStackTrace();
        
      }
      finally{
        myShepherd.rollbackDBTransaction();
        myShepherd.closeDBTransaction();
      }
      return proportionStats;
   }
   */
  
  public static double getOverallFlukeMatchScore(HttpServletRequest request, double intersectionsValue, double dtwValue, double i3sValue, double proportionsValue, SummaryStatistics intersectionStats, SummaryStatistics dtwStats,SummaryStatistics i3sStats, SummaryStatistics proportionStats, double numIntersectionStdDev,double numDTWStdDev,double numI3SStdDev,double numProportionStdDev, double intersectHandicap, double dtwHandicap, double i3sHandicap, double proportionHandicap){
    double score=0;
     
    String context="context0";
    context=ServletUtilities.getContext(request);
    Shepherd myShepherd = new Shepherd(context);
    myShepherd.beginDBTransaction();
    //set up for response
  
      try {
       
        
        
        double intersectionStdDev=intersectionStats.getStandardDeviation();
        double dtwStdDev=dtwStats.getStandardDeviation();
        double i3sStdDev=i3sStats.getStandardDeviation();
        double proportionStdDev=proportionStats.getStandardDeviation();
      
        //int intersectionsValue, double dtwValue, double i3sValue, double proportionsValue
        
        //just do simple single std dev tests
        
        //score intersections
        if((intersectionsValue>=(intersectionStats.getMean()-intersectionStdDev*numIntersectionStdDev))){
          
          //exceptionally strong score!
          
          if(intersectionsValue>=(intersectionStats.getMean()+intersectionStdDev*numIntersectionStdDev)){
            if(intersectHandicap>3){intersectHandicap=3;}
            score=score+3-intersectHandicap;
          }
          //strong score
          else if(intersectionsValue>=intersectionStats.getMean()){
            if(intersectHandicap>2){intersectHandicap=2;}
            score=score+2-intersectHandicap;
          }
          //moderate score
          else{
            if(intersectHandicap>1){intersectHandicap=1;}
            score=score+1-intersectHandicap;
          }
          
        }
        
        //score FastDTW
        if((dtwValue<=(dtwStats.getMean()+dtwStdDev*numDTWStdDev))){
        //exceptionally strong score!
          if(dtwValue<=(dtwStats.getMean()-dtwStdDev*numDTWStdDev)){
            if(dtwHandicap>3){dtwHandicap=3;}
            score=score+3-dtwHandicap;
          }
          //strong score
          else if(dtwValue<=dtwStats.getMean()){
            if(dtwHandicap>2){dtwHandicap=2;}
            score=score+2-dtwHandicap;
          }
          //moderate score
          else{
            if(dtwHandicap>1){dtwHandicap=1;}
            score=score+1-dtwHandicap;
          }
        }
        
        
        //score I3S
        if((i3sValue>0)&&(i3sValue<=(i3sStats.getMean()+i3sStdDev*numI3SStdDev))){
        //exceptionally strong score!
          if(i3sValue<=(i3sStats.getMean()-i3sStdDev*numI3SStdDev)){
            if(i3sHandicap>3){i3sHandicap=3;}
            score=score+3-i3sHandicap;
          }
          //strong score
          else if(i3sValue<=i3sStats.getMean()){
            if(i3sHandicap>2){i3sHandicap=2;}
            score=score+2-i3sHandicap;
          }
          //moderate score
          else{
            if(i3sHandicap>1){i3sHandicap=1;}
            score=score+1-i3sHandicap;
          }
        }
        
        //score Proportions
        if((proportionsValue<=(proportionStats.getMean()+proportionStdDev*numProportionStdDev))){
        //exceptionally strong score!
          if(proportionsValue<=(proportionStats.getMean()-proportionStdDev*numProportionStdDev)){
            if(proportionHandicap>3){proportionHandicap=3;}
            score=score+3-proportionHandicap;
          }
          //strong score
          else if(proportionsValue<=proportionStats.getMean()){
            if(proportionHandicap>2){proportionHandicap=2;}
            score=score+2-proportionHandicap;
          }
          //moderate score
          else{
            if(proportionHandicap>1){proportionHandicap=1;}
            score=score+1-proportionHandicap;
          }
        }
        
        


      } 
      catch (Exception le) {
        le.printStackTrace();
        
      }
      finally{
        myShepherd.rollbackDBTransaction();
        myShepherd.closeDBTransaction();
      }

     
    return score; 
   }
   
  public static AdaBoostM1 getAdaBoostClassifier(HttpServletRequest request, String fullPathToClassifierFile, Instances instances){
    
    File classifierFile=new File(fullPathToClassifierFile);
    try{
    
      if(classifierFile.exists()){
        AdaBoostM1 booster = (AdaBoostM1)deserializeWekaClassifier(request,fullPathToClassifierFile);
        return booster;
      }
    }
    catch(Exception e){
      e.printStackTrace();
      
    }
    
    return null;
    
    
  }
  
  public static AdaBoostM1 buildAdaBoostClassifier(HttpServletRequest request, String fullPathToClassifierFile, Instances instances){
    
    AdaBoostM1 booster=new AdaBoostM1();
    try {
      //getAbsolutePathToInstances(String genusSpecies,HttpServletRequest request)
      booster.buildClassifier(instances);
      //serialize out the classifier
      serializeWekaClassifier(request,booster,fullPathToClassifierFile);
    } 
    catch (Exception e) {
      // TODO Auto-generated catch block
      e.printStackTrace();
      return null;
    }
    return booster;
    
    
  }
  
  public static Instances getAdaboostInstances(HttpServletRequest request, String fullPathToInstancesFile){
    String context="context0";
    context=ServletUtilities.getContext(request);
    Shepherd myShepherd = new Shepherd(context);
    
    
    //FIRST
    //first check for file and return it if it exists
    File classifierFile=new File(fullPathToInstancesFile);
    try{
    
      if(classifierFile.exists()){
        Instances instances= deserializeWekaInstances(request,fullPathToInstancesFile);
        return instances;
      }
      else{
        System.out.println("     I could not find a classifier file at: "+fullPathToInstancesFile);
      }
    }
    catch(Exception e){
      e.printStackTrace();
      
    }
   return null;
    
  }
  
    public static void serializeWekaClassifier(HttpServletRequest request, Classifier cls, String absolutePath){
      
      String rootWebappPath = request.getSession().getServletContext().getRealPath("/");
      File webappsDir = new File(rootWebappPath).getParentFile();
      File shepherdDataDir = new File(webappsDir, CommonConfiguration.getDataDirectoryName(ServletUtilities.getContext(request)));
      File classifiersDir = new File(shepherdDataDir,"classifiers");
      if(!classifiersDir.exists()){classifiersDir.mkdirs();}
      
      
   // serialize model
      try {
        weka.core.SerializationHelper.write(absolutePath, cls);
      } catch (Exception e) {
        // TODO Auto-generated catch block
        e.printStackTrace();
      }
      
    }
    
    private static Classifier deserializeWekaClassifier(HttpServletRequest request, String absolutePath){
      
   // serialize model
      try {
        return  (Classifier) weka.core.SerializationHelper.read(absolutePath);
      } catch (Exception e) {
        // TODO Auto-generated catch block
        e.printStackTrace();
      }
      return null;
    }
    
    public static String getAbsolutePathToClassifier(String genusSpecies,HttpServletRequest request){
      String rootWebappPath = request.getSession().getServletContext().getRealPath("/");
      File webappsDir = new File(rootWebappPath).getParentFile();
      File shepherdDataDir = new File(webappsDir, CommonConfiguration.getDataDirectoryName(ServletUtilities.getContext(request)));
      File classifiersDir = new File(shepherdDataDir,"classifiers");
      File classifierFile=new File(classifiersDir,(genusSpecies+".adaboostM1"));
      return classifierFile.getAbsolutePath();
    }
    
    public static String getAbsolutePathToInstances(String genusSpecies,HttpServletRequest request){
      String rootWebappPath = request.getSession().getServletContext().getRealPath("/");
      File webappsDir = new File(rootWebappPath).getParentFile();
      File shepherdDataDir = new File(webappsDir, CommonConfiguration.getDataDirectoryName(ServletUtilities.getContext(request)));
      File classifiersDir = new File(shepherdDataDir,"classifiers");
      File classifierFile=new File(classifiersDir,(genusSpecies+".instances"));
      return classifierFile.getAbsolutePath();
    }
  
    
    public static void serializeWekaInstances(HttpServletRequest request, Instances instances, String absolutePath){
      
      String rootWebappPath = request.getSession().getServletContext().getRealPath("/");
      File webappsDir = new File(rootWebappPath).getParentFile();
      File shepherdDataDir = new File(webappsDir, CommonConfiguration.getDataDirectoryName(ServletUtilities.getContext(request)));
      File classifiersDir = new File(shepherdDataDir,"classifiers");
      if(!classifiersDir.exists()){classifiersDir.mkdirs();}
      
      
   // serialize model
      try {
        
        
        //weka.core.SerializationHelper.write(absolutePath, instances);
        
        ArffSaver saver = new ArffSaver();
        saver.setInstances(instances);
        saver.setFile(new File(absolutePath));
        saver.writeBatch();
        
        
        
      } catch (Exception e) {
        // TODO Auto-generated catch block
        e.printStackTrace();
      }
      
    }
    
    private static Instances deserializeWekaInstances(HttpServletRequest request, String absolutePath){
      
     try {
        ArffLoader loader =new ArffLoader();
        loader.setFile(new File(absolutePath));
        Instances instances=loader.getDataSet();
        int cIdx=instances.numAttributes()-1;
        instances.setClassIndex(cIdx);
        return instances;
        //return  (Instances) weka.core.SerializationHelper.read(absolutePath);
      } 
      catch (Exception e) {
        // TODO Auto-generated catch block
        e.printStackTrace();
      }
      return null;
    }
    
    
    /*
     * 
     * The function will try to build a 50-50 set of matches/nonmatches for a classifier
     * 
     * 
     */
    public static Instances buildAdaboostInstances(HttpServletRequest request, String fullPathToInstancesFile){
      String context="context0";
      context=ServletUtilities.getContext(request);
      Shepherd myShepherd = new Shepherd(context);
      
      
      double intersectionProportion=0.2;
      
    //create text file so we can also use this training data in the Neuroph UI
      BufferedWriter writer = null;
      
      int numMatches=0;
      int numNonMatches=0;
      
      
      //prep weka for AdaBoost
      // Declare numeric attributes
      Attribute intersectAttr = new Attribute("intersect");
      Attribute fastDTWAttr = new Attribute("fastDTW");
      Attribute i3sAttr = new Attribute("I3S");
      Attribute proportionAttr = new Attribute("proportion");
      Attribute msmAttr = new Attribute("MSM");
      
      //class vector
      // Declare the class attribute along with its values
      FastVector fvClassVal = new FastVector(2);
      fvClassVal.addElement("match");
      fvClassVal.addElement("nonmatch");
      Attribute ClassAttribute = new Attribute("theClass", fvClassVal);
      
      //define feature vector
      // Declare the feature vector
      FastVector fvWekaAttributes = new FastVector(6);
      fvWekaAttributes.addElement(intersectAttr);
      fvWekaAttributes.addElement(fastDTWAttr);
      fvWekaAttributes.addElement(i3sAttr);
      fvWekaAttributes.addElement(proportionAttr);
      fvWekaAttributes.addElement(msmAttr);
      fvWekaAttributes.addElement(ClassAttribute);
      
      
      myShepherd.beginDBTransaction();
      
        try {
         
          
          
          
         // File trainingFile = new File(shepherdDataDir.getAbsolutePath()+"/fluke_perceptron.input");
         // writer = new BufferedWriter(new FileWriter(trainingFile));
          
          //StringBuffer writeMe=new StringBuffer();
          
          
          Vector encounters=myShepherd.getAllEncountersNoFilterAsVector();
          int numEncs=encounters.size();
          
          Instances isTrainingSet = new Instances("Rel", fvWekaAttributes, (2*numEncs*(numEncs-1)/2));
          //Instances isTrainingSet = new Instances("Rel", fvWekaAttributes, 1000);
          
          isTrainingSet.setClassIndex(5);
          AdaBoostM1 booster=new AdaBoostM1();
          
          for(int i=0;i<(numEncs-1);i++){
          //for(int i=0;i<1000;i++){
            for(int j=(i+1);j<numEncs;j++){
              
              Encounter enc1=(Encounter)encounters.get(i);
              Encounter enc2=(Encounter)encounters.get(j);
              //make sure both have spots!
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
                   
                    
                    //FastDTW
                    TimeWarpInfo twi=EncounterLite.fastDTW(el1, el2, 30);
                    
                    java.lang.Double distance = new java.lang.Double(-1);
                    if(twi!=null){
                      WarpPath wp=twi.getPath();
                        String myPath=wp.toString();
                      distance=new java.lang.Double(twi.getDistance());
                    }   
                    
                    //I3S
                    I3SMatchObject newDScore=EncounterLite.improvedI3SScan(el1, el2);
                    double i3sScore=-1;
                    if(newDScore!=null){i3sScore=newDScore.getI3SMatchValue();}
                    
                    //Proportion metric
                    Double proportion=EncounterLite.getFlukeProportion(el1,el2);
                    
                    //balance the training set to make sure nonmatches do not outweigh matches and cause the NN to cheat
                    /*
                    if((output==0)||(numNonMatches<numMatches)){
                      trainingSet. addRow (
                          new DataSetRow (new double[]{finalInter, distance, i3sScore, proportion},
                          new double[]{output}));
                      
                      //write the line too
                      writeMe.append(round(finalInter,4)+","+round(distance,4)+","+round(i3sScore,4)+","+round(proportion,4)+","+output+"\n");
                      
                      if(output==0){numMatches++;}
                      else{numNonMatches++;}
                      
                    }
                    */
                    
                    
                    Double msm=MSM.getMSMDistance(el1, el2);
                    
                    // Create the instance
                    Instance iExample = new Instance(6);
                    iExample.setValue((Attribute)fvWekaAttributes.elementAt(0), numIntersections.doubleValue());
                    iExample.setValue((Attribute)fvWekaAttributes.elementAt(1), distance.doubleValue());
                    iExample.setValue((Attribute)fvWekaAttributes.elementAt(2), i3sScore);
                    iExample.setValue((Attribute)fvWekaAttributes.elementAt(3), proportion.doubleValue());
                    iExample.setValue((Attribute)fvWekaAttributes.elementAt(4), msm.doubleValue());
                    
                    if(output==0){
                      iExample.setValue((Attribute)fvWekaAttributes.elementAt(5), "match");
                      numMatches++;
                    }
                    else{
                      iExample.setValue((Attribute)fvWekaAttributes.elementAt(5), "nonmatch");
                      numNonMatches++;
                    }
                    // add the instance
                    isTrainingSet.add(iExample);
                    
                    //END FIRST PASS
                    
                    //SECOND PASS-reverse order

                    
                    //HolmbergIntersection
                    Double numIntersections2=EncounterLite.getHolmbergIntersectionScore(el2, el1,intersectionProportion);
                    double finalInter2=-1;
                    if(numIntersections2!=null){finalInter2=numIntersections2.intValue();}
                   
                    
                    //FastDTW
                    TimeWarpInfo twi2=EncounterLite.fastDTW(el2, el1, 30);
                    
                    java.lang.Double distance2 = new java.lang.Double(-1);
                    if(twi2!=null){
                      WarpPath wp2=twi2.getPath();
                        String myPath2=wp2.toString();
                      distance2=new java.lang.Double(twi2.getDistance());
                    }   
                    
                    //I3S
                    I3SMatchObject newDScore2=EncounterLite.improvedI3SScan(el2, el1);
                    double i3sScore2=-1;
                    if(newDScore2!=null){i3sScore2=newDScore2.getI3SMatchValue();}
                    
                    //Proportion metric
                    Double proportion2=EncounterLite.getFlukeProportion(el2,el1);
                    
                    //balance the training set to make sure nonmatches do not outweigh matches and cause the NN to cheat
                    /*
                    if((output==0)||(numNonMatches<numMatches)){
                      trainingSet. addRow (
                          new DataSetRow (new double[]{finalInter, distance, i3sScore, proportion},
                          new double[]{output}));
                      
                      //write the line too
                      writeMe.append(round(finalInter,4)+","+round(distance,4)+","+round(i3sScore,4)+","+round(proportion,4)+","+output+"\n");
                      
                      if(output==0){numMatches++;}
                      else{numNonMatches++;}
                      
                    }
                    */
                    
                  //score MSM
                    
                    Double msmScore=MSM.getMSMDistance(el1, el2);
                    
                    
                    // Create the instance
                    Instance iExample2 = new Instance(6);
                    iExample2.setValue((Attribute)fvWekaAttributes.elementAt(0), numIntersections2.doubleValue());
                    iExample2.setValue((Attribute)fvWekaAttributes.elementAt(1), distance2.doubleValue());
                    iExample2.setValue((Attribute)fvWekaAttributes.elementAt(2), i3sScore2);
                    iExample2.setValue((Attribute)fvWekaAttributes.elementAt(3), proportion2.doubleValue());
                    iExample2.setValue((Attribute)fvWekaAttributes.elementAt(4), msmScore.doubleValue());
                    
                    
                    if(output==0){
                      iExample2.setValue((Attribute)fvWekaAttributes.elementAt(5), "match");
                      numMatches++;
                    }
                    else{
                      iExample2.setValue((Attribute)fvWekaAttributes.elementAt(5), "nonmatch");
                      numNonMatches++;
                    }
                    // add the instance
                    isTrainingSet.add(iExample2);
                    
                    
                    
                    //END SECOND PASS
                    
                    
                    
                  }
                  catch(Exception e){
                    e.printStackTrace();
                  }
    
                
                  
                }
           
              }
            }
          
          //ok, now we need to build a set if Instances that only have matches and then add an equal number of nonmatches
          Instances balancedInstances = new Instances("Rel", fvWekaAttributes, (numMatches*2));
          balancedInstances.setClassIndex(5);
          for(int i=0;i<isTrainingSet.numInstances();i++){
            
            Instance myInstance=isTrainingSet.instance(i);
            if(myInstance.stringValue(5).equals("match")){
              isTrainingSet.delete(i);
              balancedInstances.add(myInstance);
              //pop it off the original stack
              
              i--;
              System.out.println("  Balanced match added!");
            }
            
          }
          //now get the equal number of false instances to test with
          int sampledFalseInstances=0;
          while(sampledFalseInstances<numMatches){
            Random myRan=new Random();
            int selected=myRan.nextInt(isTrainingSet.numInstances()-1);
            Instance popMe=isTrainingSet.instance(selected);
            if(popMe.stringValue(5).equals("nonmatch")){
              isTrainingSet.delete(selected);
              balancedInstances.add(popMe);
              sampledFalseInstances++;
            }
          }
          
          
          
          //write it out
          try {
            serializeWekaInstances(request,balancedInstances,fullPathToInstancesFile);
          } 
          catch (Exception e) {
            // TODO Auto-generated catch block
            e.printStackTrace();
            return null;
          }
          
           
          //DONE-return newly trained instances!
          return balancedInstances;
          
          }
          catch(Exception e){return null;}
        finally{
          myShepherd.rollbackDBTransaction();
          myShepherd.closeDBTransaction();
        }
      
        }
  
}
	
	
