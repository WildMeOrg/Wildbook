<%@ page contentType="application/json; charset=utf-8" language="java" import="org.joda.time.LocalDateTime,
org.joda.time.format.DateTimeFormatter,
org.joda.time.format.ISODateTimeFormat,java.net.*,
org.ecocean.grid.*,org.ecocean.ia.*,java.util.*,
java.io.*,java.util.*, java.io.FileInputStream, 

org.json.*,
org.ecocean.identity.*,
java.lang.reflect.Method,
java.security.NoSuchAlgorithmException,
java.security.InvalidKeyException,
org.ecocean.social.*,
org.datanucleus.api.jdo.JDOPersistenceManager,
org.ecocean.cache.*,
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

// Returns a somewhat rest-like JSON object containing the metadata
public JSONObject uiJson(MarkedIndividual indy, HttpServletRequest request, Shepherd myShepherd, ArrayList<Annotation> al) throws JSONException {
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
	  jobj.put("relationships",jArray);
  }


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
		
		//annotations
		if(enc.getAnnotations()!=null){
			JSONArray annotArray =new JSONArray();
			for(Annotation annot:enc.getAnnotations()){
				if(annot.getAcmId()==null)continue;
				al.add(annot);
				JSONObject jobjAnnot = new JSONObject();
				jobjAnnot.put("iaClass",annot.getIAClass());
				jobjAnnot.put("viewpoint",annot.getViewpoint());
				jobjAnnot.put("acmID",annot.getAcmId());
				jobjAnnot.put("sourceMediaAssetAcmID",annot.getMediaAsset().getAcmId());
				annotArray.put(jobjAnnot);
			}
			jobjEnc.put("annotations",annotArray);
		}
		
    	jArray.put(jobjEnc);
    }
    jobj.put("encounters",jArray);
  	return jobj;
}

