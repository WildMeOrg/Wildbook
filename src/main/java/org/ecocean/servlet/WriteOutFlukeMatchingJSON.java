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


import java.util.Collections;
import org.ecocean.*;
import org.ecocean.grid.*;
import org.ecocean.neural.*;
import org.ecocean.identity.*;
import java.util.Comparator;
import javax.servlet.ServletConfig;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.*;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.concurrent.ThreadPoolExecutor;
import com.google.gson.*;

//train weka
import weka.core.Attribute;
import weka.core.Instances;
import weka.core.DenseInstance;
import weka.core.Instance;
import weka.classifiers.Evaluation;
import weka.classifiers.Classifier;
import weka.classifiers.bayes.BayesNet;
import org.ecocean.neural.WildbookInstance;



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
        if(newEnc.getIndividualID()!=null){
          newEncShark = newEnc.getIndividualID();
        }
        //if(newEnc.getSizeAsDouble()!=null){newEncSize = newEnc.getSize() + " meters";}

        //MatchObject[] res = new MatchObject[0];

        ArrayList<MatchObject> res = gm.getMatchObjectsForTask(taskID);


        //this will wait for the results from IBEISIA -- but usually(?) should never have to since all the other algorithms take longer
        ArrayList<String> tid = new ArrayList<String>();
        tid.add(taskID);
        IBEISIA.waitForTrainingJobs(tid, myShepherd);

        HashMap<String,Object> ires = IBEISIA.getTaskResultsAsHashMap(taskID, myShepherd);
        if ((ires.get("success") != null) && (Boolean)ires.get("success") && (ires.get("results") != null)) {
            System.out.println("got legit results from IBEIS-IA" + ires.toString());
            HashMap<String,Object> r = (HashMap<String,Object>)ires.get("results");
            if (r.get(encNumber) == null) {
                System.out.println(" .... curiously, we have no result for our encNumber=" + encNumber + "; skipping IBEIS results");
            } else {
                HashMap<String,Double> iscores = (HashMap<String,Double>)r.get(encNumber);  //the thing we are really after, encNum=>score
                //now we iterate through the existing MatchObjects and set IBEISColor accordingly
                for (MatchObject mo : res) {
                    Object s = iscores.get(mo.getEncounterNumber());
                    if (s == null) {
                        mo.setIBEISColorValue(0.0);
                    } else {
                        mo.setIBEISColorValue((Double)s);
System.out.println("* got an ibeis value for enc=" + mo.getEncounterNumber() + " -> " + mo.getIBEISColorValue());
                    }
                }
            }
        } else {
            System.out.println("*** apparently did not get successful results from IBEIS-IA  :(");
        }


        boolean righty = false;
        if (taskID.startsWith("scanR")) {
          righty = true;
        }

        //successfulWrite = writeResult(res, encNumber, CommonConfiguration.getR(context), CommonConfiguration.getEpsilon(context), CommonConfiguration.getSizelim(context), CommonConfiguration.getMaxTriangleRotation(context), CommonConfiguration.getC(context), newEncDate, newEncShark, newEncSize, righty, cutoff, myShepherd,context);

        boolean successfulWrite = writeResult(res, encNumber,  newEncDate, newEncShark, myShepherd, context, request);

        

        //myShepherd.commitDBTransaction();
        

        //let's cleanup after a successful commit
        ThreadPoolExecutor es = SharkGridThreadExecutorService.getExecutorService();
        es.execute(new ScanTaskCleanupThread(request.getParameter("number")));

       
        statusText = "success";
        


      } 
      catch (Exception e) {
        //myShepherd.rollbackDBTransaction();
        System.out.println("scanResultsServlet registered the following error...");
        e.printStackTrace();
        statusText = "failure";
      }
      finally{
        myShepherd.commitDBTransaction();
        myShepherd.closeDBTransaction();
        response.setContentType("text/plain");
        out = response.getWriter();
        out.println(statusText);
        out.close();
        
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
      //myShepherd.beginDBTransaction();
      Encounter gsEnc=myShepherd.getEncounter(num);
      String tempGenusSpecies="undefined";
      if((gsEnc.getGenus()!=null)&&(!gsEnc.getGenus().trim().equals(""))&&(gsEnc.getSpecificEpithet()!=null)&&(!gsEnc.getSpecificEpithet().trim().equals(""))){
        tempGenusSpecies=gsEnc.getGenus()+gsEnc.getSpecificEpithet();
      }
      final String genusSpecies=tempGenusSpecies;
      
      
      
      //to get rank, copy the swirs array into a WIldbookInstance array
      ArrayList<WildbookInstance> list=new ArrayList<WildbookInstance>();
      int numResults=swirs.size();
      for(int i=0;i<numResults;i++){
        WildbookInstance wi=new WildbookInstance(new DenseInstance(TrainNetwork.getWekaAttributesPerSpecies(genusSpecies).size()-1));
        wi.setMatchObject(swirs.get(i));
        list.add(wi);
      }
      int listSize=list.size();
      
      /*
      //now we have to populate instance rank attributes so we can boost those
      Collections.sort(list,new RankComparator("intersection"));
      for(int i=0;i<listSize;i++){
        WildbookInstance wi=list.get(i);
        DenseInstance inst=wi.getInstance();
        inst.setValue(9, (i+1));
        //System.out.println("intersection score: "+wi.getMatchObject().getIntersectionCount()+" and rank: "+(i+1));
      }
      Collections.sort(list,new RankComparator("fastDTW"));
      for(int i=0;i<listSize;i++){
        WildbookInstance wi=list.get(i);
        DenseInstance inst=wi.getInstance();
        inst.setValue(10, (i+1));
        //System.out.println("FastDTW score: "+wi.getMatchObject().getLeftFastDTWResult()+" and rank: "+(i+1));
        
      }
      Collections.sort(list,new RankComparator("i3s"));
      for(int i=0;i<listSize;i++){
        WildbookInstance wi=list.get(i);
        DenseInstance inst=wi.getInstance();
        inst.setValue(11, (i+1));
        //System.out.println("I3S score: "+wi.getMatchObject().getI3SMatchValue()+" and rank: "+(i+1));
        
      }
      Collections.sort(list,new RankComparator("proportion"));
      for(int i=0;i<listSize;i++){
        WildbookInstance wi=list.get(i);
        DenseInstance inst=wi.getInstance();
        inst.setValue(12, (i+1));
        //System.out.println("prop. score: "+wi.getMatchObject().getProportionValue()+" and rank: "+(i+1));
        
      }
      Collections.sort(list,new RankComparator("MSM"));
      for(int i=0;i<listSize;i++){
        WildbookInstance wi=list.get(i);
        DenseInstance inst=wi.getInstance();
        inst.setValue(13, (i+1));
        //System.out.println("MSM score: "+wi.getMatchObject().getMSMValue()+" and rank: "+(i+1));
        
      }
      Collections.sort(list,new RankComparator("swale"));
      for(int i=0;i<listSize;i++){
        WildbookInstance wi=list.get(i);
        DenseInstance inst=wi.getInstance();
        inst.setValue(14, (i+1));
        //System.out.println("Swale score: "+wi.getMatchObject().getSwaleValue()+" and rank: "+(i+1));
        
      }
      Collections.sort(list,new RankComparator("euclidean"));
      for(int i=0;i<listSize;i++){
        WildbookInstance wi=list.get(i);
        DenseInstance inst=wi.getInstance();
        inst.setValue(15, (i+1));
        //System.out.println("Euc. score: "+wi.getMatchObject().getEuclideanDistanceValue()+" and rank: "+(i+1));
        
      }
      */
      
      
      
      
      String pathToClassifierFile=TrainNetwork.getAbsolutePathToClassifier(genusSpecies,request);
      String instancesFileFullPath=TrainNetwork.getAbsolutePathToInstances(genusSpecies, request);
      
      System.out.println("     I expect to find a classifier file here: "+pathToClassifierFile);
      System.out.println("     I expect to find an instances file here: "+instancesFileFullPath);
      
      final Instances instances=TrainNetwork.getWekaInstances(request, instancesFileFullPath);
      final Classifier booster=TrainNetwork.getWekaClassifier(request, pathToClassifierFile, instances);
      //String optionString = "-P 100 -S 1 -I 10 -W weka.classifiers.trees.RandomForest -- -I 100 -K 0 -S 1";
      //booster.setOptions(weka.core.Utils.splitOptions(optionString));
      

      
      
      System.out.println("     ...Prepping to write matching JSON file for encounter "+num+" after loading "+instances.numInstances()+" training instances");

      //now setup the XML write for the encounter
      
      //MatchObject[] matches = swirs;

      //Arrays.sort(swirs, new FlukeMatchComparator(request,booster,bayesBooster,instances));
      Collections.sort(list, new Comparator<WildbookInstance>(){

        public int compare(WildbookInstance a1, WildbookInstance b1)
        {
          
            
          /*
          //define our weights
          double intersectionWeight=0.127;
          double dtwWeight=0.094;
          double i3sWeight=0.181;
          double patterningCodeWeight=1;
          double eucWeight=0.491;
          double swaleWeight=0.35;
          double msmWeight=0.435;
          
          
            
            //Create our voting council
            double a1VoteTotal=0;
            double b1VoteTotal=0;
            
            MatchObject a1mo = a1.getMatchObject();
            MatchObject b1mo = b1.getMatchObject();
            
            //Intersection
            
            if(a1mo.getIntersectionCount()>b1mo.getIntersectionCount()){a1VoteTotal=a1VoteTotal+1*intersectionWeight;}
            else if(b1mo.getIntersectionCount()>a1mo.getIntersectionCount()){b1VoteTotal=b1VoteTotal+1*intersectionWeight;}
            
            //FastDTW
            
            if(a1mo.getLeftFastDTWResult()<b1mo.getLeftFastDTWResult()){a1VoteTotal=a1VoteTotal+1*dtwWeight;}
            else if(b1mo.getLeftFastDTWResult()<a1mo.getLeftFastDTWResult()){b1VoteTotal=b1VoteTotal+1*dtwWeight;}
            
            //I3S
            
            if(a1mo.getI3SMatchValue()<b1mo.getI3SMatchValue()){a1VoteTotal=a1VoteTotal+1*i3sWeight;}
            else if(b1mo.getI3SMatchValue()<a1mo.getI3SMatchValue()){b1VoteTotal=b1VoteTotal+1*i3sWeight;}
            
            //MSM
            
            if(a1mo.getMSMValue()<b1mo.getMSMValue()){a1VoteTotal=a1VoteTotal+1*msmWeight;}
            else if(b1mo.getMSMValue()<a1mo.getMSMValue()){b1VoteTotal=b1VoteTotal+1*msmWeight;}
            
            //SWALE

            if(a1mo.getSwaleValue()>b1mo.getSwaleValue()){a1VoteTotal=a1VoteTotal+1*swaleWeight;}
            else if(b1mo.getSwaleValue()>a1mo.getSwaleValue()){b1VoteTotal=b1VoteTotal+1*swaleWeight;}
            
            //EUCLIDEAN

            if(a1mo.getEuclideanDistanceValue()<b1mo.getEuclideanDistanceValue()){a1VoteTotal=a1VoteTotal+1*eucWeight;}
            else if(b1mo.getEuclideanDistanceValue()<a1mo.getEuclideanDistanceValue()){b1VoteTotal=b1VoteTotal+1*eucWeight;}
            
            //PatterningCode

            if(a1mo.getPatterningCodeDiff()<b1mo.getPatterningCodeDiff()){a1VoteTotal=a1VoteTotal+1*patterningCodeWeight;}
            else if(b1mo.getPatterningCodeDiff()<a1mo.getPatterningCodeDiff()){b1VoteTotal=b1VoteTotal+1*patterningCodeWeight;}
            
            
            return Double.compare(a1VoteTotal, b1VoteTotal);
            
            */
            
              
               double a1_adjustedValue=0;
                double b1_adjustedValue=0;

            
              Instance a1Example = a1.getInstance();
              Instance b1Example = b1.getInstance();
          
              a1Example.setDataset(instances);
              a1Example.setValue(0, a1.getMatchObject().getIntersectionCount());
              a1Example.setValue(1, a1.getMatchObject().getLeftFastDTWResult().doubleValue());
              a1Example.setValue(2,  a1.getMatchObject().getI3SMatchValue());
              a1Example.setValue(3, (new Double(a1.getMatchObject().getProportionValue()).doubleValue()));
              a1Example.setValue(4, (new Double(a1.getMatchObject().getMSMValue()).doubleValue()));
              a1Example.setValue(5, (new Double(a1.getMatchObject().getSwaleValue()).doubleValue()));
              a1Example.setValue(6, (new Double(a1.getMatchObject().getDateDiff()).doubleValue()));
              a1Example.setValue(7, (new Double(a1.getMatchObject().getEuclideanDistanceValue()).doubleValue()));
              a1Example.setValue(8, (new Double(a1.getMatchObject().getPatterningCodeDiff()).doubleValue()));
              a1Example.setValue(9, ((a1.getMatchObject().getIBEISColorValue() == null) ? 0.0 : new Double(a1.getMatchObject().getIBEISColorValue()).doubleValue()));
              
              
              b1Example.setDataset(instances);
              b1Example.setValue(0, b1.getMatchObject().getIntersectionCount());
              b1Example.setValue(1, b1.getMatchObject().getLeftFastDTWResult().doubleValue());
              b1Example.setValue(2,  b1.getMatchObject().getI3SMatchValue());
              b1Example.setValue(3, (new Double(b1.getMatchObject().getProportionValue()).doubleValue()));
              b1Example.setValue(4, (new Double(b1.getMatchObject().getMSMValue()).doubleValue()));
              b1Example.setValue(5, (new Double(b1.getMatchObject().getSwaleValue()).doubleValue()));
              b1Example.setValue(6, (new Double(b1.getMatchObject().getDateDiff()).doubleValue()));
              b1Example.setValue(7, (new Double(b1.getMatchObject().getEuclideanDistanceValue()).doubleValue()));
              b1Example.setValue(8, (new Double(b1.getMatchObject().getPatterningCodeDiff()).doubleValue()));
              b1Example.setValue(9, ((b1.getMatchObject().getIBEISColorValue() == null) ? 0.0 : new Double(b1.getMatchObject().getIBEISColorValue()).doubleValue()));
              
              Double aClass=0.0;
              Double bClass=0.0;
              
              if(a1.getMatchObject().getIBEISColorValue()>b1.getMatchObject().getIBEISColorValue()){
                a1_adjustedValue=1;
                b1_adjustedValue=0;
              }
              else if(a1.getMatchObject().getIBEISColorValue()<b1.getMatchObject().getIBEISColorValue()){
                a1_adjustedValue=0;
                b1_adjustedValue=1;
              }
              else{
              
                try{
                  aClass=booster.classifyInstance(a1Example);
                  System.out.println("Predicted Aclass: "+aClass);
                  
                  //int ArrayResultPosition=0;
                  //if(aClass==1.0)ArrayResultPosition=1;
                  a1_adjustedValue=booster.distributionForInstance(a1Example)[0];
                 
                  //int BrrayResultPosition=0;
                  //if(bClass==1.0)BrrayResultPosition=1;
                  bClass=booster.classifyInstance(b1Example);
                  //System.out.println("Predicted Bclass: "+bClass);
                  b1_adjustedValue=booster.distributionForInstance(b1Example)[0];
                }
                catch(Exception e){e.printStackTrace();}
            
              }
              //first check IBEIS
              
              
              if(a1_adjustedValue > b1_adjustedValue){return -1;}
              else if(a1_adjustedValue < b1_adjustedValue){return 1;}
              else{return 0;}
              
              
              
             
        }
      });
      
      int resultsSize = list.size();
      
      
      System.out.println("     ...Results sorted.");

      
      StringBuffer resultsJSON = new StringBuffer();
      
      

      
      //build our JSON with GSON
      Gson gson = new GsonBuilder().create();
      StringBuffer jsonOut=new StringBuffer("[\n");
      
      //overarching array
      JsonArray wrapperArray =new JsonArray();
      
      
          
       String[] header= {"individualID", "encounterID", "rank","adaboost_match","overall_score", "IBEISColor", "score_holmbergIntersection", "score_fastDTW", "score_I3S", "msm","swale","euclidean","patterningCode","dateDiff"};
       jsonOut.append(gson.toJson(header)+",\n");
       
       
       
      int numMatches=list.size();
      for (int i = 0; i < numMatches; i++) {
        MatchObject mo = list.get(i).getMatchObject();
        Encounter enc = myShepherd.getEncounter(mo.getEncounterNumber());
        System.out.println("           Writing out result for: "+mo.getEncounterNumber());
        
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
       
        
        
        
        Instance iExample = list.get(i).getInstance();
       iExample.setDataset(instances);
        //TrainNetwork.populateInstanceValues(genusSpecies, iExample, new EncounterLite(),new EncounterLite(),mo,myShepherd);
        
        Double myClass=booster.classifyInstance(iExample);
        double[] fDistribution = booster.distributionForInstance(iExample);
        //System.out.println("IndividualID: "+individualID+"    fClass: "+myClass+"   fDistribution score: "+fDistribution[myClass.intValue()]);
        //individual scores
        result.add(new JsonPrimitive(i+1));
        
        
        
        result.add(new JsonPrimitive(fDistribution[0]));
      //result.add(new JsonPrimitive(""));
        result.add(new JsonPrimitive(0));
        
        if (mo.getIBEISColorValue() != null) {
            result.add(new JsonPrimitive(TrainNetwork.round(mo.getIBEISColorValue().doubleValue(),3)));
        } else {
            result.add(new JsonPrimitive(""));
        }

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
        
        /*
        if(mo.getProportionValue()!=null){
          result.add(new JsonPrimitive(mo.getProportionValue()));
        }
        else{
          result.add(new JsonPrimitive(""));
        }
        */
        
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
      
      if(mo.getEuclideanDistanceValue()!=null){  
        result.add(new JsonPrimitive(TrainNetwork.round(mo.getEuclideanDistanceValue().doubleValue(),4)));
      }
      else{
        result.add(new JsonPrimitive(""));
      }
      
      if(mo.getPatterningCodeDiff()!=null){  
        result.add(new JsonPrimitive(TrainNetwork.round(mo.getPatterningCodeDiff().doubleValue(),3)));
      }
      else{
        result.add(new JsonPrimitive(""));
      }
      
      if(mo.getDateDiff()!=null){  
        int days = (int) (mo.getDateDiff() / (1000*60*60*24));
        result.add(new JsonPrimitive(days));
      }
      else{
        result.add(new JsonPrimitive(""));
      }

        
        //result.add(new JsonPrimitive(fDistribution[1]));
        
      //if(myClass==0.0){
        jsonOut.append(gson.toJson(result)+",\n");
      //}   
        
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
