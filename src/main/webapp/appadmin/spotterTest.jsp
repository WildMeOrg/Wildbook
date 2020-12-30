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
java.nio.file.Files,
org.json.JSONObject,
org.json.JSONArray,
org.apache.commons.lang3.StringUtils,
org.ecocean.movement.SurveyTrack,
org.ecocean.servlet.ServletUtilities,

org.ecocean.media.*
              "
%>




<%

String context = ServletUtilities.getContext(request);
Shepherd myShepherd = new Shepherd(context);

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

String idString = request.getParameter("id");
if (idString != null) {
    myShepherd.beginDBTransaction();
    FeatureType.initAll(myShepherd);
    int id = Integer.parseInt(idString);
    JSONObject tripData = SpotterConserveIO.getTrip(id);

    String flavor = tripData.optString("_tripFlavor", "__FAIL__");
    if (flavor.equals("ci")) {
        Survey surv = SpotterConserveIO.ciToSurvey(tripData, myShepherd);
        myShepherd.getPM().makePersistent(surv);
        System.out.println("spotterTest: created " + surv.toString());
        out.println("<p><a target=\"_new\" href=\"foo?" + surv.getID() + "\">" + surv + "</a></p><ul>");
        ArrayList<SurveyTrack> tracks = surv.getSurveyTracks();
        if (tracks != null) {
            for (SurveyTrack trk : tracks) {
                out.println("<li>" + trk + "<ul>");
                ArrayList<Occurrence> occs = trk.getOccurrences();
                if (occs == null) {
                    out.println("<li>(no Occurrences)</li>");
                } else {
                    for (Occurrence occ : occs) {
                        out.println("<li>" + occ + "</li>");
                    }
                }
                out.println("</ul></li>");
                out.println("<p>Path: " + trk.getPath() + "</p>");
            }
        }
        out.println("</ul>");

    } else if (flavor.equals("wa")) {
        List<Occurrence> occs = SpotterConserveIO.waToOccurrences(tripData, myShepherd);
        for (Occurrence occ : occs) {
            myShepherd.getPM().makePersistent(occ);
            out.println("<p><a target=\"_new\" href=\"occurrence.jsp?number=" + occ.getID() + "\">[" + occ.getID() + "]</a> " + occ + "</p>");
        }

    } else {
        out.println("<p>ERROR: Unknown trip flavor <b>" + flavor + "</b></p>");
    }

    out.println("<p><hr /></p><xmp style=\"font-size: 0.8em; color: #888;\">" + tripData.toString(1) + "</xmp>");
    myShepherd.commitDBTransaction();
    //myShepherd.rollbackDBTransaction();
    return;
}

long sinceWA = (long)SpotterConserveIO.waGetLastSync(context);
long sinceCI = (long)SpotterConserveIO.ciGetLastSync(context);

out.println("<p><b>Whale Alert</b> last sync: <i>" + sinceWA + "</i> (" + new DateTime(sinceWA * 1000) + ")<br />");
out.println("<b>Channel Island</b> last sync: <i>" + sinceCI + "</i> (" + new DateTime(sinceCI * 1000) + ")</p>");


JSONObject waTripList = null;
JSONObject ciTripList = null;
try {
    waTripList = SpotterConserveIO.waGetTripList(context);
    ciTripList = SpotterConserveIO.ciGetTripList(context);
} catch (Exception ex) {
    out.println("<p class=\"error\">Warning: unable to fetch trip data; threw " + ex.toString() + "</p>");
    return;
}

out.println("<p><b>Whale Alert raw trip data:</b> <xmp style=\"font-size: 0.8em; color: #888;\">" + waTripList.toString(1) + "</xmp></p>");
out.println("<p><b>Channel Island raw trip data:</b> <xmp style=\"font-size: 0.8em; color: #888;\">" + ciTripList.toString(1) + "</xmp></p>");


out.println("<p><b>Whale Alert trips</b> (click to import)<ul>");
JSONArray trips = waTripList.optJSONArray("trips");
for (int i = 0 ; i < trips.length() ; i++) {
    JSONObject t = trips.optJSONObject(i);
    int id = t.optInt("id");
    out.println("<li><a target=\"_new\" href=\"?id=" + id + "\">" + id + "</a> (" + t.optString("start_date") + ")</li>");
}
out.println("</ul>");

out.println("<p><b>Channel Island trips</b> (click to import)<ul>");
trips = ciTripList.optJSONArray("trips");
for (int i = 0 ; i < trips.length() ; i++) {
    JSONObject t = trips.optJSONObject(i);
    int id = t.optInt("id");
    out.println("<li><a target=\"_new\" href=\"?id=" + id + "\">" + id + "</a> (" + t.optString("start_date") + ")</li>");
}
out.println("</ul>");

int resetTime = 1529107200;  //2018-06-16
SpotterConserveIO.waSetLastSync(context, resetTime);
SpotterConserveIO.ciSetLastSync(context, resetTime);




/*
FeatureType.initAll(myShepherd);
File jsonFile = new File("/tmp/example_trip_channel_11194.json");
String json = StringUtils.join(Files.readAllLines(jsonFile.toPath(), java.nio.charset.Charset.defaultCharset()), "");
JSONObject jin = new JSONObject(json);


Object foo = SpotterConserveIO.ciToSurveyTrack(jin);
*/

%>



