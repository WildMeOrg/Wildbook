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

import javax.servlet.http.HttpServletRequest;

import org.apache.commons.math.stat.descriptive.SummaryStatistics;

import java.io.*;

import org.ecocean.grid.*;

import java.util.Vector;
import java.util.Random;

//import com.fastdtw.timeseries.TimeSeriesBase.*;
import com.fastdtw.dtw.*;
//import com.fastdtw.util.Distances;
//import com.fastdtw.timeseries.TimeSeriesBase.Builder;
//import com.fastdtw.timeseries.*;

import org.ecocean.grid.msm.*;

//train weka
import weka.core.Attribute;
import weka.core.FastVector;
import weka.core.Instances;
import weka.core.Instance;
import weka.core.SerializationHelper;
import weka.classifiers.meta.AdaBoostM1;
import weka.classifiers.bayes.BayesNet;
import weka.classifiers.Evaluation;
import weka.classifiers.Classifier;
import weka.core.converters.ArffSaver;
import weka.core.converters.ArffLoader;

import java.util.ArrayList;

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
      
      
    
    //create text file so we can also use this training data in the Neuroph UI
      BufferedWriter writer = null;
      
      int numMatches=0;
      int numNonMatches=0;
      String genusSpecies=genus+specificEpithet;
      

      
      myShepherd.beginDBTransaction();
      
        try {
         
          
          
          
         // File trainingFile = new File(shepherdDataDir.getAbsolutePath()+"/fluke_perceptron.input");
         // writer = new BufferedWriter(new FileWriter(trainingFile));
          
          //StringBuffer writeMe=new StringBuffer();
          
          
          ArrayList<Encounter> encounters=myShepherd.getAllEncountersForSpeciesWithSpots(genus, specificEpithet);
          int numEncs=encounters.size();
          System.out.println("Using a training set size: "+numEncs);
          
          Instances isTrainingSet = new Instances("Rel", getWekaAttributesPerSpecies(genusSpecies), (2*numEncs*(numEncs-1)/2));
          isTrainingSet.setClassIndex(getClassIndex(genusSpecies));
          
          
          //RESTORE ME
          for(int i=0;i<(numEncs-1);i++){
           // for(int i=0;i<10;i++){
            for(int j=(i+1);j<numEncs;j++){
              
              EncounterLite enc1=new EncounterLite((Encounter)encounters.get(i));
              EncounterLite enc2=new EncounterLite((Encounter)encounters.get(j));
              
             try{
                        System.out.println("Learning: "+enc1.getEncounterNumber()+" and "+enc2.getEncounterNumber());
                        
                        //if both have spots, then we need to compare them
                     
                        // add the instance
                        Instance iExample=buildInstance(genusSpecies,isTrainingSet);
                        Instance iExample2=buildInstance(genusSpecies,isTrainingSet);
                        
                        MatchObject mo=getMatchObject(genusSpecies,enc1, enc2);
                        populateInstanceValues(genusSpecies, iExample, enc1,enc2,mo,myShepherd);
                        populateInstanceValues(genusSpecies, iExample2, enc2,enc1,mo,myShepherd);
                        
                        isTrainingSet.add(iExample);
                        isTrainingSet.add(iExample2);
                        System.out.println("     isTrainingSetSize: "+isTrainingSet.numInstances());
                  
                    
                        
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
                        if(output==0){
                          numMatches+=2;
                        }
                        else{
                          numNonMatches+=2;
                          }
                        // add the instance
                        
                        
                        
                        
             }
              catch(Exception e){
                        e.printStackTrace();
                      }
        
                    
                      
             
              }
            }
          
          //ok, now we need to build a set if Instances that only have matches and then add an equal number of nonmatches
          Instances balancedInstances = new Instances("Rel", getWekaAttributesPerSpecies(genusSpecies), (numMatches*2));
          System.out.println("     isTrainingSet size is: "+isTrainingSet.numInstances());
          balancedInstances.setClassIndex(getClassIndex(genusSpecies));
          for(int i=0;i<isTrainingSet.numInstances();i++){
            
            Instance myInstance=isTrainingSet.instance(i);
            if(myInstance.stringValue(getClassIndex(genusSpecies)).equals("match")){
              isTrainingSet.delete(i);
              balancedInstances.add(myInstance);
              //pop it off the original stack
              
              i--;
              //System.out.println("  Balanced match added!");
            }
            
          }
          //now get a number of false instances to test with
          
          int sampledFalseInstances=0;
          //let's use the golden proportion and have 1.61 more false matches to train with than matches
          while(sampledFalseInstances<(numMatches*3.22)){
            Random myRan=new Random();
            int selected=myRan.nextInt(isTrainingSet.numInstances()-1);
            Instance popMe=isTrainingSet.instance(selected);
            if(popMe.stringValue(getClassIndex(genusSpecies)).equals("nonmatch")){
              isTrainingSet.delete(selected);
              balancedInstances.add(popMe);
              sampledFalseInstances++;
            }
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
          catch(Exception e){return null;}
        finally{
          myShepherd.rollbackDBTransaction();
          myShepherd.closeDBTransaction();
        }
      
        }
 
    public static FastVector getWekaAttributesPerSpecies(String genusSpecies){
      
      genusSpecies=genusSpecies.replaceAll(" ", "");
      
      FastVector fvWekaAttributes = new FastVector();
      
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
     
      // Declare the class attribute along with its values
      FastVector fvClassVal = new FastVector(2);
      fvClassVal.addElement("match");
      fvClassVal.addElement("nonmatch");
      Attribute ClassAttribute = new Attribute("theClass", fvClassVal);
      
      
      if(genusSpecies.equals("Physetermacrocephalus")){
        fvWekaAttributes = new FastVector(10);
        fvWekaAttributes.addElement(intersectAttr);
        fvWekaAttributes.addElement(fastDTWAttr);
        fvWekaAttributes.addElement(i3sAttr);
        fvWekaAttributes.addElement(proportionAttr);
        fvWekaAttributes.addElement(msmAttr);
        fvWekaAttributes.addElement(swaleAttr);
        fvWekaAttributes.addElement(dateAttr);
        fvWekaAttributes.addElement(euclideanAttr);
        fvWekaAttributes.addElement(patterningCodeDiffAttr);
        fvWekaAttributes.addElement(ClassAttribute);
        //System.out.println("Building attributes for: "+genusSpecies);
      }
      else if(genusSpecies.equals("Tursiopstruncatus")){
        fvWekaAttributes = new FastVector(10);
        fvWekaAttributes.addElement(intersectAttr);
        fvWekaAttributes.addElement(fastDTWAttr);
        fvWekaAttributes.addElement(i3sAttr);
        fvWekaAttributes.addElement(proportionAttr);
        fvWekaAttributes.addElement(msmAttr);
        fvWekaAttributes.addElement(swaleAttr);
        fvWekaAttributes.addElement(dateAttr);
        fvWekaAttributes.addElement(euclideanAttr);
        fvWekaAttributes.addElement(patterningCodeDiffAttr);
        fvWekaAttributes.addElement(ClassAttribute);
        //System.out.println("Building attributes for: "+genusSpecies);
      }
      else if(genusSpecies.equals("Megapteranovaeangliae")){
        fvWekaAttributes = new FastVector(10);
        fvWekaAttributes.addElement(intersectAttr);
        fvWekaAttributes.addElement(fastDTWAttr);
        fvWekaAttributes.addElement(i3sAttr);
        fvWekaAttributes.addElement(proportionAttr);
        fvWekaAttributes.addElement(msmAttr);
        fvWekaAttributes.addElement(swaleAttr);
        fvWekaAttributes.addElement(dateAttr);
        fvWekaAttributes.addElement(euclideanAttr);
        fvWekaAttributes.addElement(patterningCodeDiffAttr);
        fvWekaAttributes.addElement(ClassAttribute);
        //System.out.println("Building attributes for: "+genusSpecies);
      }
      
      //System.out.println("     fvWekaAttriubutes has a size of: "+fvWekaAttributes.size());
      return fvWekaAttributes;
      
      
    }
    
    
    public static Instance buildInstance(String genusSpecies,Instances isTrainingSet){
      
      //concat genus and species to a single string of characters with no spaces
      genusSpecies=genusSpecies.replace(" ", "");
      // Create the instance
      Instance instance = null;
      
     FastVector fvWekaAttributes = getWekaAttributesPerSpecies(genusSpecies);

     //return our result
      instance=new Instance(fvWekaAttributes.size());
      instance.setDataset(isTrainingSet);
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
      FastVector fvWekaAttributes=getWekaAttributesPerSpecies(genusSpecies);
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
      FastVector attrs=getWekaAttributesPerSpecies(genusSpecies);
      //System.out.println("Num attributes: "+attrs.size());
      
      int numAttrs=attrs.size();
      for(int i=0;i<numAttrs;i++){
        //System.out.println("Name: "+((Attribute)attrs.elementAt(i)).name());
        if(((Attribute)attrs.elementAt(i)).name().equals("theClass")){
          //System.out.println("class index at: "+i);
          return i;
        }
      }
      return -1;
    }
    
    
    public static MatchObject getMatchObject(String genusSpecies,EncounterLite el1, EncounterLite el2){
        MatchObject mo=new MatchObject();
        
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
        double i3sScore=Instance.missingValue();
        if((newDScore!=null)&&(newDScore.getI3SMatchValue()!=java.lang.Double.MAX_VALUE)){i3sScore=newDScore.getI3SMatchValue();}
        
        //Proportion metric
        Double proportion=EncounterLite.getFlukeProportion(el1,el2);
        
        
        
        
        Double msm=MSM.getMSMDistance(el1, el2);
        
        //swale setup - default is flukes for Physeter macrocephalus
        double penalty=0;
        double reward=25;
        double epsilon=0.002089121713611485;
        if(EncounterLite.isDorsalFin(el1)){
          reward=50.0;
          epsilon=0.0006;
          penalty=0;

          
        }
        
        Double swaleVal=EncounterLite.getSwaleMatchScore(el1, el2, penalty, reward, epsilon);
        
        double date = Instance.missingValue();
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
        
        double pattCodeDiff = Instance.missingValue();
        if((el1.getPatterningCode()!=null)&&(el2.getPatterningCode()!=null)){
          String enc1Val=el1.getPatterningCode().replaceAll("[^\\d.]", "");
          String enc2Val=el2.getPatterningCode().replaceAll("[^\\d.]", "");
          
          //at this point, we should have just numbers in the String
          try{
            double enc1code=(new Double(enc1Val)).doubleValue();
            double enc2code=(new Double(enc2Val)).doubleValue();
            pattCodeDiff=Math.abs(enc1code-enc2code);
            System.out.println("Found a patterning code difference of: "+pattCodeDiff);
          }
          catch(Exception diffe){
            System.out.println("Found a potentially non-numeric-able patterning code on Encounter "+el1.getEncounterNumber()+" of "+el1.getPatterningCode());
            System.out.println("Found a potentially non-numeric-able patterning code on Encounter "+el2.getEncounterNumber()+" of "+el2.getPatterningCode());
            
            diffe.printStackTrace();
          }
          
        }
        
        
        
        
        mo.setIntersectionCount(numIntersections.doubleValue());
        mo.setI3SValues(new Vector(), i3sScore);
        mo.setLeftFastDTWResult(distance);
        mo.setMSMSValue(msm);
        mo.setSwaleValue(swaleVal);
        mo.setDateDiff(date);
        mo.setEuclideanDistanceValue(eucVal);
        mo.setPatterningCodeDiffValue(pattCodeDiff);
        
        return mo;
    }
    
    
}
	
	
