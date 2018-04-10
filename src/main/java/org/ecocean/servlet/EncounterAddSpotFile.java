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
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.PrintWriter;

import javax.xml.bind.DatatypeConverter;

/**
 *
 * This servlet allows the user to upload an extracted, processed patterning file that corresponds to
 * a previously uploaded set of spots. This file is then used for visualization of the extracted pattern
 * and visualizations of potentially matched spots.
 * @author jholmber
 *
 */
public class EncounterAddSpotFile extends HttpServlet {


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
    myShepherd.setAction("EncounterAddSpotFile.class");

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
    boolean removedJPEG = false, locked = false;
    String fileName = "None", encounterNumber = "None";
    String side = "left";


		String imageContents = request.getParameter("imageContents");


		if (imageContents != null) {
			encounterNumber = request.getParameter("number");
			if ((request.getParameter("rightSide") != null) && request.getParameter("rightSide").equals("true")) side = "right";

			byte[] imgBytes = new byte[100];
			try {
				imgBytes = DatatypeConverter.parseBase64Binary(imageContents);
			} catch (IllegalArgumentException ex) {
				System.out.println("could not parse imageContents base64: " + ex.toString());
			}
			if (imgBytes.length > 0) {
				if (side.equals("right")) {
					fileName = "extractRight" + encounterNumber + ".jpg";
				} else {
					fileName = "extract" + encounterNumber + ".jpg";
				}
				File spotFile = new File(Encounter.dir(shepherdDataDir, encounterNumber), fileName);
//System.out.println("got imgBytes! -> " + spotFile.toString());
				FileOutputStream stream = new FileOutputStream(spotFile);
				try {
					stream.write(imgBytes);
				} finally {
					stream.close();
      		myShepherd.beginDBTransaction();
        	Encounter add2shark = myShepherd.getEncounter(encounterNumber);
        	try {
          	if (side.equals("right")) {
            	add2shark.setRightSpotImageFileName(fileName);
            	//add2shark.hasRightSpotImage = true;
          	} else {
            	add2shark.setSpotImageFileName(fileName);
            	//add2shark.hasSpotImage = true;
          	}

          	String user = "Unknown User";
          	if (request.getRemoteUser() != null) {
            	user = request.getRemoteUser();
          	}
          	add2shark.addComments("<p><em>" + user + " on " + (new java.util.Date()).toString() + "</em><br>" + "Submitted new " + side + "-side spot data graphic.</p>");

        	} catch (Exception le) {
          	locked = true;
          	myShepherd.rollbackDBTransaction();
          	le.printStackTrace();
        	}

        	if (!locked) {
          	myShepherd.commitDBTransaction();
          	myShepherd.closeDBTransaction();
					}
				}
			}

		}



    if (imageContents == null) try {

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
          if (name.equals("rightSide")) {
            if (value.equals("true")) {
              side = "right";
            }
          }


        }

        //File thisEncounterDir = new File(encountersDir, Encounter.subdir(encounterNumber));
				File thisEncounterDir = new File(Encounter.dir(shepherdDataDir, encounterNumber));
        if (part.isFile()) {
          FilePart filePart = (FilePart) part;
          fileName = ServletUtilities.cleanFileName(filePart.getFileName());
          if ((fileName != null)&&(myShepherd.isAcceptableImageFile(fileName))) {
            //File thisSharkDir = new File(thisEncounterDir.getAbsolutePath() + "/" + encounterNumber);


            //eliminate the previous JPG version of this file if it existed      										//eliminate the previous JPG if it existed
            try {

              String sideAddition = "";
              if (side.equals("right")) {
                sideAddition = "Right";
                fileName = "RIGHT" + fileName;
              } else {
                fileName = "LEFT" + fileName;
              }

              File jpegVersion = new File(thisEncounterDir, ("extract" + sideAddition + encounterNumber + ".jpg"));
              if (jpegVersion.exists()) {
                removedJPEG = jpegVersion.delete();
              }

            } catch (SecurityException thisE) {
              thisE.printStackTrace();
              System.out.println("Error attempting to delete the old JPEG version of a submitted spot data image!!!!");
              removedJPEG = false;
            }

            long file_size = filePart.writeTo(
              new File(thisEncounterDir, fileName)
            );


          }
          else{
            locked = true;
            myShepherd.rollbackDBTransaction();
          }
        }
      }


      //System.out.println(encounterNumber);
      //System.out.println(fileName);
      myShepherd.beginDBTransaction();
      if ((myShepherd.isEncounter(encounterNumber))&&!locked) {
        Encounter add2shark = myShepherd.getEncounter(encounterNumber);
        try {
          if (side.equals("right")) {
            add2shark.setRightSpotImageFileName(fileName);
            //add2shark.hasRightSpotImage = true;
          } else {
            add2shark.setSpotImageFileName(fileName);
            //add2shark.hasSpotImage = true;
          }

          String user = "Unknown User";
          if (request.getRemoteUser() != null) {
            user = request.getRemoteUser();
          }
          add2shark.addComments("<p><em>" + user + " on " + (new java.util.Date()).toString() + "</em><br>" + "Submitted new " + side + "-side spot data graphic.</p>");

        } catch (Exception le) {
          locked = true;
          myShepherd.rollbackDBTransaction();
          le.printStackTrace();
        }


        if (!locked) {
          myShepherd.commitDBTransaction();
          myShepherd.closeDBTransaction();
          String sideAddition = "";
          if (side.equals("right")) {
            sideAddition = "&rightSide=true";
          }
          out.println(ServletUtilities.getHeader(request));
          out.println("<strong>Step 2 Confirmed:</strong> I have successfully uploaded your " + side + "-side spot data image file.");
          out.println("<p><a href=\"http://" + CommonConfiguration.getURLLocation(request) + "/encounters/encounter.jsp?number=" + encounterNumber + "#spotpatternmatching\">Return to encounter " + encounterNumber + "</a></p>\n");
          out.println(ServletUtilities.getFooter(context));
        } else {
          out.println(ServletUtilities.getHeader(request));
          out.println("<strong>Step 2 Failed:</strong> This encounter is currently locked and modified by another user. Please try to resubmit your spot data and add this image again in a few seconds.");

          out.println("<p><a href=\"http://" + CommonConfiguration.getURLLocation(request) + "/encounters/encounter.jsp?number=" + encounterNumber + "\">Return to encounter " + encounterNumber + "</a></p>\n");
          out.println(ServletUtilities.getFooter(context));
        }
      } else {
        myShepherd.rollbackDBTransaction();
        myShepherd.closeDBTransaction();
        out.println(ServletUtilities.getHeader(request));
        out.println("<strong>Error:</strong> I was unable to upload your file. I cannot find the encounter that you intended it for in the database, or the file type uploaded is not supported.");
        out.println(ServletUtilities.getFooter(context));
      }
    } catch (IOException lEx) {
      lEx.printStackTrace();
      out.println(ServletUtilities.getHeader(request));
      out.println("<strong>Error:</strong> I was unable to upload your file.");
      out.println(ServletUtilities.getFooter(context));
      myShepherd.rollbackDBTransaction();
      myShepherd.closeDBTransaction();
    }
    out.close();
  }


}


