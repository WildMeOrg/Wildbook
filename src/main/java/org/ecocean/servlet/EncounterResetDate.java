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
import org.joda.time.LocalDateTime;
import org.joda.time.format.DateTimeFormatter;
import org.joda.time.format.ISODateTimeFormat;

import javax.servlet.ServletConfig;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import java.io.IOException;
import java.io.PrintWriter;
import java.net.URISyntaxException;
import java.util.Locale;
import java.util.Properties;
import java.util.StringTokenizer;


public class EncounterResetDate extends HttpServlet {

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
    Properties encprops = ShepherdProperties.getProperties("encounter.properties", langCode, context);
    Shepherd myShepherd = new Shepherd(context);
    //set up for response
    response.setContentType("text/html");
    PrintWriter out = response.getWriter();
    boolean locked = false;
    boolean isOwner = true;

    // Prepare for user response.
    String link = "#";
    try {
      link = CommonConfiguration.getServerURL(request, request.getContextPath()) + String.format("/encounters/encounter.jsp?number=%s", request.getParameter("number"));
    }
    catch (URISyntaxException ex) {
    }
    ActionResult actionResult = new ActionResult(locale, "encounter.editField", true, link)
            .setLinkParams(request.getParameter("number"));

    if ((request.getParameter("number") != null) ) {
      myShepherd.beginDBTransaction();
      Encounter fixMe = myShepherd.getEncounter(request.getParameter("number"));
      setDateLastModified(fixMe);
      String oldDate = "";
      String newDate = "";


      try {

        oldDate = fixMe.getDate();
        
        if ((request.getParameter("datepicker") == null)||(request.getParameter("datepicker").trim().equals(""))){
          fixMe.setYear(0);
          fixMe.setMonth(0);
          fixMe.setDay(0);
          fixMe.setHour(0);
          newDate=fixMe.getDate();
        }
        else{
        
        /**
         * Old method of parsing
        fixMe.setDay(Integer.parseInt(request.getParameter("day")));
        fixMe.setMonth(Integer.parseInt(request.getParameter("month")));
        fixMe.setYear(Integer.parseInt(request.getParameter("year")));
        fixMe.setHour(Integer.parseInt(request.getParameter("hour")));
        fixMe.setMinutes(request.getParameter("minutes"));
        */
        
        
        //new method using a datepicker
        //switch to datepicker
        //if(getVal(fv, "datepicker")!=null){
          //System.out.println("Trying to read date: "+getVal(fv, "datepicker").replaceAll(" ", "T"));
          
          DateTimeFormatter parser1 = ISODateTimeFormat.dateOptionalTimeParser();
          //LocalDateTime reportedDateTime=parser1.parseLocalDateTime(request.getParameter("datepicker").replaceAll(" ", "T"));
          LocalDateTime reportedDateTime=new LocalDateTime(parser1.parseMillis(request.getParameter("datepicker").replaceAll(" ", "T")));
          
          //System.out.println("Day of month is: "+reportedDateTime.getDayOfMonth()); 
          StringTokenizer str=new StringTokenizer(request.getParameter("datepicker").replaceAll(" ", "T"),"-");        
          
          int numTokens=str.countTokens();
          if(numTokens>=1){
            try { fixMe.setYear(new Integer(reportedDateTime.getYear())); } catch (Exception e) { fixMe.setYear(-1);}
          }
          if(numTokens>=2){
            try { fixMe.setMonth(new Integer(reportedDateTime.getMonthOfYear())); } catch (Exception e) { fixMe.setMonth(-1);}
          }
          else{fixMe.setMonth(-1);}
          //see if we can get a day, because we do want to support only yyy-MM too
          if(str.countTokens()>=3){
            try { fixMe.setDay(new Integer(reportedDateTime.getDayOfMonth())); } catch (Exception e) { fixMe.setDay(0); }
          }
          else{fixMe.setDay(0);}
          
          
          
          //see if we can get a time and hour, because we do want to support only yyy-MM too
          StringTokenizer strTime=new StringTokenizer(request.getParameter("datepicker").replaceAll(" ", "T"),"T");        
          if(strTime.countTokens()>1){
            try { fixMe.setHour(new Integer(reportedDateTime.getHourOfDay())); } catch (Exception e) { fixMe.setHour(-1); }
            try {fixMe.setMinutes(new Integer(reportedDateTime.getMinuteOfHour()).toString()); } catch (Exception e) {}
          } 
          else{fixMe.setHour(-1);}
       //}
        
        
        newDate = fixMe.getDate();
        fixMe.addComments("<p><em>" + request.getRemoteUser() + " on " + (new java.util.Date()).toString() + "</em><br>Changed encounter date from " + oldDate + " to " + newDate + ".</p>");

        if((fixMe.getIndividualID()!=null)&&(!fixMe.getIndividualID().equals("Unassigned"))){
          String indieName=fixMe.getIndividualID();
          if(myShepherd.isMarkedIndividual(indieName)){
            MarkedIndividual indie=myShepherd.getMarkedIndividual(indieName);
            indie.refreshDependentProperties(context);
          }
        }
        
        } //end else 
      } catch (Exception le) {
        locked = true;
        le.printStackTrace();
        myShepherd.rollbackDBTransaction();
      }


      out.println(ServletUtilities.getHeader(request));
      if (!locked) {
        myShepherd.commitDBTransaction();

        String nd = newDate, od = oldDate;
        if (nd == null || "".equals(nd) || "Unknown".equals(nd))
          nd = encprops.getProperty("unknown");
        if (od == null || "".equals(od) || "Unknown".equals(od))
          od = encprops.getProperty("unknown");
        actionResult.setMessageOverrideKey("resetDate").setParams(request.getParameter("number"), nd, od);

        String message = "The date of encounter " + request.getParameter("number") + " was changed from " + oldDate + " to " + newDate + ".";
        ServletUtilities.informInterestedParties(request, request.getParameter("number"), message,context);
      } else {
        actionResult.setSucceeded(false).setMessageOverrideKey("locked");
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
