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
org.ecocean.social.SocialUnit,
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

long startTime=System.currentTimeMillis();

if (request.getParameter("acmId") != null) {

	response.setHeader("Access-Control-Allow-Origin", "*"); 
	
	String context="context0";
	context=ServletUtilities.getContext(request);
	

	
	String acmId = request.getParameter("acmId");
	String projectIdPrefix = request.getParameter("projectIdPrefix");
	
	String cacheName="iaResultsJson_"+acmId;
	JSONArray janns = new JSONArray();
	
	StringTokenizer str=new StringTokenizer(acmId,",");
	HashMap<String,String> locIDPrefixMap=new HashMap<String,String>();
	
	try {
		
		JSONObject rtn = new JSONObject();

		QueryCache qc=QueryCacheFactory.getQueryCache(context);
		if(qc.getQueryByName(cacheName)!=null && System.currentTimeMillis()<qc.getQueryByName(cacheName).getNextExpirationTimeout() && request.getParameter("refresh")==null){
			JSONObject cachedObj=Util.toggleJSONObject(qc.getQueryByName(cacheName).getJSONSerializedQueryResult());
			//System.out.println("Getting iaResultsJson cache: "+cacheName);
			//this is a single annotations
			if(cachedObj.optBoolean("success", true)){
				rtn=cachedObj;
			}
			//this is a cached list of annotations
			else{
				
				rtn.put("success", true);
				janns.put(cachedObj);
	        	rtn.put("annotations", janns);
			}
			
				
			
		}
		else{
				Shepherd myShepherd = new Shepherd(context);
				myShepherd.setAction("iaResultsAnnotFeed.jsp1");
				myShepherd.beginDBTransaction();
				try{
					
				    ArrayList<Annotation> anns = new ArrayList<Annotation>();
					rtn = new JSONObject("{\"success\": false}");
					
					while(str.hasMoreTokens()){
						try {

							String token=str.nextToken();
							
							String localCacheName="iaResultsJson_"+token;
							if(qc.getQueryByName(localCacheName)!=null && System.currentTimeMillis()<qc.getQueryByName(localCacheName).getNextExpirationTimeout() && request.getParameter("refresh")==null){
								JSONObject cacheItem=Util.toggleJSONObject(qc.getQueryByName(localCacheName).getJSONSerializedQueryResult());
								//System.out.println("Getting iaResultsJson cache: "+localCacheName);
								janns.put(cacheItem);
								//System.out.println("found cached annotation!");
							}
							else{
							
								anns.addAll(myShepherd.getAnnotationsWithACMId(token));
								//System.out.println("adding new annotation!");
							}
							
						} 
						catch (Exception ex) {ex.printStackTrace();}
					}
					
					
					//System.out.println("anns.size: "+anns.size()+";janns.length: "+janns.length());
					if (anns.size() < 1 && janns.length()<1) {
						rtn.put("error", "unknown annotation-related error");
					} 
					else {
						
						//System.out.println("trying projectIdPrefix in iaResults... "+projectIdPrefix);
						Project project = null;
						if (Util.stringExists(projectIdPrefix)) {
							project = myShepherd.getProjectByProjectIdPrefix(projectIdPrefix.trim());
						}
						//System.out.println("end first project processing: "+(System.currentTimeMillis()-startTime));
						
						String locationIdPrefix = null;
						int locationIdPrefixDigitPadding = 3; //had to pick a non-null default
				        for (Annotation ann : anns) {
							if (ann.getMatchAgainst()==true) {
								JSONObject jann = new JSONObject();
								
								jann.put("id", ann.getId());
								jann.put("acmId", ann.getAcmId());
								Encounter enc = ann.findEncounter(myShepherd);
								//System.out.println("      end findEncounter processing: "+(System.currentTimeMillis()-startTime));
								
					 			if (enc != null) {
					 				jann.put("encounterId", enc.getCatalogNumber());
					 				jann.put("encounterLocationId", enc.getLocationID());
				          			jann.put("encounterDate", enc.getDate());
									locationIdPrefix = enc.getPrefixForLocationID();
									//System.out.println("      end locationIdPrefix processing: "+(System.currentTimeMillis()-startTime));
									
									jann.put("encounterLocationIdPrefix", locationIdPrefix);
									locationIdPrefixDigitPadding = enc.getPrefixDigitPaddingForLocationID();
									//System.out.println("      end locationIdPrefixDigitPadding processing: "+(System.currentTimeMillis()-startTime));
									
									jann.put("encounterLocationIdPrefixDigitPadding", locationIdPrefixDigitPadding);
									
									String encounterLocationNextValue = "";
									if(locIDPrefixMap.containsKey(locationIdPrefix)){
										encounterLocationNextValue=locIDPrefixMap.get(locationIdPrefix);
									}
									else{
										encounterLocationNextValue=MarkedIndividual.nextNameByPrefix(locationIdPrefix, locationIdPrefixDigitPadding);
										locIDPrefixMap.put(locationIdPrefix,encounterLocationNextValue);
									}
									jann.put("encounterLocationNextValue", encounterLocationNextValue);
									//System.out.println("      end nextName processing: "+(System.currentTimeMillis()-startTime));
									
		
					 			}
					 			//System.out.println("end encounter processing: "+(System.currentTimeMillis()-startTime));
								MediaAsset ma = ann.getMediaAsset();
								if (ma != null) {
									JSONObject jm = ma.sanitizeJson(request, new org.datanucleus.api.rest.orgjson.JSONObject());
	                                if (ma.getStore() instanceof TwitterAssetStore) jm.put("url", ma.webURL());
	                                jm.put("rotation", rotationInfo(ma));
							        jann.put("asset", jm);
								}
								//System.out.println("end MediaAsset processing: "+(System.currentTimeMillis()-startTime));
								
								MarkedIndividual individual = null;
								//found data edge cases where this can throw an exception. catching for safety.
								//leaving individual as null is acceptable if exception thrown
								try{
										individual=enc.getIndividual();
								}
								catch(Exception dd){}
								if (individual!=null) {
									//SocialUnit-related info
									List<SocialUnit> socialUnits = myShepherd.getAllSocialUnitsForMarkedIndividual(individual);
									if(socialUnits!=null && socialUnits.size()>0){
										//deliberate decision to only show 1, which is the vast majority of animals, revisit assumption in CODEX
										jann.put("socialUnitName", socialUnits.get(0).getSocialUnitName());
									}
								}
								//System.out.println("end MarkedIndividual processing: "+(System.currentTimeMillis()-startTime));
								
								
								if (project!=null) {
									try {
		
										if (project.getEncounters()!=null&&project.getEncounters().contains(enc)) {
											//System.out.println("num encounters in project: "+project.getEncounters().size());
											//MarkedIndividual individual = enc.getIndividual();
											if (individual!=null) {
												
												//Project-related info
												List<String> projectNames = individual.getNamesList(projectIdPrefix);
												if(projectNames!=null && projectNames.size()>0){
													jann.put("incrementalProjectId", projectNames.get(0));
												}
												jann.put("projectIdPrefix", projectIdPrefix);
												jann.put("projectUUID", project.getId());
												

												
											}
										}
									} 
									catch (Exception e) {
										e.printStackTrace();
									}
								}
								//System.out.println("end project processing: "+(System.currentTimeMillis()-startTime));
								
								janns.put(jann);
									
								//Store annotation
								String localCacheName="iaResultsJson_"+ann.getAcmId();
						        CachedQuery cq2=new CachedQuery(localCacheName,Util.toggleJSONObject(jann), false, myShepherd);
						        cq2.nextExpirationTimeout=System.currentTimeMillis()+60000;
						        qc.addCachedQuery(cq2);
							
								}
							}
				        	rtn.put("success", true);
				        	rtn.put("annotations", janns);
				        
							//out.println(rtn.toString());
				        
				        	CachedQuery cq=new CachedQuery(cacheName,Util.toggleJSONObject(rtn), false, myShepherd);
				        	cq.nextExpirationTimeout=System.currentTimeMillis()+60000;
				        	qc.addCachedQuery(cq);
							
						}
					
					
		

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
			
	        //System.out.println("Completed iaResultsAnnotFeed: "+(System.currentTimeMillis()-startTime));

	
	}
	catch(Exception e){
		e.printStackTrace();
	}

}
else{
	%>
	<p>You must specify the ?acmId= parameter.</p>
	<%
}
%>