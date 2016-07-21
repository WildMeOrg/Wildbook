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

import org.ecocean.*;

import org.apache.commons.io.FilenameUtils;

import javax.servlet.ServletConfig;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import java.io.File;
import java.io.IOException;
import java.io.PrintWriter;
import java.net.URISyntaxException;
import java.util.Locale;
import java.util.Map;


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
    String context = ServletUtilities.getContext(request);
    String langCode = ServletUtilities.getLanguageCode(request);
    Locale locale = new Locale(langCode);

    Shepherd myShepherd = new Shepherd(context);
    //setup data dir
    String rootWebappPath = getServletContext().getRealPath("/");
    File webappsDir = new File(rootWebappPath).getParentFile();
    File shepherdDataDir = new File(webappsDir, CommonConfiguration.getDataDirectoryName(context));
    if(!shepherdDataDir.exists()){shepherdDataDir.mkdirs();}
    File encountersDir=new File(shepherdDataDir.getAbsolutePath()+"/encounters");
    if(!encountersDir.exists()){encountersDir.mkdirs();}
    
    //set up for response
    response.setContentType("text/html");
    PrintWriter out = response.getWriter();
    boolean locked = false;

    // Prepare for user response.
    String link = "#";
    ActionResult actionResult = new ActionResult(locale, "encounter.editField", true, link);

    String fileName = "None";
    String encounterNumber = "None";
    String fullPathFilename="";

    try {
      MultipartParser mp = new MultipartParser(request, (CommonConfiguration.getMaxMediaSizeInMegabytes(context) * 1048576)); 
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
            try {
              link = CommonConfiguration.getServerURL(request, request.getContextPath()) + String.format("/encounters/encounter.jsp?number=%s", encounterNumber);
              actionResult.setLink(link);
              actionResult.setLinkParams(encounterNumber);
            }
            catch (URISyntaxException ex) {
            }
          }

        }


////TODO this will need to be generified for offsite storage prob via SinglePhotoVideo? as in EncounterForm?
        if (part.isFile()) {
          FilePart filePart = (FilePart) part;
          fileName = ServletUtilities.cleanFileName(filePart.getFileName());
          if (fileName != null) {
						//fileName = Util.generateUUID() + "-orig." + FilenameUtils.getExtension(fileName);
            //File thisSharkDir = new File(encountersDir.getAbsolutePath() +"/"+ Encounter.subdir(encounterNumber));
            File thisSharkDir = new File(Encounter.dir(shepherdDataDir, encounterNumber));
            if(!thisSharkDir.exists()){thisSharkDir.mkdirs();}
            File finalFile=new File(thisSharkDir, fileName);
            fullPathFilename=finalFile.getCanonicalPath();
            long file_size = filePart.writeTo(finalFile);

          }
        }
      }
      

      //File thisEncounterDir = new File(encountersDir, Encounter.subdir(encounterNumber));
			File thisEncounterDir = new File(Encounter.dir(shepherdDataDir, encounterNumber));
      
      myShepherd.beginDBTransaction();
      if (myShepherd.isEncounter(encounterNumber)) {

        int positionInList = 10000;

        Encounter enc = myShepherd.getEncounter(encounterNumber);
        try {


          SinglePhotoVideo newSPV = new SinglePhotoVideo(encounterNumber,(new File(fullPathFilename)));
          enc.addSinglePhotoVideo(newSPV);
					enc.refreshAssetFormats(context, ServletUtilities.dataDir(context, rootWebappPath), newSPV, false);
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
          actionResult.setMessageOverrideKey("addImage").setMessageParams(encounterNumber);

          if (positionInList == 1) {
            String resetLink = String.format("http://%s/resetThumbnail.jsp?number=%s", CommonConfiguration.getURLLocation(request), encounterNumber);
            actionResult.setCommentOverrideKey("addImage-withReset").setCommentParams(resetLink);
          }

          String message = "An additional image file has been uploaded for encounter #" + encounterNumber + ".";
          ServletUtilities.informInterestedParties(request, encounterNumber, message,context);
        } else {
          actionResult.setSucceeded(false).setMessageOverrideKey("locked");
        }
      } else {
        myShepherd.rollbackDBTransaction();
        actionResult.setSucceeded(false);
      }
    } catch (IOException lEx) {
      lEx.printStackTrace();
      actionResult.setSucceeded(false);
    } catch (NullPointerException npe) {
      npe.printStackTrace();
      actionResult.setSucceeded(false);
    }

    // Reply to user.
    request.getSession().setAttribute(ActionResult.SESSION_KEY, actionResult);
    getServletConfig().getServletContext().getRequestDispatcher(ActionResult.JSP_PAGE).forward(request, response);

    out.close();
    myShepherd.closeDBTransaction();
  }
}
	
	
