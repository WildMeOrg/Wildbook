
package org.ecocean.servlet;

import java.io.IOException;
import java.io.PrintWriter;

import javax.jdo.Query;
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

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;


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
        int action = jsonIn.optInt("action", 1);
        JSONObject value = jsonIn.optJSONObject("value");
        JSONObject multiple = jsonIn.optJSONObject("multiple");  //special multiple prop/value set!
        List<String> skipUsers = Arrays.asList("cmv2", "cmvolunteer", "testvolunteer1", "tomcat", "volunteer", "kitizenscience");
        Encounter enc = myShepherd.getEncounter(encId);
        //TODO we could make this check owner of Encounter(s) etc etc
        if (enc == null) {
            rtn.put("error", "invalid encounterId passed");
        } else if (multiple != null) {  //this wins over single property/value type
            JSONArray ids = new JSONArray();
            String multId = Util.generateUUID();
            for (Object kobj : multiple.keySet()) {
                String key = (String) kobj;
                JSONObject val = multiple.optJSONObject(key);
                if (val == null) continue;
                val.put("_multipleId", multId);

                if (action == 0) {
                    deleteFlag(response, out, myShepherd, rtn, enc, value.getJSONArray("value").get(0).toString());
                } else {
                    Decision dec = new Decision(user, enc, key, val);
                    myShepherd.getPM().makePersistent(dec);
                    ids.put(dec.getId());
                    Decision.updateEncounterStateBasedOnDecision(myShepherd, enc, skipUsers);
                }
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
            if (action == 0) {
                deleteFlag(response, out, myShepherd, rtn, enc, value.getJSONArray("value").get(0).toString());
            } else {
                Decision dec = new Decision(user, enc, prop, value);
                myShepherd.getPM().makePersistent(dec);
                rtn.put("success", true);
                rtn.put("decisionId", dec.getId());
                Decision.updateEncounterStateBasedOnDecision(myShepherd, enc, skipUsers);
            }

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

    private void deleteFlag(HttpServletResponse response, PrintWriter out, Shepherd myShepherd, JSONObject rtn, Encounter enc, String value) {
        try {
            //String jdoql = "SELECT FROM DECISION WHERE ENCOUNTER_CATALOGNUMBER_OID=='" + enc.getCatalogNumber() + "' && VALUE == '" + value + "' && PROPERTY == 'flag'";
            String jdoql = "SELECT FROM DECISION WHERE ENCOUNTER_CATALOGNUMBER_OID=='" + enc.getCatalogNumber() + "'";
            //String jdoql = "DELETE FROM DECISION WHERE ENCOUNTER_CATALOGNUMBER_OID LIKE '" + enc.getCatalogNumber() + "' AND VALUE LIKE '%" + value + "%' AND PROPERTY LIKE 'flag'";
            rtn.put("query", jdoql);
            Query query = myShepherd.getPM().newQuery(jdoql);
            Collection col = (Collection) query.execute();
            List<Decision> decs = new ArrayList<Decision>(col);
            for (Decision d : decs) {
                if(d.getProperty().equals("flag") && d.getValueAsString().contains(value)){
                    myShepherd.getPM().deletePersistent(d);
                }
            }
            myShepherd.commitDBTransaction();
            rtn.put("success", true);
        } catch (Exception e) {
            rtn.put("success", false);
            rtn.put("JSP-error", e.getMessage());
            myShepherd.rollbackDBTransaction();
        } finally {
            myShepherd.closeDBTransaction();
            response.setContentType("text/json");
            out.println(rtn);
            out.close();
        }
    }

}
