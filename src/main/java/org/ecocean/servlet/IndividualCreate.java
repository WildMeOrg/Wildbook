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

import org.ecocean.CommonConfiguration;

import javax.servlet.ServletConfig;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import java.io.File;
import java.io.IOException;
import java.io.PrintWriter;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ThreadPoolExecutor;


public class IndividualCreate extends HttpServlet {

  public void init(ServletConfig config) throws ServletException {
    super.init(config);
  }


  public void doGet(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
    doPost(request, response);
  }


  private void setDateLastModified(Encounter enc) {
    String strOutputDateTime = ServletUtilities.getDate();
    System.out.println("ServletUtilities.getDate output: "+strOutputDateTime);
    enc.setDWCDateLastModified(strOutputDateTime);
  }


  public void doPost(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
    request.setCharacterEncoding("UTF-8");
    String context="context0";
    context=ServletUtilities.getContext(request);
    String langCode = ServletUtilities.getLanguageCode(request);
    Shepherd myShepherd = new Shepherd(context);
    myShepherd.setAction("IndividualCreate.class");
    //set up for response
    response.setContentType("text/html");
    PrintWriter out = response.getWriter();
    boolean locked = false;

    boolean isOwner = true;
    
    //setup data dir
    String rootWebappPath = getServletContext().getRealPath("/");
    File webappsDir = new File(rootWebappPath).getParentFile();
    File shepherdDataDir = new File(webappsDir, CommonConfiguration.getDataDirectoryName(context));
    if(!shepherdDataDir.exists()){shepherdDataDir.mkdirs();}
    File individualsDir=new File(shepherdDataDir.getAbsolutePath()+"/individuals");
    if(!individualsDir.exists()){individualsDir.mkdirs();}

    String newIndividualID="";
    if(request.getParameter("individual")!=null){
      newIndividualID=request.getParameter("individual");
      
      //strip out problematic characters
      newIndividualID=ServletUtilities.cleanFileName(newIndividualID);
      
    }


    //create a new MarkedIndividual from an encounter

    if ( (request.getParameter("number") != null) &&  (!newIndividualID.trim().equals(""))) {
      myShepherd.beginDBTransaction();
      Encounter enc2make = myShepherd.getEncounter(request.getParameter("number").trim());
      setDateLastModified(enc2make);

      String belongsTo = enc2make.getIndividualID();
      String submitter = enc2make.getSubmitterEmail();
      String photographer = enc2make.getPhotographerEmail();
      String informers = enc2make.getInformOthers();
      
      boolean ok2add=true;

      if (!(myShepherd.isMarkedIndividual(newIndividualID))) {


        if ((belongsTo == null) && (newIndividualID != null)) {
          MarkedIndividual newShark = null;
          try {
            newShark = new MarkedIndividual(newIndividualID, enc2make);
            enc2make.assignToMarkedIndividual(newIndividualID);
            enc2make.setMatchedBy("Unmatched first encounter");
            newShark.addComments("<p><em>" + request.getRemoteUser() + " on " + (new java.util.Date()).toString() + "</em><br>" + "Created " + newIndividualID + ".</p>");
            newShark.setDateTimeCreated(ServletUtilities.getDate());
            newShark.refreshDependentProperties(context);
            
            ok2add=myShepherd.addMarkedIndividual(newShark);
            
            enc2make.addComments("<p><em>" + request.getRemoteUser() + " on " + (new java.util.Date()).toString() + "</em><br>" + "Added to newly marked individual " + newIndividualID + ".</p>");
          } 
          catch (Exception le) {
            locked = true;
            le.printStackTrace();
            myShepherd.rollbackDBTransaction();
            myShepherd.closeDBTransaction();
          }

          if (!locked&&ok2add) {
            myShepherd.commitDBTransaction();
            myShepherd.closeDBTransaction();
            if (request.getParameter("noemail") == null) {

              ThreadPoolExecutor es = MailThreadExecutorService.getExecutorService();

              // Notify new-submissions address (try "newsub" template, or fallback to standard)
              Map<String, String> tagMap = NotificationMailer.createBasicTagMap(request, newShark, enc2make);
              String mailTo = CommonConfiguration.getNewSubmissionEmail(context);
              NotificationMailer mailer = new NotificationMailer(context, langCode, mailTo, "individualCreate", tagMap);
              mailer.appendToSubject(" (sent to submitters)");
      			  es.execute(mailer);

      			  // Notify submitters, photographers, and informOthers values
              Set<String> cSubmitters = new HashSet<>();
              if (submitter != null)
                cSubmitters.addAll(NotificationMailer.splitEmails(submitter));
              if (photographer != null)
                cSubmitters.addAll(NotificationMailer.splitEmails(photographer));
              if (informers != null)
                cSubmitters.addAll(NotificationMailer.splitEmails(informers));
              if (newShark != null)
                tagMap.put(NotificationMailer.EMAIL_NOTRACK, "individual=" + newShark.getIndividualID());
              for (String emailTo : cSubmitters) {
                tagMap.put(NotificationMailer.EMAIL_HASH_TAG, Encounter.getHashOfEmailString(emailTo));
                es.execute(new NotificationMailer(context, langCode, emailTo, "individualCreate", tagMap));
              }
              es.shutdown();

              String rssTitle = "New marked individual: " + newIndividualID;
              String rssLink = request.getScheme()+"://" + CommonConfiguration.getURLLocation(request) + "/individuals.jsp?number=" + newIndividualID;
              String rssDescription = newIndividualID + " has been added.";
              //File rssFile = new File(getServletContext().getRealPath(("/"+context+"/rss.xml")));

              
              File rssFile = new File(shepherdDataDir,"rss.xml");

              
              ServletUtilities.addRSSEntry(rssTitle, rssLink, rssDescription, rssFile);
              //File atomFile = new File(getServletContext().getRealPath(("/"+context+"/atom.xml")));
              File atomFile = new File(shepherdDataDir,"atom.xml");

              
              ServletUtilities.addATOMEntry(rssTitle, rssLink, rssDescription, atomFile,context);
              
            }
            //set up the directory for this individual
            File thisSharkDir = new File(individualsDir, newIndividualID);


            if (!(thisSharkDir.exists())) {
              thisSharkDir.mkdirs();
            }
            ;

            //output success statement
            out.println(ServletUtilities.getHeader(request));
            out.println("<strong>Success:</strong> Encounter " + request.getParameter("number") + " was successfully used to create <strong>" + newIndividualID + "</strong>.");
            out.println("<p><a href=\""+request.getScheme()+"://" + CommonConfiguration.getURLLocation(request) + "/encounters/encounter.jsp?number=" + request.getParameter("number") + "\">Return to encounter #" + request.getParameter("number") + "</a></p>\n");
            out.println("<p><a href=\""+request.getScheme()+"://" + CommonConfiguration.getURLLocation(request) + "/individuals.jsp?number=" + newIndividualID + "\">View <strong>" + newIndividualID + "</strong></a></p>\n");
            out.println(ServletUtilities.getFooter(context));
            String message = "Encounter " + request.getParameter("number") + " was identified as a new individual. The new individual has been named " + newIndividualID + ".";
            if (request.getParameter("noemail") == null) {
              ServletUtilities.informInterestedParties(request, request.getParameter("number"), message,context);
            }
          } else {
            out.println(ServletUtilities.getHeader(request));
            out.println("<strong>Failure:</strong> Encounter " + request.getParameter("number") + " was NOT used to create a new individual. This encounter is currently being modified by another user. Please go back and try to create the new individual again in a few seconds.");
            out.println("<p><a href=\""+request.getScheme()+"://" + CommonConfiguration.getURLLocation(request) + "/encounters/encounter.jsp?number=" + request.getParameter("number") + "\">Return to encounter #" + request.getParameter("number") + "</a></p>\n");
            out.println("<p><a href=\""+request.getScheme()+"://" + CommonConfiguration.getURLLocation(request) + "/individuals.jsp?number=" + newIndividualID + "\">View <strong>" + newIndividualID + "</strong></a></p>\n");
            out.println(ServletUtilities.getFooter(context));

          }


        } else {

          myShepherd.rollbackDBTransaction();
          myShepherd.closeDBTransaction();

        }

      } else if ((myShepherd.isMarkedIndividual(newIndividualID))) {
        myShepherd.rollbackDBTransaction();
        myShepherd.closeDBTransaction();
        out.println(ServletUtilities.getHeader(request));
        out.println("<strong>Error:</strong> A marked individual by this name already exists in the database. Select a different name and try again.");
        out.println("<p><a href=\""+request.getScheme()+"://" + CommonConfiguration.getURLLocation(request) + "/encounters/encounter.jsp?number=" + request.getParameter("number") + "\">Return to encounter #" + request.getParameter("number") + "</a></p>\n");
        out.println(ServletUtilities.getFooter(context));

      } else {
        myShepherd.rollbackDBTransaction();
        myShepherd.closeDBTransaction();
        out.println(ServletUtilities.getHeader(request));
        out.println("<strong>Error:</strong> You cannot make a new marked individual from this encounter because it is already assigned to another marked individual. Remove it from its previous individual if you want to re-assign it elsewhere.");
        out.println("<p><a href=\""+request.getScheme()+"://" + CommonConfiguration.getURLLocation(request) + "/encounters/encounter.jsp?number=" + request.getParameter("number") + "\">Return to encounter #" + request.getParameter("number") + "</a></p>\n");
        out.println(ServletUtilities.getFooter(context));
      }


    } 
    else {
      out.println(ServletUtilities.getHeader(request));
      out.println("<strong>Error:</strong> I didn't receive enough data to create a marked individual from this encounter.");
      out.println(ServletUtilities.getFooter(context));
      myShepherd.closeDBTransaction();
    }


    out.close();
    
  }
}


