
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
org.ecocean.social.*,
java.util.zip.GZIPOutputStream,
java.io.File, java.io.FileNotFoundException, org.ecocean.*,org.ecocean.servlet.*,javax.jdo.*, java.lang.StringBuffer, java.util.Vector, java.util.Iterator, java.lang.NumberFormatException"%>

<%!

// Returns a somewhat rest-like JSON object containing the metadata
public JSONObject uiJson(Occurrence indy, HttpServletRequest request) throws JSONException {
  JSONObject jobj = new JSONObject();

  jobj.put("occurrenceID", indy.getOccurrenceID());

  	JSONArray jArray =new JSONArray();
    for (Encounter enc : indy.getEncounters()) {
    	JSONObject jobjEnc = new JSONObject();
    	boolean hasIDs=false;
		if(enc.getIndividual()!=null)jobjEnc.put("individualID", enc.getIndividual().getIndividualID());
		
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
myShepherd.setAction("occurrenceGraphJson.jsp");


String genusFilter="";
if(request.getParameter("genus")!=null){
	genusFilter="&& enc.genus == '"+request.getParameter("genus")+"'";
}


String filter="SELECT FROM org.ecocean.Occurrence where encounters.size() > 1 && encounters.contains(enc) && enc.individual != null "+genusFilter+" VARIABLES org.ecocean.Encounter enc";

Query query=null;


try {
	
	JSONObject jsonobj = new JSONObject();
	JSONArray jarray=new JSONArray();

	PersistenceManagerFactory pmf = myShepherd.getPM().getPersistenceManagerFactory();
	
	QueryCache qc=QueryCacheFactory.getQueryCache(context);
	
	
	javax.jdo.FetchGroup grp = pmf.getFetchGroup(Occurrence.class, "occurrenceResults");
	grp.addMember("encounters").addMember("occurrenceID");
	javax.jdo.FetchGroup grp2 = pmf.getFetchGroup(Encounter.class, "indyResults");
	grp2.addMember("individual");
	
	
	//GET FORMAL RELATIONSHIPS BUT IGNORE OLD FORMAT COMMUNITYMEMBERSHIP THAT IS NOW REPLACED WITH SOCIALUNIT and MEMBERSHIP objects
	if(qc.getQueryByName("occurrenceJson")!=null && System.currentTimeMillis()<qc.getQueryByName("occurrenceJson").getNextExpirationTimeout() && request.getParameter("refresh")==null){
		jsonobj=Util.toggleJSONObject(qc.getQueryByName("occurrenceJson").getJSONSerializedQueryResult());
		System.out.println("Getting occurrenceJson cache!");
	}
	else{
		System.out.println("Refreshing relationshipJson cache!");
		

		query=myShepherd.getPM().newQuery(filter);
	
		myShepherd.getPM().getFetchPlan().setGroup("occurrenceResults");
		myShepherd.getPM().getFetchPlan().addGroup("indyResults");
		
		myShepherd.beginDBTransaction();
	
		Collection result = (Collection)query.execute();
		ArrayList<Occurrence> rels=new ArrayList<Occurrence>(result);
		
		
	        
	        for(Occurrence rel:rels){
	        	jarray.put(uiJson(rel,request));
	        }
	        

	        
	      //somehow add jsonobjSU to jsonobj results jarray
	      
	        jsonobj.put("results",jarray);
	      
	        CachedQuery cq=new CachedQuery("occurrenceJson",Util.toggleJSONObject(jsonobj), false, myShepherd);
	        cq.nextExpirationTimeout=System.currentTimeMillis()+300000;
	        qc.addCachedQuery(cq);
	        		
		}
	
	

    	  
        
        tryCompress(request, response, jsonobj.getJSONArray("results"), true);

	

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

