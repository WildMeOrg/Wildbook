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

import org.ecocean.Encounter;
import org.ecocean.Shepherd;
import org.ecocean.grid.MatchComparator;
import org.ecocean.grid.MatchObject;
import org.ecocean.grid.VertexPointMatch;

import javax.servlet.ServletConfig;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import java.io.IOException;
import java.io.PrintWriter;
import java.util.Arrays;
import java.util.Vector;

//import servletbible.utils.*;
//import javax.jdo.*;
//import com.poet.jdo.*;
//import com.oreilly.servlet.multipart.*;
//import java.lang.StringBuffer;


public class TrackerFeed extends HttpServlet {

  public void init(ServletConfig config) throws ServletException {
    super.init(config);
    System.out.println("Initiating trackerFeed servlet to display results...");
  }


  public void doGet(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
    doPost(request, response);
  }


  public void doPost(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {

    
    String context="context0";
    context=ServletUtilities.getContext(request);
    //set up the needed shepherds
    Shepherd newEncShepherd = new Shepherd(context);
    Shepherd myShepherd = new Shepherd(context);

    System.out.println("Starting POST of trackerFeed servlet...");

    String num = request.getParameter("number");

    //set up for response
    response.setContentType("text/xml");
    PrintWriter out = response.getWriter();

    //begin processing
    newEncShepherd.beginDBTransaction();
    Encounter newEnc = newEncShepherd.getEncounter(num);


    String newEncDate = newEnc.getDate();
    String newEncShark = "";
    if(newEnc.getIndividualID()!=null){
      newEncShark=newEnc.getIndividualID();
    }
    String newEncSize = (new Double(newEnc.getSize())).toString() + " meters";
    String newEncLocation = newEnc.getVerbatimLocality();
    newEncShepherd.rollbackDBTransaction();

    System.out.println("I have setup my new encounter details.");

    try {

      Vector matches2 = myShepherd.matches;
      int resultsSize = matches2.size();
      //System.out.println(resultsSize);
      MatchObject[] matches = new MatchObject[resultsSize];
      for (int a = 0; a < resultsSize; a++) {
        matches[a] = (MatchObject) matches2.get(a);
      }
      Arrays.sort(matches, new MatchComparator());
      //System.out.println("trackerFeed is returning num matches: "+myShepherd.matches.size());

      out.println("<?xml version=\"1.0\" encoding=\"UTF-8\"?>");
      out.println("<matchSet>");

      if (matches[0].matchValue > 0) {


        //now put out the results one at a time
        for (int i = 0; i < matches.length; i++) {
          MatchObject mo = matches[i];
          String finalscore = (new Double(mo.matchValue * mo.adjustedMatchValue)).toString();
          if (finalscore.length() > 7) finalscore = finalscore.substring(0, 6);
          out.println("<match points=\"" + finalscore + "\" finalscore=\"" + finalscore + "\">");

          String moDate = "";
          String moSex = "";
          String moIndividualName = "";
          String moSize = "";
          String moLocation = "";
          myShepherd.beginDBTransaction();
          Encounter enc = myShepherd.getEncounter(mo.encounterNumber);
          moDate = enc.getDate();
          
          
          if(enc.getSex()!=null){moSex = enc.getSex();}
          else{moSex="unknown";}
          
          moLocation = enc.getVerbatimLocality();
          moIndividualName = ServletUtilities.handleNullString(enc.getIndividualID());
          if (mo.getSize() > 0) {
            moSize = Double.toString(mo.getSize());
          }
          myShepherd.rollbackDBTransaction();

          out.println("<encounter number=\"" + mo.encounterNumber + "\" date=\"" + moDate + "\" sex=\"" + moSex + "\" assignedToShark=\"" + moIndividualName + "\" size=\"" + moSize + " meters\" location=\"" + moLocation + "\">");
          for (int k = 0; k < mo.scores.size(); k++) {

            //map the spots
            out.println("     <spot x=\"" + ((VertexPointMatch) mo.scores.get(k)).oldX + "\" y=\"" + ((VertexPointMatch) mo.scores.get(k)).oldY + "\" />");

          }
          out.println("</encounter>");
          out.println("<encounter number=\"" + num + "\" date=\"" + newEncDate + "\" sex=\"" + mo.newSex + "\" assignedToShark=\"" + newEncShark + "\" size=\"" + newEncSize + "\" location=\"" + newEncLocation + "\">");
          for (int j = 0; j < mo.scores.size(); j++) {

            //map the spots
            out.println("     <spot x=\"" + ((VertexPointMatch) mo.scores.get(j)).newX + "\" y=\"" + ((VertexPointMatch) mo.scores.get(j)).newY + "\" />");

          }
          out.println("</encounter>");

          out.println("</match>");
        }

      }

      out.println("</matchSet>");
      out.close();
    } catch (Exception e) {
      System.out.println("FAILURE IN SERVLET: trackerFeed");
      e.printStackTrace();
    }
    newEncShepherd.closeDBTransaction();
    newEncShepherd = null;
    myShepherd = null;

  }
}
	
	
