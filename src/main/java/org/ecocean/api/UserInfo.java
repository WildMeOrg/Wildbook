package org.ecocean.api;

import java.io.IOException;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.ServletException;

import org.ecocean.servlet.ServletUtilities;
import org.ecocean.Shepherd;
import org.ecocean.User;
import org.json.JSONObject;

public class UserInfo extends ApiBase {
    // for polling we do a simple HEAD response
    protected void doHead(HttpServletRequest request, HttpServletResponse response)
    throws ServletException, IOException {
        String context = ServletUtilities.getContext(request);
        Shepherd myShepherd = new Shepherd(context);

        myShepherd.setAction("api.UserInfo.HEAD");
        myShepherd.beginDBTransaction();

        User currentUser = myShepherd.getUser(request);
        if (currentUser == null) {
            response.setStatus(401);
            // response.setHeader("Content-Type", "application/json");
            // response.getWriter().write("{\"success\": false}");
            myShepherd.rollbackDBTransaction();
            myShepherd.closeDBTransaction();
            return;
        }
        response.setStatus(200);
        response.setHeader("X-User-Id", currentUser.getId());
        // TODO: evaluate if other header information (notifications, login time) should be set here
        myShepherd.rollbackDBTransaction();
        myShepherd.closeDBTransaction();
    }

    protected void doGet(HttpServletRequest request, HttpServletResponse response)
    throws ServletException, IOException {
        String context = ServletUtilities.getContext(request);
        Shepherd myShepherd = new Shepherd(context);

        myShepherd.setAction("api.UserInfo.GET");
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
        JSONObject results = null;
        String arg = request.getPathInfo();
        if ((arg == null) || arg.equals("/")) { // current user (no guid)
            results = currentUser.infoJSONObject(context, true);
        } else {
            User otherUser = myShepherd.getUserByUUID(arg.substring(1));
            if (otherUser == null) {
                response.setStatus(404);
                response.setHeader("Content-Type", "application/json");
                response.getWriter().write("{\"success\": false}");
                myShepherd.rollbackDBTransaction();
                myShepherd.closeDBTransaction();
                return;
            } else if (otherUser.getId().equals(currentUser.getId())) {
                results = currentUser.infoJSONObject(context, true);
            } else {
                results = otherUser.infoJSONObject(context, false);
            }
        }
        myShepherd.rollbackDBTransaction();
        myShepherd.closeDBTransaction();
        response.setStatus(200);
        response.setCharacterEncoding("UTF-8");
        response.setHeader("Content-Type", "application/json");
        response.getWriter().write(results.toString());
    }
}
