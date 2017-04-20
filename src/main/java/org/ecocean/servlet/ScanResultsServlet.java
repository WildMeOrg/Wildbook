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

import org.dom4j.Document;
import org.dom4j.DocumentHelper;
import org.dom4j.Element;
import org.ecocean.Encounter;
import org.ecocean.Shepherd;
import org.ecocean.CommonConfiguration;
import org.ecocean.grid.MatchComparator;
import org.ecocean.grid.MatchObject;
import org.ecocean.grid.VertexPointMatch;

import javax.servlet.ServletConfig;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.http.HttpSession;

import java.io.*;
import java.util.Arrays;
import java.util.Vector;


public class ScanResultsServlet extends HttpServlet {


  public void init(ServletConfig config) throws ServletException {
    super.init(config);
  }


  private static Object receiveObject(ObjectInputStream con) throws Exception {
    System.out.println("scanresultsServlet: I am about to read in the byte array!");
    Object obj = new Vector();
    try {
      obj = (Object) con.readObject();
    } catch (java.lang.NullPointerException npe) {
      System.out.println("scanResultsServlet received an empty results set...no matches whatsoever.");
      return obj;
    }
    System.out.println("scanresultsServlet: I successfully read in the byte array!");
    return obj;

  }


  public void doGet(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
    doPost(request, response);
  }

  public boolean writeXML(HttpServletRequest request, Vector results, String num, String newEncDate, String newEncShark, String newEncSize) {
    String context="context0";
    context=ServletUtilities.getContext(request);
    try {
      System.out.println("Prepping to write XML file for encounter " + num);

      //now setup the XML write for the encounter
      int resultsSize = results.size();
      MatchObject[] matches = new MatchObject[resultsSize];
      for (int a = 0; a < resultsSize; a++) {
        matches[a] = (MatchObject) results.get(a);
      }
      Arrays.sort(matches, new MatchComparator());
      StringBuffer resultsXML = new StringBuffer();
      Document document = DocumentHelper.createDocument();
      Element root = document.addElement("matchSet");
      root.addAttribute("scanDate", (new java.util.Date()).toString());
      root.addAttribute("R", request.getParameter("R"));
      root.addAttribute("epsilon", request.getParameter("epsilon"));
      root.addAttribute("Sizelim", request.getParameter("Sizelim"));
      root.addAttribute("maxTriangleRotation", request.getParameter("maxTriangleRotation"));
      root.addAttribute("C", request.getParameter("C"));
      for (int i = 0; i < matches.length; i++) {
        MatchObject mo = matches[i];
        Element match = root.addElement("match");
        match.addAttribute("points", (new Double(mo.matchValue)).toString());
        match.addAttribute("adjustedpoints", (new Double(mo.adjustedMatchValue)).toString());
        match.addAttribute("pointBreakdown", mo.pointBreakdown);
        String finalscore = (new Double(mo.matchValue * mo.adjustedMatchValue)).toString();
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

        Element enc = match.addElement("encounter");
        enc.addAttribute("number", mo.encounterNumber);
        enc.addAttribute("date", mo.date);
        enc.addAttribute("sex", mo.catalogSex);
        enc.addAttribute("assignedToShark", mo.getIndividualName());
        enc.addAttribute("size", ((new Double(mo.size)).toString() + " meters"));
        for (int k = 0; k < mo.scores.size(); k++) {
          Element spot = enc.addElement("spot");
          spot.addAttribute("x", (new Double(((VertexPointMatch) mo.scores.get(k)).oldX)).toString());
          spot.addAttribute("y", (new Double(((VertexPointMatch) mo.scores.get(k)).oldY)).toString());
        }
        Element enc2 = match.addElement("encounter");
        enc2.addAttribute("number", num);
        enc2.addAttribute("date", newEncDate);
        enc2.addAttribute("sex", mo.newSex);
        enc2.addAttribute("assignedToShark", newEncShark);
        enc2.addAttribute("size", (newEncSize + " meters"));
        for (int j = 0; j < mo.scores.size(); j++) {
          Element spot = enc2.addElement("spot");
          spot.addAttribute("x", (new Double(((VertexPointMatch) mo.scores.get(j)).newX)).toString());
          spot.addAttribute("y", (new Double(((VertexPointMatch) mo.scores.get(j)).newY)).toString());
        }


      }

      //prep for writing out the XML
      
      //setup data dir
      String rootWebappPath = getServletContext().getRealPath("/");
      File webappsDir = new File(rootWebappPath).getParentFile();
      File shepherdDataDir = new File(webappsDir, CommonConfiguration.getDataDirectoryName(context));
      //if(!shepherdDataDir.exists()){shepherdDataDir.mkdirs();}
      //File encountersDir=new File(shepherdDataDir.getAbsolutePath()+"/encounters");
      //if(!encountersDir.exists()){encountersDir.mkdirs();}

      //in case this is a right-side scan, change file name to save to
      String fileAddition = "";
      if ((request.getParameter("rightSide") != null) && (request.getParameter("rightSide").equals("true"))) {
        fileAddition = "Right";
      }
      //File file=new File((new File(".")).getCanonicalPath()+File.separator+"webapps"+File.separator+"ROOT"+File.separator+"encounters"+File.separator+num+File.separator+"lastFull"+fileAddition+"Scan.xml");
      //File file = new File(encountersDir.getAbsoluteFile()+"/"+ num + "/lastFull" + fileAddition + "Scan.xml");
      File file = new File(Encounter.dir(shepherdDataDir, num) + "/lastFull" + fileAddition + "Scan.xml");


      FileWriter mywriter = new FileWriter(file);
      org.dom4j.io.OutputFormat format = org.dom4j.io.OutputFormat.createPrettyPrint();
      format.setLineSeparator(System.getProperty("line.separator"));
      org.dom4j.io.XMLWriter writer = new org.dom4j.io.XMLWriter(mywriter, format);
      writer.write(document);
      writer.close();
      System.out.println("Successful write.");
      return true;
    } catch (Exception e) {
      System.out.println("Encountered an error trying to write back XML results!");
      e.printStackTrace();
      return false;
    }
  }

