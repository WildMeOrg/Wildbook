<%@ page contentType="text/html; charset=utf-8" language="java"
import="org.ecocean.*,
    java.util.ArrayList,
    java.util.List,
    java.io.BufferedReader,
    java.io.IOException,
    java.io.InputStream,
    java.io.InputStreamReader,
    org.joda.time.DateTime,
    java.io.File,
    java.util.Collection,
    java.nio.file.Files,
    org.json.JSONObject,
    org.json.JSONArray,
    javax.jdo.Query,
    org.apache.commons.lang3.StringUtils,
    org.ecocean.movement.SurveyTrack,
    org.ecocean.servlet.ServletUtilities,

    org.ecocean.media.*
    "
    %>

    <%!

    private static void fetchLog(String msg) {
        System.out.println("[spotterFetch " + (new DateTime()).toString().substring(0,19) + "] " + msg);
    }

private static String niceDate(int t) {
    return (new DateTime((long)t * 1000L)).toString().substring(0,19);
}

private static String tripListSummary(JSONObject s) {
    if ((s == null) || (s.optJSONArray("trips") == null)) return "no trip data in list";
    return s.getJSONArray("trips").length() + " trips in list";
}

//probably move this into SpotterIO
private static Occurrence loadByTripId(Shepherd myShepherd, int tripId) {
    String jdo = "SELECT FROM org.ecocean.Occurrence WHERE source=='SpotterConserveIO:ci:" + tripId;
    jdo += "' || source=='SpotterConserveIO:wa:" + tripId + "'";
    jdo += " || source=='SpotterConserveIO:cw:" + tripId + "'";
    Query q = myShepherd.getPM().newQuery(jdo);
    Collection results = (Collection)q.execute();
    if (results.size() < 1) return null;
    return (Occurrence)results.iterator().next();
}

