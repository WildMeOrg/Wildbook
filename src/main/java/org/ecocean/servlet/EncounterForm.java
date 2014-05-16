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

//////
//import java.io.*;
//import java.util.*;
 
import java.util.List;
import org.apache.commons.fileupload.FileItem;
import org.apache.commons.fileupload.FileUploadException;
import org.apache.commons.fileupload.disk.DiskFileItemFactory;
import org.apache.commons.fileupload.servlet.ServletFileUpload;
import org.apache.commons.io.output.*;
/////

import org.ecocean.CommonConfiguration;
import org.ecocean.Encounter;
import org.ecocean.Shepherd;
import org.ecocean.SinglePhotoVideo;
import org.ecocean.User;

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
public class EncounterForm extends HttpServlet {

  public void init(ServletConfig config) throws ServletException {
    super.init(config);
  }

  public void doGet(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
    doPost(request, response);
  }
 
private final String UPLOAD_DIRECTORY = "/tmp";

  public void doPost(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
    
    String context="context0";
    //context=ServletUtilities.getContext(request);
    Shepherd myShepherd = new Shepherd(context);
System.out.println("in context " + context);

    //setup data dir
    String rootWebappPath = getServletContext().getRealPath("/");
    File webappsDir = new File(rootWebappPath).getParentFile();
    File shepherdDataDir = new File(webappsDir, CommonConfiguration.getDataDirectoryName(context));
    if(!shepherdDataDir.exists()){shepherdDataDir.mkdir();}
    File encountersDir=new File(shepherdDataDir.getAbsolutePath()+"/users");
    if(!encountersDir.exists()){encountersDir.mkdir();}
    
    //set up for response
    response.setContentType("text/html");
    PrintWriter out = response.getWriter();
    boolean locked = false;

    String fileName = "None";
    String username = "None";
    String fullPathFilename="";



		String doneMessage = "huh?";

		if (ServletFileUpload.isMultipartContent(request)) {
			try {
				List<FileItem> multiparts = new ServletFileUpload(new DiskFileItemFactory()).parseRequest(request);
							
				for(FileItem item : multiparts){
					if (item.isFormField()) {  //plain field
System.out.println("got regular field (" + item.getFieldName() + ")=(" + item.getString() + ")");

					} else {  //file
						String name = new File(item.getName()).getName();
System.out.println("file name = " + name);
						item.write( new File(UPLOAD_DIRECTORY + File.separator + name));
					}
				}

				doneMessage = "File Uploaded Successfully";
			} catch (Exception ex) {
				doneMessage = "File Upload Failed due to " + ex;
			}

		} else {
			doneMessage = "Sorry this Servlet only handles file upload request";
		}
        ///request.getRequestDispatcher("/result.jsp").forward(request, response);

      //Encounter enc = new Encounter(day, month, year, hour, minutes, guess, location, submitterName, submitterEmail, images);

    //myShepherd.rollbackDBTransaction();
    //myShepherd.closeDBTransaction();
    out.println(ServletUtilities.getHeader(request));
    out.println("something happened! -> " + doneMessage);
    out.println(ServletUtilities.getFooter(context));
    out.close();
System.out.println("done??????");
  }


}
  
  
