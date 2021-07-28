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
java.lang.reflect.Method,
org.datanucleus.api.jdo.JDOPersistenceManager,
org.ecocean.cache.*,
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
  System.out.println("deleteMe got here a1.5");
//System.out.println("??? TRY COMPRESS ??");
    //String s = scrubJson(req, jo).toString();
    String s = jo.toString();
    System.out.println("deleteMe got here a2");
    if (!useComp || (s.length() < 3000)) {  //kinda guessing on size here, probably doesnt matter
        System.out.println("deleteMe got here a3");
        resp.getWriter().write(s);
        System.out.println("deleteMe got here a4");
    } else {
      System.out.println("deleteMe got here a5");
        resp.setHeader("Content-Encoding", "gzip");
        System.out.println("deleteMe got here a6");
    OutputStream o = resp.getOutputStream();
    System.out.println("deleteMe got here a7");
    GZIPOutputStream gz = new GZIPOutputStream(o);
    System.out.println("deleteMe got here a8");
    try{
      gz.write(s.getBytes());
      gz.flush();
      System.out.println("deleteMe got here a9");
    }catch(Error e){
      System.out.println("deleteMe got here a9.5");
      System.out.println("error writing gz: ");
      e.printStackTrace();
    }
        System.out.println("deleteMe got here a10");
        gz.close();
        System.out.println("deleteMe got here a11");
        o.close();
        System.out.println("deleteMe got here a12");
    }
}

%>
<%

response.setHeader("Access-Control-Allow-Origin", "*");

String context="context0";
context=ServletUtilities.getContext(request);

Shepherd myShepherd=new Shepherd(context);
myShepherd.setAction("socialJson.jsp");





String genus="Physeter";
String specificEpithet="macrocephalus";

if(request.getParameter("genus")!=null){
	genus=request.getParameter("genus");
}
if(request.getParameter("specificEpithet")!=null){
	specificEpithet=request.getParameter("specificEpithet");
}

String cacheName="socialJson_"+genus+"_"+specificEpithet;

String filter="SELECT FROM org.ecocean.MarkedIndividual where encounters.contains(enc1) && enc1.genus==\'"+genus+"' && enc1.specificEpithet=='"+specificEpithet+"' VARIABLES org.ecocean.Encounter enc1";

Query query=null;



try {

	JSONObject jsonobj = new JSONObject();


	QueryCache qc=QueryCacheFactory.getQueryCache(context);
	if(qc.getQueryByName(cacheName)!=null && System.currentTimeMillis()<qc.getQueryByName(cacheName).getNextExpirationTimeout() && request.getParameter("refresh")==null){
		jsonobj=Util.toggleJSONObject(qc.getQueryByName(cacheName).getJSONSerializedQueryResult());
		System.out.println("Getting socialJson cache!");
	}
	else{
		System.out.println("Refreshing socialJson cache!");
		PersistenceManagerFactory pmf = myShepherd.getPM().getPersistenceManagerFactory();

		//individual
		javax.jdo.FetchGroup grp = pmf.getFetchGroup(MarkedIndividual.class, "individualSearchResults");
		grp.addMember("individualID").addMember("sex").addMember("names").addMember("numberEncounters").addMember("timeOfBirth").addMember("timeOfDeath").addMember("dateFirstIdentified").addMember("dateTimeLatestSighting").addMember("encounters");

		//encounter
		javax.jdo.FetchGroup grp2 = pmf.getFetchGroup(Encounter.class, "encounterSearchResults");
		grp2.addMember("lifeStage").addMember("dateInMilliseconds").addMember("decimalLatitude").addMember("decimalLongitude");

		query=myShepherd.getPM().newQuery(filter);

		myShepherd.getPM().getFetchPlan().setGroup("individualSearchResults");
		myShepherd.getPM().getFetchPlan().addGroup("encounterSearchResults");


		myShepherd.beginDBTransaction();

		Collection result = (Collection)query.execute();
		ArrayList<MarkedIndividual> indies=new ArrayList<MarkedIndividual>(result);

		JSONArray jarray=new JSONArray();

	        for(MarkedIndividual indy:indies){
	        	jarray.put(uiJson(indy,request));
	        }


	      jsonobj.put("results",jarray);

		}

        System.out.println("deleteMe got here a12");
        tryCompress(request, response, jsonobj.getJSONArray("results"), true);
        System.out.println("deleteMe got here a1");
        CachedQuery cq=new CachedQuery(cacheName,Util.toggleJSONObject(jsonobj), false, myShepherd);
        cq.nextExpirationTimeout=System.currentTimeMillis()+300000;
        qc.addCachedQuery(cq);

}
catch(Exception e){
	e.printStackTrace();
}
finally{
	if(query!=null)query.closeAll();
	myShepherd.rollbackDBTransaction();
	myShepherd.closeDBTransaction();

}
%>
