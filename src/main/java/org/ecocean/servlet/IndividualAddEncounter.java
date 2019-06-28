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

import org.datanucleus.api.jdo.JDOPersistenceManager;
import org.datanucleus.api.rest.RESTUtils;
import org.ecocean.*;
import org.ecocean.ai.nmt.azure.DetectTranslate;

import javax.servlet.ServletConfig;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import java.io.File;
import java.io.IOException;
import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ThreadPoolExecutor;

import java.util.Properties;

import javax.jdo.*;


public class IndividualAddEncounter extends HttpServlet {


  public void init(ServletConfig config) throws ServletException {
    super.init(config);
  }

  private void setDateLastModified(Encounter enc) {

    String strOutputDateTime = ServletUtilities.getDate();
    enc.setDWCDateLastModified(strOutputDateTime);
  }

  public void doGet(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
    doPost(request, response);
  }

  public void doPost(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
    request.setCharacterEncoding("UTF-8");
    String context="context0";
    context=ServletUtilities.getContext(request);
    String langCode = ServletUtilities.getLanguageCode(request);
    Shepherd myShepherd = new Shepherd(context);
    myShepherd.setAction("IndividualAddEncounter.class");
    //set up for response
    //response.setContentType("text/html");
    response.setContentType("application/json");
    String responseJSON="";
    PrintWriter out = response.getWriter();
    boolean locked = false, isOwner = true;
    boolean isAssigned = false;

    String action = request.getParameter("action");

    //add encounter to a MarkedIndividual

    String indivID = request.getParameter("individual");
    if ((request.getParameter("number") != null) && (indivID != null) && (request.getParameter("matchType") != null)) {

      String nickname = "";
      myShepherd.beginDBTransaction();
      Encounter enc2add = myShepherd.getEncounter(request.getParameter("number"));
      if (enc2add == null) throw new RuntimeException("invalid encounter id=" + request.getParameter("number"));
      setDateLastModified(enc2add);
     
      boolean newIndy = false;
      if (enc2add.getIndividualID()==null) {
        MarkedIndividual addToMe = null;
        //if we dont already have this individual, we now make it  TODO this may fail because of security (in the future) so we need to take that into consideration
        if (!myShepherd.isMarkedIndividual(indivID)) {
            try {
                newIndy = true;
                addToMe = new MarkedIndividual(indivID, enc2add);
                myShepherd.storeNewMarkedIndividual(addToMe);
                myShepherd.updateDBTransaction();
                addToMe.refreshNamesCache();
                //enc2add.setIndividualID(indivID);
            } 
            catch (Exception ex) {
                ex.printStackTrace();
                myShepherd.rollbackDBTransaction();
                throw new RuntimeException("unable to create new MarkedIndividual " + indivID);
            }
        } else {
           System.out.println("Retrieving an existing individual");
            addToMe = myShepherd.getMarkedIndividual(indivID);
        }

        try {


          boolean sexMismatch = false;
          //myShepherd.beginDBTransaction();
          //if ((addToMe.getNickName() != null) && (!addToMe.getNickName().equals(""))) {
          //  nickname = " ("+addToMe.getNickName() + ")";
          //}
          try {
            if (!addToMe.getEncounters().contains(enc2add)) {
              addToMe.addEncounter(enc2add);
              System.out.println("Now adding the Encounter to the individual");
            }
            enc2add.setMatchedBy(request.getParameter("matchType"));
            enc2add.addComments("<p><em>" + request.getRemoteUser() + " on " + (new java.util.Date()).toString() + "</em><br>" + "Added to " + request.getParameter("individual") + ".</p>");
            addToMe.addComments("<p><em>" + request.getRemoteUser() + " on " + (new java.util.Date()).toString() + "</em><br>" + "Added encounter " + request.getParameter("number") + ".</p>");
            
            
            
            if ((addToMe.getSex()!=null)&&(enc2add.getSex()!=null)&&(!addToMe.getSex().equals(enc2add.getSex()))) {
               //if (((addToMe.getSex().equals("Male")) & (enc2add.getSex().equals("Female"))) || ((addToMe.getSex().equals("Female")) & (enc2add.getSex().equals("Male")))) {
                sexMismatch = true;
              //}
            }
            else if ( ((addToMe.getSex()==null)||(addToMe.getSex().equals("unknown"))) &&(enc2add.getSex()!=null)) {
              addToMe.setSex(enc2add.getSex());
            }
            responseJSON=RESTUtils.getJSONObjectFromPOJO(addToMe, ((JDOPersistenceManager)myShepherd.getPM()).getExecutionContext()).toString();
            
            
            
            try{
              //let's do a YouTube post-back check
              System.out.println("In IndividualAddEncounter trying to fire YouTube..");
              if(enc2add.getOccurrenceID()!=null){
                if(myShepherd.isOccurrence(enc2add.getOccurrenceID())){
                  System.out.println("...In IndividualAddEncounter found an occurrence..");
                  Occurrence occur=myShepherd.getOccurrence(enc2add.getOccurrenceID());
                  //TBD-support more than just en language
                  
                  //determine language for response
                  String ytRemarks=enc2add.getOccurrenceRemarks().trim().toLowerCase();
                  int commentEnd=ytRemarks.indexOf("from youtube video:");
                  if(commentEnd>0){
                    ytRemarks=ytRemarks.substring(commentEnd);
                  }
                  String detectedLanguage="en";
                  try{
                    detectedLanguage= DetectTranslate.detectLanguage(ytRemarks);

                    if(!detectedLanguage.toLowerCase().startsWith("en")){
                      ytRemarks= DetectTranslate.translateToEnglish(ytRemarks);
                    }
                    if(detectedLanguage.startsWith("es")){detectedLanguage="es";}
                    else{detectedLanguage="en";}
                  }
                  catch(Exception e){
                    System.out.println("I hit an exception trying to detect language.");
                    e.printStackTrace();
                  }
                  //end determine language for response

                  
                  
                  Properties ytProps=ShepherdProperties.getProperties("quest.properties", detectedLanguage);
                  String message=ytProps.getProperty("individualAddEncounter").replaceAll("%INDIVIDUAL%", enc2add.getIndividualID());
                  System.out.println("Will post back to YouTube OP this message if appropriate: "+message);
                  YouTube.postOccurrenceMessageToYouTubeIfAppropriate(message, occur, myShepherd, context);
                }
              }
            }
            catch(Exception e){e.printStackTrace();}
            
            
            
          } catch (Exception le) {
            System.out.println("Hit locked exception on action: " + action);
            le.printStackTrace();
            locked = true;
            myShepherd.rollbackDBTransaction();

          }


          if (!locked) {
            
            myShepherd.commitDBTransaction();

            // Retrieve background service for processing emails
            ThreadPoolExecutor es = MailThreadExecutorService.getExecutorService();

            myShepherd.beginDBTransaction();

      			List<String> allAssociatedEmails = addToMe.getAllEmailsToUpdate();

            //inform all encounter submitters for this Marked Individual about the modification to their animal

            if (request.getParameter("noemail") == null) {

              // Specify email template type.
              String emailTemplate = "individualAddEncounter";
              if (newIndy==true) {
                emailTemplate = "individualCreate";
              }
              String emailTemplate2 = "individualUpdate";

              
              // Notify administrator address
              Map<String, String> tagMap = NotificationMailer.createBasicTagMap(request, addToMe, enc2add);
              String mailTo = CommonConfiguration.getAutoEmailAddress(context);
              NotificationMailer mailer = new NotificationMailer(context, langCode, mailTo, emailTemplate, tagMap);
              mailer.appendToSubject(" (sent to submitters)");
      			  es.execute(mailer);

      			  // Notify submitters, photographers, and informOthers values
              Set<String> cSubmitters = new HashSet<>();
              if (enc2add.getSubmitterEmails() != null)cSubmitters.addAll(enc2add.getSubmitterEmails());
              if (enc2add.getPhotographerEmails() != null)cSubmitters.addAll(enc2add.getPhotographerEmails());
              if (enc2add.getInformOthersEmails() != null)cSubmitters.addAll(enc2add.getInformOthersEmails());
              //if (enc2add.getInformOthers() != null)
                //cSubmitters.addAll(NotificationMailer.splitEmails(enc2add.getInformOthersEmails()));
              
              for (String emailTo : cSubmitters) {
                if (!"".equals(emailTo)) {
                  tagMap.put(NotificationMailer.EMAIL_NOTRACK, "number=" + enc2add.getCatalogNumber());
                  tagMap.put(NotificationMailer.EMAIL_HASH_TAG, Encounter.getHashOfEmailString(emailTo));
                  es.execute(new NotificationMailer(context, langCode, emailTo, emailTemplate, tagMap));
                }
              }

      			  // Notify other who need to know
              Set<String> cOthers = new HashSet<>(allAssociatedEmails);
              cOthers.removeAll(cSubmitters);
              //System.out.println("cOthers size is: "+cOthers.size());
              for (String emailTo : cOthers) {
                tagMap.put(NotificationMailer.EMAIL_NOTRACK, "number=" + enc2add.getCatalogNumber());
                tagMap.put(NotificationMailer.EMAIL_HASH_TAG, Encounter.getHashOfEmailString(emailTo));
                //System.out.println("Emailing cOthers member:" +emailTo);
                es.execute(new NotificationMailer(context, langCode, emailTo, emailTemplate2, tagMap));
              }

              // Notify adopters
	            Extent encClass = myShepherd.getPM().getExtent(Adoption.class, true);
	            Query query = myShepherd.getPM().newQuery(encClass);
              List<String> cAdopters = myShepherd.getAdopterEmailsForMarkedIndividual(query, ServletUtilities.handleNullString(addToMe.getIndividualID()));
              query.closeAll();
              cAdopters.removeAll(allAssociatedEmails);
              for (String emailTo : cAdopters) {
                tagMap.put(NotificationMailer.EMAIL_NOTRACK, "number=" + enc2add.getCatalogNumber());
                tagMap.put(NotificationMailer.EMAIL_HASH_TAG, Encounter.getHashOfEmailString(emailTo));
                tagMap.put(NotificationMailer.STANDARD_CONTENT_TAG, tagMap.get("@ENCOUNTER_LINK@"));
                es.execute(new NotificationMailer(context, langCode, emailTo, emailTemplate + "-adopter", tagMap));
              }

              String rssTitle = request.getParameter("individual") + " Resight";
              String rssLink = request.getScheme()+"://" + CommonConfiguration.getURLLocation(request) + "/encounters/encounter.jsp?number=" + request.getParameter("number");
              String rssDescription = request.getParameter("individual") + " was resighted on " + enc2add.getShortDate() + ".";
              //File rssFile = new File(getServletContext().getRealPath(("/"+context+"/rss.xml")));

              //setup data dir
              String rootWebappPath = getServletContext().getRealPath("/");
              File webappsDir = new File(rootWebappPath).getParentFile();
              File shepherdDataDir = new File(webappsDir, CommonConfiguration.getDataDirectoryName(context));
              if(!shepherdDataDir.exists()){shepherdDataDir.mkdirs();}
              File rssFile = new File(shepherdDataDir,"rss.xml");

              ServletUtilities.addRSSEntry(rssTitle, rssLink, rssDescription, rssFile);
              //File atomFile = new File(getServletContext().getRealPath(("/"+context+"/atom.xml")));
              File atomFile = new File(shepherdDataDir,"atom.xml");

              
              ServletUtilities.addATOMEntry(rssTitle, rssLink, rssDescription, atomFile,context);
            }


            myShepherd.rollbackDBTransaction();


            //print successful result notice
            //out.println(ServletUtilities.getHeader(request));
            //out.println("<strong>Success:</strong> Encounter " + request.getParameter("number") + " was successfully added to " + request.getParameter("individual") + ".");
            response.setStatus(HttpServletResponse.SC_OK);
            out.println(responseJSON);
            
            
            //if (sexMismatch) {
            //  out.println("<p><strong>Warning! There is conflict between the designated sex of the new encounter and the designated sex in previous records. You should resolve this conflict for consistency.</strong></p>");
            //}
            //out.println("<p><a href=\""+request.getScheme()+"://" + CommonConfiguration.getURLLocation(request) + "/encounters/encounter.jsp?number=" + request.getParameter("number") + "\">Return to encounter #" + request.getParameter("number") + "</a></p>\n");
            //out.println("<p><a href=\""+request.getScheme()+"://" + CommonConfiguration.getURLLocation(request) + "/individuals.jsp?number=" + request.getParameter("individual") + "\">View individual " + request.getParameter("individual") + "</a></p>\n");
            //out.println(ServletUtilities.getFooter(context));
            //String message = "Encounter #" + request.getParameter("number") + " was added to " + request.getParameter("individual") + ".";

            /*
            if (request.getParameter("noemail") == null) {
              ServletUtilities.informInterestedParties(request, request.getParameter("number"), message,context);
              ServletUtilities.informInterestedIndividualParties(request, request.getParameter("individual"), message,context);
            }
            */
            es.shutdown();
          }

          //if lock exception thrown
          else {
            //out.println(ServletUtilities.getHeader(request));
            response.setStatus(HttpServletResponse.SC_BAD_REQUEST);
            out.println("<strong>Failure:</strong> Encounter #" + request.getParameter("number") + " was NOT added to " + request.getParameter("individual") + ". Another user is currently modifying this record in the database. Please try to add the encounter again after a few seconds.");
            //out.println("<p><a href=\""+request.getScheme()+"://" + CommonConfiguration.getURLLocation(request) + "/encounters/encounter.jsp?number=" + request.getParameter("number") + "\">Return to encounter #" + request.getParameter("number") + "</a></p>\n");
            //out.println("<p><a href=\""+request.getScheme()+"://" + CommonConfiguration.getURLLocation(request) + "/individuals.jsp?number=" + request.getParameter("individual") + "\">View " + request.getParameter("individual") + "</a></p>\n");
            //out.println(ServletUtilities.getFooter(context));

          }


        } 
        catch (Exception e) {

          //out.println(ServletUtilities.getHeader(request));
          out.println("<strong>Error:</strong> No such record exists in the database.");
          //out.println(ServletUtilities.getFooter(context));
          myShepherd.rollbackDBTransaction();
          e.printStackTrace();
          //myShepherd.closeDBTransaction();
        }
      } else {
        //out.println(ServletUtilities.getHeader(request));
        out.println("<strong>Error:</strong> You can't add this encounter to a marked individual when it's already assigned to another one.");
        //out.println(ServletUtilities.getFooter(context));
        myShepherd.rollbackDBTransaction();
        //myShepherd.closeDBTransaction();
      }


    } else {
      //out.println(ServletUtilities.getHeader(request));
      out.println("<strong>Error:</strong> I didn't receive enough data to add this encounter to a marked individual.");
      //out.println(ServletUtilities.getFooter(context));
    }


    out.close();
    myShepherd.closeDBTransaction();
  }

}
