
<!--
  A version of fixSomeFields.jsp, kept semi-permanent because it might be useful later.
  On the excel spreadsheets from the SpotaShark team, each location ID is a two-letter code,
  so batch-uploaded encounters have a two-letter location ID. This file replaces those
  two-letter abbreviations (e.g. LN) with "abbreviation - fullname" (e.g. "LN - Location Name")
-->

<!DOCTYPE html PUBLIC "-//W3C//DTD XHTML 1.0 Transitional//EN"
        "http://www.w3.org/TR/xhtml1/DTD/xhtml1-transitional.dtd">
<%@ page contentType="text/html; charset=utf-8" language="java" import="org.joda.time.LocalDateTime,
org.joda.time.format.DateTimeFormatter,
org.joda.time.format.ISODateTimeFormat,java.net.*,java.io.*,java.util.*, java.io.FileInputStream, java.io.File, java.io.FileNotFoundException, org.ecocean.*,org.ecocean.servlet.*,javax.jdo.*, java.lang.StringBuffer, java.util.Vector, java.util.Iterator, java.lang.NumberFormatException"%>

<%

String context="context0";
context=ServletUtilities.getContext(request);

Shepherd myShepherd=new Shepherd(context);

// pg_dump -Ft sharks > sharks.out

//pg_restore -d sharks2 /home/webadmin/sharks.out


%>

<html>
<head>
<title>Fix Location ID</title>

</head>


<body>
<%

myShepherd.beginDBTransaction();

//build queries

Extent encClass=myShepherd.getPM().getExtent(Encounter.class, true);
Query encQuery=myShepherd.getPM().newQuery(encClass);
Iterator<Encounter> allEncs;





Extent sharkClass=myShepherd.getPM().getExtent(MarkedIndividual.class, true);
Query sharkQuery=myShepherd.getPM().newQuery(sharkClass);
Iterator<MarkedIndividual> allSharks;



try{




allEncs=myShepherd.getAllEncounters(encQuery);
allSharks=myShepherd.getAllMarkedIndividuals(sharkQuery);

int numIssues=0;

HashMap<String,String> fullLocationMap = new HashMap<String,String>();
boolean moreLocationIDs=true;
   int siteNum=0;
   String abr; String full;
   while(moreLocationIDs) {
       String currentLocationID = "locationID"+siteNum;
       if (CommonConfiguration.getProperty(currentLocationID,context)!=null) {
    	   full = CommonConfiguration.getProperty(currentLocationID,context);
         abr = full.substring(0,2);
    	   fullLocationMap.put(abr, full);
    	    %>
    	    &quot;<%=abr %>&quot; will be replaced with &quot;<%=(full) %>&quot;. <br />
    	    <%
    	   siteNum++;
       } else {
         moreLocationIDs=false;
       }
  }
   %><br />Modified Encounters:<br/><%

while(allEncs.hasNext()){


	Encounter sharky=allEncs.next();

	try{
  	if(sharky.getLocationID()!=null && sharky.getLocationID().length()>1){
      String locAbr = sharky.getLocationID().substring(0,2);
      if (fullLocationMap.containsKey(locAbr)) {
        String newLocID = fullLocationMap.get(locAbr);
        sharky.setLocationID(newLocID);

        %>
        <%=sharky.getCatalogNumber() %> set location to: <%=newLocID %>. <br />
        <%

        myShepherd.commitDBTransaction();
        myShepherd.beginDBTransaction();

      }
  	}
  }
		catch(Exception e){
			numIssues++;
			%>
			<%=sharky.getCatalogNumber() %> has an issue with locID. <br />
			<%
		}
	}
  myShepherd.commitDBTransaction();
	myShepherd.closeDBTransaction();
	myShepherd=null;

  %>
  <p>Done successfully!</p>
  <p><%=numIssues %> issues found.</p>
  <%
}
catch(Exception ex) {

	System.out.println("!!!An error occurred on page fixLocID.jsp. The error was:");
	ex.printStackTrace();
	//System.out.println("fixSomeFields.jsp page is attempting to rollback a transaction because of an exception...");
	encQuery.closeAll();
	encQuery=null;
	//sharkQuery.closeAll();
	//sharkQuery=null;
	myShepherd.rollbackDBTransaction();
	myShepherd.closeDBTransaction();
	myShepherd=null;

}
%>


</body>
</html>
