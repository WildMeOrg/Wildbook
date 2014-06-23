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

import org.ecocean.*;
import org.ecocean.security.Collaboration;

import javax.servlet.ServletConfig;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import java.io.*;
import java.util.*;

import com.google.gson.Gson;

public class Collaborate extends HttpServlet {


  public void init(ServletConfig config) throws ServletException {
    super.init(config);
  }


  public void doGet(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
    doPost(request, response);
  }

  public void doPost(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
    String context = ServletUtilities.getContext(request);
    Shepherd myShepherd = new Shepherd(context);

		Properties props = new Properties();
		String langCode = ServletUtilities.getLanguageCode(request);
		props = ShepherdProperties.getProperties("collaboration.properties", langCode, context);


    String username = request.getParameter("username");
    boolean useJson = !(request.getParameter("json") == null);
System.out.println("useJson = "+useJson);

		HashMap rtn = new HashMap();
		rtn.put("success", false);

		if (request.getUserPrincipal() == null) {
			rtn.put("message", props.getProperty("inviteResponseMessageAnon"));

		} else if ((username == null) || username.equals("")) {
			rtn.put("message", props.getProperty("inviteResponseMessageNoUsername"));

		} else {
			String currentUsername = request.getUserPrincipal().getName();
			Collaboration collab = Collaboration.collaborationBetweenUsers(context, currentUsername, username);
			if (collab != null) {
				rtn.put("message", props.getProperty("inviteResponseMessageAlready"));
				System.out.println("collab already exists, state=" + collab.getState());
			} else {
				collab = Collaboration.create(currentUsername, username);
  			myShepherd.storeNewCollaboration(collab);
				rtn.put("success", true);
			}
		}

System.out.println(rtn);
    PrintWriter out = response.getWriter();
		if (useJson) {
    	response.setContentType("application/json");
    	response.setCharacterEncoding("UTF-8");
    	String json = new Gson().toJson(rtn);
System.out.println(json);
			out.println(json);

		} else {
    	response.setContentType("text/html");
			out.println(ServletUtilities.getHeader(request));
			if (Boolean.TRUE.equals(rtn.get("success"))) {
				out.println("<p class=\"collaboration-invite-success\">" + props.getProperty("inviteSuccess") + "</p>");
			} else {
				out.println("<p class=\"collaboration-invite-failure\">" + props.getProperty("inviteFailure") + "</p>");
			}
			if (rtn.get("message") != null) out.println("<p class=\"collaboration-invite-message\">" + rtn.get("message") + "</p>");
			out.println(ServletUtilities.getFooter(context));
		}
/*
        myShepherd.commitDBTransaction();
        myShepherd.closeDBTransaction();
        out.println("<strong>Success!</strong> I have successfully removed user account '" + username + "'.");
        out.println("<p><a href=\"http://" + CommonConfiguration.getURLLocation(request) + "/appadmin/users.jsp?context=context0" + "\">Return to User Administration" + "</a></p>\n");
*/

		out.close();
  }


}
  
  