private static String justSomeTrips(String[] tripIds, Shepherd myShepherd) {
    if ((tripIds == null) || (tripIds.length < 1)) return "<p>no tripIds passed to justSomeTrips()</p>";
    String rtn = "<p>specific trip IDs: <b>" + String.join(", ", tripIds) + "</b></p>";
    rtn += "<p>Do trips already exist?<ul>";
    List<Integer> intIds = new ArrayList<Integer>();
    for (int i = 0 ; i < tripIds.length ; i++) {
        int id = -1;
        try {
            id = Integer.parseInt(tripIds[i]);
        } catch (NumberFormatException ex) {}
        intIds.add(id);
        Occurrence occ = loadByTripId(myShepherd, id);
        if (occ != null) {
            rtn += "<li class=\"exists\"><b>" + id + "</b> exists: <a href=\"occurrence.jsp?number=" + occ.getOccurrenceID() + "\">Occ " + occ.getOccurrenceID() + "</a></li>";
        } else {
            rtn += "<li><b>" + id + "</b> does not exist</li>";
        }
    }
    rtn += "</ul></p><p><b>Creation results:</b><ul>";

    for (Integer id : intIds) {
        JSONObject tripData = null;
        try {
            tripData = SpotterConserveIO.getTrip(id);
        } catch (Exception ex) {
            fetchLog("getTrip(" + id + ") threw " + ex.toString());
            rtn += "<li>exception getting trip id=" + id + ": " + ex.toString() + "</li>";
            continue;
        }
        Object res = doImport(myShepherd, tripData);
        if (res == null) {
            rtn += "<li><i>null for trip id=" + id + "</i></li>";
        } else {
            String flavor = tripData.optString("_tripFlavor", "UNKNOWN");
            rtn += "<li>trip id=<b>" + id + "</b> (flavor=" + flavor + ") -> " + res + "</li>";
        }
    }
    rtn += "</ul></p>";

    return rtn;
}

    //  here j => {"end_date":"2019-05-10 16:51:00+00:00","creator":"whaleWebAK","id":22120,"create_date":"2019-05-10 16:51:00+00:00","start_date":"2019-05-10 16:51:00+00:00"}
    private static String tryTrip(Shepherd myShepherd, JSONObject j, String flavor) {
        if (j == null) return "<li>tryTrip NULL input</li>";
        int id = j.optInt("id", -1);
        if (id < 0) return "<li>tryTrip invalid id=" + id + "</li>";
        Occurrence occ = loadByTripId(myShepherd, id);
        if (occ != null) {
            return "<li class=\"exists\"><b>" + id + "</b> (" + j.optString("start_date") + ") exists: <a href=\"occurrence.jsp?number=" + occ.getOccurrenceID() + "\">Occ " + occ.getOccurrenceID() + "</a></li>";
        } else {
            JSONObject tripData = null;
            try {
                tripData = SpotterConserveIO.getTrip(id);
            } catch (Exception ex) {
                fetchLog("getTrip(" + id + ") threw " + ex.toString());
                return "<li>exception getting trip id=" + id + " (flavor " + flavor + "): " + ex.toString() + "</li>";
            }
            Object res = doImport(myShepherd, tripData);
            if (res == null) return "<li><i>null for trip id=" + id + " (flavor " + flavor + ")</i></li>";
            return "<li>trip id=<b>" + id + "</b> (" + flavor + ") -> " + res + "</li>";
        }
    }


    private static Object doImport(Shepherd myShepherd, JSONObject tripData) {
        if (tripData == null) return null;
        String flavor = tripData.optString("_tripFlavor", "__FAIL__");
        String tripId = tripData.optString("_tripId", "(unknown id)");  //note: this could safely be an int, i suppose
        fetchLog("doImport() trip.id=" + tripId + " (flavor=" + flavor + ")");
        if (flavor.equals("ci")) {

            Survey surv = SpotterConserveIO.ciToSurvey(tripData, myShepherd);
            myShepherd.getPM().makePersistent(surv);
            fetchLog("spotterFetch: created " + surv.toString());
            ArrayList<SurveyTrack> tracks = surv.getSurveyTracks();
            if (tracks != null) {
                for (SurveyTrack trk : tracks) {
                    fetchLog("- " + trk);
                    ArrayList<Occurrence> occs = trk.getOccurrences();
                    if (occs == null) {
                        fetchLog("- no Occurrences");
                    } else {
                        for (Occurrence occ : occs) {
                            myShepherd.getPM().makePersistent(occ);
                            fetchLog("- " + occ);
                        }
                    }
                    fetchLog("- " + ((trk.getPath() == null) ? "no path" : trk.getPath()));
                }
            } else {
                fetchLog("- no tracks");
            }
            myShepherd.getPM().makePersistent(surv);
            return surv;

        } else if (flavor.equals("wa")) {
            List<Occurrence> occs = SpotterConserveIO.waToOccurrences(tripData, myShepherd);
            if (occs == null) {
                fetchLog("- no Occurrences");
                return null;
            }
            for (Occurrence occ : occs) {
                myShepherd.getPM().makePersistent(occ);
                fetchLog("- " + occ);
            }
            return occs;

        } else if (flavor.equals("cw")) {
            Survey surv = SpotterConserveIO.cwToSurvey(tripData, myShepherd);
            myShepherd.getPM().makePersistent(surv);
            fetchLog("spotterFetch: created " + surv.toString());
            ArrayList<SurveyTrack> tracks = surv.getSurveyTracks();
            if (tracks != null) {
                for (SurveyTrack trk : tracks) {
                    fetchLog("- " + trk);
                    ArrayList<Occurrence> occs = trk.getOccurrences();
                    if (occs == null) {
                        fetchLog("- no Occurrences");
                    } else {
                        for (Occurrence occ : occs) {
                            myShepherd.getPM().makePersistent(occ);
                            fetchLog("- " + occ);
                        }
                    }
                    fetchLog("- " + ((trk.getPath() == null) ? "no path" : trk.getPath()));
                }
            } else {
                fetchLog("- no tracks");
            }
            myShepherd.getPM().makePersistent(surv);
            return surv;

        } else {
            fetchLog("ERROR: unknown flavor " + flavor);
        }
        return null;
    }

    %>


        <%

        String context = ServletUtilities.getContext(request);
    Shepherd myShepherd = new Shepherd(context);
    myShepherd.beginDBTransaction();
    FeatureType.initAll(myShepherd);

    try {
        SpotterConserveIO.init(context);
    } catch (Exception ex) {
        out.println("<p class=\"error\">Warning: SpotterConserveIO.init() threw <b>" + ex.toString() + "</b></p>");
        return;
    }

    if (!SpotterConserveIO.hasBeenInitialized()) {
        out.println("<p class=\"error\">Warning: SpotterConserveIO appears to not have been initialized; failing</p>");
        return;
    }

    out.println("<p><i>Successful init.</i> Using: <b>" + SpotterConserveIO.apiUrlPrefix + "</b></p>");

    //this is a little short-circuit to do just specific trip(s)
    String[] tripIds = request.getParameterValues("tripId");
    if ((tripIds != null) && (tripIds.length > 0)) {
        out.println(justSomeTrips(tripIds, myShepherd));
    return;
}

