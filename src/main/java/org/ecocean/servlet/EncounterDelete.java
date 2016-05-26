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
import java.net.URISyntaxException;
import java.util.*;
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
    String context = ServletUtilities.getContext(request);
    String langCode = ServletUtilities.getLanguageCode(request);
    Locale locale = new Locale(langCode);
    Map<String, String> mapI18n = CommonConfiguration.getI18nPropertiesMap("encounterState", langCode, context, false);

    Shepherd myShepherd = new Shepherd(context);
    //set up for response
    response.setContentType("text/html");
    PrintWriter out = response.getWriter();
    boolean locked = false;

    // Prepare for user response.
    String link = "#";
    try {
      link = CommonConfiguration.getServerURL(request, request.getContextPath()) + String.format("/encounters/encounter.jsp?number=%s", request.getParameter("number"));
    }
    catch (URISyntaxException ex) {
    }
    ActionResult actionResult = new ActionResult(locale, "encounter.editField", true, link).setLinkParams(request.getParameter("number"));
    actionResult.setMessageParams(request.getParameter("number"));

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
        actionResult.setSucceeded(false).setMessageOverrideKey("delete-assignedToOccurrence");
      }
      else if ((enc2trash.getIndividualID()==null)||(enc2trash.isAssignedToMarkedIndividual().equals("Unassigned"))) {

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

          actionResult.setMessageOverrideKey("delete");
          List<String> linkParams = new ArrayList<>();
          Map<String, String> states = CommonConfiguration.getIndexedValuesMap("encounterState", context);
          for (Map.Entry<String, String> me : states.entrySet()) {
            linkParams.add(mapI18n.get(me.getValue()));
            linkParams.add(String.format("encounters/searchResults.jsp?state=%s", me.getValue()));
          }
          actionResult.setLink("#").setLinkOverrideKey("delete").setLinkParams(linkParams.toArray());

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
          actionResult.setSucceeded(false).setMessageOverrideKey("locked");
        }
      } else {
        myShepherd.commitDBTransaction();
        actionResult.setSucceeded(false).setMessageOverrideKey("delete-assignedToIndividual");
      }
    } else {
      actionResult.setSucceeded(false);
    }

    // Reply to user.
    request.getSession().setAttribute(ActionResult.SESSION_KEY, actionResult);
    getServletConfig().getServletContext().getRequestDispatcher(ActionResult.JSP_PAGE).forward(request, response);

    out.close();
    myShepherd.closeDBTransaction();
  }
}


