<!DOCTYPE html PUBLIC "-//W3C//DTD XHTML 1.0 Transitional//EN"
        "http://www.w3.org/TR/xhtml1/DTD/xhtml1-transitional.dtd">
<%@ page contentType="text/html; charset=utf-8" language="java" import="org.joda.time.LocalDateTime,
org.joda.time.format.DateTimeFormatter,
org.joda.time.format.ISODateTimeFormat,java.net.*,org.json.JSONObject,org.json.JSONArray,
org.ecocean.grid.*,org.ecocean.media.*,org.ecocean.identity.IBEISIA,
java.io.*,java.util.*, java.io.FileInputStream, java.io.File, java.io.FileNotFoundException, org.ecocean.*,org.ecocean.servlet.*,javax.jdo.*, java.lang.StringBuffer, java.util.Vector, java.util.Iterator, java.lang.NumberFormatException"%>

<%

String context="context0";
context=ServletUtilities.getContext(request);

	Shepherd myShepherd=new Shepherd(context);

// pg_dump -Ft sharks > sharks.out

//pg_restore -d sharks2 /home/webadmin/sharks.out


%>

<html>
<head>
<title>Attach MediaAssets</title>

</head>


<body>
<p>Finding all MediaAssets, then creating Annotations and linking to associated Encounters.</p>
<ul>
<%

myShepherd.beginDBTransaction();


FeatureType.initAll(myShepherd);

//build queries

int numFixes=0;
//String iaURLBase="http://52.37.240.178:5000";

try {

	String rootDir = getServletContext().getRealPath("/");
	String baseDir = ServletUtilities.dataDir(context, rootDir).replaceAll("dev_data_dir", "caribwhale_data_dir");

  Iterator allMedia=myShepherd.getAllMediaAssets();

  boolean committing = true;

  JSONArray originalIDsForReference = new JSONArray();

// This outer loop just prevents us from sending enormous GETs to IA
int maxPerLoop = 3;
while (allMedia.hasNext()) {

  int count = 0;
  JSONArray fancyIDsForIA = new JSONArray();

  // build call to get annotations from IA
  while(allMedia.hasNext() && count < maxPerLoop){
    count++;
    MediaAsset ma = (MediaAsset) allMedia.next();
    JSONObject fancyID = toFancyUUID(ma.getUUID());
    fancyIDsForIA.put(fancyID);
    originalIDsForReference.put(ma.getUUID());
  	numFixes++;
  }

  URL iaAnnotsGet = new URL(iaURLBase+"/api/image/annot/uuids/json/?image_uuid_list=" + fancyIDsForIA.toString());
  JSONObject fromIA = RestClient.get(iaAnnotsGet);
	if ((fromIA == null) || (fromIA.optJSONArray("response") == null)) {
		out.println("empty response from annots/uuids/json for " + fancyIDsForIA);
		continue;
	}

	JSONArray alist = fromIA.getJSONArray("response");
out.println(alist);
	for (int i = 0 ; i < alist.length() ; i++) {
		JSONArray alist2 = alist.optJSONArray(i);
		if ((alist2 == null) || (alist2.length() < 1)) {
			out.println(fancyIDsForIA.getJSONObject(i) + " empty array of annots; skipping");
			continue;
		}
		for (int a = 0 ; a < alist2.length() ; a++) {
			String annId = IBEISIA.fromFancyUUID(alist2.optJSONObject(a));
			out.println(fancyIDsForIA.getJSONObject(i) + " ------> " + annId);
			tryMakingAnnotation(IBEISIA.fromFancyUUID(fancyIDsForIA.getJSONObject(i)), annId, myShepherd);
		}
	}
if (fromIA != null) {
	//out.println(fromIA.toString());
	return;
}

/*
  // list of lists of annotation UUIDs (parallel list to fancyIDsForIA)
  List<List<String>> annotsPerMA = new ArrayList<List<String>>(); //TODO: parse fromIA to make annotsPerMA;

  for (int i=0; i<fancyIDsForIA.length() ; i++) {
    String maUUID = fancyIDsForIA.getJSONObject(i).getString("__UUID__");
    MediaAsset ma = MediaAssetFactory.loadByUuid(maUUID, myShepherd);

    for (String annotUUID : annotsPerMA.get(i)) {
      // get annotation info from IA.
      String idSuffix = "?annot_uuid_list

      URL isExemplarServlet = new URL(iaURLBase + "/api/annot/exemplar/flags/json/"+idSuffix);
      JSONObject isExemplarJSON = RestClient.get(isExemplarServlet);
      boolean isExemplar = false; // TODO: parse return of above servlet

      URL bBoxServlet = new URL(iaURLBase + "/api/annot/bboxes/json/"+idSuffix);
      JSONObject bBoxJSON = RestClient.get(isExemplarServlet);
      // TODO: parse bounding box info




    }

  }
*/


}

  if (committing) {
		myShepherd.commitDBTransaction();
		myShepherd.beginDBTransaction();
  }
}
catch (Exception ex) {

	System.out.println("!!!An error occurred on page fixSomeFields.jsp. The error was:");
	ex.printStackTrace();
	myShepherd.rollbackDBTransaction();


}
finally{

	myShepherd.closeDBTransaction();
	myShepherd=null;
}
%>


</ul>
<p>Done successfully: <%=numFixes %> MediaAssets touched.</p>
</body>
</html>

