<!DOCTYPE html PUBLIC "-//W3C//DTD XHTML 1.0 Transitional//EN"
        "http://www.w3.org/TR/xhtml1/DTD/xhtml1-transitional.dtd">
<%@ page contentType="text/html; charset=utf-8" language="java" import="org.joda.time.LocalDateTime,
org.joda.time.format.DateTimeFormatter,
org.ecocean.identity.IBEISIA,
org.json.JSONObject,
org.joda.time.format.ISODateTimeFormat,java.net.*,
org.ecocean.grid.*,org.ecocean.media.*,
java.io.*,java.util.*, java.io.FileInputStream, java.io.File, java.io.FileNotFoundException, org.ecocean.*,org.ecocean.servlet.*,javax.jdo.*, java.lang.StringBuffer, java.util.Vector, java.util.Iterator, java.lang.NumberFormatException"%>
<%

String context="context0";
context=ServletUtilities.getContext(request);

Shepherd myShepherd=new Shepherd(context);


%>

<html>
<head>
<title>add filenames</title>

</head>


<body>


<ul>
<%

myShepherd.beginDBTransaction();

//build queries

try {

Query q = myShepherd.getPM().newQuery("SELECT FROM org.ecocean.media.MediaAsset WHERE parentId == null ORDER BY id RANGE 4400,4510");
//q.setOrdering("id");
Collection all = (Collection) (q.execute());


int count = 0;
for (Object o : all) {
	if (count > 5) break;
	MediaAsset ma = (MediaAsset)o;
	String path = IBEISIA.iaFilepathFromImageUUID(ma.getUUID());
	if (path != null) {
		JSONObject p = ma.getParameters();
		if (p.optString("iaSourcePath", null) != null) continue;
		p.put("iaSourcePath", path);
		out.println(" <span style=\"color: #888\">" + p.toString() + "</span>");
		ma.setParameters(p);
	}
	count++;
	out.println("<li>" + ma.getUUID() + " - " + ma + "<br /><b>" + path + "</b></li>");
}



	myShepherd.commitDBTransaction();

} catch(Exception ex) {

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
</body>
</html>
