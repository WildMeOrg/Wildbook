<%@ page contentType="text/html; charset=utf-8" language="java"
     import="org.ecocean.*,
org.ecocean.servlet.ServletUtilities,
org.ecocean.media.*,
java.util.Map,
java.io.BufferedReader,
java.io.IOException,
java.io.InputStream,
java.io.InputStreamReader,
java.io.File,
org.json.JSONObject,

org.ecocean.media.*
              "
%>




<%

Shepherd myShepherd=null;
myShepherd = new Shepherd("context0");
String rootDir = getServletContext().getRealPath("/");
String baseDir = ServletUtilities.dataDir("context0", rootDir);

String num = request.getParameter("number");
Encounter enc = myShepherd.getEncounter(num);

if (enc == null) {
	out.println("invalid encounter number: " + num);

} else {
	enc.generateAnnotations(baseDir, myShepherd);
	out.println("<a href=\"encounters/encounter.jsp?number=" + num + "\" target=\"_new\">" + num + "</a><p>");
	for (Annotation ann : enc.getAnnotations()) {
		out.println("<hr><p>" + ann.toString() + "</p><p>" + ann.getMediaAsset().toString() + "</p>");
	}
}






%>



<p>
done.
