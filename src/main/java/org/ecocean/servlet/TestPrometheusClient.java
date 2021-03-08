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

import com.oreilly.servlet.multipart.FilePart;
import com.oreilly.servlet.multipart.MultipartParser;
import com.oreilly.servlet.multipart.ParamPart;
import com.oreilly.servlet.multipart.Part;

import org.ecocean.CommonConfiguration;
import org.ecocean.Encounter;
import org.ecocean.Shepherd;

import javax.servlet.ServletConfig;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.PrintWriter;

import javax.xml.bind.DatatypeConverter;


import io.prometheus.client.Counter;

/**
 *
 * This servlet allows the user to upload an extracted, processed patterning file that corresponds to
 * a previously uploaded set of spots. This file is then used for visualization of the extracted pattern
 * and visualizations of potentially matched spots.
 * @author jholmber
 *
 */
public class TestPrometheusClient extends HttpServlet {


	//create counter with name and description  
  boolean pageVisited = false; 	
  Counter encs=null;
  Gauge numUsersInWildbook = null; 


  public void init(ServletConfig config) throws ServletException {
    super.init(config);
    encs = Counter.build()
            .name("number_encounters").help("Number encounters").register();
  }


  public void doGet(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
    doPost(request, response);
  }


  public void doPost(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
    
    
    //database connection setup
    String context="context0";
    context=ServletUtilities.getContext(request);
    Shepherd myShepherd = new Shepherd(context);
    myShepherd.setAction("TestPrometheusSevlet.class");



    //set up for response
    response.setContentType("text/html");
    PrintWriter out = response.getWriter();

    //begin db connection
      myShepherd.beginDBTransaction();
      try 
      { 
       //put the data into the database as a double
        if(!pageVisited)
        {
          this.setNumberOfUsers(myShepherd);
          this.setNumberOfEncounters(myShepherd);
          pageVisited = true; 
        }	
      } 
      catch (Exception lEx) {
    	
    	//gracefully catch any errors  
        lEx.printStackTrace();
        out.println(ServletUtilities.getHeader(request));
        out.println("<strong>Error:</strong> I was unable to upload your file.");
        out.println(ServletUtilities.getFooter(context));
        response.setStatus(HttpServletResponse.SC_BAD_REQUEST);

      }
      finally {
    	  
    	//always close DB connections  
        myShepherd.rollbackDBTransaction();
        myShepherd.closeDBTransaction();
      }

    out.close();
  }

  public void setNumberOfUsers(Sheperd ms)
  {
    int numUsers = ms.getNumUsers();
    this.updateGauge(this.numUsersInWildbook, (double)value);
    out.println("<p> Number of users is: "+this.numUsersInWildbook.get()+"</p>");
  }

  public void setNumberOfEncounters(Sheperd ms)
  {
    //get the data from the database
    /*Number of encounters */
    int numEncounters=ms.getNumEncounters(); //in aggregate
    this.encs.inc((double)numEncounters);
    out.println("<p> Number of encounters is: "+this.encs.get()+"</p>");

  }

  //method used to update the gauge.
  public void updateGauge(Gauge myGauge, double value)
  {
    myGauge.set(value);
  }

}


