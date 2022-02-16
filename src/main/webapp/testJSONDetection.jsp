<%@ page contentType="text/html; charset=utf-8" language="java" import="org.joda.time.LocalDateTime,
org.joda.time.format.DateTimeFormatter,
org.joda.time.format.ISODateTimeFormat,java.net.*,
org.ecocean.grid.*,org.ecocean.ia.*,java.util.*,
java.io.*,java.util.*, java.io.FileInputStream, 

org.json.*,
org.ecocean.identity.*,
org.joda.time.DateTime,
org.ecocean.media.*,
java.lang.reflect.Method,
java.security.NoSuchAlgorithmException,
java.security.InvalidKeyException,
org.ecocean.social.*,
org.datanucleus.api.jdo.JDOPersistenceManager,
org.ecocean.cache.*,
org.ecocean.acm.AcmUtil,
org.ecocean.ia.plugin.*,
java.util.zip.GZIPOutputStream,
java.io.File, java.io.FileNotFoundException, org.ecocean.*,org.ecocean.servlet.*,javax.jdo.*, java.lang.StringBuffer, java.util.Vector, java.util.Iterator, java.lang.NumberFormatException"%>
<%!

public static JSONObject toFancyUUID(String u) {
    JSONObject j = new JSONObject();
    try{
    	j.put("__UUID__", u);
    }
    catch(Exception e){}
    return j;
}



public static String callbackUrl(String baseUrl) {
    return baseUrl + "/ia?callback";
}

public static JSONObject hashMapToJSONObject2(HashMap<String,Object> map) {   //note: Object-flavoured
    if (map == null) return null;
    return new JSONObject(map);  // this *used to work*, i swear!!!
}


public static JSONObject sendDetect(ArrayList<MediaAsset> mas, String baseUrl, String context, Shepherd myShepherd) throws RuntimeException, MalformedURLException, IOException, NoSuchAlgorithmException, InvalidKeyException {

    Taxonomy taxy = mas.get(0).getTaxonomy(myShepherd);
    IAJsonProperties iaConfig = new IAJsonProperties();
    JSONObject detectArgs = iaConfig.getDetectionArgs(taxy, baseUrl);
    String detectUrl = iaConfig.getDetectionUrl(taxy);

    JSONObject detectArgsWithMas = Util.copy(detectArgs);
    detectArgsWithMas.put("image_uuid_list", imageUUIDList(mas));
    //System.out.println("sendDetect got detectArgs "+detectArgsWithMas.toString());

    //URL url = new URL(detectUrl);
    //System.out.println("sendDetectNew sending to url "+url);

    //return RestClient.post(url, detectArgsWithMas);
    
    return detectArgsWithMas;
}

public static JSONArray imageUUIDList(List<MediaAsset> mas) {
    JSONArray uuidList = new JSONArray();
    for (MediaAsset ma: mas) {
        uuidList.put(toFancyUUID(ma.getAcmId()));
    }
    return uuidList;
}

/*
note: sendMediaAssets() and sendAnnotations() need to be *batched* now in small chunks, particularly sendMediaAssets().
this is because we **must** get the return value from the POST, in order that we can map the corresponding (returned) acmId
values.  if we *timeout* in the POST, this *will not happen*.  and it is a lengthy process on the IA side: as IA must grab
the image over the network and generate the acmId from it!  hence, batchSize... which we kind of guestimate and cross our fingers.
*/

