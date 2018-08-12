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

import java.util.StringTokenizer;
import java.util.TreeMap;
import java.util.Vector;
import java.util.GregorianCalendar;
import java.io.*;
*/

import java.util.ArrayList;
import java.util.Iterator;
import java.util.HashMap;
import java.util.List;


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

import org.json.JSONObject;

public class ApiAccess {
  private int day = 0;
	private Document configDoc = null;



	public Document initConfig(HttpServletRequest request) {
		if (this.configDoc != null) return this.configDoc;
		HttpSession session = request.getSession(true);
    String context = "context0";
    context = ServletUtilities.getContext(request);
    //Shepherd myShepherd = new Shepherd(context);
		ServletContext sc = session.getServletContext();
		File afile = new File(sc.getRealPath("/") + "/WEB-INF/classes/apiaccess.xml");
System.out.println("reading file??? " + afile.toString());

		// h/t http://www.mkyong.com/java/how-to-read-xml-file-in-java-dom-parser/
		try {
			DocumentBuilderFactory dbFactory = DocumentBuilderFactory.newInstance();
			DocumentBuilder dBuilder = dbFactory.newDocumentBuilder();
			this.configDoc = dBuilder.parse(afile);
			this.configDoc.getDocumentElement().normalize();
		} catch (Exception ex) {
			System.out.println("could not read " + afile.toString() + ": " + ex.toString());
			this.configDoc = null;
		}
		return this.configDoc;
	}


	//this does the real work.  returns null if ok, or string with error message if request is disallowed on object
	public String checkRequest(Object obj, HttpServletRequest request, JSONObject jsonobj) {
		String err = null;
		if (!request.getMethod().equals("POST") && !request.getMethod().equals("PUT")) return null;  //we only care about PUT/POST for now
		HashMap<String, String> perm = permissions(obj, request);

		Iterator<?> keys = jsonobj.keys();
		while (keys.hasNext()) {
			String key = (String) keys.next();
System.out.println(key);
			//we dont care what the value is, just if it is being set at all and shouldnt be
			if (perm.containsKey(key) && (perm.get(key) == null)) {
				err = "altering value for " + key + " disallowed by permissions: " + perm.toString();
				break;
			}
		}
		return err;
	}


	//returns map of (negative) permissions for this user (based on role) for this object class
	// note: no hash key for a property means all access, therefore a null value means user CANNOT write
	// TODO this structure is subject to change for sure!
	public HashMap<String, String> permissions(Object o, HttpServletRequest request) {
		return permissions(o.getClass().getName(), request);
	}

	//does the real work (pass class name string)
	public HashMap<String, String> permissions(String cname, HttpServletRequest request) {
		HashMap<String, String> perm = new HashMap<String, String>();
		this.initConfig(request);
		String context = "context0";
		context = ServletUtilities.getContext(request);
    Shepherd myShepherd = new Shepherd(context);
    myShepherd.setAction("ApiAccess.class");
    myShepherd.beginDBTransaction();
		String username = "";
		if (request.getUserPrincipal() != null) username = request.getUserPrincipal().getName();
		List<Role> roleObjs = myShepherd.getAllRolesForUserInContext(username, context);
		List<String> roles = new ArrayList<String>();
		for (Role r : roleObjs) {
			roles.add(r.getRolename());
		}
		System.out.println("[class " + cname + "] roles for user '" + username + "': " + roles);

		NodeList nlist = this.configDoc.getDocumentElement().getElementsByTagName("class");
		if (nlist.getLength() < 1) {
		  myShepherd.rollbackDBTransaction();
		  myShepherd.closeDBTransaction();
		  return perm;
		}

		for (int i = 0 ; i < nlist.getLength() ; i++) {
			Node n = nlist.item(i);
			if (n.getNodeType() == Node.ELEMENT_NODE) {
				Element el = (Element) n;
				if (el.getAttribute("name").equals(cname)) {
					Node p = el.getElementsByTagName("properties").item(0);
					if (p == null) {     
					  myShepherd.rollbackDBTransaction();
					  myShepherd.closeDBTransaction();
					  return perm;
					}
//System.out.println("ok in " + cname);
					Element propsEl = (Element) p;
					NodeList props = propsEl.getElementsByTagName("property");
					for (int j = 0 ; j < props.getLength() ; j++) {
						if (props.item(j).getNodeType() == Node.ELEMENT_NODE) {
							Element pel = (Element) props.item(j);
							String propName = pel.getAttribute("name");
							if (propName != null) {
///////////// TODO for now we assume we ONLY have a sub element for <write> perm here so we skip a step
								NodeList proles = pel.getElementsByTagName("role");
								boolean allowed = false;
								for (int k = 0 ; k < proles.getLength() ; k++) {
									if (roles.contains(proles.item(k).getTextContent())) {
										allowed = true;
										k = proles.getLength() + 1;
									}
								}
								if (!allowed) perm.put(propName, "deny");
							}
						}
					}
				}
			}
		}

System.out.println(perm);
    myShepherd.rollbackDBTransaction();
    myShepherd.closeDBTransaction();
		return perm;
	}

}


