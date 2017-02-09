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

import org.ecocean.grid.GridManager;
import org.ecocean.grid.GridManagerFactory;

import javax.servlet.ServletConfig;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.io.PrintWriter;


public class GridHeartbeatReceiver extends HttpServlet {


  public void init(ServletConfig config) throws ServletException {
    super.init(config);
  }


  public void doGet(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
    doPost(request, response);
  }


  public void doPost(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {

    PrintWriter out = null;
    String statusText = "received";

    //get the gridManager and populate needed values
    GridManager gm = GridManagerFactory.getGridManager();
    String supportedAppletVersion = gm.getSupportedAppletVersion();

    if ((request.getParameter("version") != null) && (request.getParameter("version").equals(supportedAppletVersion)) && (request.getParameter("nodeIdentifier") != null)) {
      gm.processHeartbeat(request);
    } else {
      statusText = "rejected";
    }


    try {
      //setup the heartbeat response
      response.setContentType("text/plain");
      out = response.getWriter();
      out.println(statusText);
      
    } 
    catch (Exception e) {
      e.printStackTrace();
    }
    finally{
      if(out!=null)out.close();

    }


  }


}
	
	