//note: if tanns here is null, then it is exemplar for this species
public static JSONObject sendIdentify(ArrayList<Annotation> qanns, ArrayList<Annotation> tanns, JSONObject queryConfigDict,
                                      JSONObject userConfidence, String baseUrl, String context, String taskId)
                                      throws RuntimeException, MalformedURLException, IOException, NoSuchAlgorithmException, InvalidKeyException {
    //if (!isIAPrimed()) System.out.println("WARNING: sendIdentify() called without IA primed");
    String u = IA.getProperty(context, "IBEISIARestUrlStartIdentifyAnnotations");
    if (u == null) throw new MalformedURLException("configuration value IBEISIARestUrlStartIdentifyAnnotations is not set");
    URL url = new URL(u);
long startTime = System.currentTimeMillis();
Util.mark("sendIdentify-0  tanns.size()=" + ((tanns == null) ? "null" : tanns.size()), startTime);

    Shepherd myShepherd = new Shepherd(context);
    myShepherd.setAction("IBEISIA.sendIdentify");
    myShepherd.beginDBTransaction();

    HashMap<String,Object> map = new HashMap<String,Object>();
    map.put("callback_url", callbackUrl(baseUrl));
    map.put("jobid", taskId);
    if (queryConfigDict != null) map.put("query_config_dict", queryConfigDict);
    map.put("matching_state_list", IBEISIAIdentificationMatchingState.allAsJSONArray(myShepherd));  //this is "universal"
    //if (userConfidence != null) map.put("user_confidence", userConfidence);

    ArrayList<JSONObject> qlist = new ArrayList<JSONObject>();
    ArrayList<JSONObject> tlist = new ArrayList<JSONObject>();
    ArrayList<String> qnlist = new ArrayList<String>();
    ArrayList<String> tnlist = new ArrayList<String>();

///note: for names here, we make the gigantic assumption that they individualID has been migrated to uuid already!
    //String species = null;
    String iaClass = null;
Util.mark("sendIdentify-1", startTime);
    for (Annotation ann : qanns) {
        /*
    	if (!validForIdentification(ann, context)) {
            System.out.println("WARNING: IBEISIA.sendIdentify() [qanns] skipping invalid " + ann);
            continue;
        }
        */

        //if (species == null) species = org.ecocean.ia.plugin.WildbookIAM.getIASpecies(ann, myShepherd);
        // Should we fall back on gleaning species from the Enc? We do it to find the iaClass initially.. Redundant? Squishy? Discuss.
        if (iaClass==null) {
            if (ann.getIAClass()!=null) {
                iaClass = ann.getIAClass();
            } else {
                iaClass = org.ecocean.ia.plugin.WildbookIAM.getIASpecies(ann, myShepherd);
            }
        }

        qlist.add(toFancyUUID(ann.getAcmId()));
/* jonc now fixed it so we can have null/unknown ids... but apparently this needs to be "____" (4 underscores) ; also names are now just strings (not uuids)
        //TODO i guess (???) we need some kinda ID for query annotations (even tho we dont know who they are); so wing it?
        qnlist.add(toFancyUUID(Util.generateUUID()));
*/

        qnlist.add(IBEISIA.IA_UNKNOWN_NAME);
    }
Util.mark("sendIdentify-2", startTime);
    // Do we have a qaan? We need one, or load a failure response.
    if (qlist.isEmpty()) {
        JSONObject noQueryAnn = new JSONObject();
        try{
        	noQueryAnn.put("status", new JSONObject().put("message", "rejected"));
        	noQueryAnn.put("error", "No query annotation was valid for identification. ");
        }
        catch(Exception e){}
        return noQueryAnn;
    }

Util.mark("sendIdentify-A", startTime);
    boolean setExemplarCaches = false;
    if (tanns == null) {
System.out.println("--- sendIdentify() passed null tanns..... why???");
System.out.println("     gotta compute :(");
        tanns = qanns.get(0).getMatchingSet(myShepherd);
    }
Util.mark("sendIdentify-B  tanns.size()=" + ((tanns == null) ? "null" : tanns.size()), startTime);

//int ct = 0;
    if (tanns != null) for (Annotation ann : tanns) {
//Util.mark(ct + "]  sib-1 ann=" + ann.getId() + "/" + ann.getAcmId(), startTime);
        /*
		if (!validForIdentification(ann, context)) {
            System.out.println("WARNING: IBEISIA.sendIdentify() [tanns] skipping invalid " + ann);
            continue;
        }
        */
//Util.mark("      sib-2 ann=" + ann.getId() + "/" + ann.getAcmId(), startTime);
//ct++;
        tlist.add(toFancyUUID(ann.getAcmId()));
        String indivId = ann.findIndividualId(myShepherd);
/*  see note above about names
        if (Util.isUUID(indivId)) {
            tnlist.add(toFancyUUID(indivId));
        } else if (indivId == null) {
            tnlist.add(toFancyUUID(Util.generateUUID()));  //we must have one... meh?  TODO fix (and see above)
        } else {
            tnlist.add(indivId);
        }
*/
        //argh we need to standardize this and/or have a method. :/
        if ((indivId == null) || (indivId.toLowerCase().equals("unassigned"))) {
            tnlist.add(IBEISIA.IA_UNKNOWN_NAME);
        } else {
            tnlist.add(indivId);
        }
    }
//query_config_dict={'pipeline_root' : 'BC_DTW'}

Util.mark("sendIdentify-C", startTime);

    // WB-1665 now wants us to bail upon empty target annots:
if (Util.collectionIsEmptyOrNull(tlist)) {
        System.out.println("WARNING: bailing on empty target list");
        JSONObject emptyRtn = new JSONObject();
        JSONObject status = new JSONObject();
        try{
        	status.put("message", "rejected");
        	status.put("error", "Empty target annotation list");
        	status.put("emptyTargetAnnotations", true);
        	emptyRtn.put("status", status);
        }
        catch(Exception e){}
        myShepherd.rollbackDBTransaction();
        myShepherd.closeDBTransaction();
        
        return emptyRtn;
    }

    map.put("query_annot_uuid_list", qlist);
    map.put("database_annot_uuid_list", tlist);
    //We need to send IA null in this case. If you send it an empty list of annotation names or uuids it will check against nothing..
    // If the list is null it will check against everything.
    map.put("query_annot_name_list", qnlist);
    //if we have no target lists, pass null for "all"
    if (Util.collectionIsEmptyOrNull(tlist)) {
        map.put("database_annot_uuid_list", null);
    } else {
        map.put("database_annot_uuid_list", tlist);
    }
    if (Util.collectionIsEmptyOrNull(tnlist)) {
        map.put("database_annot_name_list", null);
    } else {
        map.put("database_annot_name_list", tnlist);
    }
Util.mark("sendIdentify-D", startTime);


	System.out.println("===================================== qlist & tlist ========================= [taskId=" + taskId + "]");
	System.out.println(qlist + " callback=" + callbackUrl(baseUrl));
	if (Util.collectionIsEmptyOrNull(tlist) || Util.collectionIsEmptyOrNull(tnlist)) {
	    System.out.println("tlist/tnlist == null! Checking against all.");
	} else {
	    System.out.println("tlist.size()=" + tlist.size()+" annnnd tnlist.size()="+tnlist.size());
	}
	System.out.println("qlist.size()=" + qlist.size()+" annnnd qnlist.size()="+qnlist.size()+". not printing the map about to be POSTed because it's a big'un.");
    //System.out.println(map);
	myShepherd.rollbackDBTransaction();
	myShepherd.closeDBTransaction();
Util.mark("identify process pre-post end");
    return hashMapToJSONObject2(map);
}


%>

<%

response.setHeader("Access-Control-Allow-Origin", "*"); 

String context="context0";
context=ServletUtilities.getContext(request);

Shepherd myShepherd=new Shepherd(context);
myShepherd.setAction("leopardSocialFeed.jsp");


String filter="SELECT FROM org.ecocean.MarkedIndividual WHERE encounters.contains(enc) && encounters.contains(enc1515) &&( enc1515.submitterID == 'ryosef60' || enc1515.submitterID == 'cotron.1' ) VARIABLES org.ecocean.Encounter enc;org.ecocean.Encounter enc1515";

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
		ArrayList<MarkedIndividual> indies=new ArrayList<MarkedIndividual>(result);
		
		JSONArray jarray=new JSONArray();
	        
        for(MarkedIndividual indy:indies){
        	jarray.put(uiJson(indy,request,myShepherd,annots));
        }
        
        Annotation qann = annots.remove(0);
        ArrayList<Annotation> qanns = new ArrayList<Annotation>();
        qanns.add(qann);
        
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
        
        JSONObject queryConfigDict = new JSONObject();
        
        JSONObject scobj=new JSONObject();
        scobj.put("sv_on",true);
        queryConfigDict.put("queryConfigDict", scobj);
        
        JSONObject jobj=sendIdentify(qanns, annots, queryConfigDict, new JSONObject(), baseUrl, context, Util.generateUUID());
	    %>
	    <%=jobj.toString() %>
	    
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