<!DOCTYPE html PUBLIC "-//W3C//DTD XHTML 1.0 Transitional//EN"
        "http://www.w3.org/TR/xhtml1/DTD/xhtml1-transitional.dtd">
<%@ page contentType="text/html; charset=utf-8" language="java" import="org.joda.time.LocalDateTime,
org.joda.time.format.DateTimeFormatter,
org.joda.time.format.ISODateTimeFormat,java.net.*,
org.ecocean.grid.*,
org.ecocean.datacollection.*,
java.util.*,
org.ecocean.media.*,
org.ecocean.ia.*,
org.ecocean.identity.*,
org.ecocean.*,
org.json.JSONObject,
javax.jdo.*,
java.io.*,java.util.*, java.io.FileInputStream, java.io.File, java.io.FileNotFoundException, org.ecocean.*,org.ecocean.servlet.*,javax.jdo.*, java.lang.StringBuffer, java.util.Vector, java.util.Iterator, java.lang.NumberFormatException"%>

<%
String context="context0";
context=ServletUtilities.getContext(request);

myShepherd.setAction("resendEncMedia.jsp");

String encNum = request.getParameter("encNum");
Encounter enc = null;
//List<MediaAsset> mas = new ArrayList<>();
try {

    if (encNum!=null) {
        enc = myShepherd.getEncounter(encNum);
        //mas = enc.getMedia();
    } else {
        System.out.println("Specify an encounter number to send media to detection please.");
    }
        
} catch (Exception e) {
    System.out.println("Exception retrieving encounters...");
    e.printStackTrace();
} 

%>

<html>
<head>
<title>Sending Encounter Media to Detection</title>
</head>

<body>
<ul>
<%

//int limit = 0;
int count = 0;
try {

    enc.refreshAssetFormats(myShepherd);

    for (MediaAsset ma: enc.getMedia()) {
        ma.setDetectionStatus(IBEISIA.STATUS_INITIATED);
    }
    
    Task parentTask = null;
    if (enc.getLocationID() != null) {
    
        parentTask = new Task();
        JSONObject tp = new JSONObject();
        JSONObject mf = new JSONObject();
        mf.put("locationId", enc.getLocationID());
        tp.put("matchingSetFilter", mf);
        parentTask.setParameters(tp);
    
    }

    Task task = org.ecocean.ia.IA.intakeMediaAssets(myShepherd, enc.getMedia(), parentTask); 

    myShepherd.commitDBTransaction();

} catch (Exception e){
	myShepherd.rollbackDBTransaction();
	e.printStackTrace();
}

myShepherd.closeDBTransaction();
%>

</ul>

</body>
</html>