public JSONObject sendMediaAssets(ArrayList<MediaAsset> mas, boolean checkFirst, String context, Shepherd myShepherd) throws RuntimeException, MalformedURLException, IOException, NoSuchAlgorithmException, InvalidKeyException {
    String u = IA.getProperty(context, "IBEISIARestUrlAddImages");
    if (u == null) throw new MalformedURLException("WildbookIAM configuration value IBEISIARestUrlAddImages is not set");
    URL url = new URL(u);
    int batchSize = 30;
    int numBatches = Math.round(mas.size() / batchSize + 1);

    WildbookIAM plugin = getPluginInstance(myShepherd.getContext());
    
    //sometimes (i.e. when we already did the work, like priming) we dont want to check IA first
    List<String> iaImageIds = new ArrayList<String>();
    if (checkFirst) iaImageIds = iaImageIds(context);

    //initial initialization(!)
    HashMap<String,ArrayList> map = new HashMap<String,ArrayList>();
    map.put("image_uri_list", new ArrayList<JSONObject>());
    map.put("image_unixtime_list", new ArrayList<Integer>());
    map.put("image_gps_lat_list", new ArrayList<Double>());
    map.put("image_gps_lon_list", new ArrayList<Double>());
    List<MediaAsset> acmList = new ArrayList<MediaAsset>(); //for rectifyMediaAssetIds below
    int batchCt = 1;
    JSONObject allRtn = new JSONObject();
    allRtn.put("_batchSize", batchSize);
    allRtn.put("_totalSize", mas.size());
    JSONArray bres = new JSONArray();

    for (int i = 0 ; i < mas.size() ; i++) {
        MediaAsset ma = mas.get(i);
        if (iaImageIds.contains(ma.getAcmId())) continue;
        if (ma.isValidImageForIA()!=null&&!ma.isValidImageForIA()) {
            IA.log("WARNING: WildbookIAM.sendMediaAssets() found a corrupt or otherwise invalid MediaAsset with Id: " + ma.getId());
            continue;
        }
        if (!validMediaAsset(ma,context)) {
            IA.log("WARNING: WildbookIAM.sendMediaAssets() skipping invalid " + ma);
            continue;
        }
        acmList.add(ma);
        map.get("image_uri_list").add(mediaAssetToUri(ma));
        map.get("image_gps_lat_list").add(ma.getLatitude());
        map.get("image_gps_lon_list").add(ma.getLongitude());
        DateTime t = ma.getDateTime();
        if (t == null) {
            map.get("image_unixtime_list").add(null);
        } else {
            map.get("image_unixtime_list").add((int)Math.floor(t.getMillis() / 1000));  //IA wants seconds since epoch
        }

        if ( (i == (mas.size() - 1))  ||  ((i > 0) && (i % batchSize == 0)) ) {   //end of all; or end of a batch
            if (acmList.size() > 0) {
                IA.log("INFO: WildbookIAM.sendMediaAssets() is sending " + acmList.size() + " with batchSize=" + batchSize + " (" + batchCt + " of " + numBatches + " batches)");
                allRtn = IBEISIA.hashMapToJSONObject(map);
System.out.println(batchCt + "]  sendMediaAssets() -> " + allRtn);
                List<String> acmIds = acmIdsFromResponse(allRtn);
                if (acmIds == null) {
                    IA.log("WARNING: WildbookIAM.sendMediaAssets() could not get list of acmIds from response: " + allRtn);
                } else {
                    int numChanged = AcmUtil.rectifyMediaAssetIds(acmList, acmIds);
                    IA.log("INFO: WildbookIAM.sendMediaAssets() updated " + numChanged + " MediaAsset(s) acmId(s) via rectifyMediaAssetIds()");
                }
                bres.put(allRtn);
                //initialize for next batch (if any)
                map.put("image_uri_list", new ArrayList<JSONObject>());
                map.put("image_unixtime_list", new ArrayList<Integer>());
                map.put("image_gps_lat_list", new ArrayList<Double>());
                map.put("image_gps_lon_list", new ArrayList<Double>());
                acmList = new ArrayList<MediaAsset>();
            } else {
                bres.put("EMPTY BATCH");
            }
            batchCt++;
        }
    }
 
    return allRtn;
}

//duct-tape piecemeal fixes for IA-Next
public static WildbookIAM getPluginInstance(String context) {
    IAPlugin p = IAPluginManager.getIAPluginInstanceFromClass(WildbookIAM.class, context);
    return (WildbookIAM)p;
}

public static List<String> acmIdsFromResponse(JSONObject rtn) {
    if ((rtn == null) || (rtn.optJSONArray("response") == null)) return null;
    List<String> ids = new ArrayList<String>();
    for (int i = 0 ; i < rtn.getJSONArray("response").length() ; i++) {
        if (rtn.getJSONArray("response").optJSONObject(i) == null) {
            //IA returns null when it cant localize/etc, so we need to add this to keep array length the same
            ids.add(null);
        } else {
            ids.add(fromFancyUUID(rtn.getJSONArray("response").getJSONObject(i)));
        }
    }
System.out.println("fromResponse ---> " + ids);
    return ids;
}


public static List<String> iaImageIds(String context) {
    List<String> ids = new ArrayList<String>();
    JSONArray jids = null;
    try {
        jids = apiGetJSONArray("/api/image/json/", context);
    } catch (Exception ex) {
        ex.printStackTrace();
        IA.log("ERROR: WildbookIAM.iaImageIds() returning empty; failed due to " + ex.toString());
    }
    if (jids != null) {
        try {
            for (int i = 0 ; i < jids.length() ; i++) {
                if (jids.optJSONObject(i) != null) ids.add(fromFancyUUID(jids.getJSONObject(i)));
            }
        } catch (Exception ex) {
            ex.printStackTrace();
            IA.log("ERROR: WildbookIAM.iaImageIds() parsing error " + ex.toString());
        }
    }
    return ids;
}

