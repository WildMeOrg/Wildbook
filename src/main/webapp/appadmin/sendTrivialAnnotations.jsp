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
myShepherd.setAction("generateStandardChildren.jsp");
String orgName = request.getParameter("orgName");
String commit = request.getParameter("commit");
String limitStr = request.getParameter("limit");
String filter = "this.submitterOrganization == 'Olive Ridley Project' && this.dwcDateAdded.startsWith('2019-08-19') ";
if (orgName!=null) {
    filter = "this.submitterOrganization == '"+orgName+"' ";
}
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

System.out.println("-------------------> Specified organization is "+orgName+" commit="+commit);
System.out.println("-----------------> Got "+encs.size()+" encs as candidates...");


%>

<html>
<head>
<title>Sending Organization Batch to Detection</title>

</head>

<body>
<ul>
<%

//int limit = 0;
int count = 0;
int countTrivial = 0;
int countSent = 0;
try {
    System.out.println("limit = "+limitStr);
    for (Encounter enc : encs) {

        System.out.println("count = "+count);

        if (limitStr!=null&&count<Integer.valueOf(limitStr)) {
            
            for (MediaAsset ma : enc.getMedia()) {
                List<Annotation> anns = ma.getAnnotations();
                if (anns.size()<2 && anns.get(0).isTrivial()) {
                    countTrivial++;
                } else {
                    countSent++;
                }
            }

            if ("true".equals(commit)) {
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
                System.out.println("Sent encounter # "+enc.getCatalogNumber()+" to detection...");
            }
            count++;
        } else {
            break;
        }   
    }

    System.out.println("There were "+countTrivial+" trivial annotations and "+countSent+" non-trivial.");

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