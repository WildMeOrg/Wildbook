<!DOCTYPE html PUBLIC "-//W3C//DTD XHTML 1.0 Transitional//EN"
        "http://www.w3.org/TR/xhtml1/DTD/xhtml1-transitional.dtd">
<%@ page contentType="text/html; charset=utf-8" language="java" import="org.joda.time.LocalDateTime,
org.joda.time.format.DateTimeFormatter,
org.joda.time.format.ISODateTimeFormat,java.net.*,
org.ecocean.grid.*,org.ecocean.media.*,
org.ecocean.identity.*,
org.ecocean.RestClient,
org.json.JSONObject,
java.io.*,java.util.*,java.net.*, java.io.FileInputStream, java.io.File, java.io.FileNotFoundException, org.ecocean.*,org.ecocean.servlet.*,javax.jdo.*, java.lang.StringBuffer, java.util.Vector, java.util.Iterator, java.lang.NumberFormatException"%>
<%
String context="context0";
context=ServletUtilities.getContext(request);
Shepherd myShepherd=new Shepherd(context);

%>
<html>
<head>
<title>Update</title>
</head>
<body>
  <h1>Update IA Species</h1>
<ul>
<%
try {

    String postUrl = "http://40.117.86.232:5002/api/annot/species/json/";
    URL u = new URL(postUrl);
    String commit = request.getParameter("postUrl");

    myShepherd.beginDBTransaction();

    ArrayList<String> annIdsToChange = new ArrayList<>();
    HashMap<String,ArrayList> map = new HashMap<String,ArrayList>();
    map.put("annot_uuid_list", new ArrayList<String>());
    map.put("species_text_list", new ArrayList<String>());

    Iterator<Annotation> annsIt = myShepherd.getAllAnnotationsNoQuery();
    int count = 0;
    while (annsIt.hasNext()) {
        Annotation ann = annsIt.next();
        if (ann.getAcmId()!=null&&ann.getIAClass()!=null) {
            if (!annIdsToChange.contains(ann.getAcmId())) {
                annIdsToChange.add(ann.getAcmId());
                map.get("annot_uuid_list").add(IBEISIA.toFancyUUID(ann.getAcmId()));
                map.get("species_text_list").add("lynx");
                System.out.println("Changing this ann acmid = "+ann.getAcmId()+"  to species lynx");
                count++;
            }
        }
    }

    JSONObject rtn = RestClient.post(u, IBEISIA.hashMapToJSONObject(map));
    String returnString = rtn.toString();
    System.out.println("RETURN STRING: "+returnString);
} catch (Exception e) {
	System.out.println("!!! ----------- > An error occurred updating annotation species on the IA server.");
	e.printStackTrace();
	myShepherd.rollbackDBTransaction();

} finally {

	myShepherd.closeDBTransaction();
	myShepherd=null;
}

%>

<p>We have found <%=count%> annotations to change.</p>


</ul>
</body>
</html>
