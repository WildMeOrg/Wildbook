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

package org.ecocean.servlet;

import com.reijns.I3S.Pair;
import java.util.Collections;
import org.ecocean.*;
import org.ecocean.grid.*;
import org.ecocean.neural.*;
import org.apache.commons.math.stat.descriptive.SummaryStatistics;
import java.util.Comparator;
import javax.servlet.ServletConfig;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.util.Comparator;
import java.io.*;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Properties;
import java.util.Vector;
import java.util.concurrent.ThreadPoolExecutor;

import com.google.gson.*;

//train weka
import weka.core.Attribute;
import weka.core.FastVector;
import weka.core.Instances;
import weka.core.Instance;
import weka.classifiers.meta.AdaBoostM1;
import weka.classifiers.Evaluation;
import weka.classifiers.bayes.BayesNet;


public class WriteOutFlukeMatchingJSON extends HttpServlet {


  public void init(ServletConfig config) throws ServletException {
    super.init(config);
  }

  public void doGet(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
    doPost(request, response);
  }

  public void doPost(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {

    //set up a shepherd for DB transactions
    
    String context="context0";
    context=ServletUtilities.getContext(request);
    
    Shepherd myShepherd = new Shepherd(context);
    PrintWriter out = null;
    GridManager gm = GridManagerFactory.getGridManager();

    //if ((!request.getParameter("number").equals("TuningTask")) && (!request.getParameter("number").equals("FalseMatchTask"))) {

      //double cutoff = 2;
      String statusText = "success";
      System.out.println("writeOutFlukeMatchingJSON: I am starting up.");


      myShepherd.beginDBTransaction();
      try {

        ScanTask st2 = myShepherd.getScanTask(request.getParameter("number"));
        st2.setFinished(true);
        long time = System.currentTimeMillis();
        st2.setEndTime(time);

        //let's check the checked-in value

       // boolean successfulWrite = false;
        //boolean successfulI3SWrite = false;

        System.out.println("Now setting this scanTask as finished!");
        String taskID = st2.getUniqueNumber();

        //change
        String encNumber = request.getParameter("number").replaceAll("scanL", "").replaceAll("scanR", "");

        String newEncDate = "";
        String newEncShark = "";
        String newEncSize = "";

        Encounter newEnc = myShepherd.getEncounter(encNumber);
        //newEncDate = newEnc.getDate();
        newEncShark = newEnc.isAssignedToMarkedIndividual();
        //if(newEnc.getSizeAsDouble()!=null){newEncSize = newEnc.getSize() + " meters";}

        //MatchObject[] res = new MatchObject[0];

        ArrayList<MatchObject> res = gm.getMatchObjectsForTask(taskID);


        boolean righty = false;
        if (taskID.startsWith("scanR")) {
          righty = true;
        }

        //successfulWrite = writeResult(res, encNumber, CommonConfiguration.getR(context), CommonConfiguration.getEpsilon(context), CommonConfiguration.getSizelim(context), CommonConfiguration.getMaxTriangleRotation(context), CommonConfiguration.getC(context), newEncDate, newEncShark, newEncSize, righty, cutoff, myShepherd,context);

        boolean successfulWrite = writeResult(res, encNumber,  newEncDate, newEncShark, myShepherd, context, request);

        

        myShepherd.commitDBTransaction();
        

        //let's cleanup after a successful commit
        ThreadPoolExecutor es = SharkGridThreadExecutorService.getExecutorService();
        es.execute(new ScanTaskCleanupThread(request.getParameter("number")));

       
        statusText = "success";
        


      } 
      catch (Exception e) {
        myShepherd.rollbackDBTransaction();
        System.out.println("scanResultsServlet registered the following error...");
        e.printStackTrace();
        statusText = "failure";
      }
      finally{
        response.setContentType("text/plain");
        out = response.getWriter();
        out.println(statusText);
        out.close();
        myShepherd.closeDBTransaction();
      }

  }

  public boolean writeResult(ArrayList<MatchObject> swirs, String num,  String newEncDate, String newEncIndividualID, Shepherd myShepherd, String context, HttpServletRequest request) {

    System.out.println("     ...Starting writeResult method.");

    
  //setup data dir
    String rootWebappPath = getServletContext().getRealPath("/");
    File webappsDir = new File(rootWebappPath).getParentFile();
    File shepherdDataDir = new File(webappsDir, CommonConfiguration.getDataDirectoryName(context));
    File encountersDir=new File(shepherdDataDir.getAbsolutePath()+"/encounters");
    if(!encountersDir.exists())encountersDir.mkdirs();
    File encounterDir=new File(encountersDir,num);
    if(!encounterDir.exists())encounterDir.mkdirs();
    File file = new File(Encounter.dir(shepherdDataDir, num) + "/flukeMatching.json");
    System.out.println("     ...target JSON file for output is: "+file.getAbsolutePath());

    
    
    
    FileWriter mywriter=null;
    try {
      
      
      //get the Encounter and genus and species
      myShepherd.beginDBTransaction();
      Encounter gsEnc=myShepherd.getEncounter(num);
      String tempGenusSpecies="undefined";
      if((gsEnc.getGenus()!=null)&&(!gsEnc.getGenus().trim().equals(""))&&(gsEnc.getSpecificEpithet()!=null)&&(!gsEnc.getSpecificEpithet().trim().equals(""))){
        tempGenusSpecies=gsEnc.getGenus()+gsEnc.getSpecificEpithet();
      }
      final String genusSpecies=tempGenusSpecies;
      String pathToClassifierFile=TrainNetwork.getAbsolutePathToClassifier(genusSpecies,request);
      String instancesFileFullPath=TrainNetwork.getAbsolutePathToInstances(genusSpecies, request);
      
      System.out.println("     I expect to find a classifier file here: "+pathToClassifierFile);
      System.out.println("     I expect to find an instances file here: "+instancesFileFullPath);
      
      //Instances instances=GridManager.getAdaboostInstances(request, instancesFileFullPath);
      final Instances instances=TrainNetwork.getAdaboostInstances(request, instancesFileFullPath);
      final AdaBoostM1 booster=TrainNetwork.getAdaBoostClassifier(request, pathToClassifierFile, instances);
      //String optionString = "-P 100 -S 1 -I 10 -W weka.classifiers.trees.RandomForest -- -I 100 -K 0 -S 1";
      //booster.setOptions(weka.core.Utils.splitOptions(optionString));
      

      
      
      System.out.println("     ...Prepping to write matching JSON file for encounter "+num+" after loading "+instances.numInstances()+" training instances");

      //now setup the XML write for the encounter
      
      //MatchObject[] matches = swirs;

      //Arrays.sort(swirs, new FlukeMatchComparator(request,booster,bayesBooster,instances));
      Collections.sort(swirs, new Comparator<MatchObject>(){

        public int compare(MatchObject a1, MatchObject b1)
        {
          double a1_adjustedValue=0;
          double b1_adjustedValue=0;

            
            Instance a1Example = new Instance(TrainNetwork.getWekaAttributesPerSpecies(genusSpecies).size()-1);
            Instance b1Example = new Instance(TrainNetwork.getWekaAttributesPerSpecies(genusSpecies).size()-1);
            
              a1Example.setDataset(instances);
              a1Example.setValue(0, a1.getIntersectionCount());
              a1Example.setValue(1, a1.getLeftFastDTWResult().doubleValue());
              a1Example.setValue(2,  a1.getI3SMatchValue());
              a1Example.setValue(3, (new Double(a1.getProportionValue()).doubleValue()));
              a1Example.setValue(4, (new Double(a1.getMSMValue()).doubleValue()));
              a1Example.setValue(5, (new Double(a1.getSwaleValue()).doubleValue()));
              a1Example.setValue(6, (new Double(a1.getDateDiff()).doubleValue()));
              
              
              b1Example.setDataset(instances);
              b1Example.setValue(0, b1.getIntersectionCount());
              b1Example.setValue(1, b1.getLeftFastDTWResult().doubleValue());
              b1Example.setValue(2,  b1.getI3SMatchValue());
              b1Example.setValue(3, (new Double(b1.getProportionValue()).doubleValue()));
              b1Example.setValue(4, (new Double(b1.getMSMValue()).doubleValue()));
              b1Example.setValue(5, (new Double(b1.getSwaleValue()).doubleValue()));
              b1Example.setValue(6, (new Double(b1.getDateDiff()).doubleValue()));
            
              
              
              try{
                a1_adjustedValue=booster.distributionForInstance(a1Example)[0];
               
                b1_adjustedValue=booster.distributionForInstance(b1Example)[0];
              }
              catch(Exception e){e.printStackTrace();}
            
              System.out.println("     COMPARING: "+a1_adjustedValue+ " to "+b1_adjustedValue);
            
            if(a1_adjustedValue > b1_adjustedValue){return -1;}
            else if(a1_adjustedValue < b1_adjustedValue){return 1;}
            else{return 0;}
        }
      });
      
      int resultsSize = swirs.size();
      
      
      System.out.println("     ...Results sorted.");

      
      StringBuffer resultsJSON = new StringBuffer();
      
      

      
      //build our JSON with GSON
      Gson gson = new GsonBuilder().create();
      StringBuffer jsonOut=new StringBuffer("[\n");
      
      //overarching array
      JsonArray wrapperArray =new JsonArray();
      
      
          
       String[] header= {"individualID", "encounterID", "rank","adaboost_match","overall_score", "score_holmbergIntersection", "score_fastDTW", "score_I3S", "score_proportion", "msm","swale"};
       jsonOut.append(gson.toJson(header)+",\n");
       
       
       
      int numMatches=swirs.size();
      for (int i = 0; i < numMatches; i++) {
        MatchObject mo = swirs.get(i);
        Encounter enc = myShepherd.getEncounter(mo.getEncounterNumber());
        //System.out.println("           Writing out result for: "+mo.getEncounterNumber());
        
        //resultarray
        JsonArray result=new JsonArray();
        //add individualID
        String individualID="";
        if(enc.getIndividualID()!=null){individualID=enc.getIndividualID();}
        result.add(new JsonPrimitive(individualID));
        result.add(new JsonPrimitive(enc.getCatalogNumber()));
        
        //overall score - std dev method
        //double thisScore=TrainNetwork.getOverallFlukeMatchScore(request, mo.getIntersectionCount(), mo.getLeftFastDTWResult().doubleValue(), mo.getI3SMatchValue(), new Double(mo.getProportionValue()),intersectionStats,dtwStats,i3sStats, proportionStats, intersectionStdDev,dtwStdDev,i3sStdDev,proportionStdDev,intersectHandicap, dtwHandicap,i3sHandicap,proportionHandicap);
        
        //adaboost classifier
      //prep weka for AdaBoost
       
        
        
        
        Instance iExample = new Instance(TrainNetwork.getWekaAttributesPerSpecies(genusSpecies).size());
       iExample.setDataset(instances);
        TrainNetwork.populateInstanceValues(genusSpecies, iExample, new EncounterLite(),new EncounterLite(),mo);
        
        
        double[] fDistribution = booster.distributionForInstance(iExample);
        System.out.println("    fDistribution score: "+fDistribution[0]);
        //individual scores
        result.add(new JsonPrimitive(i+1));
        result.add(new JsonPrimitive(fDistribution[0]));
        result.add(new JsonPrimitive(0));
        
        if(mo.getIntersectionCount()!=null){
          result.add(new JsonPrimitive(mo.getIntersectionCount()));
        }
        else{
          result.add(new JsonPrimitive(""));
        }
        
        
        if(mo.getLeftFastDTWResult()!=null){
          result.add(new JsonPrimitive(mo.getLeftFastDTWResult().intValue()));
        }
        else{
          result.add(new JsonPrimitive(""));
        }
        
        if(mo.getI3SMatchValue()!=Double.MAX_VALUE){
          result.add(new JsonPrimitive(mo.getI3SMatchValue()));
        }
        else{
          result.add(new JsonPrimitive(""));
        }
        
        if(mo.getProportionValue()!=null){
          result.add(new JsonPrimitive(mo.getProportionValue()));
        }
        else{
          result.add(new JsonPrimitive(""));
        }
        
      if(mo.getMSMValue()!=null){ 
        result.add(new JsonPrimitive(TrainNetwork.round(mo.getMSMValue().doubleValue(),3)));
      }
      else{
        result.add(new JsonPrimitive(""));
      }
      if(mo.getSwaleValue()!=null){  
        result.add(new JsonPrimitive(TrainNetwork.round(mo.getSwaleValue().doubleValue(),3)));
      }
      else{
        result.add(new JsonPrimitive(""));
      }
        
        //result.add(new JsonPrimitive(fDistribution[1]));
        
        jsonOut.append(gson.toJson(result)+",\n");
         
        
      }
      
      jsonOut.append("\n]");
      
      System.out.println("     ...JSON created and preparing to write output file:" +file.getAbsolutePath());

      
      mywriter = new FileWriter(file);
      //org.dom4j.io.OutputFormat format = org.dom4j.io.OutputFormat.createPrettyPrint();
      //format.setLineSeparator(System.getProperty("line.separator"));
      //org.dom4j.io.XMLWriter writer = new org.dom4j.io.XMLWriter(mywriter, format);
      
      //System.out.println("Trying to write out JSON: "+gson.toString());
      mywriter.write(jsonOut.toString());
     
      System.out.println("     Successful WriteOutFlukeMatchingJSON write.");
      return true;
    } 
    catch (Exception e) {
      e.printStackTrace();
      return false;
    }
    finally{
      if(mywriter!=null){
        try{
          mywriter.close();
          mywriter=null;
        }
        catch(Exception e){e.printStackTrace();}
      }
      
    }
  } //end writeResult method

  

  public ScanWorkItem getWI4MO(ScanWorkItemResult swir, ArrayList<ScanWorkItem> list) {

    //System.out.println("I'm looking for: "+swir.getUniqueNumberWorkItem());

    ScanWorkItem swi = new ScanWorkItem();
    for (int i = 0; i < list.size(); i++) {
      if (list.get(i).getUniqueNumber().equals(swir.getUniqueNumberWorkItem())) {
        return list.get(i);
      }
    }
    return swi;

  }

 

}
