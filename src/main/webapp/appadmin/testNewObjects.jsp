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

Iterator<Encounter> encs = myShepherd.getAllEncounters();
while (encs.hasNext()) {	
	Encounter enc = encs.next();
	System.out.println(enc.toString());
}
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
      out.println("\n EndTime? = "+sv.getEndTimeMilli());
      out.println("\n StartTime? = "+sv.getStartTimeMilli());
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

// Testing path and PointLocation
PointLocation pl = new PointLocation(44.000000,-44.000000); 
try {
	Long milli = new Long(123456783291012L);
	pl.setBearing(-99.000000);
	pl.setDateTimeInMilli(milli);	
} catch (Exception e) {
	out.println("Failed to set PointLocation attributes.");
	e.printStackTrace();
}
try {
	myShepherd.beginDBTransaction();
	myShepherd.storeNewPointLocation(pl);
	myShepherd.commitDBTransaction();
} catch (Exception e) {
	myShepherd.rollbackDBTransaction();
	e.printStackTrace();
	out.println("Failed to persist PointLocation.");
}
Path pth = null;
try {
	pth = new Path(pl);
	myShepherd.beginDBTransaction();																																																									
	myShepherd.storeNewPath(pth);
	myShepherd.commitDBTransaction();	
} catch (Exception e) {
	e.printStackTrace();
	myShepherd.rollbackDBTransaction();
}
try {
	out.println("Point Location Bearing :"+pl.getBearing().toString());
	out.println("Point Location ID :"+pl.getID().toString());
	out.println("Point Location :"+pl.getDateTimeInMilli().toString());
	pth.addPointLocation(pl);	
} catch (Exception e) {
	out.println("General Exception while trying to create Path with PointLocation.");
	e.printStackTrace();
}

try {
	out.println("\n Path ID = "+pth.getID());
	out.println("\n PointLocationID = "+pl.getID());
	out.println("\n PointLocation ID FROM Path = "+pth.getPointLocation(pl.getID()).getID());
	out.println("\n Pointlocation Milli Time = "+pl.getDateTimeInMilli());
} catch (Exception e) {
	out.println("General Exception while trying to print Path/PointLocation attributes.");
	e.printStackTrace();
}

try {
	ArrayList<PointLocation> locArr = new ArrayList<PointLocation>();
	int i = 5;
	while (i>0) {
		PointLocation pla = new PointLocation(10.0003507,-15.0005609);
		try {
			myShepherd.beginDBTransaction();																																																									
			myShepherd.storeNewPointLocation(pla);
			myShepherd.commitDBTransaction();	
			out.println("This path now contains "+locArr.size()+" PointLocations");
		} catch (Exception e) {
			e.printStackTrace();
			myShepherd.rollbackDBTransaction();
		}
		locArr.add(pla);
		i = i - 1;
	}
	pth.addPointLocationsArray(locArr);
	out.println("This path now contains "+locArr.size()+" PointLocations");
	
} catch (Exception e) {
	e.printStackTrace();
	out.println("Exception adding array of PointLocations to path.");
}



out.close();
myShepherd.closeDBTransaction();
%>

</ul>


</body>
</html>