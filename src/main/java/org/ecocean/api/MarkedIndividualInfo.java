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
                results = getNextNames(myShepherd, request);
            }
        }

        myShepherd.rollbackDBTransaction();
        myShepherd.closeDBTransaction();
        response.setStatus(results.optInt("statusCode", 500));
        response.setCharacterEncoding("UTF-8");
        response.setHeader("Content-Type", "application/json");
        response.getWriter().write(results.toString());
    }

    private JSONObject getNextNames(Shepherd myShepherd, HttpServletRequest request) {
        JSONObject rtn = new JSONObject("{\"success\": true, \"statusCode\": 200}");
        List<JSONObject> results = new ArrayList<JSONObject>();
        results.addAll(locationNames(request.getParameterValues("locationId")));
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
/*
        props= ShepherdProperties.getProperties("newIndividualNumbers.properties", "",context);
		System.out.println("Trying to find locationID code");
        //let's see if the property is defined
        if (props.getProperty(lcode) != null) {
          returnString = escapeSpecialRegexChars(props.getProperty(lcode));

          String nextID=MultiValue.nextUnusedValueForKey("*",returnString, myShepherd, "%03d");
*/

}