  public void doPost(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {

    String context="context0";
    context=ServletUtilities.getContext(request);
    //set up a shepherd for DB transactions
    Shepherd myShepherd = new Shepherd(context);
    myShepherd.setAction("ScanResultsServlet.class");

    //System.out.println("scanResultsServlet: I am starting up.");
    response.setContentType("application/octet-stream");
    //HttpSession session = request.getSession();
   
    
    String num = "";
    String newEncDate = "";
    String newEncShark = "";
    String newEncSize = "";

    myShepherd.beginDBTransaction();
    try {
      num = request.getParameter("number");
      Encounter newEnc = myShepherd.getEncounter(num);
      newEncDate = newEnc.getDate();
      if(newEnc.getIndividualID()!=null){
        newEncShark = newEnc.getIndividualID();
      }
      newEncSize = newEnc.getSize() + " meters";

    } catch (Exception jdoe) {
      jdoe.printStackTrace();
      System.out.println("!!!!DB access problem in scanResultsServlets!!!!");
    }
    myShepherd.rollbackDBTransaction();
    
    
    
    ObjectInputStream inputFromApplet = null;
    try {
      
      PrintWriter out = null;
      //BufferedReader inTest = null;

      // get an input stream from the applet
      inputFromApplet = new ObjectInputStream(request.getInputStream());
      System.out.println("scanResultsServlet: I successfully opened a stream from the applet.");

      // read the serialized results data from applet
      Vector results = (Vector) receiveObject(inputFromApplet);
      System.out.println("scanResultsServlet: I successfully received the Vector, which had " + results.size() + " matchObjects in it.");
      inputFromApplet.close();

      System.out.println("scanResultsServlet: I successfully closed the stream.");

      //myShepherd.matches = results;

      //put results into a cookie in case user selected temporary scan
      //session.setAttribute( num, myShepherd);

      System.out.println("scanResultsServlet: Successfully received data and set cookie.");

      if ((request.getParameter("writeThis") != null) && (request.getParameter("writeThis").equals("true"))) {
        writeXML(request, results, num, newEncDate, newEncShark, newEncSize);
      }

      //send response to applet
      response.setContentType("text/plain"); //setup the servlet output
      out = response.getWriter();
      out.println("success");
      out.close();
      //System.out.println("scanResultsServlet: Transmitted a result of 'success' back to the applet.");


    } catch (Exception e) {
      System.out.println("scanResultsServlet registered the following error...");
      e.printStackTrace();
      if(inputFromApplet!=null){inputFromApplet.close();}

    }

    myShepherd.closeDBTransaction();


  } //end doPost

} //end class
