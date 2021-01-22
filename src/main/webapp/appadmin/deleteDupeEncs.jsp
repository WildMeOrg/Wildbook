<!DOCTYPE html PUBLIC "-//W3C//DTD XHTML 1.0 Transitional//EN"
        "http://www.w3.org/TR/xhtml1/DTD/xhtml1-transitional.dtd">
<%@ page contentType="text/html; charset=utf-8" language="java" import="org.joda.time.LocalDateTime,
org.joda.time.format.DateTimeFormatter,
org.joda.time.format.ISODateTimeFormat,java.net.*,
java.util.List, java.util.ArrayList,
org.ecocean.grid.*,
org.ecocean.media.MediaAsset,
java.io.*,java.util.*, java.io.FileInputStream, java.io.File, java.io.FileNotFoundException, org.ecocean.*,org.ecocean.servlet.*, org.ecocean.*,org.ecocean.servlet.importer.*, javax.jdo.*, java.lang.StringBuffer, java.util.Vector, java.util.Iterator, java.lang.NumberFormatException"%>

<%!

	boolean relevantEncounter(Encounter enc, Shepherd myShepherd) {
    // return ("BPC_OvrvwWtd_Fldr161_2020_3_24".equals(enc.getOccurrenceID()));
		// long cutoff = 1610006400000l; // 1/8/2021, PST
		// long submitted = enc.getDWCDateAddedLong();
		// boolean rightTime = (submitted < cutoff);
		// if (!rightTime) return false;
		// yet even if this is old (ie rightTime=true) we might have since run it through the assigner
		// Occurrence occ = myShepherd.getOccurrence(enc);
		// if (occ == null) return false;
		// Long latestDetection = occ.getLatestDateAddedLong();
		// rightTime = latestDetection < cutoff;
		// ImportTask tasky = myShepherd.getImportTaskForEncounter(enc);
		// boolean hasTask = (tasky!=null);
		// boolean noClonesOnOcc = !occ.hasCloneEncounters();
		// return (hasTask && rightTime && noClonesOnOcc);
		return (true);
	}

%>


<%

boolean committing=false;
boolean skipMain = false;

String context="context0";
context=ServletUtilities.getContext(request);

Shepherd myShepherd=new Shepherd(context);



%>

<html>
<head>
<title>Fix Standard Children</title>

</head>


<body>

<ul>
<%

myShepherd.beginDBTransaction();

int numFixes=0;
int numDupes=0;
int numDupesDeleted=0;
int numEncs=0;
int nDirtyOccs =0;
int nCleanOccs =0;
int totalOccs =0;


int count=0;
%><p>Committing = <%=committing%>.</p><%

int stopAfter=20000;
int printPeriod = 1;

Set<String> cleanOccurrenceIds = new HashSet<String>();
Set<String> dirtyOccurrenceIds = new HashSet<String>();

List<String> cleanedOccurrenceIds = new ArrayList<String>();

Set<String> deletedEncIds = new HashSet<String>();
// just to test the filters without doing anything else
int encountersChecked = 0;
int nullOccurrences = 0;


%><p>Resetting annotations with stopAfter=<%=stopAfter%></p>
<%
try{

	System.out.println("dedupeAnnots about to get allEncs");
	List<Encounter> allEncs=myShepherd.getEncountersByField("genus","Lycaon");
	//List<Encounter> allEncs=myShepherd.getEncountersByField("genus","Eubalaena");
	System.out.println("dedupeAnnots just got allEncs");
	numEncs = allEncs.size();

	for (int i=0; i<numEncs; i++){
		Encounter enc = allEncs.get(i);
		String occId = enc.getOccurrenceID();
		if (!Util.stringExists(occId) || cleanOccurrenceIds.contains(occId) || dirtyOccurrenceIds.contains(occId)) continue;
		Occurrence occ = myShepherd.getOccurrence(occId);
		if (occ == null) continue;

		if (occ.hasDuplicateEncounters()) {
			dirtyOccurrenceIds.add(occ.getID());
		}
		else {
			cleanOccurrenceIds.add(occ.getID());
		}

		if (dirtyOccurrenceIds.size() > stopAfter) break;
	}

	nDirtyOccs = dirtyOccurrenceIds.size();
	nCleanOccs = cleanOccurrenceIds.size();
	List<String> dirtyOccIdList = Util.asSortedList(dirtyOccurrenceIds);
	%><p>Done iterating over <%=numEncs%> Encounters. Found <b><%=dirtyOccurrenceIds.size()%> dirty occs</b> and <%=cleanOccurrenceIds.size()%> clean ones.</p><%
	for (int occN=0; occN<nDirtyOccs && numFixes < stopAfter; occN++) {
		String occId = dirtyOccIdList.get(occN);
		Occurrence occ = myShepherd.getOccurrence(occId);
		if (occ == null) {
			nullOccurrences++;
			%><li><b>Null occurrence!! ID = <%=occId%></b></li><%
			continue;
		}
		int nEncsBefore = occ.numEncounters();
		// to iterate while we also delete;
		List<Encounter> encsCopy = new ArrayList<Encounter>(occ.getEncounters());
		%><li>Duplicate encs on occurrence <%=occId%><ul><%

		for (int i=0; i<nEncsBefore; i++) {
			Encounter enc1 = encsCopy.get(i);
			%><li><ul><%
			for (int j=i+1; j<nEncsBefore; j++) {
				Encounter enc2 = encsCopy.get(j);
				if (deletedEncIds.contains(enc2.getID())) continue;
				//System.out.println("i="+i+"j="+j);
				//System.out.println("enc1 = "+enc1.getID()+", enc2="+enc2.getID()); // F YOU DATANUCLEUS somehow these two printouts prevent "access a deleted object" problems
				if (enc1.equalish(enc2)) {
					numDupes++;
					%>
							<li>enc1: <%=enc1.getLongLink(request)%></li>
							<li>enc2: <%=enc2.getLongLink(request)%></li>
					<%
					deletedEncIds.add(enc2.getID());
					occ.removeEncounter(enc2);
					MarkedIndividual mark = myShepherd.getMarkedIndividualQuiet(enc2.getIndividualID());
					if (mark != null) mark.removeEncounter(enc2);
			    ImportTask imp = myShepherd.getImportTaskForEncounter(enc2);
			    if (imp != null) imp.removeEncounter(enc2);
					if (committing) myShepherd.throwAwayEncounterAndAnnots(enc2);
				}
			}
			%></ul></li><%
		}
		%></ul></li><%

		int nEncsAfter = occ.numEncounters();
		int nEncsRemoved = nEncsBefore - nEncsAfter;
		numDupesDeleted += nEncsRemoved;
		if (nEncsRemoved != 0) {
			numFixes++;
			if (committing) myShepherd.updateDBTransaction();

		}
		if (!occ.hasDuplicateEncounters()) {
			cleanedOccurrenceIds.add(occId);
			dirtyOccurrenceIds.remove(occId);
		}
	}

}
catch(Exception e){
	System.out.println("Exception on deleteDupeEncs:");
	e.printStackTrace();
	myShepherd.rollbackDBTransaction();
}
finally{
	myShepherd.closeDBTransaction();
}


%>

</ul>
<p>Done successfully: <%=numEncs %> Encounters</p>
<p>Done successfully: <%=cleanOccurrenceIds.size() %> clean occurrences</p>


<p>Done successfully: <%=numFixes %> modified Occurrences</p>
<p>Done successfully: <%=numDupes %> duplicate Encounters found</p>
<p>Done successfully: <%=numDupesDeleted %> duplicate Encounters removed</p>

</ul></p>

</body>
</html>
