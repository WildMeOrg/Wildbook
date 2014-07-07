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

/* this creates a javascript output that contains a bunch of useful data for javascript given this context/language */
package org.ecocean.servlet;

import org.ecocean.*;

import javax.servlet.ServletConfig;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import java.io.*;
import java.util.*;

import com.google.gson.Gson;

public class JavascriptGlobals extends HttpServlet {


  public void init(ServletConfig config) throws ServletException {
    super.init(config);
  }


  public void doGet(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
    doPost(request, response);
  }

  public void doPost(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
    String context="context0";
    context = ServletUtilities.getContext(request);
    Shepherd myShepherd = new Shepherd(context);
		String username = ((request.getUserPrincipal() == null) ? "" : request.getUserPrincipal().getName());

		String langCode = ServletUtilities.getLanguageCode(request);
		//Properties props = new Properties();
		//props = ShepherdProperties.getProperties("collaboration.properties", langCode, context);

		HashMap rtn = new HashMap();

		rtn.put("context", context);
		rtn.put("username", username);
		rtn.put("langCode", langCode);
		rtn.put("baseUrl", request.getContextPath());

		HashMap props = new HashMap();
		HashMap lang = new HashMap();

		lang.put("collaboration", ShepherdProperties.getProperties("collaboration.properties", langCode, context));

		props.put("lang", lang);
		rtn.put("properties", props);

    response.setContentType("text/javascript");
    response.setCharacterEncoding("UTF-8");
    String js = "//JavascriptGlobals\nvar wildbookGlobals = " + new Gson().toJson(rtn) + "\n\n";
    PrintWriter out = response.getWriter();
		out.println(js);
    out.close();
  }


//wildbookGlobals.properties.lang.collaboration.invitePromptOne

}
  
  
