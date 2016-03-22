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

//import javax.jdo.*;
//import com.poet.jdo.*;


public class IndividualAddFile extends HttpServlet {

  static SuperSpot tempSuperSpot;

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
    //if(!shepherdDataDir.exists()){shepherdDataDir.mkdirs();}
    File individualsDir=new File(shepherdDataDir.getAbsolutePath()+"/individuals");
    if(!individualsDir.exists()){individualsDir.mkdirs();}


    //set up for response
    response.setContentType("text/html");
    PrintWriter out = response.getWriter();
    boolean locked = false;
    // Prepare for user response.
    String link = "#";
    try {
      link = CommonConfiguration.getServerURL(request, request.getContextPath()) + String.format("/individuals.jsp?number=%s", request.getParameter("individual"));
    }
    catch (URISyntaxException ex) {
    }
    ActionResult actionResult = new ActionResult(locale, "individual.editField", true, link).setParams(request.getParameter("individual"));

    String fileName = "None";
    String individualName = "None";

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
          if (name.equals("individual")) {
            individualName = value;
          }

        }


        if (part.isFile()) {
          FilePart filePart = (FilePart) part;
          fileName = ServletUtilities.cleanFileName(filePart.getFileName());
          if (fileName != null) {


            //File individualsDir = new File(getServletContext().getRealPath(("/" + CommonConfiguration.getMarkedIndividualDirectory())));
            /*if (!individualsDir.exists()) {
              individualsDir.mkdirs();
            }*/


            File thisSharkDir = new File(individualsDir.getAbsolutePath()+"/"+individualName);


            if (!(thisSharkDir.exists())) {
              thisSharkDir.mkdirs();
            }
            ;
            long file_size = filePart.writeTo(
              new File(thisSharkDir, fileName)
            );

          }
        }
      }

      myShepherd.beginDBTransaction();
      if (myShepherd.isMarkedIndividual(individualName)) {
        MarkedIndividual add2shark = myShepherd.getMarkedIndividual(individualName);


        try {

          add2shark.addDataFile(fileName);
          add2shark.addComments("<p><em>" + request.getRemoteUser() + " on " + (new java.util.Date()).toString() + "</em><br>" + "Submitted new file: " + fileName + ".</p>");
        } catch (Exception le) {
          locked = true;
          myShepherd.rollbackDBTransaction();
        }


        if (!locked) {
          myShepherd.commitDBTransaction();
          actionResult.setMessageOverrideKey("addFile");
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
    }

    // Reply to user.
    request.getSession().setAttribute(ActionResult.SESSION_KEY, actionResult);
    getServletConfig().getServletContext().getRequestDispatcher(ActionResult.JSP_PAGE).forward(request, response);

    out.close();
    myShepherd.closeDBTransaction();
  }
}
