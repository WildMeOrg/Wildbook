<%@ page contentType="application/json; charset=utf-8" language="java" import="org.joda.time.LocalDateTime,
org.joda.time.format.DateTimeFormatter,
org.joda.time.format.ISODateTimeFormat,java.net.*,
org.ecocean.*,org.ecocean.ia.*,java.util.*,
java.io.*,java.util.*, java.io.FileInputStream, 
org.datanucleus.api.rest.orgjson.JSONArray,
org.datanucleus.api.rest.orgjson.JSONException,
org.datanucleus.api.rest.orgjson.JSONObject,
org.datanucleus.api.rest.RESTUtils,
org.datanucleus.ExecutionContext,
java.lang.reflect.Method,
org.datanucleus.api.jdo.JDOPersistenceManager,
org.ecocean.media.*,
org.ecocean.cache.*,
java.util.zip.GZIPOutputStream,
java.io.File, java.io.FileNotFoundException, org.ecocean.*,org.ecocean.servlet.*,javax.jdo.*, java.lang.StringBuffer, java.util.Vector, java.util.Iterator, java.lang.NumberFormatException"%>

<%

String context = ServletUtilities.getContext(request);
String langCode = ServletUtilities.getLanguageCode(request);

//confirm no match and set next automatic name
JSONObject rtn = new JSONObject("{\"success\": false}");
if (request.getParameter("encId")!=null && request.getParameter("noMatch")!=null) {
	


	String encId = request.getParameter("encId");
	Shepherd myShepherd = new Shepherd(request);
	myShepherd.setAction("iaResultsNoMatch.jsp");
	myShepherd.beginDBTransaction();
	try {
		
		User user = myShepherd.getUser(request);
		String nextNameKey = (user!=null) ? user.getIndividualNameKey() : null;
		boolean usesAutoNames = Util.stringExists(nextNameKey);
		String nextName = (usesAutoNames) ? MultiValue.nextUnusedValueForKey(nextNameKey, myShepherd) : null;
		System.out.println("nextName: "+nextName+"; useAutoNames: "+usesAutoNames+";nextNameKey: "+nextNameKey);
		String projectIdPrefix = request.getParameter("projectIdPrefix");
		String researchProjectName = null;
		String researchProjectUUID = null;
		String nextNameString = "";
		// okay, are we going to use an incremental name from the project side?
		if (Util.stringExists(projectIdPrefix)) {
			Project projectForAutoNaming = myShepherd.getProjectByProjectIdPrefix(projectIdPrefix.trim());
			if (projectForAutoNaming!=null) {
				researchProjectName = projectForAutoNaming.getResearchProjectName();
				researchProjectUUID = projectForAutoNaming.getId();
				nextNameKey = projectForAutoNaming.getProjectIdPrefix();
				nextName = projectForAutoNaming.getNextIncrementalIndividualId();
				usesAutoNames = true;
				if (usesAutoNames) {
					if (Util.stringExists(nextNameKey)) {
						nextNameString += (nextNameKey+": ");
					}
					if (Util.stringExists(nextName)) {
						nextNameString += nextName;
					}
				}
			}
		}

		
		Encounter enc = myShepherd.getEncounter(encId);
		if (enc==null) {
			rtn.put("error", "could not find Encounter "+encId+" in the database.");
			out.println(rtn.toString());
			myShepherd.rollbackDBTransaction();
			myShepherd.closeDBTransaction();
			return;
		}
		else if(!ServletUtilities.isUserAuthorizedForEncounter(enc, request)){
			rtn.put("error", "User unauthorized for encounter: " + request.getParameter("number"));
			out.println(rtn.toString());
			myShepherd.rollbackDBTransaction();
			myShepherd.closeDBTransaction();
			return;
		}

		String useNextProjectId = request.getParameter("useNextProjectId");
		boolean validToName = false;

		System.out.println("useNextProjectId= "+useNextProjectId+" Util.stringExists(nextNameKey)="+Util.stringExists(nextNameKey)+"  Util.stringExists(nextName)= "+Util.stringExists(nextName)+"");

		if (("true".equals(useNextProjectId) || Util.stringExists(nextNameKey) ) && Util.stringExists(nextName)) {
			validToName = true;
		}

		if (!validToName) {
			rtn.put("error", "Was unable to set the next automatic name. Got key="+nextNameKey+" and val="+nextName);
			out.println(rtn.toString());
			myShepherd.rollbackDBTransaction();
			myShepherd.closeDBTransaction();
			return;
		}

		MarkedIndividual mark = enc.getIndividual();
		if (mark==null) {
			mark = new MarkedIndividual(enc);
			myShepherd.getPM().makePersistent(mark);
			myShepherd.updateDBTransaction();
			IndividualAddEncounter.executeEmails(myShepherd, request,mark,true, enc, context, langCode);
		}

		if (validToName&&"true".equals(useNextProjectId)) {
			System.out.println("trying to set next PROJECT automatic name.......");
			Project projectForAutoNaming = myShepherd.getProjectByProjectIdPrefix(projectIdPrefix.trim());
			mark.addIncrementalProjectId(projectForAutoNaming);
		} else {
			System.out.println("trying to set USER BASED automatic name.......");
			// old user based id
			mark.addName(nextNameKey, nextName);
		}
		System.out.println("RTN for no match naming: "+rtn.toString());

		rtn.put("success",true);
		out.println(rtn.toString());
		myShepherd.commitDBTransaction();


	} catch (Exception e) {
		e.printStackTrace();
		myShepherd.rollbackDBTransaction();
	}
	finally{
		
		myShepherd.closeDBTransaction();
	}

}
else{
	
	rtn.put("error", "Missing parameters. Was expecting noMatch= and ecnId=");
	out.println(rtn.toString());
	
}


%>