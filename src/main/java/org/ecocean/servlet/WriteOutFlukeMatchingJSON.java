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

import org.ecocean.*;
import org.ecocean.grid.*;
import org.ecocean.neural.*;
import org.apache.commons.math.stat.descriptive.SummaryStatistics;

import javax.servlet.ServletConfig;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import java.io.*;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Properties;
import java.util.Vector;
import java.util.concurrent.ThreadPoolExecutor;

import com.google.gson.*;

import org.ecocean.grid.GridManager;

//train weka
import weka.core.Attribute;
import weka.core.FastVector;
import weka.core.Instances;
import weka.core.Instance;
import weka.classifiers.meta.AdaBoostM1;
import weka.classifiers.Evaluation;



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

        MatchObject[] res = new MatchObject[0];

        res = gm.getMatchObjectsForTask(taskID).toArray(res);


        boolean righty = false;
        if (taskID.startsWith("scanR")) {
          righty = true;
        }

        //successfulWrite = writeResult(res, encNumber, CommonConfiguration.getR(context), CommonConfiguration.getEpsilon(context), CommonConfiguration.getSizelim(context), CommonConfiguration.getMaxTriangleRotation(context), CommonConfiguration.getC(context), newEncDate, newEncShark, newEncSize, righty, cutoff, myShepherd,context);

        boolean successfulWrite = writeResult(res, encNumber,  newEncDate, newEncShark, myShepherd, context, request);

        
        //successfulI3SWrite = i3sWriteThis(myShepherd, res, encNumber, newEncDate, newEncShark, newEncSize, righty, 2.5,context);

        //boolean fastDTWWriteThis=fastDTWWriteThis(myShepherd, res, encNumber, newEncDate, newEncShark, newEncSize, righty, 2.5,context);
        
        //boolean intersectionWriteThis=intersectionWriteThis(myShepherd, res, encNumber, newEncDate, newEncShark, newEncSize, righty, 2.5,context);
        
        
        //boolean geroWriteThis=geroWriteThis(myShepherd, res, encNumber, newEncDate, newEncShark, newEncSize, righty, 2.5,context);
        
        
        //write out the boosted results
        //if(request.getParameter("boost")!=null){
        //    Properties props = new Properties();
        //    props = generateBoostedResults(res, encNumber, myShepherd);
        //    successfulWrite=writeBoostedResult(encNumber, res, encNumber, newEncDate, newEncShark, newEncSize, righty, cutoff, myShepherd, props);
        //}


        myShepherd.commitDBTransaction();
        

        //let's cleanup after a successful commit
        ThreadPoolExecutor es = SharkGridThreadExecutorService.getExecutorService();
        es.execute(new ScanTaskCleanupThread(request.getParameter("number")));

        //let's go see the written results
        //String sideAddition = "";
        //if (request.getParameter("number").indexOf("scanR") != -1) {
        //  sideAddition = "&rightSide=true";
        //}
        //String resultsURL = ("http://" + CommonConfiguration.getURLLocation(request) + "/encounters/scanEndApplet.jsp?number=" + request.getParameter("number").substring(5) + "&writeThis=true" + sideAddition);
        //response.sendRedirect(resultsURL);
        
       
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

  public boolean writeResult(MatchObject[] swirs, String num,  String newEncDate, String newEncIndividualID, Shepherd myShepherd, String context, HttpServletRequest request) {

    
  //setup data dir
    String rootWebappPath = getServletContext().getRealPath("/");
    File webappsDir = new File(rootWebappPath).getParentFile();
    File shepherdDataDir = new File(webappsDir, CommonConfiguration.getDataDirectoryName(context));
    File encountersDir=new File(shepherdDataDir.getAbsolutePath()+"/encounters");
    File file = new File(Encounter.dir(shepherdDataDir, num) + "/flukeMatching.json");

    
    
    
    FileWriter mywriter=null;
    try {
      
      
      //get the Encounter and genus and species
      myShepherd.beginDBTransaction();
      Encounter gsEnc=myShepherd.getEncounter(num);
      String genusSpecies="undefined";
      if((gsEnc.getGenus()!=null)&&(!gsEnc.getGenus().trim().equals(""))&&(gsEnc.getSpecificEpithet()!=null)&&(!gsEnc.getSpecificEpithet().trim().equals(""))){
        genusSpecies=gsEnc.getGenus()+gsEnc.getSpecificEpithet();
      }
      String pathToClassifierFile=TrainNetwork.getAbsolutePathToClassifier(genusSpecies,request);
      String instancesFileFullPath=TrainNetwork.getAbsolutePathToInstances(genusSpecies, request);
      
      Instances instances=GridManager.getAdaboostInstances(request, instancesFileFullPath);
      
      //System.out.println("Prepping to write XML file for encounter "+num);

      //now setup the XML write for the encounter
      int resultsSize = swirs.length;
      MatchObject[] matches = swirs;

      Arrays.sort(swirs, new FlukeMatchComparator(request,pathToClassifierFile,instances));
      
      
      StringBuffer resultsJSON = new StringBuffer();
      
      
      //need stats
      //TBD cache these later so writes are faster
      SummaryStatistics intersectionStats=GridManager.getIntersectionStats(request);
      SummaryStatistics dtwStats=GridManager.getDTWStats(request);
      SummaryStatistics proportionStats=GridManager.getProportionStats(request);
      SummaryStatistics i3sStats=GridManager.getI3SStats(request);
      
      
      double intersectionStdDev=0.05;
      if(request.getParameter("intersectionStdDev")!=null){intersectionStdDev=(new Double(request.getParameter("intersectionStdDev"))).doubleValue();}
      double dtwStdDev=0.41;
      if(request.getParameter("dtwStdDev")!=null){dtwStdDev=(new Double(request.getParameter("dtwStdDev"))).doubleValue();}
      double i3sStdDev=0.01;
      if(request.getParameter("i3sStdDev")!=null){i3sStdDev=(new Double(request.getParameter("i3sStdDev"))).doubleValue();}
      double proportionStdDev=0.01;
      if(request.getParameter("proportionStdDev")!=null){proportionStdDev=(new Double(request.getParameter("proportionStdDev"))).doubleValue();}
      double intersectHandicap=0;
      if(request.getParameter("intersectHandicap")!=null){intersectHandicap=(new Double(request.getParameter("intersectHandicap"))).doubleValue();}
      double dtwHandicap=0;
      if(request.getParameter("dtwHandicap")!=null){dtwHandicap=(new Double(request.getParameter("dtwHandicap"))).doubleValue();}
      double i3sHandicap=0;
      if(request.getParameter("i3sHandicap")!=null){i3sHandicap=(new Double(request.getParameter("i3sHandicap"))).doubleValue();}
      double proportionHandicap=0;
      if(request.getParameter("proportionHandicap")!=null){proportionHandicap=(new Double(request.getParameter("proportionHandicap"))).doubleValue();}

      
      
      
      
      
      //build our JSON with GSON
      Gson gson = new GsonBuilder().create();
      StringBuffer jsonOut=new StringBuffer("[\n");
      
      //overarching array
      JsonArray wrapperArray =new JsonArray();
      
      
          
       String[] header= {"individualID", "encounterID", "rank","adaboost_match","overall_score", "score_holmbergIntersection", "score_fastDTW", "score_I3S", "score_proportion"};
       jsonOut.append(gson.toJson(header)+",\n");
       
       
       
      int numMatches=matches.length;
      for (int i = 0; i < numMatches; i++) {
        MatchObject mo = matches[i];
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
        double thisScore=TrainNetwork.getOverallFlukeMatchScore(request, mo.getIntersectionCount(), mo.getLeftFastDTWResult().doubleValue(), mo.getI3SMatchValue(), new Double(mo.getProportionValue()),intersectionStats,dtwStats,i3sStats, proportionStats, intersectionStdDev,dtwStdDev,i3sStdDev,proportionStdDev,intersectHandicap, dtwHandicap,i3sHandicap,proportionHandicap);
        
        //adaboost classifier
      //prep weka for AdaBoost
       
        
        
        
        Instance iExample = new Instance(5);
        Instances myInstances=GridManager.getAdaboostInstances(request,genusSpecies);
        
        iExample.setDataset(myInstances);
        iExample.setValue(0, mo.getIntersectionCount());
        iExample.setValue(1, mo.getLeftFastDTWResult().doubleValue());
        iExample.setValue(2,  mo.getI3SMatchValue());
        iExample.setValue(3, (new Double(mo.getProportionValue()).doubleValue()));
        
        
        AdaBoostM1 booster=GridManager.getAdaBoostM1(request,pathToClassifierFile,myInstances);
        
        double[] fDistribution = booster.distributionForInstance(iExample);
        
        //individual scores
        result.add(new JsonPrimitive(i+1));
        result.add(new JsonPrimitive(fDistribution[0]));
        result.add(new JsonPrimitive(thisScore));
        result.add(new JsonPrimitive(mo.getIntersectionCount()));
        result.add(new JsonPrimitive(mo.getLeftFastDTWResult()));
        result.add(new JsonPrimitive(mo.getI3SMatchValue()));
        result.add(new JsonPrimitive(mo.getProportionValue()));
        
        //result.add(new JsonPrimitive(fDistribution[1]));
        
        jsonOut.append(gson.toJson(result)+",\n");
         
        
      }
      
      jsonOut.append("\n]");
      mywriter = new FileWriter(file);
      //org.dom4j.io.OutputFormat format = org.dom4j.io.OutputFormat.createPrettyPrint();
      //format.setLineSeparator(System.getProperty("line.separator"));
      //org.dom4j.io.XMLWriter writer = new org.dom4j.io.XMLWriter(mywriter, format);
      
      //System.out.println("Trying to write out JSON: "+gson.toString());
      mywriter.write(jsonOut.toString());
     
      System.out.println("Successful WriteOutFlukeMatchingJSON write.");
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
