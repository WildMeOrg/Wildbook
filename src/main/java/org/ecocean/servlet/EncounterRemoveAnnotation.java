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
import org.json.JSONException;
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
    
    JSONObject args = new JSONObject();

    try {
      args = ServletUtilities.jsonFromHttpServletRequest(request);
    } catch (JSONException e){
      // urgh... depending on if POSTing from Postman or $.ajax, parameters must be handled differently.
      args.put("number", request.getParameter("number"));
      args.put("annotation", request.getParameter("annotation"));
      //leave this print in case of shenanigans even though we have alternate behavior
      e.printStackTrace();
    }

    String encID = args.optString("number");
    String annotID = args.optString("annotation");
    
    //boolean isOwner = true;
    JSONObject res = new JSONObject();
    res.put("success",false);
    myShepherd.beginDBTransaction();
    try {
      if ((encID != null)&&(myShepherd.isEncounter(encID))) {
        Encounter enc = myShepherd.getEncounter(encID);
        if(    ServletUtilities.isUserAuthorizedForEncounter(enc,request) 
            && annotID != null   
            && myShepherd.getAnnotation(annotID)!=null 
            && enc.getAnnotations()!=null
            && enc.getAnnotations().contains(myShepherd.getAnnotation(annotID))
         ) {
  
            Annotation ann=myShepherd.getAnnotation(annotID);
          
                
              //overall: don't delete trivial annotations. in that case, delete image command from menu
              
              //if not trivial but has no sibs, revert to trivial
                if(!ann.isTrivial() && (ann.getSiblings()==null||ann.getSiblings().size()==0)) {
                 
                  
                  
                  List<Task> iaTasks = Task.getTasksFor(ann, myShepherd);
                  if (iaTasks!=null&&!iaTasks.isEmpty()) {
                    for (Task iaTask : iaTasks) {
                      iaTask.removeObject(ann);
                      myShepherd.updateDBTransaction();
                    }
                  }
                  
                  Annotation newAnnot=ann.revertToTrivial(myShepherd);
                  
                  myShepherd.getPM().deletePersistent(ann);
                  myShepherd.updateDBTransaction();
                  res.put("revertToTrivial",true);
                }
                //otherwise just delete and move on
                else if(!ann.isTrivial()) {
                  
                  List<Task> iaTasks = Task.getTasksFor(ann, myShepherd);
                  if (iaTasks!=null&&!iaTasks.isEmpty()) {
                    for (Task iaTask : iaTasks) {
                      iaTask.removeObject(ann);
                      myShepherd.updateDBTransaction();
                    }
                  }
                  
                  enc.removeAnnotation(ann);
                  myShepherd.getPM().deletePersistent(ann);
                  myShepherd.commitDBTransaction();
                }
                
                response.setStatus(HttpServletResponse.SC_OK);
                res.put("success",true);

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