<%!
	public static String iaURLBase = "http://52.37.240.178:5000";

	public static Annotation tryMakingAnnotation(String maUUID, String annId, Shepherd myShepherd) {
		System.out.println("################  ma=" + maUUID + " ------> ann=" + annId);
		try {
                	Annotation exist = ((Annotation) (myShepherd.getPM().getObjectById(myShepherd.getPM().newObjectIdInstance(Annotation.class, annId), true)));
			if (exist != null) {
				System.out.println(" - " + exist + " already exists; skipping creation");
				return null;
			}
		} catch (Exception ex) { }
      		String idSuffix = "?annot_uuid_list=[" + IBEISIA.toFancyUUID(annId) + "]";

    		MediaAsset ma = MediaAssetFactory.loadByUuid(maUUID, myShepherd);
		if (ma == null) {
			System.out.println(" - could not load MediaAsset with uuid=" + maUUID + "; skipping");
			return null;
		}
		System.out.println(ma);

		//now we need the bbox to make the Feature
		Feature ft = null;
		String speciesString = null;
		String indivId = null;
		try {
			JSONObject rtn = RestClient.get(new URL(iaURLBase + "/api/annot/bboxes/json/" + idSuffix));
			if ((rtn == null) || (rtn.optJSONArray("response") == null) || (rtn.getJSONArray("response").optJSONArray(0) == null)) {
				System.out.println("- could not get bbox; skipping");
				return null;
			}
			JSONArray jbb = rtn.getJSONArray("response").getJSONArray(0);
			//System.out.println(" bbox -----------> " + jbb);
			JSONObject fparams = new JSONObject();
			fparams.put("x", jbb.optInt(0, 0));
			fparams.put("y", jbb.optInt(1, 0));
			fparams.put("width", jbb.optInt(2, -1));
			fparams.put("height", jbb.optInt(3, -1));
			ft = new Feature("org.ecocean.boundingBox", fparams);

			rtn = RestClient.get(new URL(iaURLBase + "/api/annot/name/texts/json/" + idSuffix));
			if ((rtn == null) || (rtn.optJSONArray("response") == null)) {
				System.out.println("- could not get name; skipping");
				return null;
			}
			indivId = rtn.getJSONArray("response").optString(0, null);  //i guess we let null stand here?
			//if ("None".equals(indivId)) indivId = null;   // ???????????

			//rtn = RestClient.get(new URL(iaURLBase + "/api/annot/species/texts/json/" + idSuffix));  //seems to be the same as below?
			rtn = RestClient.get(new URL(iaURLBase + "/api/annot/species/json/" + idSuffix));
			if ((rtn == null) || (rtn.optJSONArray("response") == null) || (rtn.getJSONArray("response").optString(0, null) == null)) {
				System.out.println("- could not get species; skipping");
				return null;
			}
			speciesString = rtn.getJSONArray("response").getString(0);
		} catch (Exception ex) {
			System.out.println("- caught exception: " + ex.toString());
		}

		if (ft == null) {
			System.out.println("- could not make Feature; skipping");
			return null;
		}
		if (speciesString == null) {
			System.out.println("- could not get species; skipping");
			return null;
		}

		Annotation ann = new Annotation(speciesString, ft);
		try {
			JSONObject rtn = RestClient.get(new URL(iaURLBase + "/api/annot/exemplar/flags/json/" + idSuffix));
			if ((rtn != null) && (rtn.optJSONArray("response") != null)) {
				boolean exemplar = (rtn.getJSONArray("response").optInt(0, 0) == 1);
				ann.setIsExemplar(exemplar);
			}
			System.out.println(" - ???? should create one");
		} catch (Exception ex) {
			System.out.println("caught exception: " + ex.toString());
		}

		//now we need to find out what encounter to attach annot to, based on filename + indivId
System.out.println("(looking for indivId " + indivId + ")");
		Encounter enc = null;
        	//Query query = myShepherd.getPM().newQuery("SELECT FROM org.ecocean.SinglePhotoVideo WHERE filename.startsWith(\"" + maUUID + ".\")");
        	Query query = myShepherd.getPM().newQuery("SELECT FROM org.ecocean.Encounter WHERE images.contains(spv) && spv.filename.startsWith(\"" + maUUID + ".\")");
        	Collection c = (Collection) (query.execute());
        	Iterator it = c.iterator();
        	while (it.hasNext()) {
            		Encounter e = (Encounter)it.next();
System.out.println(" -----ENC----> " + e.getCatalogNumber() + " > " + e.getIndividualID());
			if ((indivId == null) || indivId.equals(e.getIndividualID())) {  //if we have no indivId coming in, we are kinda outta luck so just take first?
				enc = e;
				break;
			}
        	}    
        	query.closeAll();
		if (enc == null) {
			System.out.println("* unable to find an Encounter matching image/indivId :(");
		} else {
			System.out.println("+ found encounter " + enc.getCatalogNumber() + " matching image/indivId!");
		}
if (ann != null) return null;

		ma.addFeature(ft);
		System.out.println(" - created " + ann + " connected to " + ma + " by " + ft + " with indivId " + indivId);

		return ann;
	}

  // functions to be used in this .jsp
  public static JSONObject toFancyUUID(String uuid) {
    return IBEISIA.toFancyUUID(uuid);
  }


  public static List<JSONArray> splitJSONArray(JSONArray jarr, int maxItemsPerArray) {
    if (maxItemsPerArray < 1) return new ArrayList<JSONArray>(); // no possibility of inifinite loop
    List<JSONArray> out = new ArrayList<JSONArray>();
    int i=0;
    int total = jarr.length();
    while (i < total) {
      int k=0;
      JSONArray thisArr = new JSONArray();
      while (k < maxItemsPerArray && i < total) {
        thisArr.put(k, jarr.get(i));
        i++;
        k++;
      }
      out.add(thisArr);
    }
    return out;
  }


%>
