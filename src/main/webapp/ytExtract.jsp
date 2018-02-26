<%@ page contentType="text/plain; charset=utf-8" language="java"
     import="org.ecocean.*,
java.util.ArrayList,
java.io.BufferedReader,
java.io.IOException,
java.io.InputStream,
java.io.InputStreamReader,
java.io.File,
org.json.JSONObject,
org.json.JSONArray,

org.ecocean.identity.IBEISIA,
org.ecocean.media.*
              "
%>




<%

IBEISIA.setIAPrimed(true);  ///we do this so we can send off for detection even if IA not primed.  HACK!

JSONObject rtn = new JSONObject("{\"success\": false}");

//will create an Encounter with all MediaAssets connected via trivial Annotations
boolean createEncounter = ((request.getParameter("createEncounter") != null) && !request.getParameter("createEncounter").toLowerCase().equals("false"));

Shepherd myShepherd = new Shepherd("context0");
myShepherd.setAction("ytExtract.jsp");
myShepherd.beginDBTransaction();

String id = request.getParameter("id");
MediaAsset ma = MediaAssetFactory.load(Integer.parseInt(id), myShepherd);
if (ma == null) {
	rtn.put("error", "no MediaAsset with id=" + id);
	out.println(rtn);
	myShepherd.rollbackDBTransaction();
	myShepherd.closeDBTransaction();
	return;
}
rtn.put("assetId", id);

try{
	ArrayList<MediaAsset> frameMAs = YouTubeAssetStore.findFrames(ma, myShepherd);
	
	if ((frameMAs == null) || (frameMAs.size() < 1)) {
		boolean ok = YouTubeAssetStore.extractFramesAndParse(myShepherd, ma, false);
		if (!ok) {
			rtn.put("error", "could not extract frames");
			out.println(rtn);
			myShepherd.rollbackDBTransaction();
			myShepherd.closeDBTransaction();
			return;
		}
	
		frameMAs = YouTubeAssetStore.findFrames(ma, myShepherd);
	
	} else {
		rtn.put("info", "frames already existed; not remaking");
		createEncounter = false;  //nope you dont get one!
	}
	
	ArrayList<Annotation> anns = new ArrayList<Annotation>();
	JSONArray fs = new JSONArray();
	for (MediaAsset fma : frameMAs) {
		fs.put(fma.getId());
		if (createEncounter) anns.add(new Annotation(null, fma));
	}
	rtn.put("frameAssets", fs);
	
	if (createEncounter) {
		Encounter enc = new Encounter(anns);
		myShepherd.getPM().makePersistent(enc);
		rtn.put("encounterId", enc.getCatalogNumber());
	}
	
	rtn.put("success", true);
	out.println(rtn);
	
}
catch(Exception e){
	e.printStackTrace();
}
finally{
	myShepherd.commitDBTransaction();
	myShepherd.closeDBTransaction();
}





%>




