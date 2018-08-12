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
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.servlet.ServletConfig;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import java.io.File;
import java.io.IOException;
import java.io.PrintWriter;
import java.net.URISyntaxException;
import java.util.Iterator;
import java.util.Locale;
import java.util.Vector;

//import com.poet.jdo.*;


public class EncounterRemoveImage extends HttpServlet {
  /** SLF4J logger instance for writing log entries. */
  private static final Logger log = LoggerFactory.getLogger(EncounterRemoveImage.class);

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

    Shepherd myShepherd = new Shepherd(context);
    myShepherd.setAction("EncounterRemoveImage.class");
    //set up for response
    response.setContentType("text/html");
    PrintWriter out = response.getWriter();
    boolean locked = false;
    //boolean assigned=false;

    // Prepare for user response.
    String link = "#";
    try {
      link = CommonConfiguration.getServerURL(request, request.getContextPath()) + String.format("/encounters/encounter.jsp?number=%s", request.getParameter("number"));
    }
    catch (URISyntaxException ex) {
    }
    ActionResult actionResult = new ActionResult(locale, "encounter.editField", true, link).setLinkParams(request.getParameter("number"));

    String fileName = "None", encounterNumber = "None";
    //int positionInList = -1;
    String dcID="";

    //fileName=request.getParameter("filename").replaceAll("%20"," ");
    encounterNumber = request.getParameter("number");
    try {
      dcID = request.getParameter("dcID");
      //positionInList--;
    } catch (Exception e) {

      System.out.println("encounterRemoveImage: " + request.getParameter("number") + " " + request.getParameter("position"));
    }
    myShepherd.beginDBTransaction();
    if ((myShepherd.isEncounter(encounterNumber)) && (myShepherd.isSinglePhotoVideo(dcID))) {
      Encounter enc = myShepherd.getEncounter(encounterNumber);
      SinglePhotoVideo sid=myShepherd.getSinglePhotoVideo(dcID);
      if (enc.getIndividualID()==null) {

        //positionInList=0;
        try {


          //Vector additionalImageNames = enc.getAdditionalImageNames();
          fileName = sid.getFilename();
          int initNumberImages = enc.getImages().size();
          //remove copyrighted images to allow them to be reset

          //for(int i=0;i<initNumberImages;i++){
          //	String thisImageName=(String)additionalImageNames.get(i);
          //	if((thisImageName.equals(fileName))&&(positionInList==0)){positionInList=i;}
          //}
          //positionInList++;
          
          /*
          for (int j = positionInList; j < (initNumberImages + 1); j++) {
            //remove copyrighted images
            try {

              //File cpyrght=new File(((new File(".")).getCanonicalPath()).replace('\\','/')+"/webapps/ROOT/encounters/"+encounterNumber+"/"+j+".jpg");
              File cpyrght = new File(getServletContext().getRealPath(("/encounters/" + encounterNumber + "/" + j + ".jpg")));


              boolean successfulDelete = false;
              if (cpyrght.exists()) {
                successfulDelete = cpyrght.delete();
              }
              if (!successfulDelete) {
                System.out.println("Unsuccessful attempt to delete file: " + encounterNumber + "/" + fileName);
              }
            } catch (Exception e) {
              e.printStackTrace();
            }

          }
          */

          //enc.removeAdditionalImageName(fileName);
          enc.removeSinglePhotoVideo(sid);
          enc.addComments("<p><em>" + request.getRemoteUser() + " on " + (new java.util.Date()).toString() + "</em><br>" + "Removed encounter image graphic: " + fileName + ".</p>");
          myShepherd.getPM().deletePersistent(sid);
          
          /*
          Iterator<Keyword> keywords = myShepherd.getAllKeywords();
          String toRemove = encounterNumber + "/" + fileName;
          while (keywords.hasNext()) {
            Keyword word = keywords.next();

            //if (word.isMemberOf(toRemove)) {

              word.removeImageName(toRemove);
            }
          }
          */
          

        } catch (Exception le) {
          locked = true;
          myShepherd.rollbackDBTransaction();
          myShepherd.closeDBTransaction();
        }

        if (!locked) {
          myShepherd.commitDBTransaction();
          myShepherd.closeDBTransaction();
          actionResult.setMessageOverrideKey("removeImage").setMessageParams(encounterNumber);

          String message = "An image file named " + fileName + " has been removed from encounter#" + encounterNumber + ".";
          ServletUtilities.informInterestedParties(request, encounterNumber, message,context);
        } else {
          actionResult.setSucceeded(false).setMessageOverrideKey("locked");
        }
      } else {
        myShepherd.rollbackDBTransaction();
        actionResult.setSucceeded(false).setMessageOverrideKey("removeImage-assigned").setMessageParams(encounterNumber);
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
	
	
