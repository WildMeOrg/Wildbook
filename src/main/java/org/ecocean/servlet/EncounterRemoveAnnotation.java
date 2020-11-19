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
import org.ecocean.ia.Task;
import org.ecocean.media.MediaAsset;
import org.json.JSONObject;

import javax.servlet.ServletConfig;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import java.io.*;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;


public class EncounterRemoveAnnotation extends HttpServlet {
  /** SLF4J logger instance for writing log entries. */
  public static Logger log = LoggerFactory.getLogger(EncounterRemoveAnnotation.class);

  public void init(ServletConfig config) throws ServletException {
    super.init(config);
  }


  public void doGet(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
    doPost(request, response);
  }


  private void setDateLastModified(Encounter enc) {
    String strOutputDateTime = ServletUtilities.getDate();
    enc.setDWCDateLastModified(strOutputDateTime);
  }


  public void doPost(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
    String context="context0";
    context=ServletUtilities.getContext(request);
    //String langCode = ServletUtilities.getLanguageCode(request);
    Shepherd myShepherd = new Shepherd(context);
    myShepherd.setAction("EncounterDeleteAnnotation.class");
    //set up for response
    response.setContentType("application/json");
    response.setCharacterEncoding("UTF-8");
    response.setHeader("Access-Control-Allow-Origin", "*");
    PrintWriter out = response.getWriter();
    boolean locked = false;

    //setup data dir
    String rootWebappPath = getServletContext().getRealPath("/");
    File webappsDir = new File(rootWebappPath).getParentFile();
    File shepherdDataDir = new File(webappsDir, CommonConfiguration.getDataDirectoryName(context));
    if(!shepherdDataDir.exists()){shepherdDataDir.mkdirs();}
    File encountersDir=new File(shepherdDataDir.getAbsolutePath()+"/encounters");
    if(!encountersDir.exists()){encountersDir.mkdirs();}
    
    //boolean isOwner = true;
    JSONObject res = new JSONObject();
    res.put("success",false);
    myShepherd.beginDBTransaction();
    try {
      if ((request.getParameter("number") != null)&&(myShepherd.isEncounter(request.getParameter("number")))) {
        Encounter enc = myShepherd.getEncounter(request.getParameter("number"));
        
  
        if(ServletUtilities.isUserAuthorizedForEncounter(enc,request) && request.getParameter("annotation") != null && myShepherd.getAnnotation(request.getParameter("annotation"))!=null) {
  
            Annotation ann=myShepherd.getAnnotation(request.getParameter("annotation"));
          
              //first do the immediate task, remove this Annot from the Encounter
              enc.removeAnnotation(ann);
              setDateLastModified(enc);
              myShepherd.updateDBTransaction();
              
              //next, check if this belongs to another Encounter. 
              //If it does NOT belong to another Encounter, remove it from related tasks
              if(Encounter.findByAnnotation(ann, myShepherd)==null) {
                List<Task> iaTasks = Task.getTasksFor(ann, myShepherd);
                if (iaTasks!=null&&!iaTasks.isEmpty()) {
                  for (Task iaTask : iaTasks) {
                    iaTask.removeObject(ann);
                    myShepherd.updateDBTransaction();
                  }
                }
                
              }
              
              //if this annot is the last annot for this MediaAsset on this Encounter
              //then revert the annot to trivial to preserve the MediaAsset's association to the Annotation
              MediaAsset asset=ann.getMediaAsset();
              if(asset!=null) {
                
                List<MediaAsset> assets=enc.getMedia();
                if(!assets.contains(asset)) {
                
                  Annotation newAnnot=ann.revertToTrivial(myShepherd);
                  newAnnot.setMatchAgainst(false);
                  enc.addAnnotation(newAnnot);
                  myShepherd.updateDBTransaction();
                }
                myShepherd.commitDBTransaction();
                
                response.setStatus(HttpServletResponse.SC_OK);
                res.put("success",true);
              }
              
        
        }
        else {
          myShepherd.rollbackDBTransaction();
      
          addErrorMessage(res, "I don't know which Annotation you are refering to.");
          response.setStatus(HttpServletResponse.SC_BAD_REQUEST);
  
        }
  
        
        
      } 
      else {
        myShepherd.rollbackDBTransaction();
       
        addErrorMessage(res, "I don't know which Encounter you are refering to, or you don't have permission to modify this Encounter.");
        response.setStatus(HttpServletResponse.SC_BAD_REQUEST);
  
      }
    }
    catch(Exception e){
      e.printStackTrace();
      myShepherd.rollbackDBTransaction();
      response.setStatus(HttpServletResponse.SC_BAD_REQUEST);
      addErrorMessage(res, e.getCause().getMessage());
    }
    finally {
      myShepherd.closeDBTransaction();
      out.println(res);
    }
    out.close();

  }
  
  private void addErrorMessage(JSONObject res, String error) {
    res.put("error", error);
}
  
  
}


