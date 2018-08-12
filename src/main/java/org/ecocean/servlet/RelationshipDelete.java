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


public class RelationshipDelete extends HttpServlet {

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
    String context="context0";
    context=ServletUtilities.getContext(request);

    if ((request.getParameter("persistenceID")!=null)&&(!request.getParameter("persistenceID").equals(""))) {
      
    
        Shepherd myShepherd = new Shepherd(context);
        myShepherd.setAction("RelationshipDelete.class");
        
        Relationship rel=new Relationship();
        
        myShepherd.beginDBTransaction();
      
          Object identity = myShepherd.getPM().newObjectIdInstance(org.ecocean.social.Relationship.class, request.getParameter("persistenceID"));           
          rel=(Relationship)myShepherd.getPM().getObjectById(identity);
   
          if(rel!=null){
            myShepherd.getPM().deletePersistent(rel);
            myShepherd.commitDBTransaction();  
            myShepherd.beginDBTransaction();  
            
            if(rel.getRelatedSocialUnitName()!=null){
              
              //delete the community too if it has no relationships
              if(myShepherd.getAllRelationshipsForCommunity(rel.getRelatedSocialUnitName()).size()==0){
                SocialUnit myComm=myShepherd.getCommunity(rel.getRelatedSocialUnitName());
                myShepherd.getPM().deletePersistent(myComm);
                myShepherd.commitDBTransaction();  
                myShepherd.beginDBTransaction(); 
              }
              
            }
            
          }


        myShepherd.commitDBTransaction();    
        myShepherd.closeDBTransaction();
        myShepherd=null;
       

            //output success statement
            out.println(ServletUtilities.getHeader(request));
             out.println("<strong>Success:</strong> The relationship of type " + request.getParameter("type") + " between " + request.getParameter("markedIndividualName1")+" and "+request.getParameter("markedIndividualName2")+" was deleted.");
          
            out.println("<p><a href=\""+request.getScheme()+"://" + CommonConfiguration.getURLLocation(request) + "/individuals.jsp?number="+request.getParameter("markedIndividualName1")+ "\">Return to Marked Individual "+request.getParameter("markedIndividualName1")+ "</a></p>\n");
            out.println("<p><a href=\""+request.getScheme()+"://" + CommonConfiguration.getURLLocation(request) + "/individuals.jsp?number="+request.getParameter("markedIndividualName2")+ "\">Return to Marked Individual "+request.getParameter("markedIndividualName2")+ "</a></p>\n");
            out.println(ServletUtilities.getFooter(context));

}
else{
  out.println(ServletUtilities.getHeader(request));
  out.println("<strong>Failure:</strong> I did not have all of the information required.");

 out.println("<p><a href=\""+request.getScheme()+"://" + CommonConfiguration.getURLLocation(request) + "/individuals.jsp?number="+request.getParameter("markedIndividualName1")+ "\">Return to Marked Individual "+request.getParameter("markedIndividualName1")+ "</a></p>\n");
 out.println("<p><a href=\""+request.getScheme()+"://" + CommonConfiguration.getURLLocation(request) + "/individuals.jsp?number="+request.getParameter("markedIndividualName2")+ "\">Return to Marked Individual "+request.getParameter("markedIndividualName2")+ "</a></p>\n");
 out.println(ServletUtilities.getFooter(context));
  
  
}


    out.close();
    
  }
}


