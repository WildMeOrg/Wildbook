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
        JSONObject res = new JSONObject();
        if ((currentUser == null) || (currentUser.getId() == null)) {
            response.setStatus(401);
            res.put("error", 401);
        } else {
            String arg = request.getPathInfo();
            JSONObject query = ServletUtilities.jsonFromHttpServletRequest(request);
            response.setStatus(200);
            res.put("success", true);
        }
        response.setHeader("Content-Type", "application/json");
        response.getWriter().write(res.toString());
        response.getWriter().close();
        myShepherd.rollbackAndClose();
    }
}
