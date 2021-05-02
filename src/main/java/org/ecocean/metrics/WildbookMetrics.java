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

package org.ecocean.metrics;


import org.ecocean.Shepherd;
import org.ecocean.User;
import org.ecocean.metrics.junit.TestRunner;
import org.ecocean.servlet.ServletUtilities;
import org.ecocean.MarkedIndividual;

import javax.servlet.ServletConfig;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import java.io.BufferedWriter;
import java.io.IOException;
import java.io.PrintWriter;
import java.io.Writer;

import io.prometheus.client.Counter;
import io.prometheus.client.Gauge;
import io.prometheus.client.CollectorRegistry;
import io.prometheus.client.exporter.MetricsServlet;
import io.prometheus.client.exporter.common.TextFormat;
import java.util.*;

// import java.util.*;

// import javax.xml.bind.DatatypeConverter;
//import com.oreilly.servlet.multipart.FilePart;
//import com.oreilly.servlet.multipart.MultipartParser;
//import com.oreilly.servlet.multipart.ParamPart;
//import com.oreilly.servlet.multipart.Part;
//import org.ecocean.CommonConfiguration;
//import org.ecocean.Encounter;
//import javax.ws.rs.core.StreamingOutput;
//import java.io.File;
//import java.io.FileOutputStream;
//import java.io.OutputStreamWriter;
//import javax.xml.bind.DatatypeConverter;
//import io.prometheus.client.exporter.MetricsServlet;
//import com.sun.net.httpserver.HttpServer;
//import org.eclipse.jetty.server.Server;
//import org.eclipse.jetty.servlet.ServletContextHandler;
//import org.eclipse.jetty.servlet.ServletHolder;

/**
 *
 * This servlet allows the user to upload an extracted, processed patterning file that corresponds to
 * a previously uploaded set of spots. This file is then used for visualization of the extracted pattern
 * and visualizations of potentially matched spots.
 * @author jholmber
 *
 */
public class WildbookMetrics extends MetricsServlet {


	/*Initialize variables*/
  Shepherd myShepherd; 
  boolean pageVisited = false; 	
  Prometheus metricsExtractor; 
  

  public void init(ServletConfig config) throws ServletException 
  {
    super.init(config);
    this.metricsExtractor = new Prometheus(); 
    this.myShepherd = new Shepherd("context0");
    this.myShepherd.setAction("WildBookMetrics.class");
    this.myShepherd.beginDBTransaction();
    try 
      { 
        if(!this.pageVisited)
          {
            this.metricsExtractor.setNumberOfUsers(null, this.myShepherd);
            this.metricsExtractor.setNumberOfEncounters(null, this.myShepherd);
            this.metricsExtractor.setNumberofMediaAssets(null, this.myShepherd);
            this.metricsExtractor.setNumberOfIndividuals(null, this.myShepherd);
            this.pageVisited = true; 
          } 
      } 
      catch (Exception lEx) 
        {
          lEx.printStackTrace();
        }
      finally 
        {
          this.myShepherd.rollbackDBTransaction();
          this.myShepherd.closeDBTransaction();
        }
  }
  
}


