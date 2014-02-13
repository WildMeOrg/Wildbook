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
import java.io.File;
import java.io.IOException;
import java.io.PrintWriter;
import java.util.StringTokenizer;
import java.util.Vector;
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
    enc.setDWCDateLastModified(strOutputDateTime);
  }


  public void doPost(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
    Shepherd myShepherd = new Shepherd();
    //set up for response
    response.setContentType("text/html");
    PrintWriter out = response.getWriter();
    boolean locked = false;

    boolean isOwner = true;
    
    //setup data dir
    String rootWebappPath = getServletContext().getRealPath("/");
    File webappsDir = new File(rootWebappPath).getParentFile();
    File shepherdDataDir = new File(webappsDir, CommonConfiguration.getDataDirectoryName());
    if(!shepherdDataDir.exists()){shepherdDataDir.mkdir();}
    File individualsDir=new File(shepherdDataDir.getAbsolutePath()+"/individuals");
    if(!individualsDir.exists()){individualsDir.mkdir();}

    String newIndividualID="";
    if(request.getParameter("individual")!=null){
      newIndividualID=request.getParameter("individual");
      
      //strip out problematic characters
      newIndividualID=ServletUtilities.cleanFileName(newIndividualID);
      
    }


    //create a new MarkedIndividual from an encounter

    if ( (request.getParameter("number") != null) &&  (!newIndividualID.trim().equals(""))) {
      myShepherd.beginDBTransaction();
      Encounter enc2make = myShepherd.getEncounter(newIndividualID);
      setDateLastModified(enc2make);

      String belongsTo = enc2make.isAssignedToMarkedIndividual();
      String submitter = enc2make.getSubmitterEmail();
      String photographer = enc2make.getPhotographerEmail();
      String informers = enc2make.getInformOthers();
      
      boolean ok2add=true;

      if (!(myShepherd.isMarkedIndividual(newIndividualID))) {


        if ((belongsTo.equals("Unassigned")) && (newIndividualID != null)) {
          try {
            MarkedIndividual newShark = new MarkedIndividual(newIndividualID, enc2make);
            enc2make.assignToMarkedIndividual(newIndividualID);
            enc2make.setMatchedBy("Unmatched first encounter");
            newShark.addComments("<p><em>" + request.getRemoteUser() + " on " + (new java.util.Date()).toString() + "</em><br>" + "Created " + newIndividualID + ".</p>");
            newShark.setDateTimeCreated(ServletUtilities.getDate());
            
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
              //send the e-mail
              Vector e_images = new Vector();
              String emailUpdate = "\nNewly marked: " + newIndividualID+ "\nhttp://" + CommonConfiguration.getURLLocation(request) + "/individuals.jsp?number=" + newIndividualID + "\n\nEncounter: " + request.getParameter("number") + "\nhttp://" + CommonConfiguration.getURLLocation(request) + "/encounters/encounter.jsp?number=" + request.getParameter("number") + "\n";
              String thanksmessage = ServletUtilities.getText("createdMarkedIndividual.txt") + emailUpdate;
              ThreadPoolExecutor es = MailThreadExecutorService.getExecutorService();

              //notify the admins
              es.execute(new NotificationMailer(CommonConfiguration.getMailHost(), CommonConfiguration.getAutoEmailAddress(), CommonConfiguration.getNewSubmissionEmail(), ("Encounter update: " + request.getParameter("number")), thanksmessage, e_images));

              if (submitter.indexOf(",") != -1) {
                StringTokenizer str = new StringTokenizer(submitter, ",");
                while (str.hasMoreTokens()) {
                  String token = str.nextToken().trim();
                  if (!token.equals("")) {
                    String personalizedThanksMessage = CommonConfiguration.appendEmailRemoveHashString(request, thanksmessage, token);

                    es.execute(new NotificationMailer(CommonConfiguration.getMailHost(), CommonConfiguration.getAutoEmailAddress(), token, ("Encounter update: " + request.getParameter("number")), personalizedThanksMessage, e_images));
                  }
                }
              } else {
                String personalizedThanksMessage = CommonConfiguration.appendEmailRemoveHashString(request, thanksmessage, submitter);

                es.execute(new NotificationMailer(CommonConfiguration.getMailHost(), CommonConfiguration.getAutoEmailAddress(), submitter, ("Encounter update: " + request.getParameter("number")), personalizedThanksMessage, e_images));
              }

              if (photographer.indexOf(",") != -1) {
                StringTokenizer str = new StringTokenizer(photographer, ",");
                while (str.hasMoreTokens()) {
                  String token = str.nextToken().trim();
                  if (!token.equals("")) {
                    String personalizedThanksMessage = CommonConfiguration
                      .appendEmailRemoveHashString(request, thanksmessage, token);

                    es.execute(new NotificationMailer(CommonConfiguration.getMailHost(), CommonConfiguration.getAutoEmailAddress(), token, ("Encounter update: " + request.getParameter("number")), personalizedThanksMessage, e_images));
                  }
                }
              } else {
                String personalizedThanksMessage = CommonConfiguration
                  .appendEmailRemoveHashString(request, thanksmessage, photographer);

                es.execute(new NotificationMailer(CommonConfiguration.getMailHost(), CommonConfiguration.getAutoEmailAddress(), photographer, ("Encounter update: " + request.getParameter("number")), personalizedThanksMessage, e_images));
              }


              if ((informers != null) && (!informers.equals(""))) {

                if (informers.indexOf(",") != -1) {
                  StringTokenizer str = new StringTokenizer(informers, ",");
                  while (str.hasMoreTokens()) {
                    String token = str.nextToken().trim();
                    if (!token.equals("")) {
                      String personalizedThanksMessage = CommonConfiguration
                        .appendEmailRemoveHashString(request, thanksmessage, token);

                      es.execute(new NotificationMailer(CommonConfiguration.getMailHost(), CommonConfiguration.getAutoEmailAddress(), token, ("Encounter update: " + request.getParameter("number")), personalizedThanksMessage, e_images));
                    }
                  }
                } else {
                  String personalizedThanksMessage = CommonConfiguration.appendEmailRemoveHashString(request, thanksmessage, informers);

                  es.execute(new NotificationMailer(CommonConfiguration.getMailHost(), CommonConfiguration.getAutoEmailAddress(), informers, ("Encounter update: " + request.getParameter("number")), personalizedThanksMessage, e_images));
                }


              }


              String rssTitle = "New marked individual: " + newIndividualID;
              String rssLink = "http://" + CommonConfiguration.getURLLocation(request) + "/individuals.jsp?number=" + newIndividualID;
              String rssDescription = newIndividualID + " has been added.";
              File rssFile = new File(getServletContext().getRealPath(("/rss.xml")));

              ServletUtilities.addRSSEntry(rssTitle, rssLink, rssDescription, rssFile);
              File atomFile = new File(getServletContext().getRealPath(("/atom.xml")));

              ServletUtilities.addATOMEntry(rssTitle, rssLink, rssDescription, atomFile);
              
              //shutdown the mailing threadpool
              es.shutdown();
              
            }
            //set up the directory for this individual
            File thisSharkDir = new File(individualsDir, newIndividualID);


            if (!(thisSharkDir.exists())) {
              thisSharkDir.mkdir();
            }
            ;

            //output success statement
            out.println(ServletUtilities.getHeader(request));
            out.println("<strong>Success:</strong> Encounter " + request.getParameter("number") + " was successfully used to create <strong>" + newIndividualID + "</strong>.");
            out.println("<p><a href=\"http://" + CommonConfiguration.getURLLocation(request) + "/encounters/encounter.jsp?number=" + request.getParameter("number") + "\">Return to encounter #" + request.getParameter("number") + "</a></p>\n");
            out.println("<p><a href=\"http://" + CommonConfiguration.getURLLocation(request) + "/individuals.jsp?number=" + newIndividualID + "\">View <strong>" + newIndividualID + "</strong></a></p>\n");
            out.println(ServletUtilities.getFooter());
            String message = "Encounter #" + request.getParameter("number") + " was identified as a new individual. The new individual has been named " + newIndividualID + ".";
            if (request.getParameter("noemail") == null) {
              ServletUtilities.informInterestedParties(request, request.getParameter("number"), message);
            }
          } else {
            out.println(ServletUtilities.getHeader(request));
            out.println("<strong>Failure:</strong> Encounter " + request.getParameter("number") + " was NOT used to create a new individual. This encounter is currently being modified by another user. Please go back and try to create the new individual again in a few seconds.");
            out.println("<p><a href=\"http://" + CommonConfiguration.getURLLocation(request) + "/encounters/encounter.jsp?number=" + request.getParameter("number") + "\">Return to encounter #" + request.getParameter("number") + "</a></p>\n");
            out.println("<p><a href=\"http://" + CommonConfiguration.getURLLocation(request) + "/individuals.jsp?number=" + newIndividualID + "\">View <strong>" + newIndividualID + "</strong></a></p>\n");
            out.println(ServletUtilities.getFooter());

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
        out.println("<p><a href=\"http://" + CommonConfiguration.getURLLocation(request) + "/encounters/encounter.jsp?number=" + request.getParameter("number") + "\">Return to encounter #" + request.getParameter("number") + "</a></p>\n");
        out.println(ServletUtilities.getFooter());

      } else {
        myShepherd.rollbackDBTransaction();
        myShepherd.closeDBTransaction();
        out.println(ServletUtilities.getHeader(request));
        out.println("<strong>Error:</strong> You cannot make a new marked individual from this encounter because it is already assigned to another marked individual. Remove it from its previous individual if you want to re-assign it elsewhere.");
        out.println("<p><a href=\"http://" + CommonConfiguration.getURLLocation(request) + "/encounters/encounter.jsp?number=" + request.getParameter("number") + "\">Return to encounter #" + request.getParameter("number") + "</a></p>\n");
        out.println(ServletUtilities.getFooter());
      }


    } 
    else {
      out.println(ServletUtilities.getHeader(request));
      out.println("<strong>Error:</strong> I didn't receive enough data to create a marked individual from this encounter.");
      out.println(ServletUtilities.getFooter());
    }


    out.close();
    
  }
}


