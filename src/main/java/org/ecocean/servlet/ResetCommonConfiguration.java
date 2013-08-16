/*
 * The Shepherd Project - A Mark-Recapture Framework
 * Copyright (C) 2013 Jason Holmberg
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

import javax.servlet.ServletConfig;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import java.io.IOException;
import java.io.PrintWriter;


public class ResetCommonConfiguration extends HttpServlet {

  public void init(ServletConfig config) throws ServletException {
    super.init(config);
  }


  public void doGet(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
    doPost(request, response);
  }





  public void doPost(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
    //set up for response
    response.setContentType("text/html");
    PrintWriter out = response.getWriter();
    boolean success=CommonConfiguration.reloadProps();

    out.println(ServletUtilities.getHeader(request));
      if(success){
        
        out.println("<strong>Success:</strong> Reloading the properties from commonConfiguration.properties succeeded.");

        
      }
      else{
        
        out.println("<strong>Error:</strong> Reloading the properties from commonConfiguration.properties failed. Check the server log for details.");

      }
      out.println("<p><a href=\"http://" + CommonConfiguration.getURLLocation(request) + "/appadmin/admin.jsp" + "\">Return to Administration" + "</a></p>\n");
      
      out.println(ServletUtilities.getFooter());



    out.close();
    
  }
}


