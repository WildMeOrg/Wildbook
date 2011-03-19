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

import com.oreilly.servlet.multipart.FilePart;
import com.oreilly.servlet.multipart.MultipartParser;
import com.oreilly.servlet.multipart.ParamPart;
import com.oreilly.servlet.multipart.Part;
import org.ecocean.CommonConfiguration;
import org.ecocean.Encounter;
import org.ecocean.Shepherd;

import javax.servlet.ServletConfig;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.File;
import java.io.IOException;
import java.io.PrintWriter;

/**
 * Uploads a new image to the file system and associates the image with an Encounter record
 *
 * @author jholmber
 */
public class EncounterAddImage extends HttpServlet {

  public void init(ServletConfig config) throws ServletException {
    super.init(config);
  }

  public void doGet(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
    doPost(request, response);
  }

  public void doPost(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
    Shepherd myShepherd = new Shepherd();

    //set up for response
    response.setContentType("text/html");
    PrintWriter out = response.getWriter();
    boolean locked = false;

    String fileName = "None", encounterNumber = "None";


    try {
      MultipartParser mp = new MultipartParser(request, 10 * 1024 * 1024); // 2MB
      Part part;
      while ((part = mp.readNextPart()) != null) {
        String name = part.getName();
        if (part.isParam()) {


          // it's a parameter part
          ParamPart paramPart = (ParamPart) part;
          String value = paramPart.getStringValue();


          //determine which variable to assign the param to
          if (name.equals("number")) {
            encounterNumber = value;
          }

        }


        if (part.isFile()) {
          FilePart filePart = (FilePart) part;
          fileName = ServletUtilities.cleanFileName(filePart.getFileName());
          if (fileName != null) {

            File thisSharkDir = new File(getServletContext().getRealPath(("/encounters/" + encounterNumber)));

            long file_size = filePart.writeTo(
              new File(thisSharkDir, fileName)
            );

          }
        }
      }
      myShepherd.beginDBTransaction();
      if (myShepherd.isEncounter(encounterNumber)) {

        int positionInList = 10000;

        Encounter enc = myShepherd.getEncounter(encounterNumber);
        try {


          enc.addAdditionalImageName(fileName);
          enc.addComments("<p><em>" + request.getRemoteUser() + " on " + (new java.util.Date()).toString() + "</em><br>" + "Submitted new encounter image graphic: " + fileName + ".</p>");
          positionInList = enc.getAdditionalImageNames().size();
        } catch (Exception le) {
          locked = true;
          myShepherd.rollbackDBTransaction();
          myShepherd.closeDBTransaction();
        }


        if (!locked) {
          myShepherd.commitDBTransaction();
          myShepherd.closeDBTransaction();
          out.println(ServletUtilities.getHeader(request));
          out.println("<strong>Success!</strong> I have successfully uploaded your new encounter image file.");
          if (positionInList == 1) {
            out.println("<p><i>You should also reset the thumbnail image for this encounter. You can do so by <a href=\"http://" + CommonConfiguration.getURLLocation(request) + "/resetThumbnail.jsp?number=" + encounterNumber + "\">clicking here.</a></i></p>");
          }
          out.println("<p><a href=\"http://" + CommonConfiguration.getURLLocation(request) + "/encounters/encounter.jsp?number=" + encounterNumber + "\">Return to encounter " + encounterNumber + "</a></p>\n");
          out.println(ServletUtilities.getFooter());
          String message = "An additional image file has been uploaded for encounter #" + encounterNumber + ".";
          ServletUtilities.informInterestedParties(request, encounterNumber, message);
        } else {

          out.println(ServletUtilities.getHeader(request));
          out.println("<strong>Failure!</strong> This encounter is currently being modified by another user. Please wait a few seconds before trying to add this image again.");
          out.println("<p><a href=\"http://" + CommonConfiguration.getURLLocation(request) + "/encounters/encounter.jsp?number=" + encounterNumber + "\">Return to encounter " + encounterNumber + "</a></p>\n");
          out.println(ServletUtilities.getFooter());

        }
      } else {
        myShepherd.rollbackDBTransaction();
        myShepherd.closeDBTransaction();
        out.println(ServletUtilities.getHeader(request));
        out.println("<strong>Error:</strong> I was unable to upload your image file. I cannot find the encounter that you intended it for in the database.");
        out.println(ServletUtilities.getFooter());

      }
    } catch (IOException lEx) {
      lEx.printStackTrace();
      out.println(ServletUtilities.getHeader(request));
      out.println("<strong>Error:</strong> I was unable to upload your image file. Please contact the web master about this message.");
      out.println(ServletUtilities.getFooter());
    } catch (NullPointerException npe) {
      npe.printStackTrace();
      out.println(ServletUtilities.getHeader(request));
      out.println("<strong>Error:</strong> I was unable to upload an image as no file was specified.");
      out.println(ServletUtilities.getFooter());
    }
    out.close();
  }


}
	
	
