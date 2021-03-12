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
import org.ecocean.User;
import org.ecocean.MarkedIndividual;

import javax.servlet.ServletConfig;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.PrintWriter;

import java.util.*;

import javax.xml.bind.DatatypeConverter;


import io.prometheus.client.Counter;
import io.prometheus.client.Gauge;
import io.prometheus.client.exporter.MetricsServlet;

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
  Gauge numUsersWithoutLogin = null;
  Gauge numMediaAssetsWildbook = null;
  Gauge indiv = null;
  MetricsServlet m = new MetricsServlet();
  


  public void init(ServletConfig config) throws ServletException {
    super.init(config);
    encs = Counter.build()
            .name("number_encounters").help("Number encounters").register();
    indiv = Gauge.build().name("number_individual_wildbook").help("Number individuals by Wildbook").register();
    numUsersInWildbook = Gauge.build().name("number_users").help("Number users").register();
    numUsersWithLogin = Gauge.build().name("number_users_w_login").help("Number users with Login").register();
    numUsersWithoutLogin = Gauge.build().name("number_users_wout_login").help("Number users without Login").register();
    numMediaAssetsWildbook = Gauge.build().name("number_mediaassets_wild").help("Number of Media Assets by Wildbook").register();
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
          this.setNumberofMediaAssets(out);
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

//User Metrics
  public void setNumberOfUsers(PrintWriter out)
  {
    //Getting number of users by wildbook
    int numUsers = this.myShepherd.getNumUsers();
    this.numUsersInWildbook.set((double)numUsers);

    //get number of users w/ login privileges
    List<User> numUsersUsername = this.myShepherd.getUsersWithUsername();
    int totalNumUsersUsername = numUsersUsername.size();
    this.numUsersWithLogin.set((double)totalNumUsersUsername);

    //get number of users w/out login privileges
    int totalNumUserNoLogin = (numUsers-totalNumUsersUsername);
    this.numUsersWithoutLogin.set((double)totalNumUserNoLogin);
   
  }
//Ecounter Metrics
  public void setNumberOfEncounters(PrintWriter out)
  {
    //get the data from the database
    /*Number of encounters */
    int numEncounters=this.myShepherd.getNumEncounters(); //in aggregate
    this.encs.inc((double)numEncounters);

  }

  //Individual Metrics
  public void setNumberOfIndividuals(PrintWriter out){
    //Get num of Individuals by wildbook
    int numIndividuals = this.myShepherd.getNumMarkedIndividuals();
    this.indiv.inc((double)numIndividuals);

  }

//Media Assets Metrics
  public void setNumberofMediaAssets(PrintWriter out){
    
    //Media Assets by WildBook
    Iterator<String> numMediaAssetsWild = Arrays.asList(this.myShepherd.getAllMediaAssets());
    ArrayList<String> mediaAssestsList = new ArrayList<String>();
    // while (numMediaAssetsWild.hasNext()){
    //   String string = (String)numMediaAssetsWild.next();
    //   mediaAssestsList.add(string);
    // }
    numMediaAssetsWild.forEachRemaining(mediaAssestsList::add);
    int wildbookMA = mediaAssestsList.size();
    
    this.numMediaAssetsWildbook.set((double)wildbookMA);

    //Media Assets by Specie
    // MediaAssetSet numMediaAssetsSpecie = this.myShepherd.getMediaAssetSet();
    // int numSpeciesAssets = Integer.parseInt(numMediaAssetsSpecie);
  }

  public void printMetrics(PrintWriter out)
  {
  out.println("<p>User Metrics</p>");
    out.println("<p> Number of users is: "+this.numUsersInWildbook.get()+"</p>"); 
    out.println("<p> Number of users with login is: "+this.numUsersWithLogin.get()+"</p>");     
    out.println("<p> Number of users without login is: "+this.numUsersWithoutLogin.get()+"</p>"); 
   
   out.println("<p>Encounter Metrics</p>");
    out.println("<p> Number of encounters is: "+this.encs.get()+"</p>");

  out.println("<p>Individual Metrics</p>");
    out.println("<p> Number of Individuals by Wildbook is: "+this.indiv.get()+"</p>"); 

  out.println("<p>Media Asset Metrics</p>");
    out.println("<p> Number of Media Assets by Wildbook: "+this.numMediaAssetsWildbook.get()+"</p>");
  }

}


