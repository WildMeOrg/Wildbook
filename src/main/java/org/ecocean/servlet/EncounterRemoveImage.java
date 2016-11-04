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
import java.util.Iterator;
import java.util.Vector;

//import com.poet.jdo.*;


public class EncounterRemoveImage extends HttpServlet {


  public void init(ServletConfig config) throws ServletException {
    super.init(config);
  }


  public void doGet(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
    doPost(request, response);
  }


  public void doPost(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
    String context="context0";
    context=ServletUtilities.getContext(request);
    Shepherd myShepherd = new Shepherd(context);
    myShepherd.setAction("EncounterRemoveImage.class");
    //set up for response
    response.setContentType("text/html");
    PrintWriter out = response.getWriter();
    boolean locked = false;
    //boolean assigned=false;
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
          out.println(ServletUtilities.getHeader(request));
          out.println("<strong>Success!</strong> I have successfully removed the encounter image file. When returning to the encounter page, please make sure to refresh your browser to see the changes. Image changes will not be visible until you have done so.");

          out.println("<p><a href=\"http://" + CommonConfiguration.getURLLocation(request) + "/encounters/encounter.jsp?number=" + encounterNumber + "\">Return to encounter " + encounterNumber + "</a></p>\n");
          out.println(ServletUtilities.getFooter(context));
          String message = "An image file named " + fileName + " has been removed from encounter#" + encounterNumber + ".";
          ServletUtilities.informInterestedParties(request, encounterNumber, message,context);
        } else {

          out.println(ServletUtilities.getHeader(request));
          out.println("<strong>Failure!</strong> This encounter is currently being modified by another user. Please wait a few seconds before trying to remove this image again.");

          out.println("<p><a href=\"http://" + CommonConfiguration.getURLLocation(request) + "/encounters/encounter.jsp?number=" + encounterNumber + "\">Return to encounter " + encounterNumber + "</a></p>\n");
          out.println(ServletUtilities.getFooter(context));

        }
      } else {
        myShepherd.rollbackDBTransaction();
        myShepherd.closeDBTransaction();
        out.println(ServletUtilities.getHeader(request));
        out.println("<strong>Error:</strong> I was unable to remove your image file. For data protection, you must first remove the encounter from the marked individual it is assigned to.");
        out.println(ServletUtilities.getFooter(context));
      }
    } else {
      myShepherd.rollbackDBTransaction();
      myShepherd.closeDBTransaction();
      out.println(ServletUtilities.getHeader(request));
      out.println("<strong>Error:</strong> I was unable to remove your image file. I cannot find the encounter that you intended it for in the database. +++");
      out.println(ServletUtilities.getFooter(context));

    }
    out.close();
  }


}
	
	
