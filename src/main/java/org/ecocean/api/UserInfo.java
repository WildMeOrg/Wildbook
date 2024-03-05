package org.ecocean.api;

import java.io.IOException;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.json.JSONObject;



public class UserInfo extends ApiBase {

    protected void doGet(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
        JSONObject info = new JSONObject();

        response.setStatus(200);
        response.setHeader("Content-Type", "application/json");
        response.getWriter().write(info.toString());
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
