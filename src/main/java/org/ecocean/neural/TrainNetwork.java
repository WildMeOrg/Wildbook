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

import java.util.Set;
import java.util.SortedMap;

import javax.servlet.ServletContext;
import javax.servlet.http.HttpServletRequest;

import org.apache.commons.math.stat.descriptive.SummaryStatistics;

import java.io.*;
import java.net.URISyntaxException;

import org.ecocean.grid.*;

import java.util.Vector;
import java.util.Random;

import java.util.TreeMap;

import org.ecocean.neural.WildbookInstance;

import org.ecocean.identity.IBEISIA;

//import com.fastdtw.timeseries.TimeSeriesBase.*;
import com.fastdtw.dtw.*;
//import com.fastdtw.util.Distances;
//import com.fastdtw.timeseries.TimeSeriesBase.Builder;
//import com.fastdtw.timeseries.*;

import org.ecocean.grid.msm.*;

//train weka
import weka.core.Attribute;
import weka.core.Instances;
import weka.core.Instance;
import weka.core.DenseInstance;
import weka.core.SerializationHelper;
import weka.classifiers.meta.AdaBoostM1;
import weka.classifiers.bayes.BayesNet;
import weka.classifiers.Evaluation;
import weka.classifiers.Classifier;
import weka.core.converters.ArffSaver;
import weka.core.converters.ArffLoader;
import weka.core.Utils;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;

public class TrainNetwork {


  
  public static double round(double value, int places) {
    if (places < 0) throw new IllegalArgumentException();

    long factor = (long) Math.pow(10, places);
    value = value * factor;
    long tmp = Math.round(value);
    return (double) tmp / factor;
}
  
 /* 
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
                  Double numIntersections=EncounterLite.getHolmbergIntersectionScore(el1, el2 );
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
   
  public static Classifier getWekaClassifier(HttpServletRequest request, String fullPathToClassifierFile, Instances instances){
    
    File classifierFile=new File(fullPathToClassifierFile);
    try{
    
      if(classifierFile.exists()){
        Classifier booster = (Classifier)deserializeWekaClassifier(request,fullPathToClassifierFile);
        return booster;
      }
    }
    catch(Exception e){
      e.printStackTrace(); 
    }
    return null;
  }
  
  
  
  public static BayesNet getBayesNetClassifier(HttpServletRequest request, String fullPathToClassifierFile, Instances instances){
    File classifierFile=new File(fullPathToClassifierFile);
    try{
      if(classifierFile.exists()){
        BayesNet booster = (BayesNet)deserializeWekaClassifier(request,fullPathToClassifierFile);
        return booster;
      }
    }
    catch(Exception e){
      e.printStackTrace(); 
    }
    return null;
  }
  
  
  
  /*
  public static AdaBoostM1 buildAdaBoostClassifier(HttpServletRequest request, String fullPathToClassifierFile, Instances instances){
    
    AdaBoostM1 booster=new AdaBoostM1();
    String optionString = "-P 100 -S 1 -I 10 -W weka.classifiers.trees.RandomForest -- -I 100 -K 0 -S 1";
    try {
      booster.setOptions(weka.core.Utils.splitOptions(optionString));
    } catch (Exception e1) {
      // TODO Auto-generated catch block
      e1.printStackTrace();
    }

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
  */
  
  
  
  public static Instances getWekaInstances(HttpServletRequest request, String fullPathToInstancesFile){
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
      File classifierFile=new File(classifiersDir,(genusSpecies+".model"));
      return classifierFile.getAbsolutePath();
    }
    
