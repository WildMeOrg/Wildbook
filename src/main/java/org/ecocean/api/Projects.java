package org.ecocean.api;

import java.io.IOException;
import java.util.List;
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

    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response)
    throws ServletException, IOException {
        String context = ServletUtilities.getContext(request);
        Shepherd myShepherd = new Shepherd(context);

        myShepherd.setAction("api.Projects.GET");
        myShepherd.beginDBTransaction();

        User currentUser = myShepherd.getUser(request);
        if (currentUser == null) {
            response.setStatus(401);
            response.setHeader("Content-Type", "application/json");
            response.getWriter().write("{\"success\": false}");
            myShepherd.rollbackDBTransaction();
            myShepherd.closeDBTransaction();
            return;
        }

        JSONObject result = new JSONObject();
        String pathInfo = request.getPathInfo();

        if ((pathInfo == null) || pathInfo.equals("/")) {
            // No additional path, return projects accessible to the current user
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
        } else if (pathInfo.endsWith("/all")) {
            // /all is present, return all projects in the system
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
        } else {
            // Invalid path
            response.setStatus(404);
            response.setHeader("Content-Type", "application/json");
            response.getWriter().write("{\"success\": false, \"error\": \"Endpoint not found\"}");
        }

        myShepherd.rollbackDBTransaction();
        myShepherd.closeDBTransaction();
    }
}
