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

import org.ecocean.CommonConfiguration;
import org.ecocean.SinglePhotoVideo;
import org.ecocean.Encounter;
import org.ecocean.Shepherd;

import javax.servlet.ServletConfig;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Iterator;
import java.io.File;

import java.nio.file.Files;
import java.nio.file.Paths;

import javax.xml.bind.DatatypeConverter;

import com.google.gson.Gson;


public class EncounterCR extends HttpServlet {


  public void init(ServletConfig config) throws ServletException {
    super.init(config);
  }

  private void setDateLastModified(Encounter enc) {

    String strOutputDateTime = ServletUtilities.getDate();
    enc.setDWCDateLastModified(strOutputDateTime);
  }

  public void doGet(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
    doPost(request, response);
  }

  public void doPost(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
    Shepherd myShepherd = new Shepherd();
    boolean locked = false, isOwner = true;
    //boolean isAssigned = false;

		String errorMessage = null;
		String encID = request.getParameter("encounterID");
		String matchFilename = request.getParameter("matchFilename");
		String pngData = request.getParameter("pngData");
		Encounter enc = null;

		myShepherd.beginDBTransaction();

		if (encID != null) enc = myShepherd.getEncounter(encID);

		if (enc == null) {
			errorMessage = "invalid Encounter number";

		} else if (matchFilename == null) {
			errorMessage = "no matched file given";

		} else if (pngData == null) {
			errorMessage = "no image data sent";

		} else {
			byte[] rawPng = null;
			try {
				rawPng = DatatypeConverter.parseBase64Binary(pngData);
			} catch (IllegalArgumentException ex) {
				errorMessage = "could not parse image data";
			}

			if (rawPng != null) {
//////////////////////// TODO use the 5.x enc.path stuff
				String rootWebappPath = getServletContext().getRealPath("/");
				File webappsDir = new File(rootWebappPath).getParentFile();
				File shepherdDataDir = new File(webappsDir, CommonConfiguration.getDataDirectoryName());
				File encounterDir=new File(shepherdDataDir.getAbsolutePath()+"/encounters/" + encID);
				File sourceImg = new File(encounterDir, matchFilename);
System.out.println(sourceImg.toString());
				if (!sourceImg.exists()) {
					errorMessage = "source image does not exist";

				} else {
					int dot = matchFilename.lastIndexOf('.');
					String crFilename = matchFilename.substring(0, dot) + "-CR.png";
					File crFile = new File(encounterDir, crFilename);
System.out.println(sourceImg.toString() + " --> " + crFilename);
					Files.write(crFile.toPath(), rawPng);
System.out.println(crFile.toString() + " written");
					enc.setMmaCompatible(true);
					myShepherd.storeNewEncounter(enc, encID);
				}
			}
		}


		//myShepherd.commitDBTransaction();
    myShepherd.closeDBTransaction();


		if (errorMessage == null) {
			String url = "http://" + CommonConfiguration.getURLLocation(request) + "/encounters/encounter.jsp?number=" + encID;
			response.sendRedirect(url);

		} else {
    	response.setContentType("text/html");
    	PrintWriter out = response.getWriter();
			out.println(ServletUtilities.getHeader(request));
			out.println("<p><strong>ERROR:</strong> " + errorMessage + "</p>");
			out.println(ServletUtilities.getFooter());
    	out.close();
		}
  }

}
