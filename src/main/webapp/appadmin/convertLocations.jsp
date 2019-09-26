<!DOCTYPE html PUBLIC "-//W3C//DTD XHTML 1.0 Transitional//EN"
        "http://www.w3.org/TR/xhtml1/DTD/xhtml1-transitional.dtd">
<%@ page contentType="text/html; charset=utf-8" language="java" import="org.joda.time.LocalDateTime,
org.joda.time.format.DateTimeFormatter,
org.joda.time.format.ISODateTimeFormat,java.net.*,
org.ecocean.grid.*,
org.ecocean.datacollection.*,
java.util.*,
org.ecocean.media.*,
org.ecocean.*,
javax.jdo.*,
java.io.*,java.util.*, java.io.FileInputStream, java.io.File, java.io.FileNotFoundException, org.ecocean.*,org.ecocean.servlet.*,javax.jdo.*, java.lang.StringBuffer, java.util.Vector, java.util.Iterator, java.lang.NumberFormatException"%>

<%
String context="context0";
context=ServletUtilities.getContext(request);

Shepherd myShepherd=new Shepherd(context);
myShepherd.setAction("convertLocations.jsp");
String orgName = request.getParameter("orgName");
String commit = request.getParameter("commit");
String limitStr = request.getParameter("limit");
String filter = "this.submitterOrganization == 'Olive Ridley Project' && this.dwcDateAddedLong >= 1569024000000";
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
<title>Generating Standard Children For Organization</title>

</head>

<body>
<ul>    

<%
final String[] islandNamesArr = {"Vaavu", "Raa", "Meemu", "Laamu", "Haa", "Gaaf", "Ari", "Baa", "Dhaalu", "Thaa"};
List<String> islandNames = Arrays.asList(islandNamesArr);

//int limit = 0;
int count = 0;
try {
    System.out.println("limit = "+limitStr);
    for (Encounter enc : encs) {

        String loc = enc.getLocation();

        String locId = enc.getLocationID();

        System.out.println("Current locId: "+locId+" Current loc: "+loc);

        if (limitStr!=null&&count<Integer.valueOf(limitStr)) {
            if (locId!=null&&islandNames.contains(locId.split(" ")[0])) {
                System.out.println("islandNames matched! First phrase of locID: "+locId.split(" ")[0]);
                if ("true".equals(commit)) {

                    System.out.println("CONVERTING! encID = "+enc.getCatalogNumber());

                    if (!enc.setLocation().equals(enc.setLocationID())) {
                        enc.setLocation(locId);
                        enc.setLocationID(locId.split(" ")[0]);   
                        System.out.println("CONVERTED? loc = "+enc.getLocation()+" locId = "+enc.getLocationID());
                    }

                }
            }
        } else {
            break;
        } 
        count++;
    }

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
