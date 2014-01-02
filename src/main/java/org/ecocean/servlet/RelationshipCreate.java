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


    if ((request.getParameter("markedIndividualName1") != null) && (request.getParameter("markedIndividualName2") != null) && (request.getParameter("type") != null)) {
      

        Shepherd myShepherd = new Shepherd();
        
        Relationship rel=new Relationship();
      
        myShepherd.beginDBTransaction();
      

        if((myShepherd.isMarkedIndividual(request.getParameter("markedIndividualName1")))&&(myShepherd.isMarkedIndividual(request.getParameter("markedIndividualName2")))){
          
          rel=new Relationship(request.getParameter("type"), request.getParameter("markedIndividualName1"), request.getParameter("markedIndividualName2"));
            
          if(request.getParameter("markedIndividualRole1")!=null){
            rel.setMarkedIndividualRole1(request.getParameter("markedIndividualRole1"));
          }
          if(request.getParameter("markedIndividualRole2")!=null){
            rel.setMarkedIndividualRole2(request.getParameter("markedIndividualRole2"));
          }
          if(request.getParameter("relatedCommunityName")!=null){
            rel.setRelatedCommunityName(request.getParameter("relatedCommunityName"));
          }
          

          myShepherd.getPM().makePersistent(rel);
          createThisRelationship=true;
        }
        
        
        myShepherd.commitDBTransaction();    
        myShepherd.closeDBTransaction();
        myShepherd=null;
       

            //output success statement
            out.println(ServletUtilities.getHeader(request));
            if(createThisRelationship){
              out.println("<strong>Success:</strong> A relationship of type " + request.getParameter("type") + " was created between " + request.getParameter("markedIndividualName1")+" and "+request.getParameter("markedIndividualName1")+".");
            }
            else{
              out.println("<strong>Failure:</strong>  I could not create the relationship. Have your administrator check the log files for you to understand the problem.");
              
            }
            out.println("<p><a href=\"http://" + CommonConfiguration.getURLLocation(request) + "/individuals.jsp?number="+request.getParameter("markedIndividualName1")+ "\">Return to Marked Individual "+request.getParameter("markedIndividualName1")+ "</a></p>\n");
            out.println("<p><a href=\"http://" + CommonConfiguration.getURLLocation(request) + "/individuals.jsp?number="+request.getParameter("markedIndividualName2")+ "\">Return to Marked Individual "+request.getParameter("markedIndividualName2")+ "</a></p>\n");
            
            out.println(ServletUtilities.getFooter());
            
 
      
      
}


    out.close();
    
  }
}


