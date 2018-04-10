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

import org.ecocean.CommonConfiguration;
import org.ecocean.Encounter;
import org.ecocean.Shepherd;

import javax.servlet.ServletConfig;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import java.io.*;

//import com.poet.jdo.*;


public class ResurrectDeletedEncounter extends HttpServlet {


  public void init(ServletConfig config) throws ServletException {
    super.init(config);

  }


  public void doGet(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {

    // Here we forward to the appropriate page using the request dispatcher

    //getServletContext().getRequestDispatcher("/Noget.html").forward(req, res);
    doPost(request, response);
  }


  public void doPost(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {

    //initialize shepherd
    String context="context0";
    context=ServletUtilities.getContext(request);
    Shepherd myShepherd = new Shepherd(context);
    myShepherd.setAction("ResurrectDeletedEncounter.class");

    //set up for response
    response.setContentType("text/html");
    PrintWriter out = response.getWriter();
    boolean locked = false;

    //setup variables
    String encounterNumber = "None";
    
    //setup data dir
    String rootWebappPath = getServletContext().getRealPath("/");
    File webappsDir = new File(rootWebappPath).getParentFile();
    File shepherdDataDir = new File(webappsDir, CommonConfiguration.getDataDirectoryName(context));
    //if(!shepherdDataDir.exists()){shepherdDataDir.mkdirs();}
    File encountersDir=new File(shepherdDataDir.getAbsolutePath()+"/encounters");
    //if(!encountersDir.exists()){encountersDir.mkdirs();}


    encounterNumber = request.getParameter("number");

    myShepherd.beginDBTransaction();

    if ((request.getParameter("number") != null) && (!myShepherd.isEncounter(encounterNumber))) {
      myShepherd.rollbackDBTransaction();
      //ok, let's get the encounter object back from the .dat file
      String datFilename = request.getParameter("number") + ".dat";
      //File thisEncounterDat=new File(((new File(".")).getCanonicalPath()).replace('\\','/')+"/"+CommonConfiguration.getImageDirectory()+File.separator+request.getParameter("number")+File.separator+datFilename);
      File thisEncounterDat = new File(Encounter.dir(shepherdDataDir, request.getParameter("number")) + "/" + datFilename);


      if (thisEncounterDat.exists()) {

        try {
          FileInputStream f_in = new FileInputStream(thisEncounterDat);
          ObjectInputStream obj_in = new ObjectInputStream(f_in);

          Encounter restoreMe = (Encounter) obj_in.readObject();
          restoreMe.addComments("<p><em>" + request.getRemoteUser() + " on " + (new java.util.Date()).toString() + "</em><br>" + "Restored this encounter after accidental deletion.");
          String newnum = myShepherd.storeNewEncounter(restoreMe, (request.getParameter("number")));
          //thisEncounterDat.delete();

        } catch (Exception eres) {
          locked = true;
          myShepherd.rollbackDBTransaction();
          myShepherd.closeDBTransaction();
          eres.printStackTrace();
        }


        if (!locked) {

          out.println(ServletUtilities.getHeader(request));
          out.println("<strong>Success!</strong> I have successfully restored encounter " + request.getParameter("number") + " from accidental deletion.</p>");

          out.println("<p><a href=\""+request.getScheme()+"://" + CommonConfiguration.getURLLocation(request) + "/encounters/encounter.jsp?number=" + encounterNumber + "\">Return to encounter " + encounterNumber + "</a></p>\n");
          out.println(ServletUtilities.getFooter(context));
          //String message="The matched by type for encounter "+encounterNumber+" was changed from "+prevMatchedBy+" to "+matchedBy+".";
          //informInterestedParties(encounterNumber, message);
        } else {

          out.println(ServletUtilities.getHeader(request));
          out.println("<strong>Failure!</strong> This encounter cannot be restored due to an unknown error. Please contact the webmaster.");

          //out.println("<p><a href=\"http://"+CommonConfiguration.getURLLocation()+"/encounters/encounter.jsp?number="+encounterNumber+"\">Return to encounter "+encounterNumber+"</a></p>\n");
          out.println(ServletUtilities.getFooter(context));

        }

      } else {
        out.println(ServletUtilities.getHeader(request));
        out.println("<strong>Failure!</strong> I could not find the DAT file to restore this encounter from.");

        //out.println("<p><a href=\"http://"+CommonConfiguration.getURLLocation()+"/encounters/encounter.jsp?number="+encounterNumber+"\">Return to encounter "+encounterNumber+"</a></p>\n");
        out.println(ServletUtilities.getFooter(context));

      }

    } else {

      out.println(ServletUtilities.getHeader(request));
      out.println("<strong>Error:</strong> I was unable to resurrect the encounter because I did not know which encounter you were referring to, or this encounter still exists in the database!");
      //out.println("<p><a href=\"http://"+CommonConfiguration.getURLLocation()+"/individuals.jsp?number="+request.getParameter("shark")+"\">Return to shark "+request.getParameter("shark")+"</a></p>\n");
      out.println(ServletUtilities.getFooter(context));

    }
    out.close();
  }


}
	
	
