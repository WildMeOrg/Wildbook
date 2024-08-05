<%@ page contentType="text/html; charset=utf-8" language="java" import="org.joda.time.LocalDateTime,
org.joda.time.format.DateTimeFormatter,
org.joda.time.format.ISODateTimeFormat,java.net.*,
javax.servlet.jsp.JspWriter,
org.joda.time.DateTime,
java.util.Set,
org.json.JSONObject,
org.ecocean.*,
java.io.*,java.util.*, java.io.FileInputStream, java.io.File, java.io.FileNotFoundException, org.ecocean.*,org.ecocean.servlet.*,javax.jdo.*, java.lang.StringBuffer, java.util.Vector, java.util.Iterator, java.lang.NumberFormatException"%>
<%!


private static void dumpJson(JspWriter out, JSONObject jobj, List<String> path, boolean isSpecies) throws IOException {
	if (jobj == null) return;
	boolean spc = isSpecies;
	for (String key : (Set<String>)jobj.keySet()) {
		String cssClass = "";
		JSONObject kid = jobj.optJSONObject(key);
		if (kid != null) {
			List<String> sub = new ArrayList<String>(path);
			sub.add(key);
			dumpJson(out, kid, sub, kid.has("_detect_conf"));
		} else {
			if (key.startsWith("_")) {
				cssClass = "muted";
			}
			JSONObject next = jobj.optJSONObject(key);
			//out.println("<p> >>> " + jobj + "</p>");
			if (spc) {
				cssClass = "spec";
				spc = false;
			}
			out.println("<span class=\"" + cssClass + "\">" + String.join(".", path) + "." + key + "</span><br />");
		}
	}
	//out.println(jobj);
}

%><style>
body {
	font-family: monospace;
	color: #555;
}
.spec {
	margin-top: 1.5em;
	display: inline-block;
	background-color: #EEE;
	color: blue;
}
			.muted {
				color: #BBB;
			}
</style>
<%

//Shepherd myShepherd = new Shepherd(request);


IAJsonProperties iaConfig = IAJsonProperties.iaConfig();
List<String> path = new ArrayList<String>();
dumpJson(out, iaConfig.getJson(), path, false);


//myShepherd.rollbackDBTransaction();

%>
