<!DOCTYPE html PUBLIC "-//W3C//DTD XHTML 1.0 Transitional//EN"
        "http://www.w3.org/TR/xhtml1/DTD/xhtml1-transitional.dtd">
<%@ page contentType="text/html; charset=utf-8" language="java" import="org.joda.time.LocalDateTime,
org.joda.time.format.DateTimeFormatter,
org.joda.time.format.ISODateTimeFormat,java.net.*,
java.util.List, java.util.ArrayList,
org.ecocean.grid.*,
org.ecocean.media.MediaAsset,
java.io.*,java.util.*, java.io.FileInputStream, java.io.File, java.io.FileNotFoundException, org.ecocean.*,org.ecocean.servlet.*,javax.jdo.*, java.lang.StringBuffer, java.util.Vector, java.util.Iterator, java.lang.NumberFormatException"%>


<%

String context="context0";
context=ServletUtilities.getContext(request);

Shepherd myShepherd=new Shepherd(context);

%>

<html>
<head>
<title>Fix Standard Children</title>

</head>


<body>
<p>Setting up standardchildren for mediaassets.</p>

<ul>
<%

myShepherd.beginDBTransaction();

int numFixes=0;

int numMozambiqueFixes=0;


boolean committing=false;

Set<String> allLocationIds = new HashSet<String>();
Set<String> allLocationIdsAfter = new HashSet<String>();



%><p>Committing = <%=committing%>.</p><%


try{

	Iterator<Encounter> allEncs=myShepherd.getAllEncounters();
	int count=0;
	int stopAfter=100000;

	while (allEncs.hasNext()){

		Encounter enc = allEncs.next();
		String locID = enc.getLocationID();
		if (!Util.stringExists(locID)) continue;

		allLocationIds.add(locID);
		if (locID.contains("Mozambique.")) {
			numMozambiqueFixes++;
			enc.setLocationID(locID.replace("Mozambique.",""));
		}
		if (locID.contains("EastAustralia.")) {
			enc.setLocationID(locID.replace("EastAustralia.",""));
		}
		// reset locID for iterative fixes
		locID = enc.getLocationID();
		enc.setLocationID(locID.replace("SanSebastian", "San Sebastian"));
		enc.setLocationID(locID.replace("EastAustralia", "East Australia"));
		locID = enc.getLocationID();
		enc.setLocationID(locID.replace("CapeByron", "Cape Byron"));
		allLocationIdsAfter.add(enc.getLocationID());

		count++;
		if (count>stopAfter) break;
	}
	if (committing) {
		%><p>Committing now!</p><%
		myShepherd.commitDBTransaction();
	}
}
catch(Exception e){
	myShepherd.rollbackDBTransaction();
}
finally{
	myShepherd.closeDBTransaction();

}


%>

</ul>
<p>Done successfully: <%=numMozambiqueFixes %> Mozambique fixes</p>
<p>
	Before locId fixes: <ol>
		<%for (String locID: Util.asSortedList(allLocationIds)) {
			%><li><%=locID%></li><%
		}
		%>
	</ol>
</p>
<p>
	After locId fixes: <ol>
		<%for (String locID: Util.asSortedList(allLocationIdsAfter)) {
			%><li><%=locID%></li><%
		}
		%>
	</ol>
</p>


</body>
</html>
