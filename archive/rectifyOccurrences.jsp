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

Shepherd myShepherd=new Shepherd(context);
myShepherd.setAction("rectifyOccurrences.jsp");
String occId = request.getParameter("occId");
String commit = request.getParameter("commit");
String limitStr = request.getParameter("limit");
//String filter = "this.genus == 'Lycaon' && this.dwcDateAdded.startsWith('2020-07-13')";
String filter = "this.occurrenceID == '"+occId+"' ";


Collection c = null;
List<Encounter> encs = null;
try {
    Extent encClass = myShepherd.getPM().getExtent(Encounter.class, true);
    Query acceptedEncounters = myShepherd.getPM().newQuery(encClass, filter);
    c = (Collection) (acceptedEncounters.execute());
    encs = new ArrayList<>(c);          
    //int size=c.size();
    acceptedEncounters.closeAll();
        
} catch (Exception e) {
    System.out.println("Exception retrieving encounters...");
    e.printStackTrace();
} 



%>

<html>
<head>
<title>Sending Organization Batch to Detection</title>

</head>

<body>
<ul>
<%

try {


    Occurrence occ = myShepherd.getOccurrence(occId);

    int countUnset = 0;
    int limit = 0;
    System.out.println("Using occurrenceID = "+occId);
    if (occ!=null) {
        for (Encounter enc : encs) {
            if (enc.getOccurrenceID().equals(occId)&&!occ.getEncounters().contains(enc)) {
                limit++;
                if (String.valueOf(limit).equals(limitStr)) break;
                countUnset++;
                System.out.println("Found Encounter with occurenceID, but Occurrence doensn't contain it! ");

                if ("true".equals(commit)) {
                    myShepherd.beginDBTransaction();
                    occ.addEncounter(enc);
                    myShepherd.commitDBTransaction();
                }
            }
        }

        System.out.println("Total rectified Enc/Occurrence connections "+countUnset+".");

    } else {
        System.out.println("This Occurrence was null! Cannot rectify encounter occurrence ID's. ");
    }

} catch (Exception e){
	myShepherd.rollbackDBTransaction();
	e.printStackTrace();
}

System.out.println("Rectify occurrence complete.");

myShepherd.closeDBTransaction();
%>

</ul>

</body>
</html>


