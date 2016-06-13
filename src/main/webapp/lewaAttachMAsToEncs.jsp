<!DOCTYPE html PUBLIC "-//W3C//DTD XHTML 1.0 Transitional//EN"
        "http://www.w3.org/TR/xhtml1/DTD/xhtml1-transitional.dtd">
<%@ page contentType="text/html; charset=utf-8" language="java" import="org.joda.time.LocalDateTime,
org.joda.time.format.DateTimeFormatter,
org.joda.time.format.ISODateTimeFormat,java.net.*,org.json.JSONObject,org.json.JSONArray,
org.ecocean.grid.*,org.ecocean.media.*,org.ecocean.identity.IBEISIA,
java.io.*,java.util.*, java.io.FileInputStream, java.io.File, java.io.FileNotFoundException, org.ecocean.*,org.ecocean.servlet.*,javax.jdo.*, java.lang.StringBuffer, java.util.Vector, java.util.Iterator, java.lang.NumberFormatException"%>

<%

String context="context0";
context=ServletUtilities.getContext(request);

	Shepherd myShepherd=new Shepherd(context);

// pg_dump -Ft sharks > sharks.out

//pg_restore -d sharks2 /home/webadmin/sharks.out


%>

<html>
<head>
<title>Attach MediaAssets</title>

</head>


<body>
<p>Finding all MediaAssets, then creating Annotations and linking to associated Encounters.</p>
<ul>
<%

myShepherd.beginDBTransaction();

//build queries

int numFixes=0;
String iaURLBase="http://52.37.240.178:5000";

try {

	String rootDir = getServletContext().getRealPath("/");
	String baseDir = ServletUtilities.dataDir(context, rootDir).replaceAll("dev_data_dir", "caribwhale_data_dir");

  Iterator allMedia=myShepherd.getAllMediaAssets();

  boolean committing=false;

  JSONArray originalIDsForReference = new JSONArray();

// This outer loop just prevents us from sending enormous GETs to IA
int maxPerLoop = 100;
while (allMedia.hasNext()) {

  int count = 0
  JSONArray fancyIDsForIA = new JSONArray();

  // build call to get annotations from IA
  while(allMedia.hasNext() && count < maxPerLoop){
    count++;
    MediaAsset ma = (MediaAsset) allMedia.next();
    JSONObject fancyID = toFancyUUID(ma.getUUID());
    fancyIDsForIA.put(fancyID);
    originalIDsForReference.put(ma.getUUID());
  	numFixes++;
  }

  URL iaAnnotsGet = new URL(iaURLBase+"/api/image/annot/uuids/json/?image_uuid_list="+theseFancyIDs.toString());
  JSONObject fromIA = RestClient.get(iaAnnotsGet);

  // list of lists of annotation UUIDs (parallel list to fancyIDsForIA)
  List<List<String>> annotsPerMA = new List<List<String>>(); //TODO: parse fromIA to make annotsPerMA;

  for (int i=0; i<fancyIDsForIA.length(), i++) {
    String maUUID = fancyIDsForIA.getJSONObject(i).getString("__UUID__");
    MediaAsset ma = MediaAssetFactory.loadByUuid(maUUID, myShepherd);

    for (String annotUUID : annotsPerMA.get(i)) {
      // get annotation info from IA.
      String idSuffix = "?__UUID__="annotUUID;// TODO: check that this specifies the annotation correctly to IA

      URL isExemplarServlet = new URL(iaURLBase + "/api/annot/exemplar/flags/json/"+idSuffix);
      JSONObject isExemplarJSON = RestClient.get(isExemplarServlet);
      boolean isExemplar = false; // TODO: parse return of above servlet

      URL bBoxServlet = new URL(iaURLBase + "/api/annot/bboxes/json/"+idSuffix);
      JSONObject bBoxJSON = RestClient.get(isExemplarServlet);
      // TODO: parse bounding box info




    }

  }
}

  if (committing) {
		myShepherd.commitDBTransaction();
		myShepherd.beginDBTransaction();
  }
}
catch (Exception ex) {

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
<p>Done successfully: <%=numFixes %> MediaAssets touched.</p>
</body>
</html>

<%!
  // functions to be used in this .jsp
  public static JSONObject toFancyUUID(String uuid) {
    return IBEISIA.toFancyUUID(uuid);
  }

  public static


  List<JSONArray> splitJSONArray(JSONArray jarr, int maxItemsPerArray) {
    if (maxItemsPerArray < 1) return new List<JSONArray>(); // no possibility of inifinite loop
    List<JSONArray> out = new ArrayList<JSONArray>();
    int i=0;
    int total = jarr.length();
    while (i < total) {
      int k=0;
      JSONArray thisArr = new JSONArray();
      while (k < maxItemsPerArray && i < total) {
        thisArr.put(k, jarr.get(i));
        i++;
        k++;
      }
      out.add(thisArr);
    }
    return out;
  }


%>
