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

import javax.servlet.Servlet;
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

/**
 * Uploads a new image to the file system and associates the image with an Encounter record
 *
 * @author jholmber
 */
public class UserAddProfileImage extends HttpServlet {

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
    File encountersDir=new File(shepherdDataDir.getAbsolutePath()+"/users");
    if(!encountersDir.exists()){encountersDir.mkdirs();}
    
    //set up for response
    response.setContentType("text/html");
    PrintWriter out = response.getWriter();
    boolean locked = false;

    String fileName = "None";
    String username = "None";
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
          if (name.equals("username")) {
            username = value;
          }

        }


        if (part.isFile()) {
          
          if(request.getRequestURL().indexOf("MyAccount")!=-1){
            username=request.getUserPrincipal().getName();
          }
          
          FilePart filePart = (FilePart) part;
          fileName = ServletUtilities.cleanFileName(filePart.getFileName());
          if (fileName != null) {

            File thisSharkDir = new File(encountersDir.getAbsolutePath() +"/"+ username);
            if(!thisSharkDir.exists()){thisSharkDir.mkdirs();}
            File finalFile=new File(thisSharkDir, fileName);
            fullPathFilename=finalFile.getCanonicalPath();
            long file_size = filePart.writeTo(finalFile);

          }
        }
      }
      

      File thisEncounterDir = new File(encountersDir, username);
      
      myShepherd.beginDBTransaction();
      if (myShepherd.getUser(username)!=null) {

        int positionInList = 10000;

        User enc = myShepherd.getUser(username);
        try {

          SinglePhotoVideo spv=new SinglePhotoVideo(username,(new File(fullPathFilename)));
          spv.setCorrespondingUsername(username);
          spv.setCorrespondingEncounterNumber(null);
          enc.setUserImage(spv);
          //enc.addComments("<p><em>" + request.getRemoteUser() + " on " + (new java.util.Date()).toString() + "</em><br>" + "Submitted new encounter image graphic: " + fileName + ".</p>");
          //positionInList = enc.getAdditionalImageNames().size();
        } catch (Exception le) {
          locked = true;
          myShepherd.rollbackDBTransaction();
          myShepherd.closeDBTransaction();
        }

        String link = null;
        try {
          if (request.getRequestURL().indexOf("MyAccount") >= 0)
            link = CommonConfiguration.getServerURL(request, request.getContextPath()) + "/myAccount.jsp";
          else
            link = CommonConfiguration.getServerURL(request, request.getContextPath()) + String.format("/appadmin/users.jsp?context=%s&isEdit=true&username=%s#editUser", context, username);
        }
        catch (URISyntaxException ex) {
          throw new ServletException(ex);
        }

        if (!locked) {
          myShepherd.commitDBTransaction();
          myShepherd.closeDBTransaction();
          ActionResult actRes = new ActionResult(locale, "addProfileImage", true, link);
          request.getSession().setAttribute(ActionResult.SESSION_KEY, actRes);
          getServletConfig().getServletContext().getRequestDispatcher(ActionResult.JSP_PAGE).forward(request, response);
        } else {
          ActionResult actRes = new ActionResult(locale, "addProfileImage", false, link);
          request.getSession().setAttribute(ActionResult.SESSION_KEY, actRes);
          getServletConfig().getServletContext().getRequestDispatcher(ActionResult.JSP_PAGE).forward(request, response);
        }
      } else {
        myShepherd.rollbackDBTransaction();
        myShepherd.closeDBTransaction();
        out.println(ServletUtilities.getHeader(request));
        out.println("<strong>Error:</strong> I was unable to upload your image file. I cannot find the username that you intended it for in the database.");
        out.println(ServletUtilities.getFooter(context));

      }
    } catch (IOException lEx) {
      lEx.printStackTrace();
      out.println(ServletUtilities.getHeader(request));
      out.println("<strong>Error:</strong> I was unable to upload your image file. Please contact the webmaster about this message.");
      out.println(ServletUtilities.getFooter(context));
    } catch (NullPointerException npe) {
      npe.printStackTrace();
      out.println(ServletUtilities.getHeader(request));
      out.println("<strong>Error:</strong> I was unable to upload an image as no file was specified.");
      out.println(ServletUtilities.getFooter(context));
    }
    out.close();
  }


}
  
  
