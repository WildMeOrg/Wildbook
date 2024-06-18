package org.ecocean.api;

import java.io.IOException;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.ServletException;

import org.ecocean.servlet.ServletUtilities;
import org.ecocean.Shepherd;
import org.ecocean.User;
import org.json.JSONObject;

public class SearchApi extends ApiBase {
    protected void doPost(HttpServletRequest request, HttpServletResponse response)
    throws ServletException, IOException {
        String context = ServletUtilities.getContext(request);
        Shepherd myShepherd = new Shepherd(context);

        myShepherd.setAction("api.SearchApi.POST");
        myShepherd.beginDBTransaction();

        User currentUser = myShepherd.getUser(request);
        if ((currentUser == null) || (currentUser.getId() == null)) {
            response.setStatus(401);
            response.setHeader("Content-Type", "application/json");
            response.getWriter().write("{\"success\": false}");
            myShepherd.rollbackDBTransaction();
            myShepherd.closeDBTransaction();
            return;
        }
        String arg = request.getPathInfo();
        JSONObject query = ServletUtilities.jsonFromHttpServletRequest(request);
        JSONObject results = null;
        myShepherd.rollbackDBTransaction();
        myShepherd.closeDBTransaction();
        response.setStatus(200);
        response.setHeader("Content-Type", "application/json");
        response.getWriter().write(results.toString());
    }
}
