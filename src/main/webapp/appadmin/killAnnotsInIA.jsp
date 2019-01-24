<!DOCTYPE html PUBLIC "-//W3C//DTD XHTML 1.0 Transitional//EN"
        "http://www.w3.org/TR/xhtml1/DTD/xhtml1-transitional.dtd">
<%@ page contentType="text/html; charset=utf-8" language="java" import="org.joda.time.LocalDateTime,
org.joda.time.format.DateTimeFormatter,
org.joda.time.format.ISODateTimeFormat,java.net.*,
org.ecocean.grid.*,org.ecocean.media.*,
org.ecocean.identity.*,
org.ecocean.RestClient,
org.json.JSONObject,
org.json.JSONArray,
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

// Get da ids, get da names with the ids, look for crap names, do put call setting ids with crap names to "____"

// stupid pokemon try 
try {

    String deleteAnnotStr = "http://40.117.86.232:5002/api/annot/";
    URL deleteAnnotUrl = new URL(deleteAnnotStr);

    myShepherd.beginDBTransaction();

    final ArrayList<String> putIds = {"3302","3303","3304","3305","3306","3307","3308","3309","3310","3333","3339"};

    HashMap<String,ArrayList> putMap = new HashMap<String,ArrayList>();
    putMap.put("annot_uuid_list", putIds);

    JSONObject rtnFix = RestClient.delete(deleteAnnotUrl, IBEISIA.hashMapToJSONObject(putMap));
    System.out.println("PUT RESULTZ: "+rtnFix.toString());

} catch (Exception e) {
	System.out.println("!!! ----------- > An error occurred deleting corrupted annotations from IA.");
	e.printStackTrace();
	myShepherd.rollbackDBTransaction();
} finally {
	myShepherd.closeDBTransaction();
	myShepherd=null;
}

%>

</ul>
</body>
</html>
 

