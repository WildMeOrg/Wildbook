<%@ page contentType="application/json; charset=utf-8" language="java" import="org.joda.time.LocalDateTime,
org.joda.time.format.DateTimeFormatter,
org.joda.time.format.ISODateTimeFormat,java.net.*,
org.ecocean.grid.*,org.ecocean.ia.*,java.util.*,
java.io.*,java.util.*, java.io.FileInputStream, 

org.datanucleus.api.rest.orgjson.JSONArray,
org.datanucleus.api.rest.orgjson.JSONException,
org.datanucleus.api.rest.orgjson.JSONObject,
org.datanucleus.api.rest.RESTUtils,
org.datanucleus.ExecutionContext,
java.util.Set, java.util.HashSet,
java.lang.reflect.Method,
org.datanucleus.api.jdo.JDOPersistenceManager,
org.ecocean.cache.*,
org.ecocean.social.Relationship,
java.util.zip.GZIPOutputStream,
java.io.File, java.io.FileNotFoundException, org.ecocean.*,org.ecocean.servlet.*,javax.jdo.*, java.lang.StringBuffer, java.util.Vector, java.util.Iterator, java.lang.NumberFormatException"%>
<%!

// Returns a somewhat rest-like JSON object containing the metadata
public JSONObject uiJson(MarkedIndividual indy, HttpServletRequest request) throws JSONException {
  JSONObject jobj = new JSONObject();

  jobj.put("individualID", indy.getIndividualID());
  jobj.put("displayName", indy.getDisplayName(request));
  jobj.put("sex", indy.getSex());
  jobj.put("nickname", indy.getNickName());
  jobj.put("nickname", indy.getNickName());
  jobj.put("timeOfBirth", indy.getTimeOfBirth());
  jobj.put("timeOfDeath", indy.getTimeofDeath());
  jobj.put("dateFirstIdentified", indy.getDateFirstIdentified());
  jobj.put("dateTimeLatestSighting", indy.getDateLatestSighting());
  jobj.put("genus", request.getParameter("genus"));
  jobj.put("specificEpithet", request.getParameter("specificEpithet"));
  jobj.put("numberEncounters", indy.getNumEncounters());

  	JSONArray jArray =new JSONArray();
    for (Encounter enc : indy.getEncounterList()) {
    	JSONObject jobjEnc = new JSONObject();
		jobjEnc.put("lifeStage", enc.getLifeStage());
		jobjEnc.put("dateInMilliseconds", enc.getDateInMilliseconds());
		jobjEnc.put("decimalLatitude", enc.getDecimalLatitude());
		jobjEnc.put("decimalLongitude", enc.getDecimalLongitude());
		jobjEnc.put("year", enc.getYear());
		jobjEnc.put("month", enc.getMonth());
		jobjEnc.put("day", enc.getDay());
    	jArray.put(jobjEnc);
    }
    jobj.put("encounters",jArray);
  	return jobj;
}

%>
<%!

void tryCompress(HttpServletRequest req, HttpServletResponse resp, JSONArray jo, boolean useComp) throws IOException, JSONException {
	//System.out.println("??? TRY COMPRESS ??");
    //String s = scrubJson(req, jo).toString();
    String s = jo.toString();
    if (!useComp || (s.length() < 3000)) {  //kinda guessing on size here, probably doesnt matter
        resp.getWriter().write(s);
    } else {
        resp.setHeader("Content-Encoding", "gzip");
    OutputStream o = resp.getOutputStream();
    GZIPOutputStream gz = new GZIPOutputStream(o);
        gz.write(s.getBytes());
        gz.flush();
        gz.close();
        o.close();
    }
}

%>
<%

response.setHeader("Access-Control-Allow-Origin", "*"); 

String context="context0";
context=ServletUtilities.getContext(request);

Shepherd myShepherd=new Shepherd(context);
myShepherd.setAction("relatedIndividuals.jsp");







MarkedIndividual indiv = myShepherd.getMarkedIndividual(request.getParameter("individualID"));
if (indiv == null) {
	response.setStatus(404);
	myShepherd.rollbackDBTransaction();
	myShepherd.closeDBTransaction();
	return;
}


Set<MarkedIndividual> already = new HashSet<MarkedIndividual>();
try {
	JSONArray jarr = new JSONArray();
        jarr.put(uiJson(indiv, request));
	for (Relationship rel : indiv.getAllRelationships(myShepherd)) {
		MarkedIndividual other = rel.getMarkedIndividual1();
		if (other.getId().equals(indiv.getId())) other = rel.getMarkedIndividual2();
		if (already.contains(other)) continue;
		already.add(other);
		jarr.put(uiJson(other, request));
	}
	out.println(jarr.toString());

} catch(Exception e){
	e.printStackTrace();
}
finally{
	myShepherd.rollbackDBTransaction();
	myShepherd.closeDBTransaction();

}
%>