public static String fromFancyUUID(JSONObject u) {
    if (u == null) return null;
    return u.optString("__UUID__", null);
}


public static JSONArray apiGetJSONArray(String urlSuffix, String context) throws RuntimeException, MalformedURLException, IOException, NoSuchAlgorithmException, InvalidKeyException {
    URL u = IBEISIA.iaURL(context, urlSuffix);
    JSONObject rtn = RestClient.get(u);
    if ((rtn == null) || (rtn.optJSONObject("status") == null) || (rtn.optJSONArray("response") == null) || !rtn.getJSONObject("status").optBoolean("success", false)) {
        IA.log("WARNING: WildbookIAM.apiGetJSONArray(" + urlSuffix + ") could not parse " + rtn);
        return null;
    }
    return rtn.getJSONArray("response");
}

//basically "should we send to IA?"
public static boolean validMediaAsset(MediaAsset ma,String context) {
    if (ma == null) return false;
    if (!ma.isMimeTypeMajor("image")) return false;
    if ((ma.getWidth() < 1) || (ma.getHeight() < 1)) return false;
    if (mediaAssetToUri(ma) == null) {
        System.out.println("WARNING: WildbookIAM.validMediaAsset() failing from null mediaAssetToUri() for " + ma);
        return false;
    }
    return true;
}

private static Object mediaAssetToUri(MediaAsset ma) {
    //URL curl = ma.containerURLIfPresent();  //what is this??
    //if (curl == null) curl = ma.webURL();

    URL curl = ma.webURL();
    
    String urlStr = curl.toString();
    // THIS WILL BREAK if you need to append a query to the filename... 
    // we are double encoding the '?' in order to allow filenames that contain it to go to IA   
    if (urlStr!=null) {
        urlStr = urlStr.replaceAll("\\?", "%3F");
        if (ma.getStore() instanceof LocalAssetStore) {
            return urlStr;
        } else if (ma.getStore() instanceof S3AssetStore) {
            return ma.getParameters();
        } else {
            return urlStr;
        }
    }
    return null;
    
}

%>

<%

response.setHeader("Access-Control-Allow-Origin", "*"); 

String context="context0";
context=ServletUtilities.getContext(request);

Shepherd myShepherd=new Shepherd(context);
myShepherd.setAction("leopardSocialFeed.jsp");


String filter="SELECT FROM org.ecocean.Encounter WHERE ( submitterID == 'ryosef60' || submitterID == 'cotron.1' ) && annotations.size() > 0 ";

Query query=null;


try {
	
	ArrayList<Annotation> annots = new ArrayList<Annotation>();
	
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
	
		//myShepherd.getPM().getFetchPlan().setGroup("individualSearchResults");
		//myShepherd.getPM().getFetchPlan().addGroup("encounterSearchResults");
	
		
		myShepherd.beginDBTransaction();
	
		Collection result = (Collection)query.execute();
		ArrayList<Encounter> encs=new ArrayList<Encounter>(result);
		
		JSONArray jarray=new JSONArray();
	        
        
        ArrayList<MediaAsset> mas=encs.get(0).getMedia();

        
        String baseUrl = null;
        try {
            String containerName = IA.getProperty("context0", "containerName");
            baseUrl = CommonConfiguration.getServerURL(request, request.getContextPath());
            if (containerName!=null&&containerName!="") {
                baseUrl = baseUrl.replace("localhost", containerName);
            }

        } catch (Exception e) {
            e.printStackTrace();
        }
        
        
        JSONObject jobj=sendDetect(mas, baseUrl, context, myShepherd);
        
        Taxonomy taxy = mas.get(0).getTaxonomy(myShepherd);
        IAJsonProperties iaConfig = new IAJsonProperties();
        JSONObject detectArgs = iaConfig.getDetectionArgs(taxy, baseUrl);
        String detectUrl = iaConfig.getDetectionUrl(taxy);

        String u = IA.getProperty(context, "IBEISIARestUrlAddImages");
        if (u == null) throw new MalformedURLException("WildbookIAM configuration value IBEISIARestUrlAddImages is not set");
        URL url = new URL(u);
        
	    %>
	    <h2>MediaAsset Registration in WBIA</h2>
	    <p>POST to URL: <%=url.toString() %></p>
	    <p>JSON: <%=sendMediaAssets(mas, false, context, myShepherd).toString() %></p>
	    
	    
	    <h2>Detection URL</h2>
	    <p>POST to URL: <%=detectUrl %></p>
	    <p>JSON: <%=jobj.toString() %></p>
	    <%    

	        



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