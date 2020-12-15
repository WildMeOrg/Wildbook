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
        try {
      
          //rel=((Relationship) (myShepherd.getPM().getObjectById(myShepherd.getPM().newObjectIdInstance(Relationship.class, request.getParameter("persistenceID")), true)));
     
          rel=(Relationship) myShepherd.getPM().getObjectById(Relationship.class,request.getParameter("persistenceID"));
          
          
          if(rel!=null){
              myShepherd.getPM().deletePersistent(rel);
              myShepherd.updateDBTransaction();  
              out.println("<strong>Success:</strong> The relationship of type " + request.getParameter("type") + " between " + request.getParameter("markedIndividualName1")+" and "+request.getParameter("markedIndividualName2")+" was deleted.");

              
          }
        }
        catch(Exception e) {
          out.println("<strong>Failure:</strong> I did not have all of the information required.");
          e.printStackTrace();
        }
        finally {
          myShepherd.rollbackAndClose();
          out.close();
          return;
        }
       
    }
    else {
      out.println("<strong>Failure:</strong> I did not have all of the information required.");
      out.close();
    }

  
    
  }
}