Integer since = null;
try {
    since = Integer.parseInt(request.getParameter("since"));
} catch (NumberFormatException ex) {}

fetchLog("INIT.  passed since=" + since);

int sinceWA = SpotterConserveIO.waGetLastSync(context);
int sinceCI = SpotterConserveIO.ciGetLastSync(context);
if (since != null) {
    sinceWA = since;
    sinceCI = since;
}

out.println("<p><b>Whale Alert</b> last sync: <i>" + sinceWA + "</i> (" + niceDate(sinceWA) + ")<br />");
out.println("<b>Channel Island</b> last sync: <i>" + sinceCI + "</i> (" + niceDate(sinceCI) + ")</p>");


fetchLog("using sinceWA=" + sinceWA + " (" + niceDate(sinceWA) + "), and sinceCI=" + sinceCI + " (" + niceDate(sinceCI) + ")");


JSONObject waTripList = null;
JSONObject ciTripList = null;
try {
    waTripList = SpotterConserveIO.waGetTripListSince(sinceWA);
    ciTripList = SpotterConserveIO.ciGetTripListSince(sinceCI);
} catch (Exception ex) {
    out.println("<p class=\"error\">Warning: unable to fetch trip data; threw " + ex.toString() + "</p>");
    fetchLog("error fetching data: " + ex.toString());
    ex.printStackTrace();
    return;
}


/*
out.println("<p><b>Whale Alert raw trip data:</b> <xmp style=\"font-size: 0.8em; color: #888;\">" + waTripList.toString(1) + "</xmp></p>");
out.println("<p><b>Channel Island raw trip data:</b> <xmp style=\"font-size: 0.8em; color: #888;\">" + ciTripList.toString(1) + "</xmp></p>");
*/

out.println("<p>WA: <b>" + tripListSummary(waTripList) + "</b><br />");
out.println("CI: <b>" + tripListSummary(ciTripList) + "</b></p>");

fetchLog("fetched: " + tripListSummary(waTripList));
fetchLog("fetched: " + tripListSummary(ciTripList));

out.println("<p><b>Whale Alert trips</b><ul>");
JSONArray trips = waTripList.optJSONArray("trips");
for (int i = 0 ; i < trips.length() ; i++) {
    String msg = tryTrip(myShepherd, trips.optJSONObject(i), "wa");
    out.println(msg);
}
out.println("</ul>");

out.println("<p><b>Channel Island trips</b><ul>");
trips = ciTripList.optJSONArray("trips");
for (int i = 0 ; i < trips.length() ; i++) {
    String msg = tryTrip(myShepherd, trips.optJSONObject(i), "ci");
    out.println(msg);
}
out.println("</ul>");


fetchLog("*** finished");
myShepherd.commitDBTransaction();

//set "last sync" time to now....
SpotterConserveIO.waSetLastSync(context);
SpotterConserveIO.ciSetLastSync(context);



%>



