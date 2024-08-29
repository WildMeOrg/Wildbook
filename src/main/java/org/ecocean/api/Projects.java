package org.ecocean.api;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.ServletException;
import org.json.JSONArray;
import org.json.JSONObject;
import org.ecocean.Project;
import org.ecocean.Shepherd;
import org.ecocean.User;
import org.ecocean.servlet.ServletUtilities;

public class Projects extends ApiBase {

    protected void doGet(HttpServletRequest request, HttpServletResponse response)
    throws ServletException, IOException {
        String context = ServletUtilities.getContext(request);
        Shepherd myShepherd = new Shepherd(context);

        myShepherd.setAction("api.Projects.GET");
        myShepherd.beginDBTransaction();

        JSONObject result = new JSONObject();
        User currentUser = myShepherd.getUser(request);
        if (currentUser == null) {
            response.setStatus(401);
            response.setHeader("Content-Type", "application/json");
            response.getWriter().write("{\"success\": false}");
            return;
        }

        List<Project> userProjects = currentUser.getProjects(myShepherd);
        JSONArray userProjectsArr = new JSONArray();
        for (Project proj : userProjects) {
            JSONObject pj = new JSONObject();
            pj.put("id", proj.getId());
            pj.put("name", proj.getResearchProjectName());
            pj.put("percentComplete", proj.getPercentWithIncrementalIds());
            pj.put("numberEncounters", proj.getEncounters().size());
            userProjectsArr.put(pj);
        }
        result.put("projects", userProjectsArr);

        response.setStatus(200);
        response.setHeader("Content-Type", "application/json");
        response.getWriter().write(result.toString());
    }

    protected void AllProjects(HttpServletResponse response, Shepherd myShepherd, JSONObject result) throws Exception {
        List<Project> allSystemProjects = myShepherd.getAllProjects();
        JSONArray allProjectsArr = new JSONArray();
        for (Project proj : allSystemProjects) {
            JSONObject pj = new JSONObject();
            pj.put("id", proj.getId());
            pj.put("name", proj.getResearchProjectName());
            pj.put("percentComplete", proj.getPercentWithIncrementalIds());
            pj.put("numberEncounters", proj.getEncounters().size());
            allProjectsArr.put(pj);
        }
        result.put("projects", allProjectsArr);

        response.setStatus(200);
        response.setHeader("Content-Type", "application/json");
        response.getWriter().write(result.toString());
    }
}
