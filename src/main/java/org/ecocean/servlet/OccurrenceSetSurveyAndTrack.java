/*
 * The Shepherd Project - A Mark-Recapture Framework
 * Copyright (C) 2017 Jason Holmberg
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
import org.ecocean.Occurrence;
import org.ecocean.Shepherd;
import org.ecocean.Survey;
import org.ecocean.media.MediaAsset;
import org.ecocean.movement.SurveyTrack;

import javax.servlet.ServletConfig;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import java.io.IOException;
import java.io.PrintWriter;
import java.util.ArrayList;

public class OccurrenceSetSurveyAndTrack extends HttpServlet {

  /**
   * 
   */
  private static final long serialVersionUID = 1L;

  public void init(ServletConfig config) throws ServletException {
    super.init(config);
  }

  public void doGet(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
    doPost(request, response);
  }

  public void doPost(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
    String context=ServletUtilities.getContext(request);
    Shepherd myShepherd = new Shepherd(context);
    myShepherd.setAction("OccurrenceSetSurveyAndTrack.class");
    response.setContentType("text/html");
    PrintWriter out = response.getWriter();
    boolean locked = false;
    String occID = null;
    String surveyID = null;
    String surveyTrackID = null;
    
    try {
      if (request.getParameter("occID")!=null) {
        occID = request.getParameter("occID");        
      }
      if (request.getParameter("surveyID")!=null) {
        surveyID = request.getParameter("surveyID"); 
      }
      if (request.getParameter("surveyTrackID")!=null) {
        surveyTrackID = request.getParameter("surveyTrackID"); 
      }
    } catch (Exception e) {
      e.printStackTrace();
      System.out.println("Error grabbing parameters for change in Survey or ID for this Occurrence.");
    }
    
    System.out.println("Hit survey association servlet! OccID: "+occID+" surveyID: "+surveyID+" surveyTrackID: "+surveyTrackID);
    
    myShepherd.beginDBTransaction();
    if ((occID!=null&&myShepherd.isOccurrence(occID))&&((surveyID!=null&&myShepherd.isSurvey(surveyID))||surveyTrackID!=null&&myShepherd.isSurveyTrack(surveyTrackID))) {
      Occurrence thisOcc = myShepherd.getOccurrence(occID);
      Survey sv = null;
      if (surveyID!=null&&myShepherd.isSurvey(surveyID)) {
        sv = myShepherd.getSurvey(surveyID);        
      }
       
      if (surveyTrackID!=null&&myShepherd.isSurveyTrack(surveyTrackID)) {
        try {
          SurveyTrack st = myShepherd.getSurveyTrack(surveyTrackID);
          ArrayList<Occurrence> occs = st.getAllOccurrences();
          if (!occs.contains(thisOcc)) {
            st.addOccurrence(thisOcc, myShepherd);    
            thisOcc.setCorrespondingSurveyTrackID(surveyTrackID);
          }
        } catch (Exception e) {
          e.printStackTrace();
        }
      }
      if (sv!=null) {
        try {
          thisOcc.setCorrespondingSurveyID(surveyID);
        } catch (Exception le) {
          locked = true;
          myShepherd.rollbackDBTransaction();
          myShepherd.closeDBTransaction();
          System.out.println("Failed to add survey to occurrence.");
        }        
        try {
          if (thisOcc.getEncounters()!=null) {
            ArrayList<Encounter> encs = thisOcc.getEncounters();
            for (Encounter enc : encs) {
              if (enc.getMedia()!=null) {
                ArrayList<MediaAsset> assets = enc.getMedia();
                for (MediaAsset asset : assets) {
                  asset.setCorrespondingSurveyID(surveyID);
                  asset.setCorrespondingSurveyTrackID(surveyTrackID);
                }
              }
            }            
          }
        } catch (NullPointerException npe) {
          npe.printStackTrace();
        }
      }
      if (!locked) {
        myShepherd.commitDBTransaction();
        myShepherd.closeDBTransaction();
        out.println("The Survey/Track ID's for occurrence are now Survey: " + surveyID + " and Track: "+ surveyTrackID);
        response.setStatus(HttpServletResponse.SC_OK);
      } 
    } else {
      myShepherd.rollbackDBTransaction();
      out.println("<strong>Error:</strong> The survey specified does not exist.");
      System.out.println("Occ ID : "+occID+" SurveyID : "+surveyID+" SurveyTrackID : "+surveyTrackID);
      response.setStatus(HttpServletResponse.SC_BAD_REQUEST);
    }
    out.close();
    myShepherd.closeDBTransaction();
  }
}
  