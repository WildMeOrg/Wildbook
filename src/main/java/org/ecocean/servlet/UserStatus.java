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
import javax.jdo.Query;
import java.util.Collection;
import java.util.ArrayList;
import org.joda.time.DateTime;
import org.ecocean.Shepherd;
import org.ecocean.Route;
import org.ecocean.movement.Path;
import org.ecocean.User;
import org.ecocean.Util;
import org.ecocean.AccessControl;
import org.json.JSONObject;
import org.json.JSONArray;


public class UserStatus extends HttpServlet {

    @Override
    public void init(final ServletConfig config) throws ServletException {
        super.init(config);
    }


    @Override
    public void doGet(final HttpServletRequest request, final HttpServletResponse response) throws ServletException, IOException {
        String context = ServletUtilities.getContext(request);
        Shepherd myShepherd = new Shepherd(context);
        myShepherd.beginDBTransaction();
        PrintWriter out = response.getWriter();

        User user = AccessControl.getUser(request, myShepherd);
        if (user == null) {
            response.sendError(401, "access denied");
            response.setContentType("text/plain");
            out.println("access denied");
            myShepherd.rollbackDBTransaction();
            myShepherd.closeDBTransaction();
            return;
        }

        JSONObject rtn = new JSONObject("{\"success\": true}");
        JSONObject uinfo = new JSONObject();
        uinfo.put("username", user.getUsername());
        uinfo.put("fullName", user.getFullName());
        uinfo.put("id", user.getUUID());
        rtn.put("info", uinfo);

        //Query q = myShepherd.getPM().newQuery("SELECT FROM org.ecocean.Route WHERE startTime .....");
        Query q = myShepherd.getPM().newQuery("SELECT FROM org.ecocean.Route");
        q.setOrdering("startTime");
        Collection c = (Collection) (q.execute());
        JSONArray jarr = new JSONArray();
        for (Route rt : new ArrayList<Route>(c)) {
            JSONObject jrt = new JSONObject();
            jrt.put("id", rt.getId());
            jrt.put("name", rt.getName());
            jrt.put("locationId", rt.getLocationId());
            jrt.put("startTime", rt.getStartTime());
            jrt.put("endTime", rt.getEndTime());
            Path path = rt.getPath();
            if (path != null) {
                JSONArray pts = Path.toJSONArray(path.getPointLocations());
                jrt.put("path", pts);
            }
            jarr.put(jrt);
        }
        q.closeAll();
        rtn.put("routes", jarr);

        myShepherd.rollbackDBTransaction();
        myShepherd.closeDBTransaction();
        response.setContentType("text/json");
        out.println(rtn.toString(4));
        out.close();
    }
}
