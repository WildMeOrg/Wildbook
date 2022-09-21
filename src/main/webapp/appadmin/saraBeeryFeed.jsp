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
org.ecocean.social.*,
org.datanucleus.api.jdo.JDOPersistenceManager,
org.ecocean.cache.*,
java.util.zip.GZIPOutputStream,
java.io.File, java.io.FileNotFoundException, org.ecocean.*,org.ecocean.servlet.*,javax.jdo.*, java.lang.StringBuffer, java.util.Vector, java.util.Iterator, java.lang.NumberFormatException"%>
<%!

// Returns a somewhat rest-like JSON object containing the metadata
public JSONObject uiJson(MarkedIndividual indy, HttpServletRequest request, Shepherd myShepherd) throws JSONException {
  JSONObject jobj = new JSONObject();

  jobj.put("individualID", indy.getIndividualID());
  jobj.put("displayName", indy.getDisplayName(request));
  jobj.put("sex", indy.getSex());
  jobj.put("nickname", indy.getNickName());
  jobj.put("nickname", indy.getNickName());
  jobj.put("timeOfBirth", indy.getTimeOfBirth());
  jobj.put("timeOfDeath", indy.getTimeofDeath());
  jobj.put("genus", request.getParameter("genus"));
  jobj.put("specificEpithet", request.getParameter("specificEpithet"));
  jobj.put("numberEncounters", indy.getNumEncounters());
  
  ArrayList<Relationship> rels = myShepherd.getAllRelationshipsForMarkedIndividual(indy.getIndividualID());
  if(rels!=null){
	  JSONArray jArray =new JSONArray();
	  for(Relationship rel:rels){
		  if(rel.getMarkedIndividual1()!=null && rel.getMarkedIndividual2()!=null && rel.getMarkedIndividual1().getEncounters()!=null && rel.getMarkedIndividual1().getEncounters().size()>0 && rel.getMarkedIndividual2().getEncounters()!=null && rel.getMarkedIndividual2().getEncounters().size()>0){
			  JSONObject jobjRel = new JSONObject();
			  jobjRel.put("type",rel.getType());
			  jobjRel.put("markedIndividualUUID1",rel.getMarkedIndividualName1());
			  jobjRel.put("markedIndividualDisplayName1",rel.getMarkedIndividual1().getDisplayName());
			  jobjRel.put("markedIndividualUUID2",rel.getMarkedIndividualName2());
			  jobjRel.put("markedIndividualDisplayName2",rel.getMarkedIndividual2().getDisplayName());
			  jobjRel.put("markedIndividual1Role",rel.getMarkedIndividualRole1());
			  jobjRel.put("markedIndividual2Role",rel.getMarkedIndividualRole2());
			  jobjRel.put("relatedComments",rel.getRelatedComments());
			  jArray.put(jobjRel);
		  }
	  }
	  jobj.put("relationships",jArray);
  }


  	JSONArray jArray =new JSONArray();
    for (Encounter enc : indy.getEncounterList()) {
    	JSONObject jobjEnc = new JSONObject();
    	jobjEnc.put("catalogNumber", enc.getCatalogNumber());
		jobjEnc.put("lifeStage", enc.getLifeStage());
		jobjEnc.put("dateInMilliseconds", enc.getDateInMilliseconds());
		jobjEnc.put("decimalLatitude", enc.getDecimalLatitude());
		jobjEnc.put("decimalLongitude", enc.getDecimalLongitude());
		jobjEnc.put("year", enc.getYear());
		jobjEnc.put("month", enc.getMonth());
		jobjEnc.put("day", enc.getDay());
		jobjEnc.put("sex", enc.getSex());
		jobjEnc.put("locationID", enc.getLocationID());
		jobjEnc.put("submitterID", enc.getSubmitterID());
		jobjEnc.put("latitude", enc.getDecimalLatitude());
		jobjEnc.put("longitude", enc.getDecimalLongitude());
		
		//annotations
		if(enc.getAnnotations()!=null){
			JSONArray annotArray =new JSONArray();
			for(Annotation annot:enc.getAnnotations()){
				//JSONObject jobjAnnot = new JSONObject();
				JSONObject jobjAnnot = annot.sanitizeJson(request);
				//jobjAnnot.put("iaClass",annot.getIAClass());
				//jobjAnnot.put("viewpoint",annot.getViewpoint());
				//jobjAnnot.put("acmID",annot.getAcmId());
				if(annot.getMediaAsset()!=null){
					if(annot.getMediaAsset().getAcmId()!=null)jobjAnnot.put("sourceMediaAssetAcmID",annot.getMediaAsset().getAcmId());
					jobjAnnot.put("sourceMediaAssetURL",annot.getMediaAsset().webURL().toString());
					jobjAnnot.put("sourceMediaAssetFilename",annot.getMediaAsset().getFilename());
				}
				
				annotArray.put(jobjAnnot);
			}
			jobjEnc.put("annotations",annotArray);
		}
		
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
myShepherd.setAction("saraBeeryFeed.jsp");


String filter="SELECT FROM org.ecocean.MarkedIndividual WHERE encounters.contains(enc) && enc.genus == 'Physeter' && enc.locationID == 'Dominica' && enc.annotations.contains(annot2) && annot2.features.contains(feat2) && feat2.asset != null VARIABLES org.ecocean.Encounter enc;org.ecocean.Annotation annot2;org.ecocean.media.Feature feat2";

Query query=null;


try {
	
	JSONObject jsonobj = new JSONObject();


		//System.out.println("Refreshing socialJson cache: "+cacheName);
		PersistenceManagerFactory pmf = myShepherd.getPM().getPersistenceManagerFactory();
	    /*
		//individual
		javax.jdo.FetchGroup grp = pmf.getFetchGroup(MarkedIndividual.class, "individualSearchResults");
		grp.addMember("individualID").addMember("sex").addMember("names").addMember("numberEncounters").addMember("timeOfBirth").addMember("timeOfDeath").addMember("dateFirstIdentified").addMember("dateTimeLatestSighting").addMember("encounters");
	
		//encounter
		javax.jdo.FetchGroup grp2 = pmf.getFetchGroup(Encounter.class, "encounterSearchResults");
		grp2.addMember("lifeStage").addMember("dateInMilliseconds").addMember("decimalLatitude").addMember("decimalLongitude");
		*/
		
		query=myShepherd.getPM().newQuery(filter);
		
		//query.setRange(1, 9);
	
		//myShepherd.getPM().getFetchPlan().setGroup("individualSearchResults");
		//myShepherd.getPM().getFetchPlan().addGroup("encounterSearchResults");
	
		
		myShepherd.beginDBTransaction();
	
		Collection result = (Collection)query.execute();
		ArrayList<MarkedIndividual> indies=new ArrayList<MarkedIndividual>(result);
		
		JSONArray jarray=new JSONArray();
	    int num = 0;  
	    int size=indies.size();
	        for(MarkedIndividual indy:indies){
	        	num++;
	        	System.out.println("saraBeeryFeed: Rendering "+num+" of "+size);
	        	jarray.put(uiJson(indy,request,myShepherd));
	        }
	        
	        
	      jsonobj.put("results",jarray);  
	        		
		
	  	String filename = "saraBeeryFeed.json";
		
		Util.writeToFile(jsonobj.getJSONArray("results").toString(), "/data/wildbook_data_dir/"+filename);
        
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