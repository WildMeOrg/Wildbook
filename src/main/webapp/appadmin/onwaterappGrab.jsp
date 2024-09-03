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
java.net.URL,
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
    OnWaterApp.init(context);
} catch (Exception ex) {
    out.println("<p class=\"error\">Warning: OnWaterApp.init() threw <b>" + ex.toString() + "</b></p>");
    return;
}

if (!OnWaterApp.hasBeenInitialized()) {
    out.println("<p class=\"error\">Warning: OnWaterApp appears to not have been initialized; failing</p>");
    return;
}

out.println("<p><i>Successful init.</i> Using: <b>" + OnWaterApp.apiUrlPrefix + "</b></p>");


JSONArray list = OnWaterApp.getList(context);
out.println("<textarea style=\"width: 100em; height: 24em;\">" + list.toString(4) + "</textarea><hr /><ol>");

	for (int i = 0 ; i < list.length() ; i++) {
		JSONObject encJson = list.optJSONObject(i);
		if (encJson == null) {
			out.println("<li>JSON failed on " + i + "</li>");
			continue;
		}
		Encounter enc = null;
		try {
			System.out.println(">>> grab trying: " + encJson);
			enc = OnWaterApp.toEncounter(encJson, myShepherd);
			System.out.println(">>> grab created: " + enc);
			out.println("<li><a target=\"_blank\" href=\"/encounters/encounter.jsp?number=" + enc.getId() + "\"><b>success</b></a>: " + enc + "</li>");
		} catch (Exception ex) {
			System.out.println(">>> grab exception: " + ex.toString());
			if (!ex.toString().contains("will not created duplicate")) ex.printStackTrace();
			out.println("<li><b>skip:</b> " + ex.toString() + "</li>");
		}
	}
	out.println("</ol>");



%>



