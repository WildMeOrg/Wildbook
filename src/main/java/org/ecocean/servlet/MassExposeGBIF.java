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

import javax.jdo.Extent;
import javax.jdo.Query;
import javax.servlet.ServletConfig;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import java.io.IOException;
import java.io.PrintWriter;
import java.util.Iterator;


/**
 * Exposes all approved encounters to the GBIF.
 *
 * @author jholmber
 */
public class MassExposeGBIF extends HttpServlet {


  public void init(ServletConfig config) throws ServletException {
    super.init(config);
  }


  public void doGet(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
    doPost(request, response);
  }


  public void doPost(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
    String context="context0";
    context=ServletUtilities.getContext(request);
    Shepherd myShepherd = new Shepherd(context);

    //set up for response
    response.setContentType("text/html");
    PrintWriter out = response.getWriter();
    boolean locked = false;
    boolean madeChanges = false;
    int count = 0;

    Extent encClass = myShepherd.getPM().getExtent(Encounter.class, true);
    Query query = myShepherd.getPM().newQuery(encClass);

    myShepherd.beginDBTransaction();
    try {
      Iterator<Encounter> it = myShepherd.getAllEncounters(query);

      while (it.hasNext()) {
        Encounter tempEnc = it.next();
        if (!tempEnc.getOKExposeViaTapirLink()) {
          tempEnc.setOKExposeViaTapirLink(true);
          madeChanges = true;
          count++;
        }
      } //end while
    } catch (Exception le) {
      locked = true;
      myShepherd.rollbackDBTransaction();
      myShepherd.closeDBTransaction();
    }
    query.closeAll();
    if (!madeChanges) {
      myShepherd.rollbackDBTransaction();
      myShepherd.closeDBTransaction();
    }
    //success!!!!!!!!
    else if (!locked) {
      myShepherd.commitDBTransaction();
      myShepherd.closeDBTransaction();
      out.println(ServletUtilities.getHeader(request));
      out.println(("<strong>Success!</strong> I have successfully exposed " + count + " additional encounters to the GBIF."));
      out.println("<p><a href=\""+request.getScheme()+"://" + CommonConfiguration.getURLLocation(request) + "/appadmin/admin.jsp\">Return to the Administration page.</a></p>\n");
      out.println(ServletUtilities.getFooter(context));
    }
    //failure due to exception
    else {
      out.println(ServletUtilities.getHeader(request));
      out.println("<strong>Failure!</strong> I could not change the GBIF status of unexposed encounters.");
      out.println(ServletUtilities.getFooter(context));
    }

    out.close();
  }

}


