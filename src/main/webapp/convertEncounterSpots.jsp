<%@ page contentType="text/html; charset=utf-8" language="java"
     import="org.ecocean.*,
org.ecocean.servlet.ServletUtilities,
org.ecocean.media.*,
java.util.Map,
java.io.BufferedReader,
java.io.IOException,
java.io.InputStream,
java.io.InputStreamReader,
java.io.File,
java.util.Collection,
java.util.ArrayList,
org.json.JSONObject,
org.ecocean.identity.Feature,
org.ecocean.identity.FeatureType,
javax.jdo.Query,
javax.jdo.Extent,
java.util.HashMap,

org.ecocean.media.*
              "
%>




<%

////NOTE this is fluke-specific, so it assumes there is only one image for both left/right spots!  be warned!
      /// it also assumes that the # or ref spots is sign of dorsal-ness

Shepherd myShepherd=null;
myShepherd = new Shepherd("context0");
String rootDir = getServletContext().getRealPath("/");
String baseDir = ServletUtilities.dataDir("context0", rootDir);


/// set up all FeatureTypes we have
HashMap<String,FeatureType> ftypes = new HashMap<String,FeatureType>();
Extent allFT = myShepherd.getPM().getExtent(FeatureType.class, true);
Query ftQuery = myShepherd.getPM().newQuery(allFT);
Collection c = (Collection) (ftQuery.execute());
for (Object f : c) {
	FeatureType ft = (FeatureType) f;
	ftypes.put(ft.getId(), ft);
}

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
	ArrayList<SuperSpot> refSpots = enc.getLeftReferenceSpots();
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
	String featureTypePrefix = "org.ecocean.flukeEdge";

	if (refSpots.size() != 3) {
		isDorsalFin = true;
		featureTypePrefix = "org.ecocean.dorsalEdge";
	}

	JSONObject params = new JSONObject();
	params.put("spots", SuperSpot.listToJSONArray(refSpots));
	spotMA.addFeature(new Feature(ftypes.get(featureTypePrefix + ".referenceSpots"), params));

	boolean hasSpots = false;
	params = new JSONObject();
	if (enc.getNumSpots() > 0) {
		hasSpots = true;
		params.put("spotsLeft", SuperSpot.listToJSONArray(enc.getSpots()));
	}
	if (enc.getNumRightSpots() > 0) {
		hasSpots = true;
		params.put("spotsRight", SuperSpot.listToJSONArray(enc.getRightSpots()));
	}

	if (hasSpots) spotMA.addFeature(new Feature(ftypes.get(featureTypePrefix + ".edgeSpots"), params));

	out.println("Encounter " + enc.getEncounterNumber() + " SUCCESS: " + spotMA.getFeatures() + " added to " + spotMA);

}

}



myShepherd.commitDBTransaction();



%>



<p>
done.
