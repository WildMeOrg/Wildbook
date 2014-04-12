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
import jxl.write.WritableWorkbook;
import org.dom4j.Document;
import org.dom4j.DocumentHelper;
import org.dom4j.Element;
import org.ecocean.CommonConfiguration;
import org.ecocean.Encounter;
import org.ecocean.Shepherd;
import org.ecocean.SuperSpot;
import org.ecocean.grid.*;

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


public class WriteOutScanTask extends HttpServlet {


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

    if ((!request.getParameter("number").equals("TuningTask")) && (!request.getParameter("number").equals("FalseMatchTask"))) {

      double cutoff = 2;
      String statusText = "success";
      System.out.println("writeOutScanTask: I am starting up.");


      myShepherd.beginDBTransaction();
      try {

        ScanTask st2 = myShepherd.getScanTask(request.getParameter("number"));
        st2.setFinished(true);
        long time = System.currentTimeMillis();
        st2.setEndTime(time);

        //let's check the checked-in value

        boolean successfulWrite = false;
        boolean successfulI3SWrite = false;

        System.out.println("Now setting this scanTask as finished!");
        String taskID = st2.getUniqueNumber();

        //change
        String encNumber = request.getParameter("number").substring(5);

        String newEncDate = "";
        String newEncShark = "";
        String newEncSize = "";

        Encounter newEnc = myShepherd.getEncounter(encNumber);
        newEncDate = newEnc.getDate();
        newEncShark = newEnc.isAssignedToMarkedIndividual();
        if(newEnc.getSizeAsDouble()!=null){newEncSize = newEnc.getSize() + " meters";}

        MatchObject[] res = new MatchObject[0];

        res = gm.getMatchObjectsForTask(taskID).toArray(res);


        boolean righty = false;
        if (taskID.startsWith("scanR")) {
          righty = true;
        }

        successfulWrite = writeResult(res, encNumber, CommonConfiguration.getR(context), CommonConfiguration.getEpsilon(context), CommonConfiguration.getSizelim(context), CommonConfiguration.getMaxTriangleRotation(context), CommonConfiguration.getC(context), newEncDate, newEncShark, newEncSize, righty, cutoff, myShepherd,context);

        successfulI3SWrite = i3sWriteThis(myShepherd, res, encNumber, newEncDate, newEncShark, newEncSize, righty, 2.5,context);

        //write out the boosted results
        //if(request.getParameter("boost")!=null){
        //    Properties props = new Properties();
        //    props = generateBoostedResults(res, encNumber, myShepherd);
        //    successfulWrite=writeBoostedResult(encNumber, res, encNumber, newEncDate, newEncShark, newEncSize, righty, cutoff, myShepherd, props);
        //}


        myShepherd.commitDBTransaction();
        myShepherd.closeDBTransaction();

        //let's cleanup after a successful commit
        ThreadPoolExecutor es = SharkGridThreadExecutorService.getExecutorService();
        es.execute(new ScanTaskCleanupThread(request.getParameter("number")));

        //let's go see the written results
        String sideAddition = "";
        if (request.getParameter("number").indexOf("scanR") != -1) {
          sideAddition = "&rightSide=true";
        }
        String resultsURL = ("http://" + CommonConfiguration.getURLLocation(request) + "/encounters/scanEndApplet.jsp?number=" + request.getParameter("number").substring(5) + "&writeThis=true" + sideAddition);
        response.sendRedirect(resultsURL);


      } catch (Exception e) {
        myShepherd.rollbackDBTransaction();
        System.out.println("scanResultsServlet registered the following error...");
        e.printStackTrace();
        statusText = "failure";
      }

    } else {

      //this is how to handle a TuningTask
      String statusText = "success";
      System.out.println("writeOutScanTask - TuningTask: I am starting up.");

      //set up our StringBuffers for the test and training files
      StringBuffer train = new StringBuffer();
      StringBuffer test = new StringBuffer();

      //let's get our boosting scores in.
      String[] predictInput = new String[10];

      myShepherd.beginDBTransaction();
      ScanTask st2 = null;
      try {

        if (request.getParameter("number").equals("TuningTask")) {
          st2 = myShepherd.getScanTask("TuningTask");
        } else {
          st2 = myShepherd.getScanTask("FalseMatchTask");
        }

        st2.setFinished(true);

        long time = System.currentTimeMillis();
        st2.setEndTime(time);


        //let's write out the results

        //File file=new File((new File(".")).getCanonicalPath()+File.separator+"webapps"+File.separator+"ROOT"+File.separator+"appadmin"+File.separator+request.getParameter("number")+"_tuningTaskOutput.xls");
        /**
         File file=new File(getServletContext().getRealPath(("/appadmin/"+request.getParameter("number")+"_tuningTaskOutput.xls")));
         WritableWorkbook workbook = Workbook.createWorkbook(file);
         WritableCellFormat floatFormat = new WritableCellFormat (NumberFormats.FLOAT);
         WritableCellFormat integerFormat = new WritableCellFormat (NumberFormats.INTEGER);
         WritableSheet sheet = workbook.createSheet(request.getParameter("number")+" Output", 0);

         //add the label row
         Label label0 = new Label(0, 0, "Encounter A");

         Label label1 = new Label(1, 0, "Encounter B");

         Label label2 = new Label(2, 0, "Shark");

         Label label2a = new Label(3, 0, "Groth: Fraction Matched Triangles");

         Label label3 = new Label(4, 0, "Groth: Match Score");

         Label label5 = new Label(5, 0, "I3S Match Score");

         Label label6 = new Label(6, 0, "RefA 1x");

         Label label7 = new Label(7, 0, "RefA 1y");

         Label label8 = new Label(8, 0, "RefA 2x");

         Label label9 = new Label(9, 0, "RefA 2y");

         Label label10 = new Label(10, 0, "RefA 3x");

         Label label11 = new Label(11, 0, "RefA 3y");

         Label label6b = new Label(12, 0, "RefB 1x");

         Label label7b = new Label(13, 0, "RefB 1y");

         Label label8b = new Label(14, 0, "RefB 2x");

         Label label9b = new Label(15, 0, "RefB 2y");

         Label label10b = new Label(16, 0, "RefB 3x");

         Label label11b = new Label(17, 0, "RefB 3y");


         Label label12 = new Label(18, 0, "Spot used for A?");

         Label label13 = new Label(19, 0, "Spot used for B?");

         Label label13y = new Label(20, 0, "Side");


         //boosting scores
         Label label14 = new Label(21, 0, "Boost - match score");

         Label label15 = new Label(22, 0, "Boost - not score");


         if(!request.getParameter("number").equals("TuningTask")){

         sheet.addCell(label15);
         sheet.addCell(label14);
         sheet.addCell(label13y);
         sheet.addCell(label13);
         sheet.addCell(label12);
         sheet.addCell(label11b);
         sheet.addCell(label10b);
         sheet.addCell(label9b);
         sheet.addCell(label8b);
         sheet.addCell(label7b);
         sheet.addCell(label6b);
         sheet.addCell(label11);
         sheet.addCell(label10);
         sheet.addCell(label9);
         sheet.addCell(label8);
         sheet.addCell(label7);
         sheet.addCell(label6);
         sheet.addCell(label5);
         sheet.addCell(label3);
         sheet.addCell(label2a);
         sheet.addCell(label2);
         sheet.addCell(label1);
         sheet.addCell(label0);

         }
         */

        ArrayList<ScanWorkItem> wis = null;
        if (request.getParameter("number").equals("TuningTask")) {
          wis = gm.getRemainingWorkItemsForTask("TuningTask");
        } else {
          wis = gm.getRemainingWorkItemsForTask("FalseMatchTask");

          //System.out.println("wis size in writeOutScanTask is: " + wis.size());

        }

        ArrayList<ScanWorkItemResult> wirs = null;
        if (request.getParameter("number").equals("TuningTask")) {
          wirs = gm.getResultsForTask("TuningTask");
        } else {
          wirs = gm.getResultsForTask("FalseMatchTask");
        }


        System.out.println("writeOutScanTask: got my scanTask...");
        int resultSize = wirs.size();
        for (int i = 1; i < (resultSize + 1); i++) {

          String boostString = "";

          //get the objects we need to populate intelligent results
          MatchObject mo = wirs.get(i - 1).getResult();
          //scanWorkItem ttt=wirs.get(i-1).
          ScanWorkItem swi = getWI4MO(wirs.get(i - 1), wis);
          Encounter encA = myShepherd.getEncounter(swi.getNewEncNumber());
          Encounter encB = myShepherd.getEncounter(swi.getExistingEncNumber());


          //boost sex
          if (encA.getSex().equals(encB.getSex())) {
            boostString += (encA.getSex() + ",");
            predictInput[0] = encA.getSex();
          } else {
            boostString += "unknown,";
            predictInput[0] = "unknwon";
          }


          //boost locationCode
          String locCodeA = encA.getLocationCode().trim();
          String locCodeB = encB.getLocationCode().trim();

          boostString += (locCodeA + "," + locCodeB + ",");
          predictInput[1] = locCodeA.trim();
          predictInput[2] = locCodeB.trim();

          //add the label row
          /*Label label0a = new Label(0, i, encA.getEncounterNumber());

               Label label1a = new Label(1, i, encB.getEncounterNumber());

               Label label2aaa = new Label(2, i, mo.getIndividualName());

               Label label2aa = new Label(3, i, Double.toString(mo.adjustedMatchValue));

               Label label3a = new Label(4, i, Double.toString((mo.getMatchValue()*mo.getAdjustedMatchValue())));

               Label label5a = new Label(5, i, Double.toString(mo.i3sMatchValue));
               */

          //boost I3S score
          boostString = boostString + mo.i3sMatchValue + ",";
          predictInput[3] = Double.toString(mo.i3sMatchValue);

          //boost Groth score
          boostString = boostString + (mo.getMatchValue()) + ",";
          predictInput[4] = Double.toString((mo.getMatchValue()));

          boostString = boostString + mo.getAdjustedMatchValue() + ",";
          predictInput[5] = Double.toString((mo.getAdjustedMatchValue()));

          String logM = "";
          try {
            logM = (new Double(mo.getLogMStdDev()).toString());
          } catch (java.lang.NumberFormatException nfe) {
            logM = "0.01";
          }
          boostString = boostString + logM + ",";
          predictInput[6] = logM;

          //boost time difference in absolute years
          boostString = boostString + Math.abs((encA.getYear() - encB.getYear())) + ",";
          predictInput[7] = Integer.toString(Math.abs((encA.getYear() - encB.getYear())));

          //boost num matching keywords
          ArrayList keywords = myShepherd.getKeywordsInCommon(encA.getEncounterNumber(), encB.getEncounterNumber());
          int keywordsSize = keywords.size();
          boostString = boostString + keywordsSize + ",";
          predictInput[8] = Integer.toString(keywordsSize);

          String sizeDiffString = "unknown";
          if ((encA.getSizeAsDouble()!=null)&&(encB.getSizeAsDouble()!=null)&&(encA.getSize() > 0) && (encB.getSize() > 0)) {
            double sizeDiff = Math.abs((encA.getSize() - encB.getSize()));
            if (sizeDiff <= 2.0) {
              sizeDiffString = "small";
            } else {
              sizeDiffString = "large";
            }
          }
          predictInput[9] = sizeDiffString;
          boostString = boostString + sizeDiffString + ",";

          //boost the label
          if (request.getParameter("number").equals("TuningTask")) {
            boostString = boostString + "match";
          } else {
            boostString = boostString + "not";
          }

          //add weight
          //if((request.getParameter("boostWeight")!=null)&&(!request.getParameter("boostWeight").equals(""))){
          if (request.getParameter("number").equals("TuningTask")) {
            if ((mo.getAdjustedMatchValue() * mo.getMatchValue()) < 30) {
              boostString = boostString + ", 2.0";
            } else {
              boostString = boostString + ", 1.0";
            }
          } else {
            if ((mo.getAdjustedMatchValue() * mo.getMatchValue()) > 50) {
              boostString = boostString + ",2.0";
            } else {
              boostString = boostString + ", 1.0";
            }


          }

          //numSpotsDiff
          int numSpotsDiff = 0;
          if (swi.rightScan) {
            numSpotsDiff = Math.abs(encA.getNumRightSpots() - encB.getNumRightSpots());
          } else {
            numSpotsDiff = Math.abs(encA.getNumSpots() - encB.getNumSpots());
          }
          boostString = boostString + ", " + Integer.toString(numSpotsDiff);

          boostString = boostString + ";";

          //add to the right boosting area, left sides are used for training, right sides are used for testing
          if (swi.rightScan) {
            test.append(("//" + encA.getEncounterNumber()) + " vs. " + encB.getEncounterNumber() + "\n");
            test.append((boostString + "\n"));
          } else {
            train.append(("//" + encA.getEncounterNumber()) + " vs. " + encB.getEncounterNumber() + "\n");
            train.append((boostString + "\n"));
          }

          //now let's populate the reference spots

          String ref1x_A = "";
          String ref1y_A = "";
          String ref2x_A = "";
          String ref2y_A = "";
          String ref3x_A = "";
          String ref3y_A = "";
          String ref1x_B = "";
          String ref1y_B = "";
          String ref2x_B = "";
          String ref2y_B = "";
          String ref3x_B = "";
          String ref3y_B = "";

          //populate these for encA
          if (swi.rightScan) {
            if ((encA.getRightReferenceSpots() != null) && (encA.getRightReferenceSpots().size() > 0)) {

              ref1x_A = Double.toString(encA.getRightReferenceSpots().get(0).getCentroidX());
              ref1y_A = Double.toString(encA.getRightReferenceSpots().get(0).getCentroidY());
              ref2x_A = Double.toString(encA.getRightReferenceSpots().get(1).getCentroidX());
              ref2y_A = Double.toString(encA.getRightReferenceSpots().get(1).getCentroidY());
              ref3x_A = Double.toString(encA.getRightReferenceSpots().get(2).getCentroidX());
              ref3y_A = Double.toString(encA.getRightReferenceSpots().get(2).getCentroidY());

            }

          } else {
            if ((encA.getLeftReferenceSpots() != null) && (encA.getLeftReferenceSpots().size() > 0)) {

              ref1x_A = Double.toString(encA.getLeftReferenceSpots().get(0).getCentroidX());
              ref1y_A = Double.toString(encA.getLeftReferenceSpots().get(0).getCentroidY());
              ref2x_A = Double.toString(encA.getLeftReferenceSpots().get(1).getCentroidX());
              ref2y_A = Double.toString(encA.getLeftReferenceSpots().get(1).getCentroidY());
              ref3x_A = Double.toString(encA.getLeftReferenceSpots().get(2).getCentroidX());
              ref3y_A = Double.toString(encA.getLeftReferenceSpots().get(2).getCentroidY());

            }

          }

          //populate reference spots for encB
          if (swi.rightScan) {
            if ((encB.getRightReferenceSpots() != null) && (encB.getRightReferenceSpots().size() > 0)) {

              ref1x_B = Double.toString(encB.getRightReferenceSpots().get(0).getCentroidX());
              ref1y_B = Double.toString(encB.getRightReferenceSpots().get(0).getCentroidY());
              ref2x_B = Double.toString(encB.getRightReferenceSpots().get(1).getCentroidX());
              ref2y_B = Double.toString(encB.getRightReferenceSpots().get(1).getCentroidY());
              ref3x_B = Double.toString(encB.getRightReferenceSpots().get(2).getCentroidX());
              ref3y_B = Double.toString(encB.getRightReferenceSpots().get(2).getCentroidY());

            }

          } else {
            if ((encB.getLeftReferenceSpots() != null) && (encB.getLeftReferenceSpots().size() > 0)) {

              ref1x_B = Double.toString(encB.getLeftReferenceSpots().get(0).getCentroidX());
              ref1y_B = Double.toString(encB.getLeftReferenceSpots().get(0).getCentroidY());
              ref2x_B = Double.toString(encB.getLeftReferenceSpots().get(1).getCentroidX());
              ref2y_B = Double.toString(encB.getLeftReferenceSpots().get(1).getCentroidY());
              ref3x_B = Double.toString(encB.getLeftReferenceSpots().get(2).getCentroidX());
              ref3y_B = Double.toString(encB.getLeftReferenceSpots().get(2).getCentroidY());

            }

          }


          //Spot maps
          String aSpot = "false";
          String bSpot = "false";
          if (swi.rightScan) {
            try {
              if (encB.rightSpotImageFileName.endsWith("-mapped.jpg")) {
                bSpot = "true";
              }
            } catch (Exception f) {
              f.printStackTrace();
              System.out.println("I couldn't find the rightSpotImageFileName for encounter " + encB.getEncounterNumber());
            }
            try {
              if (encA.rightSpotImageFileName.endsWith("-mapped.jpg")) {
                aSpot = "true";
              }
            } catch (Exception f) {
              f.printStackTrace();
              System.out.println("I couldn't find the rightSpotImageFileName for encounter " + encA.getEncounterNumber());
            }

          } else {
            try {
              if (encB.spotImageFileName.endsWith("-mapped.jpg")) {
                bSpot = "true";
              }
            } catch (Exception f) {
              f.printStackTrace();
              System.out.println("I couldn't find the spotImageFileName for encounter " + encB.getEncounterNumber());
            }
            try {
              if (encA.spotImageFileName.endsWith("-mapped.jpg")) {
                aSpot = "true";
              }
            } catch (Exception f) {
              f.printStackTrace();
              System.out.println("I couldn't find the spotImageFileName for encounter " + encA.getEncounterNumber());
            }

          }


          String side = "l";
          if (swi.rightScan) {
            side = "r";
          }


        }


        //write out the Excel file
        if (!request.getParameter("number").equals("TuningTask")) {
          //finalize(workbook);
          //workbook.close();
        }

        //let's finalize the boosting files

        writeBoostFiles(train, test, request.getParameter("number"));


        myShepherd.commitDBTransaction();
        myShepherd.closeDBTransaction();

        if (request.getParameter("number").equals("TuningTask")) {
          String resultsURL = ("http://" + CommonConfiguration.getURLLocation(request) + "/appadmin/endTuningTask.jsp?number=TuningTask");
          response.sendRedirect(resultsURL);
        } else {
          String resultsURL = ("http://" + CommonConfiguration.getURLLocation(request) + "/appadmin/endTuningTask.jsp?number=FalseMatchTask");
          response.sendRedirect(resultsURL);
        }

      } catch (Exception e) {
        myShepherd.rollbackDBTransaction();
        System.out.println("writeOutScanTask registered the following error...");
        e.printStackTrace();
        statusText = "failure";
      }

    }


  }

  public boolean writeResult(MatchObject[] swirs, String num, String R, String epsilon, String Sizelim, String maxTriangleRotation, String C, String newEncDate, String newEncShark, String newEncSize, boolean rightSide, double cutoff, Shepherd myShepherd, String context) {


    try {
      //System.out.println("Prepping to write XML file for encounter "+num);

      //now setup the XML write for the encounter
      int resultsSize = swirs.length;
      MatchObject[] matches = swirs;

      Arrays.sort(matches, new MatchComparator());
      StringBuffer resultsXML = new StringBuffer();
      Document document = DocumentHelper.createDocument();
      Element root = document.addElement("matchSet");
      root.addAttribute("scanDate", (new java.util.Date()).toString());
      root.addAttribute("R", R);
      root.addAttribute("epsilon", epsilon);
      root.addAttribute("Sizelim", Sizelim);
      root.addAttribute("maxTriangleRotation", maxTriangleRotation);
      root.addAttribute("C", C);
      for (int i = 0; i < matches.length; i++) {
        MatchObject mo = matches[i];
        if ((mo.getMatchValue() > 0) && ((mo.getMatchValue() * mo.getAdjustedMatchValue()) > 2)) {

          Element match = root.addElement("match");
          match.addAttribute("points", (new Double(mo.getMatchValue())).toString());
          match.addAttribute("adjustedpoints", (new Double(mo.getAdjustedMatchValue())).toString());
          match.addAttribute("pointBreakdown", mo.getPointBreakdown());
          String finalscore = (new Double(mo.getMatchValue() * mo.getAdjustedMatchValue())).toString();
          if (finalscore.length() > 7) {
            finalscore = finalscore.substring(0, 6);
          }
          match.addAttribute("finalscore", finalscore);

          //check if logM is very small...
          try {
            match.addAttribute("logMStdDev", (new Double(mo.getLogMStdDev())).toString());
          } catch (java.lang.NumberFormatException nfe) {
            match.addAttribute("logMStdDev", "<0.01");
          }
          match.addAttribute("evaluation", mo.getEvaluation());

          Encounter firstEnc = myShepherd.getEncounter(mo.getEncounterNumber());
          Element enc = match.addElement("encounter");
          enc.addAttribute("number", firstEnc.getEncounterNumber());
          enc.addAttribute("date", firstEnc.getDate());
          enc.addAttribute("sex", firstEnc.getSex());
          enc.addAttribute("assignedToShark", firstEnc.getIndividualID());
          if(firstEnc.getSizeAsDouble()!=null){enc.addAttribute("size", (firstEnc.getSize() + " meters"));}
          enc.addAttribute("location", firstEnc.getLocation());
          enc.addAttribute("locationID", firstEnc.getLocationID());
          VertexPointMatch[] firstScores = mo.getScores();
          try {
            for (int k = 0; k < firstScores.length; k++) {
              Element spot = enc.addElement("spot");
              spot.addAttribute("x", (new Double(firstScores[k].getOldX())).toString());
              spot.addAttribute("y", (new Double(firstScores[k].getOldY())).toString());
            }
          } catch (NullPointerException npe) {
          }
          Element enc2 = match.addElement("encounter");
          Encounter secondEnc = myShepherd.getEncounter(num);
          enc2.addAttribute("number", num);
          enc2.addAttribute("date", secondEnc.getDate());
          enc2.addAttribute("sex", secondEnc.getSex());
          enc2.addAttribute("assignedToShark", secondEnc.getIndividualID());
          if(secondEnc.getSizeAsDouble()!=null){enc2.addAttribute("size", (secondEnc.getSize() + " meters"));}
          else{enc2.addAttribute("size", "unknown");}
          enc2.addAttribute("location", secondEnc.getLocation());
          enc2.addAttribute("locationID", secondEnc.getLocationID());
          try {
            for (int j = 0; j < firstScores.length; j++) {
              Element spot = enc2.addElement("spot");
              spot.addAttribute("x", (new Double(firstScores[j].getNewX())).toString());
              spot.addAttribute("y", (new Double(firstScores[j].getNewY())).toString());
            }
          } catch (NullPointerException npe) {
          }

          //let's find the keywords in common
          ArrayList keywords = myShepherd.getKeywordsInCommon(mo.getEncounterNumber(), num);
          int keywordsSize = keywords.size();
          if (keywordsSize > 0) {
            Element kws = match.addElement("keywords");
            for (int y = 0; y < keywordsSize; y++) {
              Element keyword = kws.addElement("keyword");
              keyword.addAttribute("name", ((String) keywords.get(y)));
            }
          }


        } //end if
      } //end for

      //prep for writing out the XML

      //in case this is a right-side scan, change file name to save to
      String fileAddition = "";
      if (rightSide) {
        fileAddition = "Right";
      }
      
      //setup data dir
      String rootWebappPath = getServletContext().getRealPath("/");
      File webappsDir = new File(rootWebappPath).getParentFile();
      File shepherdDataDir = new File(webappsDir, CommonConfiguration.getDataDirectoryName(context));
      //if(!shepherdDataDir.exists()){shepherdDataDir.mkdir();}
      File encountersDir=new File(shepherdDataDir.getAbsolutePath()+"/encounters");
      //if(!encountersDir.exists()){encountersDir.mkdir();}
      
      //File file=new File((new File(".")).getCanonicalPath()+File.separator+"webapps"+File.separator+"ROOT"+File.separator+"encounters"+File.separator+num+File.separator+"lastFull"+fileAddition+"Scan.xml");
      File file = new File(encountersDir.getAbsolutePath()+"/"+ num + "/lastFull" + fileAddition + "Scan.xml");

      
      
      FileWriter mywriter = new FileWriter(file);
      org.dom4j.io.OutputFormat format = org.dom4j.io.OutputFormat.createPrettyPrint();
      format.setLineSeparator(System.getProperty("line.separator"));
      org.dom4j.io.XMLWriter writer = new org.dom4j.io.XMLWriter(mywriter, format);
      writer.write(document);
      writer.close();
      System.out.println("Successful write.");
      return true;
    } catch (Exception e) {
      e.printStackTrace();
      return false;
    }
  } //end writeResult method

  public boolean i3sWriteThis(Shepherd myShepherd, MatchObject[] matches, String num, String newEncDate, String newEncShark, String newEncSize, boolean rightSide, double cutoff, String context) {
    try {

      System.out.println("scanWorkItemResultsHandler: Prepping to write I3S XML file for encounter " + num);

      //now setup the XML write for the encounter
      //int resultsSize=results.size();

      Arrays.sort(matches, new NewI3SMatchComparator());
      StringBuffer resultsXML = new StringBuffer();
      Document document = DocumentHelper.createDocument();
      Element root = document.addElement("matchSet");
      root.addAttribute("scanDate", (new java.util.Date()).toString());
      //System.out.println("Total num matches for I3S printing: "+matches.length);
      for (int i = 0; i < matches.length; i++) {
        try {
          //System.out.println();
          MatchObject mo = matches[i];
          //System.out.println("I3S match value: "+mo.getI3SMatchValue());
          if ((mo.getI3SMatchValue() > 0.001) && (mo.getI3SMatchValue() <= 2.0)) {
            Element match = root.addElement("match");
            String finalscore = (new Double(mo.getI3SMatchValue())).toString();
            if (finalscore.length() > 7) {
              finalscore = finalscore.substring(0, 6);
            }
            match.addAttribute("finalscore", finalscore);
            match.addAttribute("evaluation", mo.getEvaluation());

            Element enc = match.addElement("encounter");
            enc.addAttribute("number", mo.getEncounterNumber());
            enc.addAttribute("date", mo.getDate());
            enc.addAttribute("sex", mo.getSex());
            enc.addAttribute("assignedToShark", mo.getIndividualName());
            enc.addAttribute("size", (new Double(mo.getSize())).toString());

            //get the Map
            Vector map = mo.getMap2();
            int mapSize = map.size();
            Encounter e1 = myShepherd.getEncounter(mo.getEncounterNumber());
            for (int f = 0; f < mapSize; f++) {
              Pair tempPair = (com.reijns.I3S.Pair) map.get(f);
              int M1 = tempPair.getM1();
              ArrayList<SuperSpot> spts = new ArrayList<SuperSpot>();
              if (rightSide) {
                spts = e1.getRightSpots();
              } else {
                spts = e1.getSpots();
              }
              //System.out.println("scanWorkItemResultsHandler: I3S spots: "+spts.size()+" vs mapSize: "+mapSize);
              Element spot = enc.addElement("spot");
              spot.addAttribute("x", (new Double(spts.get(M1).getTheSpot().getCentroidX())).toString());
              spot.addAttribute("y", (new Double(spts.get(M1).getTheSpot().getCentroidY())).toString());
            }

            Element enc2 = match.addElement("encounter");
            enc2.addAttribute("number", num);
            enc2.addAttribute("date", newEncDate);
            enc2.addAttribute("sex", mo.getNewSex());
            enc2.addAttribute("assignedToShark", newEncShark);
            enc2.addAttribute("size", newEncSize);

            //reset the Iterator
            Encounter e2 = myShepherd.getEncounter(num);
            for (int g = 0; g < mapSize; g++) {
              Pair tempPair = (com.reijns.I3S.Pair) map.get(g);
              int M2 = tempPair.getM2();
              ArrayList<SuperSpot> spts = new ArrayList<SuperSpot>();
              if (rightSide) {
                spts = e2.getRightSpots();
              } else {
                spts = e2.getSpots();
              }
              Element spot = enc2.addElement("spot");
              //System.out.println("scanWorkItemResultsHandler: I3S next spots: "+spts.size()+" vs mapSize: "+mapSize);
              spot.addAttribute("x", (new Double(spts.get(M2).getTheSpot().getCentroidX())).toString());
              spot.addAttribute("y", (new Double(spts.get(M2).getTheSpot().getCentroidY())).toString());
            }

          }
        } catch (NullPointerException npe) {
          npe.printStackTrace();
        }
      }


      //prep for writing out the XML

      //in case this is a right-side scan, change file name to save to
      String fileAddition = "";
      if (rightSide) {
        fileAddition = "Right";
      }
      
      //setup data dir
      String rootWebappPath = getServletContext().getRealPath("/");
      File webappsDir = new File(rootWebappPath).getParentFile();
      File shepherdDataDir = new File(webappsDir, CommonConfiguration.getDataDirectoryName(context));
      //if(!shepherdDataDir.exists()){shepherdDataDir.mkdir();}
      File encountersDir=new File(shepherdDataDir.getAbsolutePath()+"/encounters");
      //if(!encountersDir.exists()){encountersDir.mkdir();}
      
      //File file=new File((new File(".")).getCanonicalPath()+File.separator+"webapps"+File.separator+"ROOT"+File.separator+"encounters"+File.separator+num+File.separator+"lastFull"+fileAddition+"I3SScan.xml");
      File file = new File(encountersDir.getAbsolutePath()+"/"+ num + "/lastFull" + fileAddition + "I3SScan.xml");


      FileWriter mywriter = new FileWriter(file);
      org.dom4j.io.OutputFormat format = org.dom4j.io.OutputFormat.createPrettyPrint();
      format.setLineSeparator(System.getProperty("line.separator"));
      org.dom4j.io.XMLWriter writer = new org.dom4j.io.XMLWriter(mywriter, format);
      writer.write(document);
      writer.close();
      System.out.println("writeOutScanTask: Successful I3S write.");
      return true;
    } catch (Exception e) {
      System.out.println("writeOutScanTask: Failed to write out I3S results!");
      e.printStackTrace();
      return false;
    }

  }

  public void finalize(WritableWorkbook workbook) {
    try {
      workbook.write();
    } catch (Exception e) {
      System.out.println("Unknown error writing output Excel file for TuningTask...");
      e.printStackTrace();
    }
  }

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

  public void writeBoostFiles(StringBuffer train, StringBuffer test, String number) {

    try {

      //write out the training file .train
      //File file1=new File((new File(".")).getCanonicalPath()+File.separator+"webapps"+File.separator+"ROOT"+File.separator+"appadmin"+File.separator+"boostedResults_"+number+".train");
      File file1 = new File(getServletContext().getRealPath(("/appadmin/" + "boostedResults_" + number + ".train")));


      //use buffering
      Writer output = new BufferedWriter(new FileWriter(file1));
      try {
        //FileWriter always assumes default encoding is OK!
        output.write(train.toString());
      } finally {
        output.close();
      }

      File file2 = new File(getServletContext().getRealPath(("/appadmin/" + "boostedResults_" + number + ".test")));


      //use buffering
      Writer output2 = new BufferedWriter(new FileWriter(file2));
      try {
        //FileWriter always assumes default encoding is OK!
        output2.write(test.toString());
      } finally {
        output2.close();
      }

    } catch (IOException ioe) {
      ioe.printStackTrace();
      System.out.println("Failed to write out the training and test files for JBoost...");
    }
  }


  public boolean writeBoostedResult(String encNumber, MatchObject[] swirs, String num, String newEncDate, String newEncShark, String newEncSize, boolean rightSide, double cutoff, Shepherd myShepherd, Properties props) {

    try {
      System.out.println("Prepping to write XML file for encounter " + num);

      //now setup the XML write for the encounter
      int resultsSize = swirs.length;
      MatchObject[] matches = swirs;

      // TODO: fix!
      //Arrays.sort(matches, new BoostComparator(encNumber, myShepherd, props));
      StringBuffer resultsXML = new StringBuffer();
      Document document = DocumentHelper.createDocument();
      Element root = document.addElement("matchSet");
      root.addAttribute("scanDate", (new java.util.Date()).toString());
      for (int i = 0; i < matches.length; i++) {
        MatchObject mo = matches[i];
        try {
          double boostMatchScore = (new Double(props.getProperty(mo.getEncounterNumber()))).doubleValue();
          double boostNotScore = (new Double(props.getProperty(("not" + mo.getEncounterNumber())))).doubleValue();
          if (boostMatchScore >= -1) {

            Element match = root.addElement("match");
            //match.addAttribute("points", (new Double(mo.getMatchValue())).toString());
            //match.addAttribute("adjustedpoints", (new Double(mo.getAdjustedMatchValue())).toString());
            //match.addAttribute("pointBreakdown", mo.getPointBreakdown());
            String finalscore = (new Double(boostMatchScore)).toString();
            if (finalscore.length() > 7) {
              finalscore = finalscore.substring(0, 6);
            }
            match.addAttribute("matchScore", finalscore);
            match.addAttribute("finalscore", finalscore);
            String finalscore2 = (new Double(boostNotScore)).toString();
            if (finalscore2.length() > 7) {
              finalscore2 = finalscore2.substring(0, 6);
            }
            match.addAttribute("notScore", finalscore2);

            //match.addAttribute("evaluation", mo.getEvaluation());
            Element enc = match.addElement("encounter");
            enc.addAttribute("number", mo.getEncounterNumber());
            enc.addAttribute("date", mo.getDate());
            enc.addAttribute("sex", mo.getSex());
            enc.addAttribute("assignedToShark", mo.getIndividualName());
            enc.addAttribute("size", ((new Double(mo.getSize())).toString() + " meters"));
            //	vertexPointMatch[] firstScores=mo.getScores();

            Element enc2 = match.addElement("encounter");
            enc2.addAttribute("number", num);
            enc2.addAttribute("date", newEncDate);
            enc2.addAttribute("sex", mo.getNewSex());
            enc2.addAttribute("assignedToShark", newEncShark);
            enc2.addAttribute("size", (newEncSize + " meters"));

            //let's find the keywords in common
            ArrayList keywords = myShepherd.getKeywordsInCommon(mo.getEncounterNumber(), num);
            int keywordsSize = keywords.size();
            if (keywordsSize > 0) {
              Element kws = match.addElement("keywords");
              for (int y = 0; y < keywordsSize; y++) {
                Element keyword = kws.addElement("keyword");
                keyword.addAttribute("name", ((String) keywords.get(y)));
              }
            }


          } //end if

        } catch (NullPointerException npe) {
          npe.printStackTrace();
          //System.out.println("npe on: "+mo.getEncounterNumber());
        }

      } //end for


      //prep for writing out the XML

      //in case this is a right-side scan, change file name to save to
      String fileAddition = "";
      if (rightSide) {
        fileAddition = "Right";
      }
      File file = new File(getServletContext().getRealPath(("/encounters/" + num + "/lastBoost" + fileAddition + "Scan.xml")));


      FileWriter mywriter = new FileWriter(file);
      org.dom4j.io.OutputFormat format = org.dom4j.io.OutputFormat.createPrettyPrint();
      format.setLineSeparator(System.getProperty("line.separator"));
      org.dom4j.io.XMLWriter writer = new org.dom4j.io.XMLWriter(mywriter, format);
      writer.write(document);
      writer.close();
      System.out.println("Successful write.");
      return true;
    } catch (Exception e) {
      e.printStackTrace();
      return false;
    }
  } //end writeResult method


}