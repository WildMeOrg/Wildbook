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
import javax.servlet.ServletConfig;
import javax.servlet.ServletException;
import io.prometheus.client.exporter.MetricsServlet;


/**
 *
 * This servlet allows Wildbook metrics to be accessible via endpoint. 
 * @author jholmber, Gabe Marcial, Joanna Hoang, Sarah Schibel
 *
 */
public class WildbookMetrics extends MetricsServlet {

  private static final long serialVersionUID = 1L; //an eclipse security thing
  
  /*Initialize variables*/
  Shepherd myShepherd; 
  boolean pageVisited = false; 	
  Prometheus metricsExtractor; 
  
  public void init(ServletConfig config) throws ServletException {
    super.init(config);
    metricsExtractor = new Prometheus(); 
    this.myShepherd = new Shepherd("context0");
    this.myShepherd.setAction("TestPrometheusSevlet.class");
    this.myShepherd.beginDBTransaction();
    try 
      { 
        if(!pageVisited)
          {
            metricsExtractor.setNumberOfUsers(null, this.myShepherd);
            metricsExtractor.setNumberOfEncounters(null, this.myShepherd);
            metricsExtractor.setNumberofMediaAssets(null, this.myShepherd);
            pageVisited = true; 
          } 
      } 
      catch (Exception lEx) 
        {
        //gracefully catch any errors  
          lEx.printStackTrace();
        }
      finally 
        {
          //always close DB connections  
          this.myShepherd.rollbackDBTransaction();
          this.myShepherd.closeDBTransaction();
        }
  }
}


