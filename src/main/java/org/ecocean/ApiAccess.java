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

package org.ecocean;

/*
import org.apache.commons.codec.digest.DigestUtils;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.StringTokenizer;
import java.util.TreeMap;
import java.util.Vector;
import java.util.GregorianCalendar;
import java.io.*;

import org.ecocean.genetics.*;
import org.ecocean.tag.AcousticTag;
import org.ecocean.tag.MetalTag;
import org.ecocean.tag.SatelliteTag;
import org.ecocean.Util;

*/
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.http.HttpSession;
import javax.servlet.ServletContext;

import org.ecocean.servlet.ServletUtilities;

import java.io.File;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

public class ApiAccess {
  private int day = 0;
	private Document configDoc = null;



	public Document initConfig(HttpServletRequest request) {
		if (configDoc != null) return configDoc;
		HttpSession session = request.getSession(true);
    String context = "context0";
    context = ServletUtilities.getContext(request);
    //Shepherd myShepherd = new Shepherd(context);
		ServletContext sc = session.getServletContext();
System.out.println("reading file???");
		File afile = new File(sc.getRealPath("/") + "WEB-INF/classes/apiaccess.xml");

		// h/t http://www.mkyong.com/java/how-to-read-xml-file-in-java-dom-parser/
		try {
			DocumentBuilderFactory dbFactory = DocumentBuilderFactory.newInstance();
			DocumentBuilder dBuilder = dbFactory.newDocumentBuilder();
			Document configDoc = dBuilder.parse(afile);
			configDoc.getDocumentElement().normalize();
		} catch (Exception ex) {
			System.out.println("could not read " + afile.toString() + ": " + ex.toString());
			configDoc = null;
		}
		return configDoc;
	}

}


