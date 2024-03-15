package org.ecocean.api;

import java.io.IOException;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.json.JSONObject;
import org.json.JSONArray;
import org.joda.time.DateTime;

import org.ecocean.Shepherd;
import org.ecocean.User;
import org.ecocean.Project;
import org.ecocean.Occurrence;
import org.ecocean.servlet.ServletUtilities;



public class UserHome extends ApiBase {

    protected void doGet(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
        String context = ServletUtilities.getContext(request);
        Shepherd myShepherd = new Shepherd(context);
        myShepherd.setAction("api.UserHome");
        myShepherd.beginDBTransaction();

        JSONObject home = new JSONObject();
        User currentUser = myShepherd.getUser(request);
        if (currentUser == null) {
            response.setStatus(401);
            response.setHeader("Content-Type", "application/json");
            response.getWriter().write("{\"success\": false}");
            myShepherd.rollbackDBTransaction();
            myShepherd.closeDBTransaction();
            return;
        }

        home.put("user", currentUser.infoJSONObject(true));

        // TODO ES replace
        JSONArray sightingsArr = new JSONArray();
        int count = 0;
        for (Occurrence occ : myShepherd.getOccurrencesByUser(currentUser)) {

            JSONObject oj = new JSONObject();
            oj.put("id", occ.getId());
            Long millis = occ.getMillisRobust();
            if (millis == null) {
                oj.put("dateTime", (String)null);
            } else {
                oj.put("dateTime", new DateTime(millis));
            }
            oj.put("numberEncounters", occ.getNumberEncounters());
            oj.put("numberAnnotations", occ.getNumberAnnotations());
            oj.put("taxonomies", occ.getAllSpeciesDeep());
            sightingsArr.put(oj);
            count++;
            if (count > 2) break;
        }
        home.put("latestSightings", sightingsArr);

        JSONArray projArr = new JSONArray();
        count = 0;
        for (Project proj : currentUser.getProjects(myShepherd)) {
            JSONObject pj = new JSONObject();
            pj.put("id", proj.getId());
            pj.put("name", proj.getResearchProjectName());
            pj.put("percentComplete", proj.getPercentWithIncrementalIds());
            pj.put("numberEncounters", proj.getEncounters().size());
            projArr.put(pj);
            count++;
            if (count > 2) break;
        }
        home.put("projects", projArr);

        response.setStatus(200);
        response.setHeader("Content-Type", "application/json");
        response.getWriter().write(home.toString());
        myShepherd.rollbackDBTransaction();
        myShepherd.closeDBTransaction();
    }  	

}

/*

    proposal: combine all things needed for landing page here
    (some of these can be separate query when ES exists?)
    maybe only for current user?

    * "latest data": N most recent sightings
    * latest: bulk import, individual, "matching action"
    * projects data: see/replicate projectsList.jsp

*/
