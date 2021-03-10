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
  Shepherd myShepherd; 
  boolean pageVisited = false; 	
  Counter encs=null;
  Gauge numUsersInWildbook = null; 
  Gauge numUsersWithLogin = null;


  public void init(ServletConfig config) throws ServletException {
    super.init(config);
    encs = Counter.build()
            .name("number_encounters").help("Number encounters").register();
    numUsersInWildbook = Gauge.build().name("number_users").help("Number users").register();
    numUsersWithLogin = Gauge.build().name("number_users_w_login").help("Number users with Login").register();
  }


  public void doGet(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
    doPost(request, response);
  }


  public void doPost(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
    
    
    //database connection setup
    String context="context0";
    context=ServletUtilities.getContext(request);
    this.myShepherd = new Shepherd(context);
    this.myShepherd.setAction("TestPrometheusSevlet.class");



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
          this.setNumberOfUsers(out);
          this.setNumberOfEncounters(out);
          pageVisited = true; 
        }	
        this.printMetrics(out);
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
        this.myShepherd.rollbackDBTransaction();
        this.myShepherd.closeDBTransaction();
      }

    out.close();
  }

  public void setNumberOfUsers(PrintWriter out)
  {
    //Getting number of users by wildbook
    int numUsers = this.myShepherd.getNumUsers();
    this.numUsersInWildbook.set((double)numUsers);
    //out.println("<p> Number of users is: "+this.numUsersInWildbook.get()+"</p>");

    //get number of users w/ login privileges
   // int numUsersUsername = this.myShepherd.getWithUsername();
   // int numUsersEmail = this.myShepherd.getUsersWithEmailAddresses();
    //this.numUsersWithLogin.set((double)numUsersUsername);
    //out.println("<p> Number of users is: "+this.numUsersWithLogin.get()+"</p>");
  }

  public void setNumberOfEncounters(PrintWriter out)
  {
    //get the data from the database
    /*Number of encounters */
    int numEncounters=this.myShepherd.getNumEncounters(); //in aggregate
    this.encs.inc((double)numEncounters);
    //out.println("<p> Number of encounters is: "+this.encs.get()+"</p>");

  }

  public void printMetrics(PrintWriter out)
  {
    out.println("<p> Number of users is: "+this.numUsersInWildbook.get()+"</p>"); 
   
    out.println("<p> Number of encounters is: "+this.encs.get()+"</p>");
  }

}


