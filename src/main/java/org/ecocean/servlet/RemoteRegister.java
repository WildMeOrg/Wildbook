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
import java.util.List;
import java.util.ArrayList;
//import org.joda.time.DateTime;
import org.ecocean.Shepherd;
import org.ecocean.CommonConfiguration;
import org.ecocean.SystemValue;
import org.ecocean.Role;
//import org.ecocean.movement.SurveyTrack;
//import org.ecocean.movement.Path;
import org.ecocean.User;
import org.ecocean.Util;
import org.ecocean.AccessControl;
import org.json.JSONObject;
import org.json.JSONArray;


public class RemoteRegister extends HttpServlet {

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
        myShepherd.setAction("RemoteRegister");
        PrintWriter out = response.getWriter();
        User thisUser = AccessControl.getUser(request, myShepherd);

///// first lets see if survey is complete:

        JSONObject rtn = new JSONObject();
        rtn.put("success", false);
        String errorMessage = "Unknown error message";
        String[] fields = new String[]{"cat_volunteer", "have_cats", "disability", "citsci_classification", "age", "gender", "ethnicity", "education", "how_hear", "where_live", "citsci_collecting", "citsci_photos"};
        JSONObject resp = new JSONObject();
        List<String> errors = new ArrayList<String>();
        for (int i = 0 ; i < fields.length ; i++) {
            String val = request.getParameter(fields[i]);
//System.out.println(fields[i] + ": (" + val + ")");
            if ((val == null) || (val.trim().equals(""))) {
                errors.add("missing a value for " + fields[i]);
                continue;
            }
            if (fields[i].equals("have_cats") || fields[i].equals("ethnicity")) {
                JSONArray mult = new JSONArray(request.getParameterValues(fields[i]));
                resp.put(fields[i], mult);
            } else {
                resp.put(fields[i], val);
            }
        }
System.out.println("RemoteRegister: survey response: " + resp.toString());
        if (errors.size() > 0) {
            errorMessage = String.join(", ", errors);
            rtn.put("error", errorMessage);
            out.println(rtn.toString());
            myShepherd.rollbackDBTransaction();
            myShepherd.closeDBTransaction();
            return;
        }

        boolean reg_terms = "true".equals(request.getParameter("agree-terms"));
        if (thisUser != null) {  //existing user, new survey results
            System.out.println("RemoteRegister: saving survey as logged in " + thisUser);
            if (!reg_terms) {
                System.out.println(" ... but did not agree-terms");
                rtn.put("error", "Please agree to terms and conditions");
                out.println(rtn.toString());
                myShepherd.rollbackDBTransaction();
                myShepherd.closeDBTransaction();
            } else {
                rtn.put("success", true);
                String surv_key = "survey_response_phase3_" + thisUser.getUUID();
                SystemValue.set(myShepherd, surv_key, resp);
                out.println(rtn.toString());
                myShepherd.commitDBTransaction();
                myShepherd.closeDBTransaction();
            }
            return;
        }

        //no thisUser, so lets make one!
        String reg_username = request.getParameter("username");
        String reg_email = request.getParameter("email");
        String reg_password1 = request.getParameter("password1");
        String reg_password2 = request.getParameter("password2");
        String key = request.getParameter("key");

        User user = null;
        String apiKey = CommonConfiguration.getProperty("kitsci_api_key", context);
        if (apiKey == null) apiKey = "MUST_SET_kitsci_api_key_" + Util.generateUUID();  //pretty much guarantees failure!
        String wantKey = org.ecocean.media.AssetStore.hexStringSHA256(apiKey + ":" + reg_username);
        System.out.println("RemoteRegister: key=[" + key + "] vs wantKey=[" + wantKey + "] on username=[" + reg_username + "]");

        boolean ok = ((key != null) && key.toLowerCase().equals(wantKey));  //java lib uses lowercase hex in wantKey
        if (!ok) errorMessage = "Invalid response";
        if (ok && !reg_terms) {
            ok = false;
            errorMessage = "Please agree to terms and conditions";
        }

        if (ok) try {
            user = registerUser(myShepherd, reg_username, reg_email, reg_password1, reg_password2);
        } catch (java.io.IOException ex) {
            System.out.println("RemoteRegister WARNING: registerUser() threw " + ex.getMessage());
            errorMessage = ex.getMessage();
        }

        if (user == null) {
            rtn.put("error", errorMessage);
            out.println(rtn.toString());
            myShepherd.rollbackDBTransaction();
            myShepherd.closeDBTransaction();
            return;
        }

        myShepherd.getPM().makePersistent(user);
        System.out.println("[INFO] RemoteRegister created " + user);
///if we get this far, save survey too
        String surv_key = "survey_response_phase3_" + user.getUUID();
        SystemValue.set(myShepherd, surv_key, resp);
        rtn.put("success", true);
        rtn.put("userId", user.getUUID());
        rtn.put("username", user.getUsername());
        myShepherd.commitDBTransaction();
        myShepherd.closeDBTransaction();
        out.println(rtn.toString());

/* ********************
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
        survey.addSurveyTrack(trk);
        //TODO add encounters via occurrence, if need be

        myShepherd.getPM().makePersistent(survey);
        rtn.put("success", true);
        rtn.put("surveyId", survey.getID());
        System.out.println(survey + " created by " + user + " for route=" + jsonIn.optString("routeId", null));

        if (rtn.optBoolean("success", false)) {
            myShepherd.commitDBTransaction();
        } else {
            myShepherd.rollbackDBTransaction();
        }
        response.setContentType("text/json");
        out.println(rtn);
        out.close();
*/

    }
/* ======


%><%
String context = ServletUtilities.getContext(request);
Shepherd myShepherd = new Shepherd(context);
myShepherd.setAction("remote.jsp");
myShepherd.beginDBTransaction();



%>


*/

    public static User registerUser(Shepherd myShepherd, String username, String email, String pw1, String pw2) throws java.io.IOException {
        if (!Util.stringExists(username)) throw new IOException("Invalid username format");
        username = username.toLowerCase().trim();
        if (!Util.isValidEmailAddress(email)) throw new IOException("Invalid email format");
        if (!Util.stringExists(pw1) || !Util.stringExists(pw2) || !pw1.equals(pw2)) throw new IOException("Password invalid or do not match");
        if (pw1.length() < 8) throw new IOException("Password is too short");
        username = username.toLowerCase();
        User exists = myShepherd.getUser(username);
        if (exists == null) exists = myShepherd.getUserByEmailAddress(email);
        if ((exists != null) || username.equals("admin")) throw new IOException("Invalid username/email");
        String salt = Util.generateUUID();
        String hashPass = ServletUtilities.hashAndSaltPassword(pw1, salt);
        User user = new User(username, hashPass, salt);
        user.setEmailAddress(email);
        user.setNotes("<p data-time=\"" + System.currentTimeMillis() + "\">created via registration.</p>");
        Role role = new Role(username, "cat_walk_volunteer");
        role.setContext(myShepherd.getContext());
        myShepherd.getPM().makePersistent(role);
        return user;
    }


}
