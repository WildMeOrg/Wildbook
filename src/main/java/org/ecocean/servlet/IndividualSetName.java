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

import org.ecocean.CommonConfiguration;
import org.ecocean.MarkedIndividual;
import org.ecocean.Shepherd;
import org.ecocean.Util;
import org.ecocean.MultiValue;

import javax.servlet.ServletConfig;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import java.io.IOException;
import java.io.PrintWriter;

//import javax.jdo.*;
//import com.poet.jdo.*;
//import com.oreilly.servlet.multipart.*;


//handles operations to sharks. possible operations include, create new, add encounter, remove encounter from
public class IndividualSetName extends HttpServlet {


  public void init(ServletConfig config) throws ServletException {
    super.init(config);
  }


  public void doGet(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
    doPost(request, response);
  }


  public void doPost(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
    request.setCharacterEncoding("UTF-8");
    String context="context0";
    context=ServletUtilities.getContext(request);
    Shepherd myShepherd = new Shepherd(context);
    myShepherd.setAction("IndividualSetNickname.class");
    //set up for response
    response.setContentType("text/html");
    PrintWriter out = response.getWriter();
    boolean locked = false;

    String indID = request.getParameter("individualID");

    //This servlet can create, delete, or modify a name depending on which of these params are passed in
    String newKey = request.getParameter("newKey");
    String newValue = request.getParameter("newValue");
    String oldKey = request.getParameter("oldKey");
    String oldValue = request.getParameter("oldValue");
    String deleteStr = request.getParameter("delete");
    if ("Default".equals(newKey)) newKey = MultiValue.DEFAULT_KEY_VALUE;
    if ("Default".equals(oldKey)) oldKey = MultiValue.DEFAULT_KEY_VALUE;

    // new name if we have a new value (key optional) and no old value or key
    boolean delete = (Util.stringExists(deleteStr) && Util.stringExists(oldKey));
    boolean newName = (Util.stringExists(newValue) && !Util.stringExists(oldValue) && !Util.stringExists(oldKey));
    boolean changeValueOnly = (newValue!=null && !newValue.equals(oldValue));
    boolean changeKeyOnly = (!changeValueOnly && newKey!=null && !newKey.equals(oldKey));
    boolean noChange = (Util.stringExists(newValue) && newValue.equals(oldValue) && Util.stringExists(newKey) && newKey.equals(oldKey));

    //this is because we have decided that (via UI) "editing" the *default key* actually means replacing it (not adding another)
    if (MultiValue.DEFAULT_KEY_VALUE.equals(newKey)) {
        oldKey = MultiValue.DEFAULT_KEY_VALUE;
        newName = false;
        changeValueOnly = false;
        changeKeyOnly = true;
    }

    System.out.println("SERVLET IndividualSetName has indID="+indID+" newKey="+newKey+", newValue="+newValue+", oldKey="+oldKey+", oldValue="+oldValue+", newName="+newName+", changeValueOnly="+changeValueOnly+", changeKeyOnly="+changeKeyOnly+", delete="+delete+" nochange="+noChange);
    System.out.println("myShepherd.isMarkedIndividual(indID) = "+myShepherd.isMarkedIndividual(indID));

    myShepherd.beginDBTransaction();
    if ((myShepherd.isMarkedIndividual(indID))) {

      MarkedIndividual mark=null;
      try{
        System.out.println("about to get mark");
        mark = myShepherd.getMarkedIndividual(indID);
        System.out.println("we have mark "+mark);
      } catch (Exception e) {
        System.out.println("Exception getting mark");
        e.printStackTrace();
        return;
      }

      if (mark==null) System.out.println("Mark is null in IndividualSetName???");

      try {
        System.out.println("In the try block A");
        if (noChange) {
          out.println("<strong>No action!</strong> Added a name with label \""+newKey+"\" and value \""+newValue+"\" on Marked Individual "+indID+".</p>");
          return;
        }
        System.out.println("In the try block B. delete = "+delete);

        if (delete) {

        System.out.println("In the try block DELeTE CASE we made it!!");

          // just removing one value from this multivalue
          if (Util.stringExists(oldValue) && mark.getNames().getValuesByKey(oldKey) !=null && mark.getNames().getValuesByKey(oldKey).size()>1) {
            mark.getNames().removeValuesByKey(oldKey, oldValue);
            System.out.println("Got both oldVal and oldKey! removed the value \""+oldValue+"\" from the names labeled \""+oldKey+"\" on individual "+indID);
            out.println("<strong>Success!</strong> removed the value \""+oldValue+"\" from the names labeled \""+oldKey+"\" on individual "+indID);
          } else {
            mark.getNames().removeKey(oldKey);
            System.out.println("Only oldkey provided! removed the name labeled \""+oldKey+"\" on individual "+indID);
            out.println("<strong>Success!</strong> removed the name labeled \""+oldKey+"\" on individual "+indID);
          }
          return;
        }

        if (newName) {
          if (Util.stringExists(newKey)) {
            mark.addName(newKey, newValue);
            out.println("<strong>Success!</strong> Added a name with label \""+newKey+"\" and value \""+newValue+"\" on Marked Individual "+indID+".</p>");
          } else {
            mark.addName(newValue);
            out.println("<strong>Success!</strong> Added \""+newValue+"\" to the default names for Marked Individual "+indID+".</p>");
          }
          return;
        }
        else if (changeValueOnly) {
          mark.getNames().removeValuesByKey(newKey, oldValue);
          mark.addName(newKey, newValue);
          out.println("<strong>Success!</strong> I have successfully changed the name labeled \""+newKey+"\" from \""+oldValue+"\" to \""+newValue+"\" on Marked Individual "+indID+".</p>");
          return;
        }
        else if (changeKeyOnly) {
          mark.getNames().removeKey(oldKey);
          mark.addName(newKey, newValue);
          out.println("<strong>Success!</strong> I have successfully changed the label for name \""+newValue+"\" from \""+oldValue+"\" to \""+newValue+"\" on Marked Individual "+indID+".</p>");
          return;
        }
        else {
          response.setStatus(HttpServletResponse.SC_BAD_REQUEST);
          out.println("<strong>Failure!</strong> The IndividualSetName servlet has no behavior defined for the provided variables: newKey="+newKey+", newValue="+newValue+", oldKey="+oldKey+", oldValue="+oldValue+", newName="+newName+", changeValueOnly="+changeValueOnly+", changeKeyOnly="+changeKeyOnly+".  This is a bug, please contact an administrator with this error.");
          return;
        }

      } catch (Exception le) {
        System.out.println("Exception on IndividualSetName!!!");
        le.printStackTrace();
        locked = true;
        myShepherd.rollbackDBTransaction();
        myShepherd.closeDBTransaction();
      } finally {
        if (!locked) {
          myShepherd.commitDBTransaction();
          myShepherd.closeDBTransaction();
        } else {
          response.setStatus(HttpServletResponse.SC_BAD_REQUEST);
          out.println("<strong>Failure!</strong> This record is currently being modified by another user. Please wait a few seconds before trying again.");
        }
      }
    } 
    else {
      System.out.println("IndividualSetName could not find individual "+indID);
      myShepherd.rollbackDBTransaction();
      response.setStatus(HttpServletResponse.SC_BAD_REQUEST);
      out.println("<strong>Error:</strong> Unable to change name: unable to find Marked Individual "+indID+" in the database.");
      //out.println(ServletUtilities.getFooter(context));
    }
    out.close();
    myShepherd.closeDBTransaction();
  }


}
	
	
