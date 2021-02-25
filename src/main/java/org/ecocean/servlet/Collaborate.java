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
import org.ecocean.security.Collaboration;

import javax.servlet.ServletConfig;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import java.io.*;
import java.util.*;

import java.util.concurrent.ThreadPoolExecutor;

import com.google.gson.Gson;

public class Collaborate extends HttpServlet {


  public void init(ServletConfig config) throws ServletException {
    super.init(config);
  }


  public void doGet(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
    doPost(request, response);
  }

  public void doPost(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
    String context = ServletUtilities.getContext(request);
    Shepherd myShepherd = new Shepherd(context);
    myShepherd.setAction("Collaborate.class");

    Properties props = new Properties();
    String langCode = ServletUtilities.getLanguageCode(request);
    props = ShepherdProperties.getProperties("collaboration.properties", langCode, context);

    //other user affected by the collaboration state change
  	String username = request.getParameter("username");
  	
  	//is this an approval of a previously isues Collaboration?
  	String approve = request.getParameter("approve");
  	
  	String optionalMessage = request.getParameter("message");
  	
  	//user requesting collaboration change
  	String currentUsername = ((request.getUserPrincipal() == null) ? "" : request.getUserPrincipal().getName());
  	
  	boolean useJson = !(request.getParameter("json") == null);
	
  	//used to toggle approval- effectively two way viewing privileges.
  	String collabId = request.getParameter("collabId");
  	
  	String actionForExisting = request.getParameter("actionForExisting");

  	System.out.println("in Collaborate.java!!! collabId="+collabId+"  actionForExisting="+actionForExisting);

  	HashMap rtn = new HashMap();
  	rtn.put("success", false);

  	System.out.println("/Collaborate: beginning servlet doPost with username "+username+" and currentUsernam "+currentUsername);
  	PrintWriter out = response.getWriter();
  	myShepherd.beginDBTransaction();
  	
  	try {
  	  
  	  //if nobody is logged in, we can safely exit
    	if (request.getUserPrincipal() == null) {
    	  rtn.put("message", props.getProperty("inviteResponseMessageAnon"));
  
    	} 
    	//this is a widget-only response, no change made
    	else if (request.getParameter("getNotificationsWidget") != null) {
    	  rtn.put("content", Collaboration.getNotificationsWidgetHtml(request, myShepherd));
    	  rtn.put("success", "true");
    	}
    	
    	//get Notifications for User (e.g., after login)
    	else if (request.getParameter("getNotifications") != null) {
    	  
    	  //get new collab invites
    	  List<Collaboration> collabs = Collaboration.collaborationsForUser(context, currentUsername, Collaboration.STATE_INITIALIZED);
    	  
    	  //get edit collab invites
    	  List<Collaboration> collabs_edits = Collaboration.collaborationsForUser(context, currentUsername, Collaboration.STATE_EDIT_PENDING_PRIV);
        
    	  collabs.addAll(collabs_edits);
    	  
    	  System.out.println("/Collaborate: inside getNotifications: #collabs = "+collabs.size()+"collabs = "+collabs);
    	  String html = "";
    	  for (Collaboration c : collabs) {
  
    	    System.out.println("/Collaborate: inside collabs list");
    	    String type="";
    	    String cclass="";
    	    String id="";
    	    if(c.getState().equals(Collaboration.STATE_INITIALIZED)) {
    	      type="view-only";
    	      cclass="yes";
    	    }
    	    else {
    	      type="edit";
    	      cclass="edit";
    	      //id="id=\""+c.getId()+"\"";
    	    }
    	    
    	    if (!c.getEditInitiator().equals(currentUsername)) {  //this user did not initiate
  
    	      String requesterName = c.getEditInitiator();
    	      User requester = myShepherd.getUser(requesterName);
    	      String reqEmail = (requester!=null) ? requester.getEmailAddress() : null;
    	      String emailMessage =  "";
    	      if (Util.stringExists(reqEmail)) {
    	        emailMessage = " ("+reqEmail+") ";
    	      }
    	      System.out.println("COLLABORATE: requester "+requesterName+" got user "+requester+" and emailMessage "+emailMessage);
    	      html += "<div class=\"collaboration-invite-notification\" data-username=\"" + requesterName + "\">" + requesterName +" ("+type+")" + emailMessage+ " <input "+id+" class=\""+cclass+"\" type=\"button\" value=\"" + props.getProperty("buttonApprove") + "\" /> <input class=\"no\" type=\"button\" value=\"" + props.getProperty("buttonDeny") + "\" /></div>";
    	    }
    	  } //end for loop
    	  if (html.equals("")) {
    	    rtn.put("content", "<label id='no-notifications-label'>"+props.getProperty("notificationsNone")+"</label>");
    	  } 
    	  else {
    	    // we need to find somehow the email of the requester here
    	    System.out.println("COLLABORATE.java: username="+username+", currentUsername="+currentUsername);
    	    rtn.put("content", "<h2>" + props.getProperty("notificationsTitle") + "</h2>" + html);
    	  }

  	} 
   // Change of state on existing collaboration	
    else if (collabId!=null&&!"".equals(collabId)&&actionForExisting!=null&&!"".equals(actionForExisting)) {
  		System.out.println("Changing state of existing collaboration...");
  		useJson = true;
  		Collaboration collab = myShepherd.getCollaboration(collabId);
  		//boolean isRecipient = request.getUserPrincipal().getName().equals(collab.getUsername2());
  		//if (collab!=null&&isRecipient) {
  		if (collab!=null) {
  			try {
  				if ("revoke".equals(actionForExisting)) {
  					//if we're revoking edit permissions, kick them down to view
  				  if(collab.getState()!=null && (collab.getState().equals(Collaboration.STATE_EDIT_PRIV) || collab.getState().equals(Collaboration.STATE_EDIT_PENDING_PRIV))) {
  					  collab.setState(Collaboration.STATE_APPROVED);
  					}
  				  //otherwise, kick them down to truly rejected
  					else {collab.setState(Collaboration.STATE_REJECTED);}
  					collab.setEditInitiator(null);
  					myShepherd.updateDBTransaction();
  					System.out.println("Set existing approved collab to rejected: id="+collabId+"  state="+collab.getState());
  					rtn.put("success", true);
  				}
  				if ("invite".equals(actionForExisting)) {
  				  System.out.println("Changing state of existing collaboration...invite");
  					currentUsername = request.getUserPrincipal().getName();
  					if (currentUsername.equals(collab.getUsername1())) {
  						username = collab.getUsername2();
  					} 
  					else {
  						username = collab.getUsername1();
  						// if this is reached, the user was the recipient of the original collab invite but is initiator of this one.
  						// user position indicates the flow of the invite, so we must switch and update date/time
  						collab.setUsername1(currentUsername);
  						collab.setUsername2(username);
  						collab.setDateTimeCreated();
  					}
  					rtn = sendCollaborationInvite(myShepherd, username, currentUsername, props, rtn, request, context, false);
  					collab.setState(Collaboration.STATE_INITIALIZED);
  					if(username!=null && username.equals("public"))collab.setState(Collaboration.STATE_APPROVED);
  					collab.setEditInitiator(currentUsername);
  					
  					myShepherd.updateDBTransaction();
  				}
  				
          if ("invite_edit".equals(actionForExisting)) {
            currentUsername = request.getUserPrincipal().getName();
            if (currentUsername.equals(collab.getUsername1())) {
              username = collab.getUsername2();
            } 
            else {
              username = collab.getUsername1();
              // if this is reached, the user was the recipient of the original collab invite but is initiator of this one.
              // user position indicates the flow of the invite, so we must switch and update date/time
              //collab.setUsername1(currentUsername);
              //collab.setUsername2(username);
              //collab.setDateTimeCreated();
            }
            rtn = sendCollaborationInvite(myShepherd, username, currentUsername, props, rtn, request, context, true);
            collab.setState(Collaboration.STATE_EDIT_PENDING_PRIV);
            collab.setEditInitiator(currentUsername);
            
            
            myShepherd.updateDBTransaction();
          }
  				
  				rtn.put("newState", collab.getState());
  				rtn.put("collabId", collab.getId());
  				rtn.put("action", actionForExisting);
  			} 
  			catch (Exception e) {
  				e.printStackTrace();
  				System.out.println("Error setting completing action on collaboration "+collabId+" to "+actionForExisting);
  				rtn.put("success", false);
  			}
  		} 
  		
  	} 
    	
    //respond with a message if we can't figure out who the heck they're trying to collaborate with	
    else if ((username == null) || username.equals("")) {
  		rtn.put("message", props.getProperty("inviteResponseMessageNoUsername"));
  	} 
    
    // this block contains all the approve/unapprove logic
    else if ((approve != null) && !approve.equals("")) { 
  		
  		Collaboration collab = Collaboration.collaborationBetweenUsers(myShepherd, currentUsername, username);
  		System.out.println("/Collaborate: inside approve: approve = "+approve+" and collab = "+collab);
  		if (collab == null) {
  			rtn.put("message", props.getProperty("approvalResponseMessageBad"));
  		} 
  		else {
  			if (approve.equals("yes")) {
  			  System.out.println("Approve? Yes");
  	      
  				collab.setState(Collaboration.STATE_APPROVED);
  			}	
        else if (approve.equals("edit") && collab.getState()!=null && collab.getState().equals(Collaboration.STATE_APPROVED)){
          System.out.println("Approve? Yes Edit? Yes");
          collab.setState(Collaboration.STATE_EDIT_PENDING_PRIV);
          collab.setEditInitiator(currentUsername);
        } 
  			else if (approve.equals("edit") && collab.getState()!=null && collab.getState().equals(Collaboration.STATE_EDIT_PENDING_PRIV) && !currentUsername.equals(collab.getEditInitiator())){
  			  System.out.println("Approve? Yes Edit? Yes");
  			  collab.setState(Collaboration.STATE_EDIT_PRIV);
  			} 
  			else {
  			  
          if(collab.getState()!=null && (collab.getState().equals(Collaboration.STATE_EDIT_PRIV) || collab.getState().equals(Collaboration.STATE_EDIT_PENDING_PRIV))) {
            collab.setState(Collaboration.STATE_APPROVED);
          }
          //otherwise, kick them down to truly rejected
          else {collab.setState(Collaboration.STATE_REJECTED);}
          collab.setEditInitiator(null);
  			  
  			  System.out.println("Rejected");
  				
  				
  			}
  			System.out.println("/Collaborate: new .getState() = "+collab.getState()+" for collab "+collab);
  			rtn.put("success", true);
  			myShepherd.updateDBTransaction();
  			//myShepherd.commitDBTransaction();
  		}
  	} 
    	
    //NEW INVITE - default to sending a NEW invite if no other logic accepts the request	
    else {
      System.out.println("NEW INVITE from "+currentUsername+ " to " +username);
  		rtn = sendCollaborationInvite(myShepherd, username, currentUsername, props, rtn, request, context, false);
  	}
  
  	System.out.println("/Collab: before printwriter stuff, about to return "+rtn);
      
    if (useJson) {
      response.setContentType("application/json");
      response.setCharacterEncoding("UTF-8");
      String json = new Gson().toJson(rtn);
      out.println(json);
    } 
    else {
      response.setContentType("text/html");
      out.println(ServletUtilities.getHeader(request));
      if (Boolean.TRUE.equals(rtn.get("success"))) {
        out.println("<p class=\"collaboration-invite-success\">" + props.getProperty("inviteSuccess") + "</p>");
      } 
      else {
        out.println("<p class=\"collaboration-invite-failure\">" + props.getProperty("inviteFailure") + "</p>");
      }
      if (rtn.get("message") != null) out.println("<p class=\"collaboration-invite-message\">" + rtn.get("message") + "</p>");
      out.println(ServletUtilities.getFooter(context));
  	}
  	System.out.println("/Collab: about to return "+rtn);
  }
  catch(Exception e) {
    e.printStackTrace();
  }
  finally {
  	  myShepherd.rollbackDBTransaction();
  	  myShepherd.closeDBTransaction();
  	}
	out.close();
  } //end doPost

  
  
