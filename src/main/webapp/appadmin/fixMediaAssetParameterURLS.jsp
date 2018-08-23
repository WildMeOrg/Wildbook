<!DOCTYPE html PUBLIC "-//W3C//DTD XHTML 1.0 Transitional//EN"
        "http://www.w3.org/TR/xhtml1/DTD/xhtml1-transitional.dtd">
<%@ page contentType="text/html; charset=utf-8" language="java" import="org.joda.time.LocalDateTime,
org.joda.time.format.DateTimeFormatter,
org.joda.time.format.ISODateTimeFormat,java.net.*,
org.ecocean.grid.*,org.ecocean.media.*,
java.io.*,java.util.*, java.io.FileInputStream, java.io.File, org.json.JSONObject, java.io.FileNotFoundException, org.ecocean.*,org.ecocean.servlet.*,javax.jdo.*, java.lang.StringBuffer, java.util.Vector, java.util.Iterator, java.lang.NumberFormatException"%>
<%
String context="context0";
context=ServletUtilities.getContext(request);
Shepherd myShepherd=new Shepherd(context);

String committingStr = request.getParameter("committing");
// pg_dump -Ft sharks > sharks.out
//pg_restore -d sharks2 /home/webadmin/sharks.out
%>
<html>
<head>
<title>Fix Asset urls</title>
</head>
<body>

  <h1>FIXING ASSET PARAMETERS</h1>

<ul>
<%
myShepherd.beginDBTransaction();
//build queries
int numFixes=0;
boolean committing = false;
if (committingStr!=null) {
	committing = true;
}
try{
	//String rootDir = getServletContext().getRealPath("/");
	//String baseDir = ServletUtilities.dataDir(context, rootDir);
	Iterator allEncs=myShepherd.getAllEncounters();
  Iterator allMediaAssets=myShepherd.getAllMediaAssets();
while(allMediaAssets.hasNext()){
    System.out.println();
	MediaAsset ma =(MediaAsset)allMediaAssets.next();
  //String url = ma.url;
  int id = ma.getId();
  AssetStore store = ma.getStore();
  //URL oldUrl = store.webURL(ma);

  JSONObject oldParams = ma.getParameters();
  System.out.println("OLD PARAMS? "+oldParams.toString());
  String oldParamsString = oldParams.getString("path");
  if (oldParamsString.contains("/opt/tomcat/")) {
    String newPathString = oldParamsString.replace("opt/tomcat", "var/lib/tomcat8");
    JSONObject newPath = new JSONObject();

    newPath.put("path", newPathString);
    System.out.println("Commiting == "+committing);
    //System.out.println("OLD PARAMS: "+oldParamsString);
    System.out.println("NEW PARAMS: "+newPathString);
    if (committing) {
        ma.setParameters(newPath);
	    System.out.println("SET!");
    }
  }

  %><p>MediaAsset <%=id%> has AssetStore <%=store.toString() %> with params <%=ma.getParametersAsString()%></p><%
/*  if ((url != null) && url.contains("flukebook.org")) {
    String
  };
*/
	if(committing){
		numFixes++;
		myShepherd.commitDBTransaction();
		myShepherd.beginDBTransaction();
	}
}
%>
<%
}
catch(Exception ex) {
	System.out.println("!!!An error occurred on page fixSomeFields.jsp. The error was:");
	ex.printStackTrace();
	myShepherd.rollbackDBTransaction();
}
finally{
	myShepherd.closeDBTransaction();
	myShepherd=null;
}
%>

</ul>
<p>Done successfully: <%=numFixes %></p>
</body>
</html>
