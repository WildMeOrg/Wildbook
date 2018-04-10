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

import org.ecocean.*;
import org.ecocean.genetics.*;

import javax.servlet.ServletConfig;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import java.io.IOException;
import java.io.PrintWriter;
import java.util.ArrayList;


//Set alternateID for this encounter/sighting
public class TissueSampleSetMicrosatelliteMarkers extends HttpServlet {

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
    myShepherd.setAction("TissueSampleSetMicrosatelliteMarkers.class");
    //set up for response
    response.setContentType("text/html");
    PrintWriter out = response.getWriter();
    boolean locked = false;
    System.out.println(request.toString());

    myShepherd.beginDBTransaction();
    if ( (request.getParameter("analysisID") != null) && (request.getParameter("sampleID") != null) && (request.getParameter("number")!=null) && (myShepherd.isTissueSample(request.getParameter("sampleID"), request.getParameter("number")))&&(myShepherd.isEncounter(request.getParameter("number")))) {
      String sampleID=request.getParameter("sampleID");
      String encounterNumber=request.getParameter("number");
      String analysisID=request.getParameter("analysisID");
      
      MicrosatelliteMarkersAnalysis msMarkers = new MicrosatelliteMarkersAnalysis(analysisID, sampleID, encounterNumber);

      //let's read in the microsatellite markers
      ArrayList<Locus> loci=new ArrayList<Locus>();
      int numLoci=0;
      int numPloids=2; //most covered species will be diploids
      try{
        numPloids=(new Integer(CommonConfiguration.getProperty("numPloids",context))).intValue();
      }
      catch(Exception e){System.out.println("numPloids configuration value did not resolve to an integer.");e.printStackTrace();}
      
      boolean hasMoreLoci=true;
      while(hasMoreLoci){
        System.out.println(request.toString());
        if((request.getParameter("locusName"+numLoci)!=null) && (!request.getParameter("locusName"+numLoci).trim().equals("")) && (request.getParameter(("allele"+numLoci+"0"))!=null)&&(!request.getParameter(("allele"+numLoci+"0")).trim().equals(""))){
          ArrayList<Integer> alleles=new ArrayList<Integer>(numPloids);
          //so at this point we know there is some allele data
          try{
            for(int q=0;q<numPloids;q++){
              if((request.getParameter("locusName"+numLoci)!=null)&&(!request.getParameter("locusName"+numLoci).trim().equals(""))&&(request.getParameter(("allele"+numLoci+q))!=null)&&(!request.getParameter(("allele"+numLoci+q)).equals(""))){
                alleles.add(new Integer(request.getParameter(("allele"+numLoci+q))));
                System.out.println(alleles.toString());
              }
            }
            Locus l=new Locus(request.getParameter("locusName"+numLoci),alleles);
            loci.add(l);
          }
          catch(Exception e){e.printStackTrace();}
          numLoci++;
          
        }
        else{hasMoreLoci=false;}
        
      }
      System.out.println("Detected loci are: "+loci.toString());
      
      try {
        Encounter enc=myShepherd.getEncounter(encounterNumber);
        TissueSample sample = myShepherd.getTissueSample(sampleID, encounterNumber);
        
        //TBD
        if(myShepherd.isGeneticAnalysis(sampleID, encounterNumber, analysisID, "MicrosatelliteMarkers")){
          msMarkers=myShepherd.getMicrosatelliteMarkersAnalysis(sampleID, encounterNumber, analysisID);
          
          //now set the new values
          msMarkers.setLoci(loci);
          
        }
        else{
          
          msMarkers=new MicrosatelliteMarkersAnalysis(analysisID, sampleID, encounterNumber, loci);
          
        }
        
        if(request.getParameter("processingLabTaskID")!=null){msMarkers.setProcessingLabTaskID(request.getParameter("processingLabTaskID"));}
        if(request.getParameter("processingLabName")!=null){msMarkers.setProcessingLabName(request.getParameter("processingLabName"));}
        if(request.getParameter("processingLabContactName")!=null){msMarkers.setProcessingLabContactName(request.getParameter("processingLabContactName"));}
        if(request.getParameter("processingLabContactDetails")!=null){msMarkers.setProcessingLabContactDetails(request.getParameter("processingLabContactDetails"));}

        sample.addGeneticAnalysis(msMarkers);

        enc.addComments("<p><em>" + request.getRemoteUser() + " on " + (new java.util.Date()).toString() + "</em><br />" + "Added or updated microsatellite markers of analysis "+request.getParameter("analysisID")+" for tissue sample "+request.getParameter("sampleID")+".<br />"+msMarkers.getHTMLString());
        

      } 
      catch (Exception le) {
        locked = true;
        myShepherd.rollbackDBTransaction();
        le.printStackTrace();
      }

      if (!locked) {
        myShepherd.commitDBTransaction();
        //myShepherd.closeDBTransaction();
        out.println(ServletUtilities.getHeader(request));
        out.println("<strong>Success!</strong> I have successfully set the microsatellite markers for tissue sample " + request.getParameter("sampleID") + " for encounter "+encounterNumber+".</p>");

        out.println("<p><a href=\""+request.getScheme()+"://" + CommonConfiguration.getURLLocation(request) + "/encounters/encounter.jsp?number=" + encounterNumber + "\">Return to encounter " + encounterNumber + "</a></p>\n");
        out.println(ServletUtilities.getFooter(context));
        } 
      else {

        out.println(ServletUtilities.getHeader(request));
        out.println("<strong>Failure!</strong> This encounter is currently being modified by another user or is inaccessible. Please wait a few seconds before trying to modify this encounter again.");

        out.println("<p><a href=\""+request.getScheme()+"://" + CommonConfiguration.getURLLocation(request) + "/encounters/encounter.jsp?number=" + encounterNumber + "\">Return to encounter " + encounterNumber + "</a></p>\n");
        out.println(ServletUtilities.getFooter(context));

      }
    } 
    else {
      myShepherd.rollbackDBTransaction();
      out.println(ServletUtilities.getHeader(request));
      out.println("<strong>Error:</strong> I was unable to set the microsatellite markers. I cannot find the encounter or tissue sample that you intended it for in the database.");
      out.println(ServletUtilities.getFooter(context));

    }
    out.close();
    myShepherd.closeDBTransaction();
  }


}
  
  
