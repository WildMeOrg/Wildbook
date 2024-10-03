package org.ecocean.api;

import java.io.IOException;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.ServletException;

import java.util.List;
import org.joda.time.DateTime;
import org.json.JSONArray;
import org.json.JSONObject;

import org.ecocean.Annotation;
import org.ecocean.Encounter;
import org.ecocean.ia.Task;
import org.ecocean.Project;
import org.ecocean.servlet.importer.ImportTask;
import org.ecocean.servlet.ServletUtilities;
import org.ecocean.Shepherd;
import org.ecocean.User;
import org.ecocean.Util;

// note: this is for use on any Base object (MarkedIndividual, Encounter, Occurrence)
public class BaseObject extends ApiBase {
    protected void doGet(HttpServletRequest request, HttpServletResponse response)
    throws ServletException, IOException {
        doPost(request, response);
    }

    protected void doPost(HttpServletRequest request, HttpServletResponse response)
    throws ServletException, IOException {
        String arg = request.getPathInfo();
        if ((arg == null) || arg.equals("")) arg = " ";
        String[] args = arg.substring(1).split("/");
        System.out.println("args: " + java.util.Arrays.toString(args));

        JSONObject payload = null;
        if (request.getMethod().equals("POST")) {
            payload = ServletUtilities.jsonFromHttpServletRequest(request);
        }
        String context = ServletUtilities.getContext(request);
        Shepherd myShepherd = new Shepherd(context);

        myShepherd.setAction("api.BaseObject");
        myShepherd.beginDBTransaction();

        JSONObject rtn = new JSONObject();
        User currentUser = myShepherd.getUser(request);
        if (currentUser == null) {
            response.setStatus(401);
            response.setHeader("Content-Type", "application/json");
            response.getWriter().write("{\"success\": false}");
            myShepherd.rollbackDBTransaction();
            myShepherd.closeDBTransaction();
            return;
        }

        rtn.put("success", true);
        response.setStatus(200);
        response.setCharacterEncoding("UTF-8");
        response.setHeader("Content-Type", "application/json");
        response.getWriter().write(rtn.toString());
        myShepherd.rollbackDBTransaction();
        myShepherd.closeDBTransaction();
    }
}
