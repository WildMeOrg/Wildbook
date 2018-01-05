<!DOCTYPE html PUBLIC "-//W3C//DTD XHTML 1.0 Transitional//EN"
        "http://www.w3.org/TR/xhtml1/DTD/xhtml1-transitional.dtd">
<%@ page contentType="text/html; charset=utf-8" language="java" import="org.joda.time.LocalDateTime,
org.joda.time.format.DateTimeFormatter,
org.joda.time.format.ISODateTimeFormat,java.net.*,
org.ecocean.grid.*,org.ecocean.movement.*,
java.io.*,java.util.*, java.io.FileInputStream, java.io.File, java.io.FileNotFoundException, org.ecocean.*,org.ecocean.servlet.*,javax.jdo.*, java.lang.StringBuffer, java.util.Vector, java.util.Iterator, java.lang.NumberFormatException"%>

<%
String context="context0";
context=ServletUtilities.getContext(request);

Shepherd myShepherd=new Shepherd(context);

%>

<html>
<head>
<title>New Object Testing</title>

</head>
<body>

<ul>
<%

//PrintWriter out = response.getWriter();
myShepherd.beginDBTransaction();

String dateString = "dateString";
Survey sv = new Survey(dateString);


// Begin Testing for Survey Object!
try {
	String surveyID = Util.generateUUID().toString();
	
    String project = "project";
    String organization = "organization";
    String surveyType = "surveyType";
    String startTime = "03-24-1985";
    String endTime = "08-29-1988";
    
    
    try {
      sv.setID(surveyID);
      sv.setProjectName(project);
      sv.setOrganization(organization);
      sv.setProjectType(surveyType);
      sv.setDate(dateString);
      sv.setStartTimeWithDate(startTime);
      sv.setEndTimeWithDate(endTime);   
      sv.generateID();
    } catch (Exception e) {
      e.printStackTrace();
      out.println("\nChoked on saving Survey Properties!");
    }
    
    myShepherd.beginDBTransaction();
    try {
      myShepherd.storeNewSurvey(sv);
      myShepherd.commitDBTransaction();     
      out.println("\n Success saving new survey!");
      out.println("\n ID = "+sv.getID());
      out.println("\n Project = "+sv.getProjectName());
      out.println("\n Organization = "+sv.getOrganization());
      out.println("\n Type = "+sv.getProjectType());
      out.println("\n Date = "+sv.getDate());
      out.println("\n StartTime? = "+sv.getStartTimeMilli());
      out.println("\n EndTime? = "+sv.getEndTimeMilli());
    } catch (Exception e) {
      e.printStackTrace();
      myShepherd.rollbackDBTransaction();
      out.println("\n Failed to persist new Survey.");
    }  
} catch (Exception e) {
	out.println("\n General Error creating new Survey.");
	myShepherd.rollbackDBTransaction();
	e.printStackTrace();
}

// Begin Testing Survey Track !!
SurveyTrack st = new SurveyTrack(sv.getID());

try {
    String vesselID = "Vessel ID";
    String locationID = "Location ID";
    String type = "Type";
    Measurement distance = new Measurement();
    distance.setUnits("Kilometer");
    distance.setValue(999.0);
    
    try {
      st.setVesselID(vesselID);
      st.setLocationID(locationID);
      st.setType(type);
      st.setDistance(distance);

    } catch (Exception e) {
      e.printStackTrace();
      out.println("\n Choked on saving SurveyTrack Properties!");
    }
    
    myShepherd.beginDBTransaction();
    try {
      myShepherd.storeNewSurveyTrack(st);
      myShepherd.commitDBTransaction();     
      out.println("\n Success saving new SurveyTrack!");
      out.println("\n Parent Survey ID = "+st.getParentSurveyID());
      out.println("\n VesselID = "+st.getVesselID());
      out.println("\n LocationID = "+st.getLocationID());
      out.println("\n Type = "+st.getType());
      out.println("\n Distance = "+st.getDistance().getValue()+", "+st.getDistance().getUnits());
      out.println("\n Created? = "+st.getDateTimeCreated());
      out.println("\n Modified? = "+sv.getDWCDateLastModified());
    } catch (Exception e) {
      e.printStackTrace();
      myShepherd.rollbackDBTransaction();
      out.println("\nFailed to persist new SurveyTrack.");
    }  
} catch (Exception e) {
	out.println("\nGeneral Error creating new SurveyTrack.");
	myShepherd.rollbackDBTransaction();
	e.printStackTrace();
}


finally{
	myShepherd.closeDBTransaction();
}
%>

</ul>


</body>
</html>