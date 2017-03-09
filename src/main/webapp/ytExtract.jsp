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

org.ecocean.media.*
              "
%>




<%

JSONObject rtn = new JSONObject("{\"success\": false}");

Shepherd myShepherd = new Shepherd("context0");
myShepherd.beginDBTransaction();

String id = request.getParameter("id");
MediaAsset ma = MediaAssetFactory.load(Integer.parseInt(id), myShepherd);
if (ma == null) {
	rtn.put("error", "no MediaAsset with id=" + id);
	out.println(rtn);
	myShepherd.rollbackDBTransaction();
	return;
}
rtn.put("assetId", id);

ArrayList<MediaAsset> frameMAs = YouTubeAssetStore.findFrames(ma, myShepherd);

if ((frameMAs == null) || (frameMAs.size() < 1)) {
	boolean ok = YouTubeAssetStore.extractFramesAndParse(myShepherd, ma, false);
	if (!ok) {
		rtn.put("error", "could not extract frames");
		out.println(rtn);
		myShepherd.rollbackDBTransaction();
		return;
	}

	myShepherd.commitDBTransaction();
	frameMAs = YouTubeAssetStore.findFrames(ma, myShepherd);

} else {
	rtn.put("info", "frames already existed; not remaking");
}

JSONArray fs = new JSONArray();
for (MediaAsset fma : frameMAs) {
	fs.put(fma.getId());
}
rtn.put("frameAssets", fs);

rtn.put("success", true);


out.println(rtn);



%>




