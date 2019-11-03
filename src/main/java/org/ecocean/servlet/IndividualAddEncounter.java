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
    response.setContentType("application/json");
    String responseJSON="";
    PrintWriter out = response.getWriter();
    StringBuilder failureMessage=new StringBuilder("<strong>Failure:</strong> Encounter " + request.getParameter("number") + " was NOT added to " + request.getParameter("individual") + ". Error unknown.");

    String action = request.getParameter("action");

    //add encounter to a MarkedIndividual

    String indivID = request.getParameter("individual");
    boolean forceNew = Util.booleanNotFalse(request.getParameter("forceNew"));
    if ((request.getParameter("number") != null) && (indivID != null) && (request.getParameter("matchType") != null)) {


      myShepherd.beginDBTransaction();
      Encounter enc2add = myShepherd.getEncounter(request.getParameter("number"));
      try {  
      
        if (enc2add == null) {
            failureMessage=new StringBuilder("<p>Invalid encounter id=" + request.getParameter("number")+"</p>");
            throw new RuntimeException("invalid encounter id=" + request.getParameter("number"));
        }
        setDateLastModified(enc2add);
       
        boolean newIndy = false;
        if (enc2add.getIndividual() == null) {
          MarkedIndividual addToMe = null;
          //if forceNew=true, this means we make a new indiv, so there.  (indivId should be a name in this case, basically)
          if (forceNew) {
              System.out.println("IndividualAddEncounter: forceNew=true, attempting to make indiv '" + indivID + "'.");
              try {
                  newIndy = true;
                  addToMe = new MarkedIndividual(indivID, enc2add);
                  
                  //check for duplicate individual IDs represented by another annotation with the same acmId
                  //checkForDuplicateAnnotations(enc2add, failureMessage, addToMe, myShepherd);
                  
                  
                  myShepherd.storeNewMarkedIndividual(addToMe);
                  myShepherd.updateDBTransaction();
                  addToMe.refreshNamesCache();
                  addToMe.refreshDependentProperties();
                  
              } 
              catch (Exception ex) {
                  ex.printStackTrace();
                  myShepherd.rollbackDBTransaction();
                  throw new RuntimeException(failureMessage.toString());
              }
          } 
          else {
             System.out.println("IndividualAddEncounter: Retrieving an existing individual=" + indivID);
              addToMe = myShepherd.getMarkedIndividual(indivID);
              if (addToMe == null) {
                failureMessage=new StringBuilder("<p>Invalid individual id=" + indivID+"</p>");
                throw new RuntimeException("invalid individual id=" + indivID);
              }
          }
  

            boolean sexMismatch = false;

            try {
              if (!addToMe.getEncounters().contains(enc2add)) {
              //check for duplicate individual IDs represented by another annotation with the same acmId
                checkForDuplicateAnnotations(enc2add, failureMessage, addToMe, myShepherd);
                addToMe.addEncounter(enc2add);
                System.out.println("Now adding the Encounter to the individual");
              }
              enc2add.setMatchedBy(request.getParameter("matchType"));
              enc2add.addComments("<p><em>" + request.getRemoteUser() + " on " + (new java.util.Date()).toString() + "</em><br>" + "Added to " + request.getParameter("individual") + ".</p>");
              addToMe.addComments("<p><em>" + request.getRemoteUser() + " on " + (new java.util.Date()).toString() + "</em><br>" + "Added encounter " + request.getParameter("number") + ".</p>");
              
              
              
              if ((addToMe.getSex()!=null)&&(enc2add.getSex()!=null)&&(!addToMe.getSex().equals(enc2add.getSex()))) {
                 
                  sexMismatch = true;
              
              }
              else if ( ((addToMe.getSex()==null)||(addToMe.getSex().equals("unknown"))) &&(enc2add.getSex()!=null)) {
                addToMe.setSex(enc2add.getSex());
              }
              //responseJSON=RESTUtils.getJSONObjectFromPOJO(addToMe, ((JDOPersistenceManager)myShepherd.getPM()).getExecutionContext()).toString();  
              responseJSON=addToMe.uiJson(request,false).toString();  
              
              //youTube postback check
              youTubePostback(enc2add, myShepherd, context);

            } 
            catch (Exception le) {
              le.printStackTrace();
              myShepherd.rollbackDBTransaction();
              throw new RuntimeException(failureMessage.toString());
  
            }

              myShepherd.commitDBTransaction();
              response.setStatus(HttpServletResponse.SC_OK);
              out.println(responseJSON);
  
        			
              //send emails if appropriate
              if (request.getParameter("noemail") == null) {
                try {
                  executeEmails(myShepherd, request,addToMe,newIndy, enc2add, context, langCode);
                }
                catch(Exception excepty) {
                  excepty.printStackTrace();
                  myShepherd.rollbackDBTransaction();
                }
              }

  
          } 
  
  
        else {
          response.setStatus(HttpServletResponse.SC_BAD_REQUEST);

          out.println("<strong>Error:</strong> You can't add this encounter to a marked individual when it is already assigned to another one.");

          myShepherd.rollbackDBTransaction();

        }
      
      } 
      catch (Exception e) {
        response.setStatus(HttpServletResponse.SC_BAD_REQUEST);
        out.println(failureMessage);
        myShepherd.rollbackDBTransaction();
        e.printStackTrace();
      }


    } 
    else {
      response.setStatus(HttpServletResponse.SC_BAD_REQUEST);
      out.println("<strong>Error:</strong> I didn't receive enough data to add this encounter to a marked individual.");
    }


    out.close();
    myShepherd.closeDBTransaction();
  }
  
  
  
  
  
  
  private void checkForDuplicateAnnotations(Encounter enc2add, StringBuilder failureMessage, MarkedIndividual addToMe, Shepherd myShepherd) {
    //check for duplicate individual IDs represented by another annotation with the same acmId
    ArrayList<Encounter> conflictingEncs=new ArrayList<Encounter>();
    for(Annotation annot:enc2add.getAnnotations()) {
      conflictingEncs.addAll(Annotation.checkForConflictingIDsforAnnotation(annot, addToMe.getIndividualID(), myShepherd));
    }
    if(conflictingEncs.size()>0) {
      failureMessage=new StringBuilder("Failure: ");
      failureMessage.append("<p>The following Encounters contain the same annotation but have a different individual ID. An annotation can only have one ID inherited from its Encounters. <ul>");
      for(Encounter enc:conflictingEncs) {
        failureMessage.append("<li>"+enc.getEncounterNumber()+" ("+enc.getIndividual().getIndividualID()+")</li>");
      }
      failureMessage.append("</ul></p>");
      throw new RuntimeException(failureMessage.toString());
    }
  }
  
  private void executeEmails(Shepherd myShepherd, HttpServletRequest request,MarkedIndividual addToMe,boolean newIndy, Encounter enc2add, String context, String langCode) {
    // Retrieve background service for processing emails
    ThreadPoolExecutor es = MailThreadExecutorService.getExecutorService();

    myShepherd.beginDBTransaction();

    List<String> allAssociatedEmails = addToMe.getAllEmailsToUpdate();

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
    myShepherd.rollbackDBTransaction();
    es.shutdown();
  }
  
  private void youTubePostback(Encounter enc2add, Shepherd myShepherd, String context) {
    /*
     * START YouTube PostBack check
     * 
     */
    try{
      
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

          
          
          Properties ytProps=null;
          try {
            ytProps=ShepherdProperties.getProperties("quest.properties", detectedLanguage);
          }
          catch(NullPointerException npe) {System.out.println("Exception: Could not find quest.properties for langCode="+detectedLanguage+". Falling back to en.");}
          
          if(ytProps==null) {
            try {
              ytProps=ShepherdProperties.getProperties("quest.properties", "en");
            }
            catch(NullPointerException npe2) {System.out.println("Exception: Could not find quest.properties for en.");}
          }
          
          if(ytProps!=null) {
            String message=ytProps.getProperty("individualAddEncounter").replaceAll("%INDIVIDUAL%", enc2add.getIndividualID());
            System.out.println("Will post back to YouTube OP this message if appropriate: "+message);
            YouTube.postOccurrenceMessageToYouTubeIfAppropriate(message, occur, myShepherd, context);
          }
        }
      }
    }
    catch(Exception e){e.printStackTrace();}
    /*
     * END YouTube PostBack check
     * 
     */
  }
  
  private void setDateLastModified(Encounter enc) {

    String strOutputDateTime = ServletUtilities.getDate();
    enc.setDWCDateLastModified(strOutputDateTime);
  }
  
  

}
