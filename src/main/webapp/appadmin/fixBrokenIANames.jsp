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

    final List<String> crapNames = Arrays.asList(" ____", "[____]", "[____", "____]", "____ ");

    String getIdsStr = "http://40.117.86.232:5002/api/annot/json";
    URL getIdsUrl = new URL(getIdsStr);

    //String getNamesStr = "http://40.117.86.232:5002/api/annot/name/uuid";
    //URL getNamesUrl = new URL(getNamesStr);

    String putNamesStr = "http://40.117.86.232:5002/api/annot/name/text/json";
    URL putNamesUrl = new URL(putNamesStr);

    myShepherd.beginDBTransaction();


    //first call. get ids. 
    HashMap<String,JSONArray> map = new HashMap<String,JSONArray>();
    JSONObject rtnIds = RestClient.get(getIdsUrl);
    System.out.println("HERES THE IDS :"+rtnIds.toString());
    JSONArray idJSONArr = (JSONArray) rtnIds.get("response");

    //map.put("annot_uuid_list", nameJSONArr);


    System.out.println("------------------------------------------------------------------------------------------------------------");
    //int nameNum = nameJSONArr.size();
    //for (int i=0;i<nameNum;i++) {
    //    JSONObject name = nameJSONArr.getJSONObject(i);
    //    map.get("annot_uuid_list").put(name.getString());
    //}
    //map.get("annot_uuid_list").put(name.getString()); 


    //JSONObject sending = new JSONObject(map);
    
    JSONArray nameArr = IBEISIA.iaAnnotationNamesFromUUIDs(idJSONArr, context); 
    
    //System.out.println(sending.toString());	

    //call two. Get all names for these ids.
    //JSONObject rtnNames = RestClient.get(getNamesUrl, sending);
    System.out.println("HERES THE NAMES :"+nameArr.toString());


    // PHASE 3! Iterate through the name array, and if you find one of the names in the crap array, pop it and it's corresponding uuid into 
    // two new arrays for the PUT.
    ArrayList<String> putNames = new ArrayList<>();
    ArrayList putIds = new ArrayList<>();

    //reality check
    int numIds = idJSONArr.length();
    if (idJSONArr.length()==nameArr.length()) {
        for (int i=0; i<numIds;i++) {
            //JSONObject nameJSON = nameArr.getString(i);
            String thisName = nameArr.get(i).toString();
            if (crapNames.contains(thisName)) {
                JSONObject rawId =  idJSONArr.getJSONObject(i);
                String stringId = rawId.toString();
		//Just a nice clean four underscore 
                putNames.add("____");
                putIds.add(rawId);
                System.out.println("BOOM! Caught a crap name. --------> Name: "+thisName+" ---------> toString ACMID: "+stringId);
                //System.out.println("---------------Fancy--------------> "+IBEISIA.toFancyUUID(rawId.get("__UUID__")));
                System.out.println("----------From Fancy--------------> "+IBEISIA.fromFancyUUID(rawId));
                System.out.println("------ Back to Fancy--------------> "+IBEISIA.toFancyUUID(IBEISIA.fromFancyUUID(rawId))); 
           }
        }
    } else {
        System.out.println("ERROR!! Name and uuid arrays are not the smae size.");
    }

    //Do the fixin
    HashMap<String,ArrayList> putMap = new HashMap<String,ArrayList>();
    putMap.put("annot_uuid_list", putIds);
    putMap.put("name_text_list", putNames);

    JSONObject rtnFix = RestClient.put(putNamesUrl, IBEISIA.hashMapToJSONObject(putMap));
    System.out.println("PUT RESULTZ: "+rtnFix.toString());


} catch (Exception e) {
	System.out.println("!!! ----------- > An error occurred fixing broken annotation names on the IA server.");
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
 

