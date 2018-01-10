<!DOCTYPE html PUBLIC "-//W3C//DTD XHTML 1.0 Transitional//EN"
        "http://www.w3.org/TR/xhtml1/DTD/xhtml1-transitional.dtd">
<%@ page contentType="text/html; charset=utf-8" language="java" import="org.joda.time.LocalDateTime,
org.joda.time.format.DateTimeFormatter,
org.joda.time.format.ISODateTimeFormat,java.net.*,
org.ecocean.grid.*,
java.io.*,java.util.*, java.io.FileInputStream, java.io.File, java.io.FileNotFoundException, org.ecocean.*,org.ecocean.servlet.*,org.ecocean.media.*,javax.jdo.*, java.lang.StringBuffer, java.util.Vector, java.util.Iterator, java.lang.NumberFormatException"%>

<%

String context="context0";
context=ServletUtilities.getContext(request);

	Shepherd myShepherd=new Shepherd(context);

// pg_dump -Ft inds > inds.out

//pg_restore -d inds2 /home/webadindn/inds.out


%>


<%!

  // just some helper functions to keep things clean
  public boolean needsDWCDatesFixed(Encounter enc) {
    return false;
  }
  public void fixDWCDates(Encounter enc) {
    // ???
  }
  public boolean needsIndividualIDFix(Encounter enc) {
    return (enc.getIndividualID()!=null)&&(enc.getIndividualID().toLowerCase().trim().equals("unassigned"));
  }

%>

<html>
<head>
<title>Update data fields</title>

</head>


<body>
<p>Updating every enc.dwcDateAdded and MarkedIndividual.individualID.</p>
<ul>
<%

myShepherd.beginDBTransaction();

//build queries

Extent encClass=myShepherd.getPM().getExtent(Encounter.class, true);
Query encQuery=myShepherd.getPM().newQuery(encClass);
Iterator allEncs;

Extent indClass=myShepherd.getPM().getExtent(MarkedIndividual.class, true);
Query indQuery=myShepherd.getPM().newQuery(indClass);
Iterator allInds;


int numFixes=0;
String maSetId = "603fd5ce-dbbf-4025-ba25-670219d043b6";

boolean committing = false;

try {

  allEncs=myShepherd.getAllEncounters(encQuery);
  allInds=myShepherd.getAllMarkedIndividuals(indQuery);

  int numFixedEncIndIds = 0;
  int numDWCDateFixed = 0;
  int numDWCDateLongFixed = 0;
  boolean encChanged = false;
  while (allEncs.hasNext()) {
    Encounter enc = (Encounter) allEncs.next();
    encChanged = false;
    if(needsIndividualIDFix(enc)){
      if (committing) enc.setIndividualID(null);
      encChanged = true;
      numFixedEncIndIds++;
      numFixes++;
    }
    if (needsDWCDatesFixed(enc)) {
      encChanged = true;
      fixDWCDates(enc);
      numDWCDateFixed++;
      numFixes++;
    }
    if (committing && encChanged) {
      myShepherd.commitDBTransaction();
      myShepherd.beginDBTransaction();
    }
  }
  %><p>Number fixed Encounter.IndividualID:<%=numFixedEncIndIds%></p><%
  %><p>numDWCDateFixed:<%=numDWCDateFixed%></p><%

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
<p>Done successfully</p>
</body>
</html>
