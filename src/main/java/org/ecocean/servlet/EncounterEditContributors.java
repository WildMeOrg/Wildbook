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
import org.ecocean.Encounter;
import org.ecocean.Shepherd;
import org.ecocean.User;

import javax.servlet.ServletConfig;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import java.io.IOException;

import java.util.List;


public class EncounterEditContributors extends HttpServlet {
    static final long serialVersionUID = 1L;

    public void init(ServletConfig config) throws ServletException {
        super.init(config);
    }

    private void setDateLastModified(Encounter enc) {
        String strOutputDateTime = ServletUtilities.getDate();
        enc.setDWCDateLastModified(strOutputDateTime);
    }

    public void doGet(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
        doPost(request, response);
    }

    public void doPost(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
        String context="context0";
        context=ServletUtilities.getContext(request);
        Shepherd myShepherd = new Shepherd(context);
        myShepherd.setAction("EncounterEditContributors.class");
        response.setContentType("text/html");

        int numSubmitters = Integer.valueOf(request.getParameter("numSubmitters"));
        String encNum = request.getParameter("encNum");
        Encounter enc = myShepherd.getEncounter(encNum);

        List<User> submitters=enc.getSubmitters();

        for (int i=1;i<=numSubmitters;i++) {

        }

        // for (i=0;i<numSubmitters;i++) {
        //     sendObj["submitterName-"+i] = $("submitterName-"+i).val();
        //     sendObj["submitterEmail-"+i] = $("submitterEmail-"+i).val();
        //     sendObj["submitterOrganization-"+i] = $("submitterOrganization-"+i).val();
        //     sendObj["submitterProject-"+i] = $("submitterProject-"+i).val();
        //   }
        //   // Do we have a new user to make? Need at least a name...
        //   var newName =  $("submitterName-new").val();
        //   if (newName!=null&&newName.length>0) {
        //     sendObj["submitterName-new"] = $("submitterName-new").val();
        //     sendObj["submitterEmail-new"] = $("submitterEmail-new").val();
        //     sendObj["submitterOrganization-new"] = $("submitterOrganization-new").val();
        //     sendObj["submitterProject-new"] = $("submitterProject-new").val();
        //   }



        myShepherd.closeDBTransaction();
    }

}