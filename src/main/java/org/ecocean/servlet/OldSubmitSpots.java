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
import org.ecocean.Encounter;
import org.ecocean.Shepherd;
import org.ecocean.SuperSpot;

import javax.servlet.ServletConfig;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import java.io.File;
import java.io.IOException;
import java.io.PrintWriter;
import java.util.ArrayList;

//import java.util.Vector;


//adds spots to a new encounter
public class OldSubmitSpots extends HttpServlet {


  private void deleteOldScans(String side, String num) {
    try {
      //File file=new File((new File(".")).getCanonicalPath()+File.separator+"webapps"+File.separator+"ROOT"+File.separator+"encounters"+File.separator+num+File.separator+"lastFull"+side+"Scan.xml");
      File file = new File(getServletContext().getRealPath(("/encounters/" + num + "/lastFull" + side + "Scan.xml")));

      if (file.exists()) {
        file.delete();
      }
      //file=new File((new File(".")).getCanonicalPath()+File.separator+"webapps"+File.separator+"ROOT"+File.separator+"encounters"+File.separator+num+File.separator+"lastFull"+side+"I3SScan.xml");
      file = new File(getServletContext().getRealPath(("/encounters/" + num + "/lastFull" + side + "I3SScan.xml")));
      if (file.exists()) {
        file.delete();
      }
      file = new File(getServletContext().getRealPath(("/encounters/" + num + "/lastBoost" + side + "Scan.xml")));
      if (file.exists()) {
        file.delete();
      }

    } catch (Exception e) {
      e.printStackTrace();
    }
  }


  public void init(ServletConfig config) throws ServletException {
    super.init(config);
  }


  public void doPost(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
    doGet(request, response);
  }


  public void doGet(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
    response.setContentType("text/html");

    // New location to be redirected
    String site = new String("newSpotMapping.jsp?number="+request.getParameter("number"));

    response.setStatus(response.SC_MOVED_TEMPORARILY);
    response.setHeader("Location", site); 

  }

}