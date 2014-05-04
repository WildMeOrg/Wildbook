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

import org.ecocean.Shepherd;
import org.ecocean.grid.GridManager;
import org.ecocean.grid.GridManagerFactory;
import org.ecocean.grid.GridNode;
import org.ecocean.grid.ScanWorkItemResult;

import javax.servlet.ServletConfig;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.PrintWriter;
import java.util.Vector;


public class ScanWorkItemResultsHandler extends HttpServlet {


  public void init(ServletConfig config) throws ServletException {
    super.init(config);
  }


  private static Object receiveObject(ObjectInputStream con) throws Exception {
    //System.out.println("scanresultsServlet: I am about to read in the byte array!");
    Object obj = new ScanWorkItemResult();
    try {
      obj = (Object) con.readObject();
    } catch (java.lang.NullPointerException npe) {
      System.out.println("scanResultsServlet received an empty results set...no matches whatsoever.");
      return obj;
    }
    //System.out.println("scanresultsServlet: I successfully read in the byte array!");

    return obj;

  }


  public void doGet(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
    doPost(request, response);
  }

  public void doPost(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {

    //set up a shepherd for DB transactions
    String context="context0";
    context=ServletUtilities.getContext(request);
    Shepherd myShepherd = new Shepherd(context);
    String nodeIdentifier = request.getParameter("nodeIdentifier");
    GridManager gm = GridManagerFactory.getGridManager();

    //double cutoff=2;
    String statusText = "success";
    //System.out.println("scanWorkItemResultsHandler: I am starting up.");
    response.setContentType("application/octet-stream");
    ObjectInputStream inputFromApplet = null;
    PrintWriter out = null;
    myShepherd.beginDBTransaction();
    try {

      // get an input stream and Vector of results from the applet
      inputFromApplet = new ObjectInputStream(request.getInputStream());
      Vector returnedResults = new Vector();
      returnedResults = (Vector) receiveObject(inputFromApplet);
      inputFromApplet.close();


      //send response to applet
      try {
        //setup the servlet output
        response.setContentType("text/plain");
        out = response.getWriter();
        out.println(statusText);
        out.close();
      } catch (Exception e) {
        e.printStackTrace();
      }


      int returnedSize = returnedResults.size();


      //ArrayList<String> affectedScanTasks=new ArrayList<String>();
      //String affectedTask="";
      for (int m = 0; m < returnedSize; m++) {
        ScanWorkItemResult wir = (ScanWorkItemResult) returnedResults.get(m);
        String swiUniqueNum = wir.getUniqueNumberWorkItem();

        gm.checkinResult(wir);


      }


      myShepherd.commitDBTransaction();
      myShepherd.closeDBTransaction();

      if (returnedSize > 0) {
        GridNode node = gm.getGridNode(nodeIdentifier);
        node.checkin(returnedSize);
        gm.incrementCompletedWorkItems(returnedSize);
      }


    } catch (Exception e) {
      myShepherd.rollbackDBTransaction();
      myShepherd.closeDBTransaction();
      System.out.println("scanResultsServlet registered the following error...");
      e.printStackTrace();
      //statusText="failure";
    }


  }


}