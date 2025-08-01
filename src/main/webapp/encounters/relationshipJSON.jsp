
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
java.io.File, java.io.FileNotFoundException, org.ecocean.*,org.ecocean.servlet.*,javax.jdo.*, java.lang.StringBuffer, java.util.Vector, java.util.Iterator, java.lang.NumberFormatException"%><%@ page import="org.ecocean.shepherd.core.Shepherd"%>


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
myShepherd.setAction("relationshipJson.jsp");

String cacheName="relationshipJson";

//inject individualID stuff here as all this stuff is SLOW
String individualIDClauseRel="";
String individualIDClauseSocUnit="";
if(request.getParameter("individualID")!=null){
	individualIDClauseRel=" && (markedIndividualName1 =='"+request.getParameter("individualID")+"' || markedIndividualName2 =='"+request.getParameter("individualID")+"' )";
	individualIDClauseSocUnit=" WHERE members.contains(member) && member.mi.individualID=='"+request.getParameter("individualID")+"' VARIABLES org.ecocean.social.Membership member";
	cacheName = cacheName+"_"+request.getParameter("individualID");
}

String filter="SELECT FROM org.ecocean.social.Relationship where type != 'CommunityMembership' "+individualIDClauseRel;
String filter2="SELECT FROM org.ecocean.social.SocialUnit "+individualIDClauseSocUnit;

Query query=null;
Query query2=null;


try {
	
	JSONObject jsonobj = new JSONObject();
	JSONArray jarray=new JSONArray();

	PersistenceManagerFactory pmf = myShepherd.getPM().getPersistenceManagerFactory();
	
	QueryCache qc=QueryCacheFactory.getQueryCache(context);
	
	//individual
	javax.jdo.FetchGroup grp = pmf.getFetchGroup(MarkedIndividual.class, "individualSearchResults");
	grp.addMember("individualID").addMember("sex").addMember("names").addMember("numberEncounters").addMember("timeOfBirth").addMember("timeOfDeath").addMember("dateFirstIdentified").addMember("dateTimeLatestSighting").addMember("encounters");


	
	
	//GET FORMAL RELATIONSHIPS BUT IGNORE OLD FORMAT COMMUNITYMEMBERSHIP THAT IS NOW REPLACED WITH SOCIALUNIT and MEMBERSHIP objects
	if(qc.getQueryByName(cacheName)!=null && System.currentTimeMillis()<qc.getQueryByName(cacheName).getNextExpirationTimeout() && request.getParameter("refresh")==null){
		jsonobj=Util.toggleJSONObject(qc.getQueryByName(cacheName).getJSONSerializedQueryResult());
		//System.out.println("Getting relationshipJson cache: "+cacheName);
	}
	else{
		//System.out.println("Refreshing relationshipJson cache: "+cacheName);
		
		System.out.println("query1 filter: "+filter);
		query=myShepherd.getPM().newQuery(filter);
	
		myShepherd.getPM().getFetchPlan().setGroup("individualSearchResults");

		
		myShepherd.beginDBTransaction();
	
		Collection result = (Collection)query.execute();
		ArrayList<Relationship> rels=new ArrayList<Relationship>(result);
		System.out.println("relationshipJSON.jsp query 1 complete");
		
	        
	        for(Relationship rel:rels){
	        	jarray.put(rel.uiJson(request));
	        }
	        
	    //System.out.println("query 1 processing complete");
	    //System.out.println("query2 filter: "+filter2);
		query2=myShepherd.getPM().newQuery(filter2);
	
	
		Collection result2 = (Collection)query2.execute();
		ArrayList<SocialUnit> rels2=new ArrayList<SocialUnit>(result2);
		//System.out.println("relationshipJSON.jsp query 2 complete");
	        
	        for(SocialUnit su:rels2){
	        	List<MarkedIndividual> indies=su.getMarkedIndividuals();
	        	int indiesSize=indies.size();
	        	for(int i=0;i<(indiesSize-1);i++){
	        		for(int j=i+1;j<indiesSize;j++){
	        			Relationship rel=new Relationship("CommunityMembership", indies.get(i),indies.get(j));
	        			rel.setRelatedSocialUnitName(su.getSocialUnitName());
	        			jarray.put(rel.uiJson(request));
	        		}
	        		
	        	}
	        }
	        
	        //System.out.println("query 2 processing complete");
	        
	      //somehow add jsonobjSU to jsonobj results jarray
	      
	        jsonobj.put("results",jarray);
	      
	        CachedQuery cq=new CachedQuery(cacheName,Util.toggleJSONObject(jsonobj), false, myShepherd);
	        cq.nextExpirationTimeout=System.currentTimeMillis()+300000;
	        qc.addCachedQuery(cq);
	        
	        //System.out.println("relationshipJSON.jsp query cache posting complete");
	        		
		}
	
	

    	  
        
        tryCompress(request, response, jsonobj.getJSONArray("results"), true);

	

}
catch(Exception e){
	e.printStackTrace();
}
finally{
	if(query!=null)query.closeAll();
	if(query2!=null)query2.closeAll();
	myShepherd.rollbackDBTransaction();
	myShepherd.closeDBTransaction();

}

%>

