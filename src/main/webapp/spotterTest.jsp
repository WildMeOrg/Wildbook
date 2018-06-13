<%@ page contentType="text/html; charset=utf-8" language="java"
     import="org.ecocean.*,
java.util.Map,
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

int resetTime = 1528416000;  //2018-06-08
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



