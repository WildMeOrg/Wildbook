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
org.ecocean.ia.plugin.WildbookIAM,
java.io.*,java.util.*, java.io.FileInputStream, java.io.File, java.io.FileNotFoundException, org.ecocean.*,org.ecocean.servlet.*,javax.jdo.*, java.lang.StringBuffer, java.util.Vector, java.util.Iterator, java.lang.NumberFormatException"%>

<%
String context="context0";
context=ServletUtilities.getContext(request);

Shepherd myShepherd=new Shepherd(context);
myShepherd.setAction("migrateObserver.jsp");
String orgName = request.getParameter("orgName");
String commit = request.getParameter("commit");
String limitStr = request.getParameter("limit");
String filter = "this.submitterID == 'ORP'";
//String filter = "this.submitterID == 'ORP' && this.catalogNumber == 'f6020b8a-1eae-47f4-9a9d-a4de76ac816f' ";
if (orgName!=null) {
    filter = "this.submitterOrganization == '"+orgName+"' ";
}
Collection c = null;
List<Encounter> encs = null;
try {
    Extent encClass = myShepherd.getPM().getExtent(Encounter.class, true);
    Query acceptedEncounters = myShepherd.getPM().newQuery(encClass, filter);
    acceptedEncounters.setRange(0,30);
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
<title>Migrate Observer to Additional Comments</title>

</head>

<body>
<ul>
<%

int limit = 5;
int count = 0;
try {

    System.out.println("got "+encs.size()+" to process");
	ArrayList<MediaAsset> allMas = new ArrayList<MediaAsset>();
        for (Encounter enc : encs) {
            if (count<limit) {
                Occurrence occ = myShepherd.getOccurrence(enc);
                if (occ!=null&&Util.stringExists(occ.getObserver())) {
                    String observer = occ.getObserver();
                    String occRemarks = enc.getOccurrenceRemarks();
                    String newRemarks = "Observer: "+observer;
                    if (occRemarks!=null) {
                        newRemarks += " Remarks: "+occRemarks;
                    }
    
                    System.out.println("EncNum "+enc.getId()+" New Comments: "+newRemarks);
    
                    if ("true".equals(commit)) {
                        enc.setOccurrenceRemarks(newRemarks);
                        myShepherd.updateDBTransaction();
                    }
                }
                count++;
                System.out.println("count at "+count);
            }
        }
} catch (Exception e){
	myShepherd.rollbackDBTransaction();
	e.printStackTrace();
}

myShepherd.closeDBTransaction();
%>

</ul>

</body>
</html>
