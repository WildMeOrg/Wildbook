<%@ page contentType="text/html; charset=utf-8" language="java"
     import="org.ecocean.*,
org.ecocean.servlet.ServletUtilities,
org.ecocean.media.*,
java.util.Map,
java.util.List,
java.io.BufferedReader,
java.io.IOException,
java.io.InputStream,
java.io.InputStreamReader,
java.io.File,
java.util.Collection,
java.util.ArrayList,
org.json.JSONObject,
org.ecocean.media.Feature,
org.ecocean.media.FeatureType,
javax.jdo.Query,
javax.jdo.Extent,
java.util.HashMap,

org.ecocean.media.*
              "
%>


<%!
ArrayList<SuperSpot> getOLDLeftReferenceSpots(Encounter enc, Shepherd myShepherd) {
    String queryString = "SELECT FROM org.ecocean.SuperSpot WHERE enc.leftReferenceSpots.contains(this) && enc.catalogNumber == \"" + enc.getCatalogNumber() + "\" VARIABLES org.ecocean.Encounter enc";
System.out.println(queryString);
    Query query = myShepherd.getPM().newQuery(queryString);
    return asSpots((List)query.execute());
}
ArrayList<SuperSpot> getOLDSpots(Encounter enc, Shepherd myShepherd) {
    String queryString = "SELECT FROM org.ecocean.SuperSpot WHERE enc.spots.contains(this) && enc.catalogNumber == \"" + enc.getCatalogNumber() + "\" VARIABLES org.ecocean.Encounter enc";
System.out.println(queryString);
    Query query = myShepherd.getPM().newQuery(queryString);
    return asSpots((List)query.execute());
}
ArrayList<SuperSpot> getOLDRightSpots(Encounter enc, Shepherd myShepherd) {
    String queryString = "SELECT FROM org.ecocean.SuperSpot WHERE enc.rightSpots.contains(this) && enc.catalogNumber == \"" + enc.getCatalogNumber() + "\" VARIABLES org.ecocean.Encounter enc";
System.out.println(queryString);
    Query query = myShepherd.getPM().newQuery(queryString);
    return asSpots((List)query.execute());
}


ArrayList<SuperSpot> asSpots(List results) {
	if ((results == null) || (results.size() < 1)) return null;
	ArrayList<SuperSpot> rtn = new ArrayList<SuperSpot>();
	for (Object obj : results) {
		rtn.add((SuperSpot)obj);
	}
	return rtn;
}


%>




<%

////NOTE this is fluke-specific, so it assumes there is only one image for both left/right spots!  be warned!
      /// it also assumes that the # or ref spots is sign of dorsal-ness

Shepherd myShepherd=null;
myShepherd = new Shepherd("context0");
String rootDir = getServletContext().getRealPath("/");
String baseDir = ServletUtilities.dataDir("context0", rootDir);


/// set up all FeatureTypes we have
FeatureType.initAll(myShepherd);
/*
HashMap<String,FeatureType> ftypes = new HashMap<String,FeatureType>();
Extent allFT = myShepherd.getPM().getExtent(FeatureType.class, true);
Query ftQuery = myShepherd.getPM().newQuery(allFT);
Collection c = (Collection) (ftQuery.execute());
for (Object f : c) {
	FeatureType ft = (FeatureType) f;
	ftypes.put(ft.getId(), ft);
}
*/

myShepherd.beginDBTransaction();

String num = request.getParameter("number");

ArrayList<Encounter> allEncs = new ArrayList<Encounter>();
allEncs.add(myShepherd.getEncounter(num));


for (Encounter enc : allEncs) {

if (enc == null) {
	out.println("invalid encounter number: " + num);

/*

org.ecocean.flukeEdge.referenceSpots | {"spots":[[480.9483406090681,912.4022902425414,-2],[1299.2008002664916,1097.6773592502464,-2],[2085.884102026471,971.6000755125337,-2]]}
 org.ecocean.flukeEdge.edgeSpots  
*/

} else {
	ArrayList<SuperSpot> refSpots = getOLDLeftReferenceSpots(enc, myShepherd);
	if ((refSpots == null) || (refSpots.size() < 1)) {
		out.println("Encounter " + enc.getEncounterNumber() + " has empty left reference spots; skipping");
		continue;
	}
	MediaAsset spotMA = MediaAsset.findOneByLabel(enc.getMedia(), myShepherd, "_spot");
	if (spotMA == null) {
		out.println("Encounter " + enc.getEncounterNumber() + " has no corresponding spot MediaAsset; skipping");
		continue;
	}

	if ((spotMA.getFeatures() != null) && (spotMA.getFeatures().size() > 0)) {
		out.println("Encounter " + enc.getEncounterNumber() + " already has Features; skipping");
		continue;
	}

	boolean isDorsalFin = false;
	String featureTypePrefix = "org.ecocean.whaleshark";

/*
	if (refSpots.size() != 3) {
		isDorsalFin = true;
		featureTypePrefix = "org.ecocean.dorsalEdge";
	}
*/

	JSONObject params = new JSONObject();
	params.put("spots", SuperSpot.listToJSONArray(refSpots));
	spotMA.addFeature(new Feature(featureTypePrefix + ".referenceSpots", params));

	boolean hasSpots = false;
	params = new JSONObject();
	ArrayList<SuperSpot> spotsL = getOLDSpots(enc, myShepherd);
System.out.println("spotsL -> " + spotsL);
	if ((spotsL != null) && (spotsL.size() > 0)) {
		hasSpots = true;
		params.put("spotsLeft", SuperSpot.listToJSONArray(spotsL));
	}
	ArrayList<SuperSpot> spotsR = getOLDRightSpots(enc, myShepherd);
System.out.println("spotsR -> " + spotsR);
	if ((spotsR != null) && (spotsR.size() > 0)) {
		hasSpots = true;
		params.put("spotsRight", SuperSpot.listToJSONArray(spotsR));
	}

	if (hasSpots) spotMA.addFeature(new Feature(featureTypePrefix + ".spots", params));

	out.println("Encounter " + enc.getEncounterNumber() + " SUCCESS: " + spotMA.getFeatures() + " added to " + spotMA);

}

}



myShepherd.commitDBTransaction();



%>



<p>
done.