  /*
   * 
   * Issues a collaboration invite.
   * 
   */
  private HashMap sendCollaborationInvite(Shepherd myShepherd, String username, String currentUsername, Properties props, HashMap rtn, HttpServletRequest request, String context, boolean isEdit) {
	String optionalMessage = request.getParameter("message");
	
	 System.out.println("Entering sendCollaborationInvite");
	  
	
	Collaboration collab = Collaboration.collaborationBetweenUsers(currentUsername, username, context);

	//RESET INVITE FOR EXISTINF COLLAB
	if (collab != null && (Collaboration.STATE_INITIALIZED.equals(collab.getState()) || Collaboration.STATE_EDIT_PENDING_PRIV.equals(collab.getState()))) {
		rtn.put("message", props.getProperty("inviteResponseMessageAlready"));
		System.out.println("collab is already initialized, bailing on action. state=" + collab.getState());
	} 
	else {
	  //CREATE NEW COLLAB INVITE
		if (collab==null) {
			collab = Collaboration.create(currentUsername, username);
			if(isEdit)collab.setState(Collaboration.STATE_EDIT_PENDING_PRIV );
			if(username!=null && username.equals("public"))collab.setState(Collaboration.STATE_APPROVED);
			collab.setEditInitiator(currentUsername);
			myShepherd.storeNewCollaboration(collab);
			myShepherd.updateDBTransaction();
			rtn.put("collabId",collab.getId());
		} 
		User recip = myShepherd.getUser(username);
		if ((recip != null) && recip.getReceiveEmails() && (recip.getEmailAddress() != null) && !recip.getEmailAddress().equals("") && !recip.getUsername().equals("public")) {
			String mailTo = recip.getEmailAddress();
			Map<String, String> tagMap = new HashMap<>();
			tagMap.put("@CONTEXT_NAME@", ContextConfiguration.getNameForContext(context));
			tagMap.put("@USER@", username);

			User requester = myShepherd.getUser(currentUsername);
			String reqEmail = (requester!=null) ? requester.getEmailAddress() : null;
			String requesterEmailString = "";
			if (reqEmail!=null) {
				requesterEmailString = props.getProperty("inviteEmailSenderEmailBefore");
				requesterEmailString += reqEmail;
				requesterEmailString += props.getProperty("inviteEmailSenderEmailAfter");
			} else {
				requesterEmailString = props.getProperty("inviteEmailSenderNoEmail");
			}

			tagMap.put("@SENDER@", currentUsername);
			tagMap.put("@SENDER-EMAIL@", requesterEmailString);
			tagMap.put("@LINK@", String.format("//%s/myAccount.jsp", CommonConfiguration.getURLLocation(request)));
			if (optionalMessage!=null) {
				optionalMessage = props.getProperty("inviteEmailHasMessage")+" "+optionalMessage;
			} else {
				optionalMessage = "";
			}
			tagMap.put("@TEXT_CONTENT@", optionalMessage);
			System.out.println("/Collaborate: attempting email to (" + username + ") " + mailTo);
			ThreadPoolExecutor es = MailThreadExecutorService.getExecutorService();
			es.execute(new NotificationMailer(context, null, mailTo, "collaborationInvite", tagMap));
			  es.shutdown();
		} else {
			System.out.println("/Collaborate: skipping email to uid=" + username);
		}
		rtn.put("message", props.getProperty("inviteResponseMessageSent"));
		rtn.put("success", true);
	}
	return rtn;
  }

}
  
  
