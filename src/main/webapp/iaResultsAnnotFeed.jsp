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

<%!

void tryCompress(HttpServletRequest req, HttpServletResponse resp, JSONObject jo, boolean useComp) throws IOException, JSONException {
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

String rotationInfo(MediaAsset ma) {
    if ((ma == null) || (ma.getMetadata() == null)) return null;
    HashMap<String,String> orient = ma.getMetadata().findRecurse(".*orient.*");
    if (orient == null) return null;
    for (String k : orient.keySet()) {
        if (orient.get(k).matches(".*90.*")) return orient.get(k);
        if (orient.get(k).matches(".*270.*")) return orient.get(k);
    }
    return null;
}
%>
<%

if (request.getParameter("acmId") != null) {

	response.setHeader("Access-Control-Allow-Origin", "*"); 
	
	String context="context0";
	context=ServletUtilities.getContext(request);
	

	
	String acmId = request.getParameter("acmId");
	String projectIdPrefix = request.getParameter("projectIdPrefix");
	
	String cacheName="iaResultsJson_"+acmId;
	

	
	try {
		
		JSONObject rtn = new JSONObject();

		QueryCache qc=QueryCacheFactory.getQueryCache(context);
		if(qc.getQueryByName(cacheName)!=null && System.currentTimeMillis()<qc.getQueryByName(cacheName).getNextExpirationTimeout() && request.getParameter("refresh")==null){
			rtn=Util.toggleJSONObject(qc.getQueryByName(cacheName).getJSONSerializedQueryResult());
			System.out.println("Getting iaResultsJson cache: "+cacheName);
		}
		else{
				Shepherd myShepherd = new Shepherd(context);
				myShepherd.setAction("iaResults.jsp1");
				myShepherd.beginDBTransaction();
				try{
					
				    ArrayList<Annotation> anns = null;
					rtn = new JSONObject("{\"success\": false}");
					try {
						anns = myShepherd.getAnnotationsWithACMId(acmId);
					} 
					catch (Exception ex) {ex.printStackTrace();}
					if ((anns == null) || (anns.size() < 1)) {
						rtn.put("error", "unknown annotation-related error");
					} 
					else {
						JSONArray janns = new JSONArray();
						System.out.println("trying projectIdPrefix in iaResults... "+projectIdPrefix);
						Project project = null;
						if (Util.stringExists(projectIdPrefix)) {
							project = myShepherd.getProjectByProjectIdPrefix(projectIdPrefix.trim());
						}
						String locationIdPrefix = null;
						int locationIdPrefixDigitPadding = 3; //had to pick a non-null default
				        for (Annotation ann : anns) {
							if (ann.getMatchAgainst()==true) {
								JSONObject jann = new JSONObject();
								jann.put("id", ann.getId());
								jann.put("acmId", ann.getAcmId());
								Encounter enc = ann.findEncounter(myShepherd);
					 			if (enc != null) {
					 				jann.put("encounterId", enc.getCatalogNumber());
					 				jann.put("encounterLocationId", enc.getLocationID());
				          			jann.put("encounterDate", enc.getDate());
									locationIdPrefix = enc.getPrefixForLocationID();
									jann.put("encounterLocationIdPrefix", locationIdPrefix);
									locationIdPrefixDigitPadding = enc.getPrefixDigitPaddingForLocationID();
									jann.put("encounterLocationIdPrefixDigitPadding", locationIdPrefixDigitPadding);
									jann.put("encounterLocationNextValue", MarkedIndividual.nextNameByPrefix(locationIdPrefix, locationIdPrefixDigitPadding));
		
					 			}
								MediaAsset ma = ann.getMediaAsset();
								if (ma != null) {
									JSONObject jm = ma.sanitizeJson(request, new org.datanucleus.api.rest.orgjson.JSONObject());
	                                if (ma.getStore() instanceof TwitterAssetStore) jm.put("url", ma.webURL());
	                                jm.put("rotation", rotationInfo(ma));
							        jann.put("asset", jm);
								}
								if (project!=null) {
									try {
		
										if (project.getEncounters()!=null&&project.getEncounters().contains(enc)) {
											System.out.println("num encounters in project: "+project.getEncounters().size());
											MarkedIndividual individual = enc.getIndividual();
											if (individual!=null) {
												List<String> projectNames = individual.getNamesList(projectIdPrefix);
												if(projectNames!=null && projectNames.size()>0){
													jann.put("incrementalProjectId", projectNames.get(0));
												}
												jann.put("projectIdPrefix", projectIdPrefix);
												jann.put("projectUUID", project.getId());
											}
										}
									} catch (Exception e) {
										e.printStackTrace();
									}
								}
								janns.put(jann);
							}
						}
					    rtn.put("success", true);
				        rtn.put("annotations", janns);
					}
		
					//out.println(rtn.toString());
			        
			        CachedQuery cq=new CachedQuery(cacheName,Util.toggleJSONObject(rtn), false, myShepherd);
			        cq.nextExpirationTimeout=System.currentTimeMillis()+120000;
			        qc.addCachedQuery(cq);
						
					}
					catch(Exception fe){
						fe.printStackTrace();
						rtn.put("exception", "unknown exception");
					}
					finally{
						myShepherd.rollbackAndClose();
					}
				
				
				
		        		
			}
		tryCompress(request, response, rtn, true);
	        

	
	}
	catch(Exception e){
		e.printStackTrace();
	}

}
else{
	%>
	<p>You must specicify the ?acmId= paramater.</p>
	<%
}
%>