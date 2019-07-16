/*
 * Wildbook - A Mark-Recapture Framework
 * Copyright (C) 2011-2018 Jason Holmberg
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

import org.ecocean.Shepherd;
import org.ecocean.NoteField;
import org.ecocean.User;
import org.ecocean.Util;
import org.ecocean.AccessControl;
import org.json.JSONObject;


public class NoteFieldEdit extends HttpServlet {

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

        //TODO we could make this check owner of Encounter(s) etc etc
        User user = AccessControl.getUser(request, myShepherd);
/*
        boolean isAdmin = false;
        if (user != null) isAdmin = myShepherd.doesUserHaveRole(user.getUsername(), "admin", context);
        if (!isAdmin) {
            response.sendError(401, "access denied");
            response.setContentType("text/plain");
            out.println("access denied");
        }
*/

        JSONObject rtn = new JSONObject("{\"success\": false}");
        String id = jsonIn.optString("id", null);
        String content = jsonIn.optString("content", "");
        if (!Util.isUUID(id)) throw new RuntimeException(id + " is not a uuid");
        NoteField nf = myShepherd.getNoteField(id);
        if ((nf == null) && !NoteField.canCreate(request, myShepherd)) {
            rtn.put("error", "id=" + id + " does not exist and cannot be created");
        } else if ((nf != null) && !nf.canEdit(request, myShepherd)) {
            rtn.put("error", "id=" + id + " cannot be edited");
        } else {
            rtn.put("id", id);
            if (nf == null) {
                nf = new NoteField();
                nf.setId(id);
                rtn.put("newlyCreated", true);
                myShepherd.getPM().makePersistent(nf);
            }
            nf.setContent(content);
            rtn.put("result", nf.toJSONObject());
            rtn.put("success", true);
        }
        if (rtn.optBoolean("success", false)) {
            myShepherd.commitDBTransaction();
        } else {
            myShepherd.rollbackDBTransaction();
        }
        response.setContentType("text/json");
        out.println(rtn);
        out.close();
    }
}
