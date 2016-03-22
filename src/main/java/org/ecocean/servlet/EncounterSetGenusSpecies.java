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

import java.io.IOException;
import java.io.PrintWriter;
import java.net.URISyntaxException;
import java.util.Locale;
import java.util.Properties;
import java.util.StringTokenizer;


//Set alternateID for this encounter/sighting
public class EncounterSetGenusSpecies extends HttpServlet {

  public void init(ServletConfig config) throws ServletException {
    super.init(config);
  }


  public void doGet(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {

    doPost(request, response);
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

    // Prepare for user response.
    String link = "#";
    try {
      link = CommonConfiguration.getServerURL(request, request.getContextPath()) + String.format("/encounters/encounter.jsp?number=%s", request.getParameter("encounter"));
    }
    catch (URISyntaxException ex) {
    }
    ActionResult actionResult = new ActionResult(locale, "encounter.editField", true, link).setLinkParams(request.getParameter("encounter"));

    String sharky = "None";
    sharky = request.getParameter("encounter");
    String genusSpecies = "";


    myShepherd.beginDBTransaction();
    if ((request.getParameter("encounter")!=null)&&(myShepherd.isEncounter(sharky)) && (request.getParameter("genusSpecies") != null)) {
      Encounter myShark = myShepherd.getEncounter(sharky);
      genusSpecies = request.getParameter("genusSpecies");
      //specificEpithet = request.getParameter("specificEpithet");
      try {

		String genus="";
		String specificEpithet = "";

		//now we have to break apart genus species
		StringTokenizer tokenizer=new StringTokenizer(genusSpecies," ");
		if(tokenizer.countTokens()>=2){

			genus=tokenizer.nextToken();
        	myShark.setGenus(genus);
        	specificEpithet =tokenizer.nextToken();


        		myShark.setSpecificEpithet(specificEpithet.replaceAll(",","").replaceAll("_"," "));
        		myShark.addComments("<p><em>" + request.getRemoteUser() + " on " + (new java.util.Date()).toString() + "</em><br>" + "Set genus and species to: " + genus + " "+specificEpithet.replaceAll(",","").replaceAll("_"," "));

	    }
	    else if(genusSpecies.equals("unknown")){
			myShark.setGenus(null);
        	myShark.setSpecificEpithet(null);
        	myShark.addComments("<p><em>" + request.getRemoteUser() + " on " + (new java.util.Date()).toString() + "</em><br />Set genus and species to null.");
      genusSpecies = encprops.getProperty("unknown");
		}
	    //handle malformed Genus Species formats
	    else{throw new Exception("The format of the genusSpecies parameter in servlet EncounterSetGenusSpecies did not have two tokens delimited by a space (e.g., \"Rhincodon typus\").");}

      } catch (Exception le) {
        locked = true;
        myShepherd.rollbackDBTransaction();
        myShepherd.closeDBTransaction();
      }

      if (!locked) {
        myShepherd.commitDBTransaction();
        myShepherd.closeDBTransaction();
        actionResult.setMessageOverrideKey("genusSpecies").setMessageParams(request.getParameter("encounter"), genusSpecies);
      } else {
        actionResult.setSucceeded(false).setMessageOverrideKey("locked");
      }
    } else {
      myShepherd.rollbackDBTransaction();
      actionResult.setSucceeded(false);
    }

    // Reply to user.
    request.getSession().setAttribute(ActionResult.SESSION_KEY, actionResult);
    getServletConfig().getServletContext().getRequestDispatcher(ActionResult.JSP_PAGE).forward(request, response);

    out.close();
    myShepherd.closeDBTransaction();
  }
}
