<%@ page contentType="text/plain; charset=utf-8" language="java"
     import="org.ecocean.*,
java.util.List,
java.io.BufferedReader,
java.io.IOException,
java.io.InputStream,
java.io.InputStreamReader,
java.io.File,
org.joda.time.DateTime,
org.json.JSONObject,
org.json.JSONArray,
com.google.api.services.youtube.model.SearchResult,

org.ecocean.media.*
              "
%>




<%

YouTube.init(request);

JSONObject rtn = new JSONObject("{\"success\": false}");

String ytId = request.getParameter("ytId");
String commentText = request.getParameter("commentText");

if (ytId == null) {
	rtn.put("error", "must supply ytId=videoID");
} else if (commentText == null) {
	rtn.put("error", "must supply commentText=");
} else {
	//////something results = YouTube.commentOnVideo(ytId, commentText);
	rtn.put("success", true);
	rtn.put("results", results);  //or similar.... make results into JSONObject basically
}


out.println(rtn);



%>




