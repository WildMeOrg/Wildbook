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
javax.jdo.Query,
javax.jdo.Extent,
java.util.HashMap,

org.ecocean.Annotation,
org.ecocean.media.*
              "
%>


<html><head>
<title>createMediaAssets</title>
<style>
div { font-size: 0.8em; }
</style>
</head><body>


<%

/**** IMPORTANT - this is not your typical "migrate to media asset"... this creates *unlinked* (e.g. not feature/annotation connection to encounters)
      MediaAssets from SinglePhotoVideos... really this only has limited use and if you dont know if you need to do this, you probably dont.
      if you have questions -- and you probably should -- ask jonv.
*/

Shepherd myShepherd = null;
myShepherd = new Shepherd("context0");
String rootDir = getServletContext().getRealPath("/");
String baseDir = ServletUtilities.dataDir("context0", rootDir);

//myShepherd.beginDBTransaction();

LocalAssetStore las = ((LocalAssetStore) (myShepherd.getPM().getObjectById(myShepherd.getPM().newObjectIdInstance(LocalAssetStore.class, 1), true)));
//out.println(las);

//FeatureType.initAll(myShepherd);

Extent all = myShepherd.getPM().getExtent(SinglePhotoVideo.class, true);
Query q = myShepherd.getPM().newQuery(all);
q.setOrdering("filename");
Collection allSPV = (Collection) (q.execute());


//int totalAll = allSPV.size();
int count = 0;

for (Object o : allSPV) {
	SinglePhotoVideo spv = (SinglePhotoVideo)o;
	String uuid = spv.getFilename();
	int i = uuid.indexOf(".");
	if (i > -1) uuid = uuid.substring(0,i);
	if (!Util.isUUID(uuid)) {
		System.out.println(count + ") " + uuid + " NOT uuid? skipping");
		continue;
	}
	MediaAsset exist = MediaAssetFactory.loadByUuid(uuid, myShepherd);
	if (exist != null) {
		System.out.println(count + ") " + uuid + " exists already at " + exist + "; skipping");
		continue;
	}
	String fpath = spv.getFullFileSystemPath();
	i = fpath.indexOf("encounters/");
	if (i > -1) fpath = fpath.substring(i);

	JSONObject params = new JSONObject();
	params.put("path", fpath);
	MediaAsset ma = new MediaAsset(las, params);
	ma.setUUID(uuid);
	ma.addLabel("_original");
	ma.updateMetadata();
	MediaAssetFactory.save(ma, myShepherd);
    	ma.updateStandardChildren(myShepherd);
out.println("<div>" + uuid + " -> " + fpath + " -> " + ma + "</div>");
	System.out.println(count + ") " + uuid + " -> " + ma.getId());

	count++;
	if (count > 100) break;
}
out.println("done.");


//myShepherd.commitDBTransaction();




%>



</body></html>
