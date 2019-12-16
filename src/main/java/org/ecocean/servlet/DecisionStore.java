
package org.ecocean.servlet;

import java.io.IOException;
import java.io.PrintWriter;

import javax.servlet.ServletConfig;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.ecocean.Shepherd;
import org.ecocean.Decision;
import org.ecocean.User;
import org.ecocean.Util;
import org.ecocean.Encounter;
import org.ecocean.servlet.ServletUtilities;
import org.ecocean.AccessControl;
import org.json.JSONObject;


public class DecisionStore extends HttpServlet {
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

        User user = AccessControl.getUser(request, myShepherd);
        if (user == null) {
            response.sendError(401, "access denied");
            response.setContentType("text/plain");
            out.println("access denied");
            return;
        }

        JSONObject rtn = new JSONObject("{\"success\": false}");
        String encId = jsonIn.optString("encId", "_FAIL_");
        String prop = jsonIn.optString("property", null);
        JSONObject value = jsonIn.optJSONObject("value");
        Encounter enc = myShepherd.getEncounter(encId);
        //TODO we could make this check owner of Encounter(s) etc etc
        if (enc == null) {
            rtn.put("error", "invalid encId passed");
        } else if (prop == null) {
            //TODO could check property names?
            rtn.put("error", "invalid property passed");
        } else if (value == null) {
            rtn.put("error", "invalid value passed");
        } else {  //good to go?
            Decision dec = new Decision(user, enc, prop, value);
            myShepherd.getPM().makePersistent(dec);
            rtn.put("success", true);
            rtn.put("decisionId", dec.getId());
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
