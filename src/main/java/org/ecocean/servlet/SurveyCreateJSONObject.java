/*
 * Wildbook - A Mark-Recapture Framework
 * Copyright (C) 2011-2020 Jason Holmberg
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

import java.io.IOException;
import java.io.PrintWriter;

import javax.servlet.ServletConfig;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import org.joda.time.DateTime;
import org.ecocean.Shepherd;
import org.ecocean.Survey;
import org.ecocean.Encounter;
import org.ecocean.Occurrence;
import org.ecocean.movement.SurveyTrack;
import org.ecocean.movement.Path;
import org.ecocean.User;
import org.ecocean.Util;
import org.ecocean.AccessControl;
import org.json.JSONObject;
import org.json.JSONArray;


public class SurveyCreateJSONObject extends HttpServlet {

    @Override
    public void init(final ServletConfig config) throws ServletException {
        super.init(config);
    }


    @Override
    public void doGet(final HttpServletRequest request, final HttpServletResponse response) throws ServletException, IOException {
        doPost(request, response);
    }

    @Override
    public void doPost(final HttpServletRequest request, final HttpServletResponse response) throws ServletException, IOException {
        String context = ServletUtilities.getContext(request);
        Shepherd myShepherd = new Shepherd(context);
        myShepherd.beginDBTransaction();
        JSONObject jsonIn = ServletUtilities.jsonFromHttpServletRequest(request);
        PrintWriter out = response.getWriter();

        User user = AccessControl.getUser(request, myShepherd);
        if ((user == null) || (jsonIn == null)) {
            response.sendError(401, "access denied");
            response.setContentType("text/plain");
            out.println("access denied");
            myShepherd.rollbackDBTransaction();
            myShepherd.closeDBTransaction();
            return;
        }

System.out.println("SurveyCreate jsonIn => " + jsonIn);
        JSONObject rtn = new JSONObject("{\"success\": false}");

        DateTime startTime = null;
        String st = jsonIn.optString("startTime", null);
        if (st != null) startTime = new DateTime(st);
        Survey survey = new Survey(startTime);
        DateTime endTime = null;
        String et = jsonIn.optString("endTime", null);
        if (et != null) endTime = new DateTime(et);
System.out.println(startTime + " --> " + endTime);
        if (endTime != null) survey.setEndTimeMilli(endTime.getMillis());

        survey.addComments(user.getUUID());  //will be available thru occurrence/encounters more officially
        survey.setProjectName(jsonIn.optString("routeId", null));
        survey.setProjectType("route");

        SurveyTrack trk = new SurveyTrack(Path.fromJSONArray(jsonIn.optJSONArray("path")));

        JSONArray eids = jsonIn.optJSONArray("encounterIds");
        if (eids != null) for (int i = 0 ; i < eids.length() ; i++) {
            String eid = eids.optString(i, null);
            if (eid == null) continue;
            Encounter enc = myShepherd.getEncounter(eid);
            if (enc == null) {
                System.out.println("WARNING: SurveyCreate could not load Encounter for [" + i + "] id=" + eid);
            } else {
                Occurrence occ = new Occurrence(Util.generateUUID(), enc);
                occ.setSubmitterIDFromEncs();
                occ.setSubmittersFromEncounters();
                occ.setLatLonFromEncs();
                occ.setMillisFromEncounters();
                occ.setDateFromEncounters();
                occ.setTaxonomiesFromEncounters(myShepherd);
                myShepherd.getPM().makePersistent(occ);
                trk.addOccurrence(occ);
                System.out.println("INFO: SurveyCreate created [" + i + "] " + occ + " for " + enc + " and added to " + trk);
            }
        }
System.out.println("SurveyCreate: out of loop");
        survey.addSurveyTrack(trk);
System.out.println("SurveyCreate: added SurveyTrack");

        myShepherd.getPM().makePersistent(survey);
        rtn.put("success", true);
        rtn.put("surveyId", survey.getID());
        System.out.println(survey + " created by " + user + " for route=" + jsonIn.optString("routeId", null));
        myShepherd.commitDBTransaction();
        myShepherd.closeDBTransaction();
        response.setContentType("text/json");
        out.println(rtn);
        out.close();
System.out.println("SurveyCreate: exit");
    }
}
