package org.ecocean.api;

import java.io.IOException;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.ServletException;

import java.util.ArrayList;
import java.util.List;

import org.ecocean.servlet.ServletUtilities;
import org.ecocean.shepherd.core.Shepherd;
import org.ecocean.LocationID;
import org.ecocean.MarkedIndividual;
import org.ecocean.MultiValue;
import org.ecocean.User;
import org.ecocean.Util;
import org.json.JSONArray;
import org.json.JSONObject;

public class MarkedIndividualInfo extends ApiBase {
    protected void doGet(HttpServletRequest request, HttpServletResponse response)
    throws ServletException, IOException {
        String context = ServletUtilities.getContext(request);
        Shepherd myShepherd = new Shepherd(context);

        myShepherd.setAction("api.MarkedIndividualInfo.GET");
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

        String uri = request.getRequestURI();
        String[] args = uri.substring(8).split("/");
        // eg individuals/info/fubar => args
        if (args.length < 3) throw new ServletException("Bad path");
        JSONObject results = new JSONObject("{\"success\": false, \"error\": \"unknown request\"}");

        if (args[0].equals("individuals") && args[1].equals("info")) {
            if (args[2].equals("next_name")) {
                results = getNextNames(myShepherd, request, currentUser);
            }
        }

        myShepherd.rollbackDBTransaction();
        myShepherd.closeDBTransaction();
        response.setStatus(results.optInt("statusCode", 500));
        response.setCharacterEncoding("UTF-8");
        response.setHeader("Content-Type", "application/json");
        response.getWriter().write(results.toString());
    }

    private JSONObject getNextNames(Shepherd myShepherd, HttpServletRequest request, User user) {
        JSONObject rtn = new JSONObject("{\"success\": true, \"statusCode\": 200}");
        List<JSONObject> results = new ArrayList<JSONObject>();
        results.addAll(locationNames(request.getParameterValues("locationId")));
        results.addAll(userNames(myShepherd, user));
        rtn.put("results", new JSONArray(results));
        return rtn;
    }

    private List<JSONObject> locationNames(String[] locationIds) {
        List<JSONObject> rtn = new ArrayList<JSONObject>();
        if ((locationIds == null) || (locationIds.length < 1)) return rtn;
        for (String locationId : locationIds) {
            if (locationId.equals("")) continue;
            JSONObject results = new JSONObject();
            results.put("type", "locationId");
            results.put("value", locationId);
            if (!LocationID.isValidLocationID(locationId)) {
                results.put("success", false);
                results.put("error", "invalid location id");
            } else {
                String locPrefix = LocationID.getPrefixForLocationID(locationId, null);
                results.put("success", true);
                if (Util.stringIsEmptyOrNull(locPrefix)) {
                    results.put("nextName", JSONObject.NULL);
                    results.put("debug", "no prefix for this location");
                } else {
                    int locPad = LocationID.getPrefixDigitPaddingForLocationID(locationId, null);
                    results.put("nextName", MarkedIndividual.nextNameByPrefix(locPrefix, locPad));
                    JSONObject details = new JSONObject();
                    details.put("prefix", locPrefix);
                    details.put("prefixDigitPadding", locPad);
                    results.put("details", details);
                }
            }
            rtn.add(results);
        }
        return rtn;
    }

    // this basically needs no args, i guess? based on code from iaResults.jsp
    private List<JSONObject> userNames(Shepherd myShepherd, User user) {
        List<JSONObject> rtn = new ArrayList<JSONObject>();
        if (user == null) return rtn; // snh
        String nextNameKey = user.getIndividualNameKey();
        if (nextNameKey == null) return rtn;
        // if we have a key, we return something, even if no nextName
        String nextName = MultiValue.nextUnusedValueForKey(nextNameKey, myShepherd);
        JSONObject results = new JSONObject();
        results.put("type", "user");
        results.put("nextNameKey", nextNameKey);
        results.put("success", true);
        if (nextName == null) {
            results.put("nextName", JSONObject.NULL);
        } else {
            results.put("nextName", nextName);
        }
        rtn.add(results);
        return rtn;
    }


/* from matchResults.jsp ...
String projectIdPrefix = request.getParameter("projectIdPrefix");
String researchProjectName = null;
String researchProjectUUID = null;
String nextNameString = "";
// okay, are we going to use an incremental name from the project side?
if (Util.stringExists(projectIdPrefix)) {
	Project projectForAutoNaming = myShepherd.getProjectByProjectIdPrefix(projectIdPrefix.trim());
	if (projectForAutoNaming!=null) {
		researchProjectName = projectForAutoNaming.getResearchProjectName();
		researchProjectUUID = projectForAutoNaming.getId();
		nextNameKey = projectForAutoNaming.getProjectIdPrefix();
		nextName = projectForAutoNaming.getNextIncrementalIndividualId();
		usesAutoNames = true;
		if (usesAutoNames) {
			if (Util.stringExists(nextNameKey)) {
				nextNameString += (nextNameKey+": ");
			}
			if (Util.stringExists(nextName)) {
				nextNameString += nextName;
			}
		}
	}
}
*/

/* 
    private List<JSONObject> keyNames(String[] keys) {
        // this is apparently a thing from encounter.jsp but it seems unused ?
        MultiValue.nextUnusedValueForKey("*",returnString, myShepherd, "%03d");
    }
*/



}