    public static String getAbsolutePathToInstances(String genusSpecies,HttpServletRequest request){
      String rootWebappPath = request.getSession().getServletContext().getRealPath("/");
      File webappsDir = new File(rootWebappPath).getParentFile();
      File shepherdDataDir = new File(webappsDir, CommonConfiguration.getDataDirectoryName(ServletUtilities.getContext(request)));
      File classifiersDir = new File(shepherdDataDir,"classifiers");
      File classifierFile=new File(classifiersDir,(genusSpecies+".arff"));
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
    public static Instances buildWekaInstances(HttpServletRequest request, String fullPathToInstancesFile, String genus, String specificEpithet){
      String context="context0";
      context=ServletUtilities.getContext(request);
      Shepherd myShepherd = new Shepherd(context);
      
      
    
    //create text file
      BufferedWriter writer = null;
      
      int numMatches=0;
      int numNonMatches=0;
      String genusSpecies=genus+specificEpithet;
      

      
      myShepherd.beginDBTransaction();
      
        try {
         
          
          
          
         // File trainingFile = new File(shepherdDataDir.getAbsolutePath()+"/fluke_perceptron.input");
         // writer = new BufferedWriter(new FileWriter(trainingFile));
          
          //StringBuffer writeMe=new StringBuffer();
          
          
          //ArrayList<Encounter> encounters=myShepherd.getAllEncountersForSpeciesWithSpots(genus, specificEpithet);


            //this is a little slow in computation; so should try to cache it or something in the future...
            ArrayList<Encounter> encounters = Encounter.getEncountersForMatching(Util.taxonomyString(genus, specificEpithet), myShepherd);

/*
            NOTE:  this now makes the assumption that IBEIS IA has already been done on the same set of encounters with IBEISIA.startTrainingJobs()
            and has been completed -- which could in theory be tested with IBEISIA.waitForTrainingJobs() if desired

            the hard-coded "taskPrefix" value below is really only meant for testing stages to distinguish between multiple runs and would be
            eliminated in the future somehow.  basically we need a way to distinguish one training run from another since using *only* the encounter
            id would not be enough, as the results are stored in a table keyed by these.  (i.e. each run would have same id for each encounter)
*/


          int numEncs=encounters.size();
          System.out.println("Using a training set size: "+numEncs);
          
          Instances isTrainingSet = new Instances("Rel", getWekaAttributesPerSpecies(genusSpecies), (2*numEncs*(numEncs-1)/2));
          isTrainingSet.setClassIndex(getClassIndex(genusSpecies));
          
          ArrayList<WildbookInstance> list=new ArrayList<WildbookInstance>();
          
          
int testStart = 580;
int testLimit = 400;

int nonMatchMultiplier=3;
          
          
          
          
          //Iterate through matches first
          for(int i=0;i<(numEncs-1);i++){
            for(int j=(i+1);j<numEncs;j++){
              EncounterLite enc1=new EncounterLite((Encounter)encounters.get(i));
              EncounterLite enc2=new EncounterLite((Encounter)encounters.get(j));
              if((!enc1.getIndividualID().equals(""))&&(!enc2.getIndividualID().equals(""))&&(enc1.getIndividualID().equals(enc2.getIndividualID()))){
                try{
                  System.out.println("Learning match: "+enc1.getEncounterNumber()+" and "+enc2.getEncounterNumber());
                        
                        // add the instance
                        WildbookInstance iExample=buildInstance(genusSpecies,isTrainingSet);
                        String taskID = enc1.getEncounterNumber();
                        MatchObject mo=getMatchObject(genusSpecies,enc1, enc2, taskID, request, myShepherd);
                        populateInstanceValues(genusSpecies, iExample.getInstance(), enc1,enc2,mo,myShepherd);
                        iExample.setMatchObject(mo);
                        list.add(iExample);
                        System.out.println("     isTrainingSetSize: "+list.size());
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
                        
                        
                        //if(output==0){
                          numMatches++;
                        //}
                        /*
                        else{
                          numNonMatches+=2;
                          }
                          */
                        // add the instance
              }
              catch(Exception e){
                        e.printStackTrace();
              }
            }
          }
        }
          
          
          
          //Iterate through non-matches
          for(int i=0;i<(numEncs-1);i++){
            for(int j=(i+1);j<numEncs;j++){
              EncounterLite enc1=new EncounterLite((Encounter)encounters.get(i));
              EncounterLite enc2=new EncounterLite((Encounter)encounters.get(j));
              if((((enc1.getIndividualID().equals(""))&&(enc2.getIndividualID().equals("")))||(!enc1.getIndividualID().equals(enc2.getIndividualID())))&&(numNonMatches<(numMatches*nonMatchMultiplier))){
                try{
                  System.out.println("Learning match: "+enc1.getEncounterNumber()+" and "+enc2.getEncounterNumber());
                        
                        // add the instance
                        WildbookInstance iExample=buildInstance(genusSpecies,isTrainingSet);
                        String taskID = enc1.getEncounterNumber();
                        MatchObject mo=getMatchObject(genusSpecies,enc1, enc2, taskID, request, myShepherd);
                        populateInstanceValues(genusSpecies, iExample.getInstance(), enc1,enc2,mo,myShepherd);
                        iExample.setMatchObject(mo);
                        list.add(iExample);
                        System.out.println("     isTrainingSetSize: "+list.size());
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
                        
                        
                        //if(output==0){
                          numNonMatches++;
                        //}
                        /*
                        else{
                          numNonMatches+=2;
                          }
                          */
                        // add the instance
              }
              catch(Exception e){
                        e.printStackTrace();
              }
            }
          }
        }
          
          
          
          int listSize=list.size();
          //now we have to populate instance rank attributes so we can boost those
          Collections.sort(list,new RankComparator("intersection"));
          for(int i=0;i<listSize;i++){
            WildbookInstance wi=list.get(i);
            DenseInstance inst=wi.getInstance();
            inst.setValue(10, (i+1));
            System.out.println("intersection score: "+wi.getMatchObject().getIntersectionCount()+" and rank: "+(i+1));
          }
          Collections.sort(list,new RankComparator("fastDTW"));
          for(int i=0;i<listSize;i++){
            WildbookInstance wi=list.get(i);
            DenseInstance inst=wi.getInstance();
            inst.setValue(11, (i+1));
            System.out.println("FastDTW score: "+wi.getMatchObject().getLeftFastDTWResult()+" and rank: "+(i+1));
            
          }
          Collections.sort(list,new RankComparator("i3s"));
          for(int i=0;i<listSize;i++){
            WildbookInstance wi=list.get(i);
            DenseInstance inst=wi.getInstance();
            inst.setValue(12, (i+1));
            System.out.println("I3S score: "+wi.getMatchObject().getI3SMatchValue()+" and rank: "+(i+1));
            
          }
          Collections.sort(list,new RankComparator("proportion"));
          for(int i=0;i<listSize;i++){
            WildbookInstance wi=list.get(i);
            DenseInstance inst=wi.getInstance();
            inst.setValue(13, (i+1));
            System.out.println("prop. score: "+wi.getMatchObject().getProportionValue()+" and rank: "+(i+1));
            
          }
          Collections.sort(list,new RankComparator("MSM"));
          for(int i=0;i<listSize;i++){
            WildbookInstance wi=list.get(i);
            DenseInstance inst=wi.getInstance();
            inst.setValue(14, (i+1));
            System.out.println("MSM score: "+wi.getMatchObject().getMSMValue()+" and rank: "+(i+1));
            
          }
          Collections.sort(list,new RankComparator("swale"));
          for(int i=0;i<listSize;i++){
            WildbookInstance wi=list.get(i);
            DenseInstance inst=wi.getInstance();
            inst.setValue(15, (i+1));
            System.out.println("Swale score: "+wi.getMatchObject().getSwaleValue()+" and rank: "+(i+1));
            
          }
          Collections.sort(list,new RankComparator("euclidean"));
          for(int i=0;i<listSize;i++){
            WildbookInstance wi=list.get(i);
            DenseInstance inst=wi.getInstance();
            inst.setValue(16, (i+1));
            System.out.println("Euc. score: "+wi.getMatchObject().getEuclideanDistanceValue()+" and rank: "+(i+1));
            
          }
          
          
          
          
          //now add the fully populated examples
          
          for(int i=0;i<listSize;i++){
            isTrainingSet.add(list.get(i).getInstance());
            System.out.println(" Adding instance: "+i);
          }
          
          
          
          //ok, now we need to build a set if Instances that only have matches and then add an equal number of nonmatches
          Instances balancedInstances = new Instances("Rel", getWekaAttributesPerSpecies(genusSpecies), (numMatches));
          System.out.println("     isTrainingSet size is: "+isTrainingSet.numInstances());
          balancedInstances.setClassIndex(getClassIndex(genusSpecies));
          for(int i=0;i<isTrainingSet.numInstances();i++){
            
            Instance myInstance=isTrainingSet.instance(i);
            //if(myInstance.stringValue(getClassIndex(genusSpecies)).equals("match")){
              isTrainingSet.delete(i);
              balancedInstances.add(myInstance);
              //pop it off the original stack
              
              i--;
              //System.out.println("  Balanced match added!");
            //}
            
          }

          
          
          System.out.println("About to serialize with balancedInstances size: "+balancedInstances.numInstances());
          
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
        catch(Exception e){
            e.printStackTrace();
            return null;
        }
        finally{
          myShepherd.rollbackDBTransaction();
          myShepherd.closeDBTransaction();
        }
      
        }
 
    public static ArrayList getWekaAttributesPerSpecies(String genusSpecies){
      
      genusSpecies=genusSpecies.replaceAll(" ", "");
      
      ArrayList fvWekaAttributes = new ArrayList();
      
      //prep weka for AdaBoost
      // Declare numeric attributes
      Attribute intersectAttr = new Attribute("intersect");
      Attribute fastDTWAttr = new Attribute("fastDTW");
      Attribute i3sAttr = new Attribute("I3S");
      Attribute proportionAttr = new Attribute("proportion");
      Attribute msmAttr = new Attribute("MSM");
      Attribute swaleAttr = new Attribute("Swale");     
      Attribute dateAttr = new Attribute("dateDiffLong");   
      Attribute euclideanAttr = new Attribute("EuclideanDistance"); 
      Attribute patterningCodeDiffAttr = new Attribute("PatterningCodeDiff"); 
      Attribute ibeisColorAttr = new Attribute("ibeisColor"); 
      
      //ranks of matchers
      Attribute intersectRankAttr  = new Attribute("interectRank"); 
      Attribute fastDTWRankAttr = new Attribute("fastDTWRank");
      Attribute i3sRankAttr = new Attribute("I3SRank"); 
      Attribute proportionRankAttr = new Attribute("proportionRank"); 
      Attribute msmRankAttr = new Attribute("MSMRank");
      Attribute swaleRankAttr = new Attribute("SwaleRank"); 
      Attribute eucRankAttr = new Attribute("euclideanRank"); 
      
     
      // Declare the class attribute along with its values
      ArrayList fvClassVal = new ArrayList(2);
      fvClassVal.add("match");
      fvClassVal.add("nonmatch");
      Attribute ClassAttribute = new Attribute("theClass", fvClassVal);
      
      
      if(genusSpecies.equals("Physetermacrocephalus")){
        fvWekaAttributes = new ArrayList();
        fvWekaAttributes.add(intersectAttr);
        fvWekaAttributes.add(fastDTWAttr);
        fvWekaAttributes.add(i3sAttr);
        fvWekaAttributes.add(proportionAttr);
        fvWekaAttributes.add(msmAttr);
        fvWekaAttributes.add(swaleAttr);
        fvWekaAttributes.add(dateAttr);
        fvWekaAttributes.add(euclideanAttr);
        fvWekaAttributes.add(patterningCodeDiffAttr);
        fvWekaAttributes.add(ibeisColorAttr);
        
        
        fvWekaAttributes.add(intersectRankAttr);
        fvWekaAttributes.add(fastDTWRankAttr);
        fvWekaAttributes.add(i3sRankAttr);
        fvWekaAttributes.add(proportionRankAttr);
        fvWekaAttributes.add(msmRankAttr);
        fvWekaAttributes.add(swaleRankAttr);
        fvWekaAttributes.add(eucRankAttr);
        
        fvWekaAttributes.add(ClassAttribute);
        //System.out.println("Building attributes for: "+genusSpecies);
      }
      else if(genusSpecies.equals("Tursiopstruncatus")){
        fvWekaAttributes = new ArrayList();
        fvWekaAttributes.add(intersectAttr);
        fvWekaAttributes.add(fastDTWAttr);
        fvWekaAttributes.add(i3sAttr);
        fvWekaAttributes.add(proportionAttr);
        fvWekaAttributes.add(msmAttr);
        fvWekaAttributes.add(swaleAttr);
        fvWekaAttributes.add(dateAttr);
        fvWekaAttributes.add(euclideanAttr);
        fvWekaAttributes.add(patterningCodeDiffAttr);
        fvWekaAttributes.add(ibeisColorAttr);
        
        
        fvWekaAttributes.add(intersectRankAttr);
        fvWekaAttributes.add(fastDTWRankAttr);
        fvWekaAttributes.add(i3sRankAttr);
        fvWekaAttributes.add(proportionRankAttr);
        fvWekaAttributes.add(msmRankAttr);
        fvWekaAttributes.add(swaleRankAttr);
        fvWekaAttributes.add(eucRankAttr);
        
        fvWekaAttributes.add(ClassAttribute);
        //System.out.println("Building attributes for: "+genusSpecies);
      }
      else if(genusSpecies.equals("Megapteranovaeangliae")){
        fvWekaAttributes = new ArrayList();
        fvWekaAttributes.add(intersectAttr);
        fvWekaAttributes.add(fastDTWAttr);
        fvWekaAttributes.add(i3sAttr);
        fvWekaAttributes.add(proportionAttr);
        fvWekaAttributes.add(msmAttr);
        fvWekaAttributes.add(swaleAttr);
        fvWekaAttributes.add(dateAttr);
        fvWekaAttributes.add(euclideanAttr);
        fvWekaAttributes.add(patterningCodeDiffAttr);
        fvWekaAttributes.add(ibeisColorAttr);
        
        fvWekaAttributes.add(intersectRankAttr);
        fvWekaAttributes.add(fastDTWRankAttr);
        fvWekaAttributes.add(i3sRankAttr);
        fvWekaAttributes.add(proportionRankAttr);
        fvWekaAttributes.add(msmRankAttr);
        fvWekaAttributes.add(swaleRankAttr);
        fvWekaAttributes.add(eucRankAttr);
        
        
        
        fvWekaAttributes.add(ClassAttribute);
        System.out.println("Building attributes for: "+genusSpecies);
      }
      
      System.out.println("     fvWekaAttriubutes has a size of: "+fvWekaAttributes.size());
      return fvWekaAttributes;
      
      
    }
    
    
    public static WildbookInstance buildInstance(String genusSpecies,Instances isTrainingSet){
      System.out.println("Building an instance...");
      //concat genus and species to a single string of characters with no spaces
      genusSpecies=genusSpecies.replace(" ", "");
      // Create the instance
      WildbookInstance instance = null;
      
     ArrayList fvWekaAttributes = getWekaAttributesPerSpecies(genusSpecies);

     //return our result
     DenseInstance di=new DenseInstance(fvWekaAttributes.size());
     di.setDataset(isTrainingSet);
      instance=new WildbookInstance(di);
      System.out.println("Returning an instance...");
      return instance;
      
    }
    
    
    public static void populateInstanceValues(String genusSpecies, Instance iExample, EncounterLite enc1, EncounterLite enc2, MatchObject mo, Shepherd myShepherd){
      

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
      
      
      

      
      
      // Create the instance
      ArrayList fvWekaAttributes=getWekaAttributesPerSpecies(genusSpecies);
      //System.out.println("!!!!!!fvvvvv: "+fvWekaAttributes.size());
      
      
      
      if(genusSpecies.equals("Physetermacrocephalus")){
          iExample.setValue(0, mo.getIntersectionCount().doubleValue());
          iExample.setValue(1, mo.getLeftFastDTWResult().doubleValue());
          iExample.setValue(2, mo.getI3SMatchValue());
          iExample.setValue(3, mo.getProportionValue().doubleValue());
          iExample.setValue(4, mo.getMSMValue().doubleValue());
          iExample.setValue(5, mo.getSwaleValue().doubleValue());
          iExample.setValue(6, mo.getDateDiff().doubleValue());
          iExample.setValue(7, mo.getEuclideanDistanceValue().doubleValue());
          iExample.setValue(8, mo.getPatterningCodeDiff().doubleValue());
          iExample.setValue(9, mo.getIBEISColorValue().doubleValue());
      }
      else if(genusSpecies.equals("Tursiopstruncatus")){
        iExample.setValue(0, mo.getIntersectionCount().doubleValue());
        iExample.setValue(1, mo.getLeftFastDTWResult().doubleValue());
        iExample.setValue(2, mo.getI3SMatchValue());
        iExample.setValue(3, mo.getProportionValue().doubleValue());
        iExample.setValue(4, mo.getMSMValue().doubleValue());
        iExample.setValue(5, mo.getSwaleValue().doubleValue());
        iExample.setValue(6, mo.getDateDiff().doubleValue());
        iExample.setValue(7, mo.getEuclideanDistanceValue().doubleValue());
        iExample.setValue(8, mo.getPatterningCodeDiff().doubleValue());
        iExample.setValue(9, mo.getIBEISColorValue().doubleValue());
    }
    else if(genusSpecies.equals("Megapteranovaeangliae")){
        iExample.setValue(0, mo.getIntersectionCount().doubleValue());
        iExample.setValue(1, mo.getLeftFastDTWResult().doubleValue());
        iExample.setValue(2, mo.getI3SMatchValue());
        iExample.setValue(3, mo.getProportionValue().doubleValue());
        iExample.setValue(4, mo.getMSMValue().doubleValue());
        iExample.setValue(5, mo.getSwaleValue().doubleValue());
        iExample.setValue(6, mo.getDateDiff().doubleValue());
        iExample.setValue(7, mo.getEuclideanDistanceValue().doubleValue());
        iExample.setValue(8, mo.getPatterningCodeDiff().doubleValue());
        iExample.setValue(9, mo.getIBEISColorValue());

        
    }
      
      //sometimes we don't want to populate this, such as for new match attempts
      if(enc1.getIndividualID()!=null){
        if(output==0){
          iExample.setValue(getClassIndex(genusSpecies), "match");
          
        }
        else{
          iExample.setValue(getClassIndex(genusSpecies), "nonmatch");
          
        }
      }
      
      
    }
    
    public static int getClassIndex(String genusSpecies){
      ArrayList attrs=getWekaAttributesPerSpecies(genusSpecies);
      //System.out.println("Num attributes: "+attrs.size());
      
      int numAttrs=attrs.size();
      for(int i=0;i<numAttrs;i++){
        //System.out.println("Name: "+((Attribute)attrs.elementAt(i)).name());
        if(((Attribute)attrs.get(i)).name().equals("theClass")){
          //System.out.println("class index at: "+i);
          return i;
        }
      }
      return -1;
    }
    
    
    public static MatchObject getMatchObject(String genusSpecies,EncounterLite el1, EncounterLite el2, String taskID, HttpServletRequest request, Shepherd myShepherd){
        

      
      MatchObject mo=new MatchObject();
        
        
        System.out.println("Starting TrainNetwork.getMatchObject");
        
        //FIRST PASS
        
        //HolmbergIntersection
        Double numIntersections=EncounterLite.getHolmbergIntersectionScore(el1, el2 );
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
        double i3sScore=weka.core.Utils.missingValue();
        if((newDScore!=null)&&(newDScore.getI3SMatchValue()!=java.lang.Double.MAX_VALUE)){i3sScore=newDScore.getI3SMatchValue();}
        
        //Proportion metric
        Double proportion=EncounterLite.getFlukeProportion(el1,el2);
        
        
        
        
        Double msm=MSM.getMSMDistance(el1, el2);
        
        //Swale setup - default is flukes for Physeter macrocephalus
        double penalty=0;
        double reward=25;
        double epsilon=0.002089121713611485;
        if(EncounterLite.isDorsalFin(el1)){
          reward=50.0;
          epsilon=0.0006;
          penalty=0;

          
        }
        
        Double swaleVal=EncounterLite.getSwaleMatchScore(el1, el2, penalty, reward, epsilon);
        
        double date = weka.core.Utils.missingValue();
        if((el1.getDateLong()!=null)&&(el2.getDateLong()!=null)){
          try{
            date=Math.abs((new Long(el1.getDateLong()-el2.getDateLong())).doubleValue());
          }
          catch(Exception e){
            e.printStackTrace();
          }
        }
        
        //Euclidean distance
        Double eucVal=EncounterLite.getEuclideanDistanceScore(el1, el2);
        
        double pattCodeDiff = weka.core.Utils.missingValue();
        if((el1.getPatterningCode()!=null)&&(el2.getPatterningCode()!=null)){
          String enc1Val=el1.getPatterningCode().replaceAll("[^\\d.]", "");
          String enc2Val=el2.getPatterningCode().replaceAll("[^\\d.]", "");
          
          //at this point, we should have just numbers in the String
          try{
            double enc1code=(new Double(enc1Val)).doubleValue();
            double enc2code=(new Double(enc2Val)).doubleValue();
            pattCodeDiff=Math.abs(enc1code-enc2code);
            //System.out.println("Found a patterning code difference of: "+pattCodeDiff);
          }
          catch(Exception diffe){
            System.out.println("Found a potentially non-numeric-able patterning code on Encounter "+el1.getEncounterNumber()+" of "+el1.getPatterningCode());
            System.out.println("Found a potentially non-numeric-able patterning code on Encounter "+el2.getEncounterNumber()+" of "+el2.getPatterningCode());
            
            diffe.printStackTrace();
          }
          
        }
        
        
        //let's get the IBEIS value ===================================================
        String taskPrefix = "T6-";   //see previous note about this
        HashMap<String,Object> res = IBEISIA.getTaskResultsAsHashMap(taskPrefix + taskID, myShepherd);
        
        System.out.println("I HAVE an IBEIS results object: "+res.toString());

        //see longer note about ibeis training previously, but now we dont do concurrent IA matching, so we assume by the time we get here
        // all the results have been run -- and if we *do not* have one (or do not have success) then it is just tough luck. (something failed)
        if ((res.get("success") != null) && (Boolean)res.get("success") && (res.get("results") != null)) {
          
          System.out.println("[" + taskPrefix + taskID + "] I HAVE an IBEIS results object and it claims success!");
          
          HashMap<String,Object> rout=(HashMap<String,Object>)res.get("results");
          if(rout.get(el1.getEncounterNumber())!=null){
            HashMap<String,Double> thisResult=(HashMap<String,Double>)rout.get(el1.getEncounterNumber());
            if(thisResult.get(el2.getEncounterNumber())!=null){
              Double score=(Double)thisResult.get(el2.getEncounterNumber());
              mo.setIBEISColorValue(score);
              System.out.println("Setting IBEIS COLOR SCORE of: "+score);
            }
            else{
              System.out.println(" IBEIS COLOR el2 object was null "+el2.getEncounterNumber() + " so setting to 0.0.");
              //basic not-a-match return value for when IBEIS runs the match but it's clearly not a match
              mo.setIBEISColorValue(0.0);
            }
          }
          else{System.out.println(" IBEIS COLOR el1 object was null: "+el1.getEncounterNumber());}

        } else {
            System.out.println("[" + taskPrefix + taskID + "] IBEIS COLOR results object was null or got non-success.");
        }

        if(mo.getIBEISColorValue()==null){mo.setIBEISColorValue(weka.core.Utils.missingValue());}
        
        
        mo.setIntersectionCount(numIntersections.doubleValue());
        mo.setI3SValues(new Vector(), i3sScore);
        mo.setLeftFastDTWResult(distance);
        mo.setMSMSValue(msm);
        mo.setSwaleValue(swaleVal);
        mo.setDateDiff(date);
        mo.setEuclideanDistanceValue(eucVal);
        mo.setPatterningCodeDiffValue(pattCodeDiff);
        mo.setProportionValue(proportion);
        
        return mo;
    }
    
    
    
    
}
	
	
