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
import java.util.ArrayList;
import java.util.StringTokenizer;
import java.util.Vector;
import java.util.concurrent.ThreadPoolExecutor;

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
    String context="context0";
    context=ServletUtilities.getContext(request);
    Shepherd myShepherd = new Shepherd(context);
    //set up for response
    response.setContentType("text/html");
    PrintWriter out = response.getWriter();
    boolean locked = false, isOwner = true;
    boolean isAssigned = false;

    String action = request.getParameter("action");

    //add encounter to a MarkedIndividual

    if ((request.getParameter("number") != null) && (request.getParameter("individual") != null) && (request.getParameter("matchType") != null)) {

      String nickname = "";
      myShepherd.beginDBTransaction();
      Encounter enc2add = myShepherd.getEncounter(request.getParameter("number"));
      setDateLastModified(enc2add);
      String tempName = enc2add.isAssignedToMarkedIndividual();
      if ((tempName.equals("Unassigned")) && (myShepherd.isMarkedIndividual(request.getParameter("individual")))) {
        try {


          boolean sexMismatch = false;

          //myShepherd.beginDBTransaction();
          MarkedIndividual addToMe = myShepherd.getMarkedIndividual(request.getParameter("individual"));
          if ((addToMe.getNickName() != null) && (!addToMe.getNickName().equals(""))) {
            nickname = " ("+addToMe.getNickName() + ")";
          }
          try {
            if (!addToMe.getEncounters().contains(enc2add)) {
              addToMe.addEncounter(enc2add, context);
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
            
            
            
          } catch (Exception le) {
            System.out.println("Hit locked exception on action: " + action);
            le.printStackTrace();
            locked = true;
            myShepherd.rollbackDBTransaction();

          }


          if (!locked) {

            myShepherd.commitDBTransaction();
            Vector e_images = new Vector();

            String updateMessage = ServletUtilities.getText(CommonConfiguration.getDataDirectoryName(context),"markedIndividualUpdate.html",ServletUtilities.getLanguageCode(request));
			
            String thanksmessage = ServletUtilities.getText(CommonConfiguration.getDataDirectoryName(context),"add2MarkedIndividual.html",ServletUtilities.getLanguageCode(request));

            String add2update=ServletUtilities.getText(CommonConfiguration.getDataDirectoryName(context),"add2MarkedIndividual.html",ServletUtilities.getLanguageCode(request));
            
            String adopterUpdate=ServletUtilities.getText(CommonConfiguration.getDataDirectoryName(context),"adopterUpdate.html",ServletUtilities.getLanguageCode(request));
            
            //let's get ready for emailing
            ThreadPoolExecutor es = MailThreadExecutorService.getExecutorService();


            myShepherd.beginDBTransaction();

            //String emailUpdate = "\nPreviously identified record: " + request.getParameter("individual");
            //emailUpdate = emailUpdate + altID;
            String emailUpdate = "<a href=\"http://" + CommonConfiguration.getURLLocation(request) + "/individuals.jsp?number=" + request.getParameter("individual") + "\">"+request.getParameter("individual")+" "+nickname+"</a><br><a href=\"http://"+CommonConfiguration.getURLLocation(request)+"/encounters/encounter.jsp?number="+request.getParameter("number")+"\">See the new encounter</a><br>";

            thanksmessage =thanksmessage.replaceAll("INSERTTEXT", emailUpdate);
            updateMessage=updateMessage.replaceAll("INSERTTEXT",  emailUpdate);
            add2update=add2update.replaceAll("INSERTTEXT",  emailUpdate);
            adopterUpdate=add2update.replaceAll("INSERTTEXT",  emailUpdate);
            
            
            
            //updateMessage += emailUpdate;

			ArrayList allAssociatedEmails=addToMe.getAllEmailsToUpdate();

            int numEncounters = addToMe.totalEncounters();

            //inform all encounter submitters for this Marked Individual about the modification to their animal

            if (request.getParameter("noemail") == null) {


              //notify the administrators
              NotificationMailer mailer = new NotificationMailer(CommonConfiguration.getMailHost(context), CommonConfiguration.getAutoEmailAddress(context), CommonConfiguration.getAutoEmailAddress(context), ("Encounter update sent to submitters: " + request.getParameter("number")), add2update.replaceAll("REMOVEME", ""), e_images,context);
			  es.execute(mailer);

			  //notify submitters, photographers, and informOthers values
	          int emailSize=allAssociatedEmails.size();
	          for(int z=0;z<emailSize;z++){
				  String submitter=(String)allAssociatedEmails.get(z);

				if((enc2add.getSubmitterEmail().indexOf(submitter)!=-1)||(enc2add.getPhotographerEmail().indexOf(submitter)!=-1)||(enc2add.getInformOthers().indexOf(submitter)!=-1)){

				  	String personalizedThanksMessage = CommonConfiguration.appendEmailRemoveHashString(request, thanksmessage, submitter,context);
				  	personalizedThanksMessage=personalizedThanksMessage.replaceAll("INSERTTEXT",  emailUpdate);
				  	es.execute(new NotificationMailer(CommonConfiguration.getMailHost(context), CommonConfiguration.getAutoEmailAddress(context), submitter, ("Encounter update: " + request.getParameter("number")), personalizedThanksMessage, e_images,context));
				}
				else{
					  	String personalizedThanksMessage = CommonConfiguration.appendEmailRemoveHashString(request, updateMessage, submitter,context);
					  	personalizedThanksMessage=personalizedThanksMessage.replaceAll("INSERTTEXT",  emailUpdate);
	            
					  	es.execute(new NotificationMailer(CommonConfiguration.getMailHost(context), CommonConfiguration.getAutoEmailAddress(context), submitter, ("Encounter update: " + request.getParameter("number")), personalizedThanksMessage, e_images,context));

				}


			  }

              //StringBuffer allSubs = new StringBuffer();
			  /**
              //notify other submitters
              for (int l = 0; l < numEncounters; l++) {
                Encounter tempEnc = addToMe.getEncounter(l);


                if (allSubs.indexOf(tempEnc.getSubmitterEmail()) == -1) {

                  String submitter = tempEnc.getSubmitterEmail();
                  if (submitter.indexOf(",") != -1) {
                    StringTokenizer str = new StringTokenizer(submitter, ",");
                    while (str.hasMoreTokens()) {
                      String token = str.nextToken().trim();
                      if (!token.equals("")) {
                        String personalizedThanksMessage = CommonConfiguration.appendEmailRemoveHashString(request, thanksmessage, token);

                        es.execute(new NotificationMailer(CommonConfiguration.getMailHost(), CommonConfiguration.getAutoEmailAddress(), token, ("Encounter update: " + request.getParameter("number")), personalizedThanksMessage, e_images));
                        allSubs.append(token);
                      }
                    }
                  } else {
                    String personalizedThanksMessage = CommonConfiguration.appendEmailRemoveHashString(request, thanksmessage, submitter);

                    es.execute(new NotificationMailer(CommonConfiguration.getMailHost(), CommonConfiguration.getAutoEmailAddress(), submitter, ("Encounter update: " + request.getParameter("number")), personalizedThanksMessage, e_images));
                    allSubs.append(submitter);
                  }


                }
                if ((tempEnc.getPhotographerEmail() != null) && (!tempEnc.getPhotographerEmail().equals("")) && (!tempEnc.getPhotographerEmail().equals(enc2add.getPhotographerEmail())) && (allSubs.indexOf(tempEnc.getPhotographerEmail()) == -1)) {

                  String submitter = tempEnc.getPhotographerEmail();
                  if (submitter.indexOf(",") != -1) {
                    StringTokenizer str = new StringTokenizer(submitter, ",");
                    while (str.hasMoreTokens()) {
                      String token = str.nextToken().trim();
                      if (!token.equals("")) {
                        String personalizedThanksMessage = CommonConfiguration.appendEmailRemoveHashString(request, thanksmessage, token);

                        es.execute(new NotificationMailer(CommonConfiguration.getMailHost(), CommonConfiguration.getAutoEmailAddress(), token, ("Encounter update: " + request.getParameter("number")), personalizedThanksMessage, e_images));
                        allSubs.append(token);
                      }
                    }
                  } else {
                    String personalizedThanksMessage = CommonConfiguration.appendEmailRemoveHashString(request, thanksmessage, submitter);
                    es.execute(new NotificationMailer(CommonConfiguration.getMailHost(), CommonConfiguration.getAutoEmailAddress(), submitter, ("Encounter update: " + request.getParameter("number")), personalizedThanksMessage, e_images));
                    allSubs.append(submitter);
                  }

                }


                if ((tempEnc.getInformOthers() != null) && (!tempEnc.getInformOthers().equals("")) && (allSubs.indexOf(tempEnc.getInformOthers()) == -1)) {

                  String submitter = tempEnc.getInformOthers();
                  if (submitter.indexOf(",") != -1) {
                    StringTokenizer str = new StringTokenizer(submitter, ",");
                    while (str.hasMoreTokens()) {
                      String token = str.nextToken().trim();
                      if (!token.equals("")) {
                        String personalizedThanksMessage = CommonConfiguration.appendEmailRemoveHashString(request, thanksmessage, token);

                        es.execute(new NotificationMailer(CommonConfiguration.getMailHost(), CommonConfiguration.getAutoEmailAddress(), token, ("Encounter update: " + request.getParameter("number")), personalizedThanksMessage, e_images));
                        allSubs.append(token);
                      }
                    }
                  } else {
                    String personalizedThanksMessage = CommonConfiguration.appendEmailRemoveHashString(request, thanksmessage, submitter);

                    es.execute(new NotificationMailer(CommonConfiguration.getMailHost(), CommonConfiguration.getAutoEmailAddress(), submitter, ("Encounter update: " + request.getParameter("number")), personalizedThanksMessage, e_images));
                    allSubs.append(submitter);
                  }
                }


              }
              */


              //notify adopters
	            Extent encClass = myShepherd.getPM().getExtent(Adoption.class, true);
	            Query query = myShepherd.getPM().newQuery(encClass);
              ArrayList adopters = myShepherd.getAdopterEmailsForMarkedIndividual(query,request.getParameter("individual"));
              for (int t = 0; t < adopters.size(); t++) {
                String adEmail = (String) adopters.get(t);
                if ((allAssociatedEmails.indexOf(adEmail) == -1)) {
                  es.execute(new NotificationMailer(CommonConfiguration.getMailHost(context), CommonConfiguration.getAutoEmailAddress(context), adEmail, ("Sighting update: " + request.getParameter("individual")), ServletUtilities.getText(CommonConfiguration.getDataDirectoryName(context),"adopterUpdate.html",ServletUtilities.getLanguageCode(request)) + adopterUpdate, e_images,context));
                  allAssociatedEmails.add(adEmail);
                }
              }
              query.closeAll();

              String rssTitle = request.getParameter("individual") + " Resight";
              String rssLink = "http://" + CommonConfiguration.getURLLocation(request) + "/encounters/encounter.jsp?number=" + request.getParameter("number");
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
            out.println(ServletUtilities.getHeader(request));
            out.println("<strong>Success:</strong> Encounter " + request.getParameter("number") + " was successfully added to " + request.getParameter("individual") + ".");
            if (sexMismatch) {
              out.println("<p><strong>Warning! There is conflict between the designated sex of the new encounter and the designated sex in previous records. You should resolve this conflict for consistency.</strong></p>");
            }
            out.println("<p><a href=\"http://" + CommonConfiguration.getURLLocation(request) + "/encounters/encounter.jsp?number=" + request.getParameter("number") + "\">Return to encounter #" + request.getParameter("number") + "</a></p>\n");
            out.println("<p><a href=\"http://" + CommonConfiguration.getURLLocation(request) + "/individuals.jsp?number=" + request.getParameter("individual") + "\">View individual " + request.getParameter("individual") + "</a></p>\n");
            out.println(ServletUtilities.getFooter(context));
            String message = "Encounter #" + request.getParameter("number") + " was added to " + request.getParameter("individual") + ".";

            if (request.getParameter("noemail") == null) {
              ServletUtilities.informInterestedParties(request, request.getParameter("number"), message,context);
              ServletUtilities.informInterestedIndividualParties(request, request.getParameter("individual"), message,context);
            }
            es.shutdown();
          }

          //if lock exception thrown
          else {
            out.println(ServletUtilities.getHeader(request));
            out.println("<strong>Failure:</strong> Encounter #" + request.getParameter("number") + " was NOT added to " + request.getParameter("individual") + ". Another user is currently modifying this record in the database. Please try to add the encounter again after a few seconds.");
            out.println("<p><a href=\"http://" + CommonConfiguration.getURLLocation(request) + "/encounters/encounter.jsp?number=" + request.getParameter("number") + "\">Return to encounter #" + request.getParameter("number") + "</a></p>\n");
            out.println("<p><a href=\"http://" + CommonConfiguration.getURLLocation(request) + "/individuals.jsp?number=" + request.getParameter("individual") + "\">View " + request.getParameter("individual") + "</a></p>\n");
            out.println(ServletUtilities.getFooter(context));

          }


        } catch (Exception e) {

          out.println(ServletUtilities.getHeader(request));
          out.println("<strong>Error:</strong> No such record exists in the database.");
          out.println(ServletUtilities.getFooter(context));
          myShepherd.rollbackDBTransaction();
          e.printStackTrace();
          //myShepherd.closeDBTransaction();
        }
      } else {
        out.println(ServletUtilities.getHeader(request));
        out.println("<strong>Error:</strong> You can't add this encounter to a marked individual when it's already assigned to another one, or you may be trying to add this encounter to a nonexistent individual.");
        out.println(ServletUtilities.getFooter(context));
        myShepherd.rollbackDBTransaction();
        //myShepherd.closeDBTransaction();
      }


    } else {
      out.println(ServletUtilities.getHeader(request));
      out.println("<strong>Error:</strong> I didn't receive enough data to add this encounter to a marked individual.");
      out.println(ServletUtilities.getFooter(context));
    }


    out.close();
    myShepherd.closeDBTransaction();
  }

}