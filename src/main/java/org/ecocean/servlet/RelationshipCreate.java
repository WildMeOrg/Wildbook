/*
 * The Shepherd Project - A Mark-Recapture Framework
 * Copyright (C) 2014 Jason Holmberg
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
import org.ecocean.social.*;

import com.oreilly.servlet.multipart.FilePart;
import com.oreilly.servlet.multipart.MultipartParser;
import com.oreilly.servlet.multipart.ParamPart;
import com.oreilly.servlet.multipart.Part;

import javax.servlet.ServletConfig;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import java.io.File;
import java.io.IOException;
import java.io.PrintWriter;
import java.util.ArrayList;

import org.joda.time.DateTime;


public class RelationshipCreate extends HttpServlet {

  public void init(ServletConfig config) throws ServletException {
    super.init(config);
  }


  public void doGet(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
    doPost(request, response);
  }


  public void doPost(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
    

    //set up for response
    response.setContentType("text/html");
    PrintWriter out = response.getWriter();
    boolean createThisRelationship = false;

    System.out.println("RelationshipCreate: "+request.getQueryString());

    if ((request.getParameter("markedIndividualName1") != null) && (request.getParameter("markedIndividualName2") != null) && (request.getParameter("type") != null)) {
      

      //boolean isEdit=false;
      String context="context0";
      context=ServletUtilities.getContext(request);
        Shepherd myShepherd = new Shepherd(context);
        myShepherd.setAction("RelationshipCreate.class");
        
        Relationship rel=new Relationship();
        SocialUnit comm=new SocialUnit();
      
        myShepherd.beginDBTransaction();

       try{
          if((myShepherd.isMarkedIndividual(request.getParameter("markedIndividualName1")))&&(myShepherd.isMarkedIndividual(request.getParameter("markedIndividualName2")))){
            
            
            //if(myShepherd.isRelationship(request.getParameter("type"), request.getParameter("markedIndividualName1"), request.getParameter("markedIndividualName2"), request.getParameter("markedIndividualRole1"), request.getParameter("markedIndividualRole2"), false)){
            //  rel=myShepherd.getRelationship(request.getParameter("type"), request.getParameter("markedIndividualName1"), request.getParameter("markedIndividualName2"), request.getParameter("markedIndividualRole1"), request.getParameter("markedIndividualRole2"));
           // }
            
            if((request.getParameter("persistenceID")!=null)&&(!request.getParameter("persistenceID").equals(""))){  
              Object identity = myShepherd.getPM().newObjectIdInstance(org.ecocean.social.Relationship.class, request.getParameter("persistenceID"));           
              rel=(Relationship)myShepherd.getPM().getObjectById(identity);
            }
            else{
              rel=new Relationship(request.getParameter("type"), request.getParameter("markedIndividualName1"), request.getParameter("markedIndividualName2"));
              myShepherd.getPM().makePersistent(rel); 
              myShepherd.commitDBTransaction();
              myShepherd.beginDBTransaction();
              
            }
            
            if(request.getParameter("type")!=null){
              rel.setType(request.getParameter("type"));
            }
            
            if(request.getParameter("markedIndividualRole1")!=null){
              rel.setMarkedIndividualRole1(request.getParameter("markedIndividualRole1"));
            }
            else{
              rel.setMarkedIndividualRole1(null);
            }
            
            if(request.getParameter("markedIndividualRole2")!=null){
              rel.setMarkedIndividualRole2(request.getParameter("markedIndividualRole2"));
            }
            else{
              rel.setMarkedIndividualRole2(null);
            }
            
            if(request.getParameter("markedIndividualName1")!=null){
              rel.setMarkedIndividualName1(request.getParameter("markedIndividualName1"));
            }
            
            if(request.getParameter("markedIndividualName2")!=null){
              rel.setMarkedIndividualName2(request.getParameter("markedIndividualName2"));
            }
            
            if((request.getParameter("relatedCommunityName")!=null)&&(!request.getParameter("relatedCommunityName").trim().equals(""))){
              rel.setRelatedSocialUnitName(ServletUtilities.cleanFileName(request.getParameter("relatedCommunityName")));
            }
            else{rel.setRelatedSocialUnitName(null);}
            
            //start and end time setting
            if(request.getParameter("startTime")!=null){
              try{
                String startTime=request.getParameter("startTime");
                if(!startTime.trim().equals("")){
                  DateTime dt=new DateTime(startTime);
                  rel.setStartTime(dt.getMillis());
                }
                else{rel.setStartTime(-1);}
              }
              catch(Exception e){}
            }
            else{rel.setStartTime(-1);}
            
            
            if(request.getParameter("endTime")!=null){
              try{
                String endTime=request.getParameter("endTime");
                if(!endTime.trim().equals("")){
                  DateTime dt=new DateTime(endTime);
                  rel.setEndTime(dt.getMillis());
                }
                else{rel.setEndTime(-1);}
              }
              catch(Exception e){}
            }
            else{rel.setEndTime(-1);}
            
            //relationship descriptors setting
            if(request.getParameter("markedIndividual1DirectionalDescriptor")!=null){
              rel.setMarkedIndividual1DirectionalDescriptor(request.getParameter("markedIndividual1DirectionalDescriptor"));
            }
            else{
              rel.setMarkedIndividual1DirectionalDescriptor(null);
            }
            
            if(request.getParameter("markedIndividual2DirectionalDescriptor")!=null){
              rel.setMarkedIndividual2DirectionalDescriptor(request.getParameter("markedIndividual2DirectionalDescriptor"));
            }
            else{
              rel.setMarkedIndividual2DirectionalDescriptor(null);
            }
            
            //bidirectional boolean descriptor setting
            if(request.getParameter("bidirectional")!=null){
            
              String bi=request.getParameter("bidirectional");
              if(request.getParameter("bidirectional").toLowerCase().trim().equals("true")){
                rel.setBidirectional(true);
              }
              else if(request.getParameter("bidirectional").toLowerCase().trim().equals("false")){
                rel.setBidirectional(false);
              }
              else{rel.setBidirectional(null);}
            
            }
            else{rel.setBidirectional(null);}
            myShepherd.commitDBTransaction();   
            
            createThisRelationship=true;
            
            //check the community and create it if not present
            if((request.getParameter("relatedCommunityName")!=null)&&(!request.getParameter("relatedCommunityName").trim().equals(""))&&(!myShepherd.isCommunity(request.getParameter("relatedCommunityName")))){
              comm.setSocialUnitName(request.getParameter("relatedCommunityName"));
              myShepherd.beginDBTransaction();
              myShepherd.getPM().makePersistent(comm);
              myShepherd.commitDBTransaction();   
            }
  
          }
        }
        catch(Exception e){
          myShepherd.rollbackDBTransaction();
          e.printStackTrace();
        }
       finally{
        myShepherd.closeDBTransaction();
        myShepherd=null;
       }

            //output success statement
            out.println(ServletUtilities.getHeader(request));
            if(createThisRelationship){
              out.println("<strong>Success:</strong> A relationship of type " + request.getParameter("type") + " was created between " + request.getParameter("markedIndividualName1")+" and "+request.getParameter("markedIndividualName2")+".");
            }
            else{
              out.println("<strong>Failure:</strong>  I could not create the relationship. Have your administrator check the log files for you to understand the problem.");
              
            }
            out.println("<p><a href=\""+request.getScheme()+"://" + CommonConfiguration.getURLLocation(request) + "/individuals.jsp?number="+request.getParameter("markedIndividualName1")+ "\">Return to Marked Individual "+request.getParameter("markedIndividualName1")+ "</a></p>\n");
            out.println("<p><a href=\""+request.getScheme()+"://" + CommonConfiguration.getURLLocation(request) + "/individuals.jsp?number="+request.getParameter("markedIndividualName2")+ "\">Return to Marked Individual "+request.getParameter("markedIndividualName2")+ "</a></p>\n");
            
            out.println(ServletUtilities.getFooter(context));
            
 
      
      
}


    out.close();
    
  }
}


