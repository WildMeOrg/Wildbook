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
import org.ecocean.Util;

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

        int numSubmitters = 0; 
        if (request.getParameter("numSubmitters")!=null) {
            numSubmitters = Integer.valueOf(request.getParameter("numSubmitters"));
        }
        int numPhotographers = 0;
        if (request.getParameter("numPhotographers")!=null) {
            numPhotographers = Integer.valueOf(request.getParameter("numPhotographers"));
        }

        String encNum = request.getParameter("encNum");
        Encounter enc = myShepherd.getEncounter(encNum);
        setDateLastModified(enc);

        //Run through submitter creation/modification
        try {
            System.out.println("Lets try to get some submitter data.");
            for (int i=0; i<numSubmitters;i++) {

                String newName = request.getParameter("submitterName-"+i);
                String newEmail = request.getParameter("submitterEmail-"+i);
                String newAff = request.getParameter("submitterOrganization-"+i);
                String newProj = request.getParameter("submitterProject-"+i);
                String submitterId = request.getParameter("submitterId-"+i);
                System.out.println("Name: "+newName+" Email: "+newEmail+" Aff: "+newProj+" ID: "+submitterId);
                if (!stringIsNullOrEmpty(request.getParameter("submitterName-"+i))||!stringIsNullOrEmpty(request.getParameter("submitterEmail-"+i))) {
                    
                    User u = myShepherd.getUserByUUID(submitterId);

                    if (u!=null) {

                        System.out.println("Trying to set the new fields...");
                        myShepherd.beginDBTransaction();
                        if (!stringIsNullOrEmpty(newName)) u.setFullName(newName);
                        if (!stringIsNullOrEmpty(newEmail)) u.setEmailAddress(newEmail);
                        if (!stringIsNullOrEmpty(newAff)) u.setAffiliation(newAff);
                        if (!stringIsNullOrEmpty(newProj)) u.setUserProject(newProj);
                        myShepherd.commitDBTransaction();

                        System.out.println("Current Name: "+u.getFullName());
                    }
                }
            }

            if (stringIsNullOrEmpty(request.getParameter("submitterName-new"))) {
                String newName  = request.getParameter("submitterName-new");
                String newEmail  = request.getParameter("submitterEmail-new");
                String newAff  = request.getParameter("submitterOrganization-new");
                String newProj  = request.getParameter("submitterProject-new");

                User newU = new User(Util.generateUUID());
                newU.setFullName(newName);
                newU.setEmailAddress(newEmail);
                newU.setAffiliation(newAff);
                newU.setUserProject(newProj);

                myShepherd.storeNewUser(newU);
            }
        } catch (Exception e) {
            myShepherd.rollbackDBTransaction();
            System.out.println("Error modifying submitter data for encounter num:"+enc.getCatalogNumber());
            e.printStackTrace();
        }
        
        //Run through photographer creation/modification
        try {
            System.out.println("Lets try to get some photographer data.");
            for (int i=0; i<numPhotographers;i++) {

                System.out.println("Checking for modifications to existing photographer num: "+i);
                String newName = request.getParameter("photographerName-"+i);
                String newEmail = request.getParameter("photographerEmail-"+i);
                String newAff = request.getParameter("photographerOrganization-"+i);
                String newProj = request.getParameter("photographerProject-"+i);
                String photographerId = request.getParameter("photographerId-"+i);
                System.out.println("Current Name: "+newName+" Email: "+newEmail+" Aff: "+newProj+" ID: "+photographerId);
                if (!stringIsNullOrEmpty(request.getParameter("photographerName-"+i))||!stringIsNullOrEmpty(request.getParameter("photographerEmail-"+i))) {
                    
                    User u = myShepherd.getUserByUUID(photographerId);

                    if (u!=null) {

                        System.out.println("Trying to set the new fields...");
                        myShepherd.beginDBTransaction();
                        if (!stringIsNullOrEmpty(newName)) u.setFullName(newName);
                        if (!stringIsNullOrEmpty(newEmail)) u.setEmailAddress(newEmail);
                        if (!stringIsNullOrEmpty(newAff)) u.setAffiliation(newAff);
                        if (!stringIsNullOrEmpty(newProj)) u.setUserProject(newProj);
                        myShepherd.commitDBTransaction();

                        System.out.println("Current Name: "+u.getFullName());

                    }
                }
            }

            if (stringIsNullOrEmpty(request.getParameter("photographerName-new"))) {
                System.out.println("Creating a new photographer entry...");
                String newName  = request.getParameter("photographerName-new");
                String newEmail  = request.getParameter("photographerEmail-new");
                String newAff  = request.getParameter("photographerOrganization-new");
                String newProj  = request.getParameter("photographerProject-new");

                User newU = new User(Util.generateUUID());
                newU.setFullName(newName);
                newU.setEmailAddress(newEmail);
                newU.setAffiliation(newAff);
                newU.setUserProject(newProj);

                myShepherd.storeNewUser(newU);
            }
        } catch (Exception e) {
            myShepherd.rollbackDBTransaction();
            System.out.println("Error modifying photographer data for encounter num:"+enc.getCatalogNumber());
            e.printStackTrace();
        } 

        myShepherd.closeDBTransaction();
    }

    private boolean stringIsNullOrEmpty(String str) {
        if (str==null||str.isEmpty()) {
            return true;
        }
        return false;
    }

}