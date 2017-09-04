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

import org.ecocean.*;

import javax.servlet.ServletConfig;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import java.io.*;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Vector;
import java.util.concurrent.ThreadPoolExecutor;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;


public class EncounterDelete extends HttpServlet {
  /** SLF4J logger instance for writing log entries. */
  public static Logger log = LoggerFactory.getLogger(EncounterDelete.class);

  public void init(ServletConfig config) throws ServletException {
    super.init(config);
  }


  public void doGet(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
    doPost(request, response);
  }


  private void setDateLastModified(Encounter enc) {
    String strOutputDateTime = ServletUtilities.getDate();
    enc.setDWCDateLastModified(strOutputDateTime);
  }


  public void doPost(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
    String context="context0";
    context=ServletUtilities.getContext(request);
    String langCode = ServletUtilities.getLanguageCode(request);
    Shepherd myShepherd = new Shepherd(context);
    myShepherd.setAction("EncounterDelete.class");
    //set up for response
    response.setContentType("text/html");
    PrintWriter out = response.getWriter();
    boolean locked = false;

    //setup data dir
    String rootWebappPath = getServletContext().getRealPath("/");
    File webappsDir = new File(rootWebappPath).getParentFile();
    File shepherdDataDir = new File(webappsDir, CommonConfiguration.getDataDirectoryName(context));
    if(!shepherdDataDir.exists()){shepherdDataDir.mkdirs();}
    File encountersDir=new File(shepherdDataDir.getAbsolutePath()+"/encounters");
    if(!encountersDir.exists()){encountersDir.mkdirs();}
    
    boolean isOwner = true;


    if (request.getParameter("number") != null) {
      String message = "Encounter " + request.getParameter("number") + " was deleted from the database.";
      ServletUtilities.informInterestedParties(request, request.getParameter("number"), message,context);
      myShepherd.beginDBTransaction();
      Encounter enc2trash = myShepherd.getEncounter(request.getParameter("number"));
      setDateLastModified(enc2trash);

      if((enc2trash.getOccurrenceID()!=null)&&(myShepherd.isOccurrence(enc2trash.getOccurrenceID()))) {
        myShepherd.commitDBTransaction();
        out.println(ServletUtilities.getHeader(request));
        out.println("Encounter " + request.getParameter("number") + " is assigned to an Occurrence and cannot be deleted until it has been removed from that occurrence.");
        out.println("<p><a href=\"http://" + CommonConfiguration.getURLLocation(request) + "/encounters/encounter.jsp?number=" + request.getParameter("number") + "\">Return to encounter " + request.getParameter("number") + "</a>.</p>\n");
        
        out.println(ServletUtilities.getFooter(context));
      }
      else if (enc2trash.getIndividualID()==null) {

        try {

          Encounter backUpEnc = myShepherd.getEncounterDeepCopy(enc2trash.getEncounterNumber());

          String savedFilename = request.getParameter("number") + ".dat";
          File thisEncounterDir = new File(Encounter.dir(shepherdDataDir, request.getParameter("number")));
          if(!thisEncounterDir.exists()){
            thisEncounterDir.mkdirs();
            System.out.println("Trying to create the folder to store a dat file in EncounterDelete2: "+thisEncounterDir.getAbsolutePath());
          
          }

          File serializedBackup = new File(thisEncounterDir, savedFilename);
          FileOutputStream fout = new FileOutputStream(serializedBackup);
          ObjectOutputStream oos = new ObjectOutputStream(fout);
          oos.writeObject(backUpEnc);
          oos.close();
          
          if((enc2trash.getOccurrenceID()!=null)&&(myShepherd.isOccurrence(enc2trash.getOccurrenceID()))) {
            Occurrence occur=myShepherd.getOccurrence(enc2trash.getOccurrenceID());
            occur.removeEncounter(enc2trash);
            enc2trash.setOccurrenceID(null);
            
            //delete Occurrence if it's last encounter has been removed.
            if(occur.getNumberEncounters()==0){
              myShepherd.throwAwayOccurrence(occur);
            }
            
            myShepherd.commitDBTransaction();
            myShepherd.beginDBTransaction();
     
          }

          //record who deleted this encounter
          enc2trash.addComments("<p><em>" + request.getRemoteUser() + " on " + (new java.util.Date()).toString() + "</em><br>" + "Deleted this encounter from the database.");
          myShepherd.commitDBTransaction();

          //now delete for good
          myShepherd.beginDBTransaction();
          myShepherd.throwAwayEncounter(enc2trash);


        } catch (Exception edel) {
          locked = true;
          log.warn("Failed to serialize encounter: " + request.getParameter("number"), edel);
          edel.printStackTrace();
          myShepherd.rollbackDBTransaction();

        }


        if (!locked) {
          myShepherd.commitDBTransaction();

          //log it
          Logger log = LoggerFactory.getLogger(EncounterDelete.class);
		  log.info("Click to restore deleted encounter: <a href=\"http://" + CommonConfiguration.getURLLocation(request) + "/ResurrectDeletedEncounter?number=" + request.getParameter("number")+"\">"+request.getParameter("number")+"</a>");


          out.println(ServletUtilities.getHeader(request));
          out.println("<strong>Success:</strong> I have removed encounter " + request.getParameter("number") + " from the database. If you have deleted this encounter in error, please contact the webmaster and reference encounter " + request.getParameter("number") + " to have it restored.");
          List<String> allStates=CommonConfiguration.getIndexedPropertyValues("encounterState",context);
          int allStatesSize=allStates.size();
          if(allStatesSize>0){
            for(int i=0;i<allStatesSize;i++){
              String stateName=allStates.get(i);
              out.println("<p><a href=\"encounters/searchResults.jsp?state="+stateName+"\">View all "+stateName+" encounters</a></font></p>");   
            }
          }
          
          out.println(ServletUtilities.getFooter(context));

          // Notify new-submissions address
          Map<String, String> tagMap = NotificationMailer.createBasicTagMap(request, enc2trash);
          tagMap.put("@USER@", request.getRemoteUser());
          tagMap.put("@ENCOUNTER_ID@", request.getParameter("number"));
          String mailTo = CommonConfiguration.getNewSubmissionEmail(context);
          NotificationMailer mailer = new NotificationMailer(context, null, mailTo, "encounterDelete", tagMap);
          ThreadPoolExecutor es = MailThreadExecutorService.getExecutorService();
          es.execute(mailer);
          es.shutdown();
        } 
        else {
          out.println(ServletUtilities.getHeader(request));
          out.println("<strong>Failure:</strong> I have NOT removed encounter " + request.getParameter("number") + " from the database. An exception occurred in the deletion process.");
          out.println("<p><a href=\"http://" + CommonConfiguration.getURLLocation(request) + "/encounters/encounter.jsp?number=" + request.getParameter("number") + "\">Return to encounter " + request.getParameter("number") + "</a>.</p>\n");
          
          List<String> allStates=CommonConfiguration.getIndexedPropertyValues("encounterState",context);
          int allStatesSize=allStates.size();
          if(allStatesSize>0){
            for(int i=0;i<allStatesSize;i++){
              String stateName=allStates.get(i);
              out.println("<p><a href=\"encounters/searchResults.jsp?state="+stateName+"\">View all "+stateName+" encounters</a></font></p>");   
            }
          }
          out.println(ServletUtilities.getFooter(context));


        }
      } else {
        myShepherd.commitDBTransaction();
        out.println(ServletUtilities.getHeader(request));
        out.println("Encounter " + request.getParameter("number") + " is assigned to a Marked Individual and cannot be deleted until it has been removed from that individual.");
        out.println("<p><a href=\"http://" + CommonConfiguration.getURLLocation(request) + "/encounters/encounter.jsp?number=" + request.getParameter("number") + "\">Return to encounter " + request.getParameter("number") + "</a>.</p>\n");
        
        out.println(ServletUtilities.getFooter(context));
      }
    } else {
      out.println(ServletUtilities.getHeader(request));
      out.println("<strong>Error:</strong> I don't know which encounter you're trying to remove.");
      out.println(ServletUtilities.getFooter(context));

    }


    out.close();
    myShepherd.closeDBTransaction();
  }
}


