package org.ecocean.api;

import java.io.IOException;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.ServletException;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.ecocean.Occurrence;
import org.ecocean.social.Relationship;

import org.ecocean.servlet.ServletUtilities;
import org.ecocean.shepherd.core.Shepherd;
import org.ecocean.Encounter;
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
            response.getWriter().write("{\"success\": false, \"statusCode\": 401}");
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
            } else if (args[2].equals("social-data")) {
                results = getSocialData(myShepherd, request, currentUser);
            }
        }

        myShepherd.rollbackDBTransaction();
        myShepherd.closeDBTransaction();
        response.setStatus(results.optInt("statusCode", 500));
        response.setCharacterEncoding("UTF-8");
        response.setHeader("Content-Type", "application/json");
        response.getWriter().write(results.toString());
    }

    private JSONObject getSocialData(Shepherd myShepherd, HttpServletRequest request, User user) {
        JSONObject rtn = new JSONObject();
        rtn.put("success", false);
        String id = request.getParameter("id");
        if (!Util.stringExists(id)) {
            rtn.put("statusCode", 400);
            rtn.put("error", "missing id");
            return rtn;
        }
        MarkedIndividual indiv = myShepherd.getMarkedIndividual(id.trim());
        if (indiv == null) {
            rtn.put("statusCode", 404);
            rtn.put("error", "not found");
            return rtn;
        }
        if (!indiv.canUserView(user, myShepherd)) {
            rtn.put("statusCode", 403);
            rtn.put("error", "access denied");
            return rtn;
        }
        rtn.put("individualId", indiv.getId());
        Map<String, Boolean> encViewCache = new HashMap<String, Boolean>();
        Map<String, String> occCache = new HashMap<String, String>();
        JSONArray encArr = new JSONArray();
        if (indiv.getEncounters() != null) {
            for (Encounter enc : indiv.getEncounters()) {
                if (!encCanView(enc, user, myShepherd, encViewCache)) continue;
                JSONObject e = new JSONObject();
                e.put("catalogNumber", enc.getCatalogNumber());
                Long dim = enc.getDateInMilliseconds();
                if (dim != null) e.put("dateInMilliseconds", dim.longValue());
                e.put("year", enc.getYear());
                e.put("month", enc.getMonth());
                e.put("day", enc.getDay());
                e.put("locationID", enc.getLocationID());
                e.put("occurrenceID", enc.getOccurrenceID());
                e.put("sex", enc.getSex());
                e.put("behavior", enc.getBehavior());
                e.put("alternateid", enc.getAlternateID());
                e.put("dataTypes", computeDataTypes(enc));
                e.put("occurringWith", occurringWith(enc, indiv, user, myShepherd, encViewCache, occCache));
                encArr.put(e);
            }
        }
        rtn.put("encounters", encArr);
        Map<String, Boolean> partnerViewCache = new HashMap<String, Boolean>();
        JSONArray relArr = new JSONArray();
        for (Relationship rel : myShepherd.getAllRelationshipsForMarkedIndividual(indiv.getId())) {
            JSONObject row = relationshipRow(rel, indiv, user, myShepherd, partnerViewCache);
            if (row != null) relArr.put(row);
        }
        rtn.put("relationships", relArr);
        rtn.put("success", true);
        rtn.put("statusCode", 200);
        return rtn;
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


    // ACL-correct co-occurrence: per the spec, a companion is included ONLY if the
    // companion's own encounter in the occurrence passes enc.canUserView — never
    // occurrence-level auth (which admits an occurrence if ANY encounter is viewable).
    private String occurringWith(Encounter enc, MarkedIndividual focal, User user,
        Shepherd myShepherd, Map<String, Boolean> encViewCache, Map<String, String> occCache) {
        String occId = enc.getOccurrenceID();
        if (occId == null) return "";
        String memo = occCache.get(occId);
        if (memo != null) return memo;
        Occurrence occ = enc.getOccurrence(myShepherd);
        String focalId = focal.getId();
        java.util.LinkedHashSet<String> names = new java.util.LinkedHashSet<String>();
        if ((occ != null) && (occ.getEncounters() != null)) {
            for (Encounter coEnc : occ.getEncounters()) {
                MarkedIndividual coInd = coEnc.getIndividual();
                if (coInd == null) continue;
                if ((focalId != null) && focalId.equals(coInd.getIndividualID())) continue;
                if (!encCanView(coEnc, user, myShepherd, encViewCache)) continue;
                String dn = coInd.getDisplayName();
                if (Util.stringExists(dn)) names.add(dn);
            }
        }
        String joined = String.join(", ", names);
        occCache.put(occId, joined);
        return joined;
    }

    private boolean encCanView(Encounter enc, User user, Shepherd myShepherd,
        Map<String, Boolean> cache) {
        String eid = enc.getId();
        if (eid == null) return enc.canUserView(user, myShepherd);
        Boolean cached = cache.get(eid);
        if (cached != null) return cached.booleanValue();
        boolean v = enc.canUserView(user, myShepherd);
        cache.put(eid, v);
        return v;
    }

    // Mirrors the legacy encounter-calls.js dataTypes precedence (ANY annotation counts;
    // tissue type is the static "TissueSample").
    private String computeDataTypes(Encounter enc) {
        List<org.ecocean.genetics.TissueSample> ts = enc.getTissueSamples();
        boolean hasTissue = (ts != null) && !ts.isEmpty();
        List<org.ecocean.Annotation> anns = enc.getAnnotations();
        boolean hasAnn = (anns != null) && !anns.isEmpty();
        if (hasTissue && hasAnn) return "both";
        if (hasTissue) return "TissueSample";
        if (hasAnn) {
            String eventID = enc.getEventID();
            if ((eventID != null) && (eventID.indexOf("youtube") > -1)) return "youtube-image";
            return "image";
        }
        return "";
    }

    private JSONObject relationshipRow(Relationship rel, MarkedIndividual focal, User user,
        Shepherd myShepherd, Map<String, Boolean> partnerViewCache) {
        MarkedIndividual partner = rel.getOtherMarkedIndividual(focal);
        if (partner == null) { // object link not populated; fall back to non-self name
            String focalId = focal.getId();
            String n1 = rel.getMarkedIndividualName1();
            String n2 = rel.getMarkedIndividualName2();
            String otherName = null;
            if ((n1 != null) && !n1.equals(focalId)) otherName = n1;
            else if ((n2 != null) && !n2.equals(focalId)) otherName = n2;
            if (otherName != null) partner = myShepherd.getMarkedIndividual(otherName);
        }
        if (partner == null) return null;
        if (!partnerCanView(partner, user, myShepherd, partnerViewCache)) return null;

        JSONObject r = new JSONObject();
        r.put("_id", relationshipDatastoreId(rel));
        r.put("type", rel.getType());
        r.put("relatedSocialUnitName", rel.getRelatedSocialUnitName());
        r.put("markedIndividualName1", rel.getMarkedIndividualName1());
        r.put("markedIndividualName2", rel.getMarkedIndividualName2());
        r.put("markedIndividualRole1", rel.getMarkedIndividualRole1());
        r.put("markedIndividualRole2", rel.getMarkedIndividualRole2());
        r.put("startTime", rel.getStartTime());
        r.put("endTime", rel.getEndTime());

        JSONObject p = new JSONObject();
        p.put("individualID", partner.getIndividualID());
        p.put("displayName", partner.getDisplayName());
        p.put("nickName", partner.getNickName());
        p.put("alternateid", partner.getAlternateID());
        p.put("sex", partner.getSex());
        p.put("localHaplotypeReflection", partner.getHaplotype());
        r.put("partner", p);
        return r;
    }

    private boolean partnerCanView(MarkedIndividual partner, User user, Shepherd myShepherd,
        Map<String, Boolean> cache) {
        String pid = partner.getId();
        if (pid == null) return partner.canUserView(user, myShepherd);
        Boolean cached = cache.get(pid);
        if (cached != null) return cached.booleanValue();
        boolean v = partner.canUserView(user, myShepherd);
        cache.put(pid, v);
        return v;
    }

    // Emits the DataNucleus datastore-identity string in the legacy `_id` form, so the
    // retained edit/remove buttons (which append "[OID]org.ecocean.social.Relationship")
    // resolve the same row via getObjectById. Verified by relationshipIdRoundTripsThroughGetObjectById.
    private String relationshipDatastoreId(Relationship rel) {
        Object oid = javax.jdo.JDOHelper.getObjectId(rel);
        if (oid == null) return null;
        String s = oid.toString();
        int idx = s.indexOf("[OID]");
        return (idx > -1) ? s.substring(0, idx) : s;
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
