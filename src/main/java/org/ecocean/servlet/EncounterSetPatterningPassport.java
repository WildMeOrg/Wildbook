/*
 * The Shepherd Project - A Mark-Recapture Framework
 * Copyright (C) 2012 Jason Holmberg
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

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.io.Reader;
import java.io.StringWriter;
import java.io.Writer;
import java.util.Enumeration;

import javax.servlet.ServletConfig;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.ecocean.CommonConfiguration;
import org.ecocean.Encounter;
import org.ecocean.PatterningPassport;
import org.ecocean.Shepherd;
import org.ecocean.SinglePhotoVideo;

import com.oreilly.servlet.multipart.FilePart;
import com.oreilly.servlet.multipart.ParamPart;
import com.oreilly.servlet.multipart.MultipartParser;
import com.oreilly.servlet.multipart.Part;
import com.oreilly.servlet.MultipartRequest;

/**
 * Populates a patterning passport for a specific image/mediafile encounterId
 * mediaId
 * 
 * @author estastny
 */
public class EncounterSetPatterningPassport extends HttpServlet {

  public void init(ServletConfig config) throws ServletException {
    super.init(config);
  }

  public void doGet(HttpServletRequest request, HttpServletResponse response)
      throws ServletException, IOException {
    doPost(request, response); // Just forwards to the POST
  }

  /*
   * (non-Javadoc)
   * 
   * @see javax.servlet.http.HttpServlet#doPost(javax.servlet.http.HttpServletRequest,
   *      javax.servlet.http.HttpServletResponse)
   */
  public void doPost(HttpServletRequest request, HttpServletResponse response)
      throws ServletException, IOException {
    // set up for response
    response.setContentType("text/html");
    PrintWriter out = response.getWriter();
    String responseMsg = "";
    
    String context="context0";
    context=ServletUtilities.getContext(request);

    String mediaId = "";
    String encounterId = "";

    File xmlFile = null;

    String fileUploadLocation = getServletContext().getRealPath("/");

    // constructs a new MultipartRequest to handle the specified request
    MultipartRequest multipartRequest = new MultipartRequest(request,
        fileUploadLocation);

    // get params
    Enumeration parameters = multipartRequest.getParameterNames();
    while (parameters.hasMoreElements()) {
      String name = (String) parameters.nextElement();
      String val = (String) multipartRequest.getParameter(name);

      if (name.equals("mediaId")) {
        mediaId = val;
      } else if (name.equals("encounterId")) {
        encounterId = val;
      }
    }

    // extract the uploaded files as an Enumeration
    Enumeration files = multipartRequest.getFileNames();
    // getting a few details about each uploaded file
    while (files.hasMoreElements()) {
      String name = (String) files.nextElement();
//      String type = multipartRequest.getContentType(name);
//      String filename = multipartRequest.getFilesystemName(name);
//      String originalFilename = multipartRequest.getOriginalFileName(name);
     
      xmlFile = multipartRequest.getFile(name);
    }

    // update it only if there is a stream
    if (mediaId != null && encounterId != null && xmlFile != null) {
      responseMsg += this.setPassportForMediaObject(mediaId, encounterId,
          xmlFile,context);
    } else {
      responseMsg += "<br/>Not enough data to setPassportForMediaObject. <ul><li>"
          + mediaId + "<li>" + encounterId + "</ul> ";
    }

    // response
    out.println(ServletUtilities.getHeader(request));
    out.println(responseMsg);
    out.println(ServletUtilities.getFooter(context));
    out.close();

    return;
  }

  public String setPassportForMediaObject(String mediaId, String encounterId, File xmlFile,String context) {
    String returnString = "";
    FileInputStream passportXmlStream;

    // Convert file to input stream
    try {
      passportXmlStream = new FileInputStream(xmlFile);
    } catch (java.io.FileNotFoundException e) {
      return ("Failed to convert file to FileInputStream.");
    }

    // Make string of contents
    String xmlString;
    try {
      xmlString = this.convertStreamToString(passportXmlStream);
      // returnString += "File converted to String object. || ";
    } catch (IOException e) {
      return ("Failed to read file as string.");
    }

    // Set contexts/locations for PP
    // NOTE: need to do this from servlet, because servletContext is referenced.
    

    
    // Setup data dir
    String rootWebappPath = getServletContext().getRealPath("/");
    File webappsDir = new File(rootWebappPath).getParentFile();
    File shepherdDataDir = new File(webappsDir, CommonConfiguration
        .getDataDirectoryName(context));

    Shepherd myShepherd = new Shepherd(context);
    myShepherd.setAction("EncounterSetPatterningPassport.class");
    // Get the Encounter object for this
    Encounter enc = null;
    if (myShepherd.isEncounter(encounterId)) {
      enc = myShepherd.getEncounter(encounterId);
      //returnString += "Encounter " + encounterId + " found! || ";
    } else {
      return ("Failure: Found no encounter matching encounterId:" + encounterId);
    }

    // Get the SinglePhotoVideo object this refers to
    SinglePhotoVideo mediaObject = null;
    if (enc != null) {
      mediaObject = myShepherd.getSinglePhotoVideo(mediaId);
    }

    // fail if no valid mediaObject
    if (mediaObject == null) {
      // error message
      return ("Failure: no media object match for mediaId:" + mediaId);
    } 

    myShepherd.beginDBTransaction();
    // Get the PatterningPassport from that media object
    //PatterningPassport pp = new PatterningPassport();
    PatterningPassport pp = mediaObject.getPatterningPassport();
    
    // following vars need setting from this servlet context
    pp.setWebappsDir(webappsDir);
    pp.setEncounterId(encounterId);
    pp.setMediaId(mediaId);

    // apply the data (node of pattern matching xml) to the patterningPassport
    Boolean setSuccess = pp.setPassportDataXml(xmlString,context);
    if (setSuccess.equals(Boolean.TRUE)) {
      returnString += "PatterningPassport successfully attached!<br/>";
      
      // TEMP -> 
      System.out.println("-----\n");
      System.out.println("Here is the patterning passport OBJECT's data: \n");
      System.out.println(pp.getPassportDataXml(context));
      // <- TEMP
      
      myShepherd.commitDBTransaction();
    } else {
      returnString += "PatterningPassport attach FAILED.  Are you sure it's valid XML?<br/>";
      myShepherd.rollbackDBTransaction();
    }
    
    myShepherd.closeDBTransaction();
    
    return returnString;
  }

  /**
   * Converts given InputStream to a String
   * 
   * @param is
   * @return
   * @throws IOException
   */
  public String convertStreamToString(FileInputStream is) throws IOException {
    /*
     * To convert the InputStream to String we use the Reader.read(char[]
     * buffer) method. We iterate until the Reader return -1 which means there's
     * no more data to read. We use the StringWriter class to produce the
     * string.
     */
    if (is != null) {
      Writer writer = new StringWriter();

      char[] buffer = new char[1024];
      try {
        Reader reader = new BufferedReader(new InputStreamReader(is, "UTF-8"));
        int n;
        while ((n = reader.read(buffer)) != -1) {
          writer.write(buffer, 0, n);
        }
      } finally {
        is.close();
      }
      return writer.toString();
    } else {
      return "";
    }
  }
}
