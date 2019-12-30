
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
import org.json.JSONArray;


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
        String encId = jsonIn.optString("encounterId", "_FAIL_");
        String prop = jsonIn.optString("property", null);
        JSONObject value = jsonIn.optJSONObject("value");
        JSONObject multiple = jsonIn.optJSONObject("multiple");  //special multiple prop/value set!
        Encounter enc = myShepherd.getEncounter(encId);
        //TODO we could make this check owner of Encounter(s) etc etc
        if (enc == null) {
            rtn.put("error", "invalid encId passed");
        } else if (multiple != null) {  //this wins over single property/value type
            JSONArray ids = new JSONArray();
            String multId = Util.generateUUID();
            for (Object kobj : multiple.keySet()) {
                String key = (String)kobj;
                JSONObject val = multiple.optJSONObject(key);
                if (val == null) continue;
                val.put("_multipleId", multId);
                Decision dec = new Decision(user, enc, key, val);
                myShepherd.getPM().makePersistent(dec);
                ids.put(dec.getId());
            }
            if (ids.length() > 0) {
                rtn.put("success", true);
                rtn.put("decisionIds", ids);
            } else {
                rtn.put("error", "could not find values for multiple");
            }
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
