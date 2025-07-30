<!DOCTYPE html PUBLIC "-//W3C//DTD XHTML 1.0 Transitional//EN"
        "http://www.w3.org/TR/xhtml1/DTD/xhtml1-transitional.dtd">
<%@ page contentType="text/html; charset=utf-8" language="java" import="
org.json.JSONObject,
org.json.JSONArray,
org.ecocean.media.*,
java.io.*,java.util.*, java.io.FileInputStream, java.io.File, java.io.FileNotFoundException, org.ecocean.*,org.ecocean.servlet.*,javax.jdo.*, java.lang.StringBuffer, java.util.Vector, java.util.Iterator, java.lang.NumberFormatException"%>
<%@ page import="org.ecocean.shepherd.core.Shepherd" %>

<%!

private MediaAsset getMediaAsset(String id, Shepherd myShepherd) throws IOException {
    List<MediaAsset> mas = myShepherd.getMediaAssetsWithACMId(id);
    if (Util.collectionSize(mas) > 0) return mas.get(0);

    File imageSourceFile = new File("/data/wildbook_data_dir/checkerboard-2400x2400-gray.jpg");
    AssetStore astore = AssetStore.getDefault(myShepherd);

    JSONObject sp = astore.createParameters(new File(Encounter.subdir(id) + File.separator + id + ".jpg"));
    MediaAsset ma = new MediaAsset(astore, sp);
    //ma.setSkipAutoIndexing(true);
    ma.addLabel("_original");
    ma.copyIn(imageSourceFile);
    //ma.updateMetadata();
/*
    try {
        ma.copyIn(file);
        //ma.updateMetadata();
    } catch (IOException ioe) {
        System.out.println("UploadedFiles.makeMediaAsset() failed on " + file + ": " + ioe);
        ioe.printStackTrace();
    }
*/
    return ma;
}

/*
dont really need this cuz we are faking images!!!
private JSONObject findImageJson(JSONArray imageArr, String id) {
    for (int i = 0 ; i < imageArr.length() ; i++) {
        JSONObject ij = imageArr.optJSONObject(i);
        if (ij == null) return null; // oops
        String id = ij.opt
    }
    return null;
}
*/

%>

<%

int makeNew = -1;
try {
    makeNew = Integer.parseInt(request.getParameter("makeNew"));
} catch (Exception ex) {}


File annotationJsonFile = new File("/data/wildbook_data_dir/annot.json");
File embeddingV2JsonFile = new File("/data/wildbook_data_dir/embed-v2.json");
File embeddingV3JsonFile = new File("/data/wildbook_data_dir/embed-v3.json");

JSONObject annotationJson = new JSONObject(Util.readFromFile(annotationJsonFile.toString()));
JSONArray embeddingV2Arr = new JSONArray(Util.readFromFile(embeddingV2JsonFile.toString()));
JSONArray embeddingV3Arr = new JSONArray(Util.readFromFile(embeddingV3JsonFile.toString()));

JSONArray annotArr = annotationJson.optJSONArray("annotations");
JSONArray imageArr = annotationJson.optJSONArray("images");

/*
    "annot_uuid": "a4606e5c-6227-4599-bac5-54e3ec081d6c",
    "embedding": [
      0.5188146829605103,
      0.6490556597709656,
*/

out.println("<p>makeNew=<b>" + makeNew + "</b> (set via ?makeNew=xxx)</p>");

out.println("<p>num annots=<b>" + annotArr.length() + "</b><br />");
out.println("<p>num images=<b>" + imageArr.length() + "</b></p>");

out.println("<p>num v2 embeds=<b>" + embeddingV2Arr.length() + "</b><br />");
out.println("<p>num v3 embeds=<b>" + embeddingV3Arr.length() + "</b></p>");

if (makeNew < 1) {
    out.println("<p>no makeNew; stopping</p>");
    return;
}

Shepherd myShepherd = new Shepherd(request);
myShepherd.beginDBTransaction();

int ct = 0;
for (int i = 0 ; i < annotArr.length() ; i++) {
    JSONObject annJson = annotArr.optJSONObject(i);
    if (annJson == null) throw new RuntimeException("no json object at i=" + i);
    String annId = annJson.optString("uuid", null);
    Annotation ann = myShepherd.getAnnotation(annId);
    if (ann != null) continue;
    String annImageId = annJson.optString("image_uuid", null);
    String annTx = annJson.optString("species", null);
    String annName = annJson.optString("name", null);
    String annViewpoint = annJson.optString("viewpoint", null);
    JSONArray bboxArr = annJson.optJSONArray("bbox");
    double annTheta = annJson.optDouble("theta", 0.0d);
    if ((annId == null) || (bboxArr == null) || (annImageId == null) || (annName == null) || (annViewpoint == null) || (annTx == null)) throw new RuntimeException("invalid json at i=" + i + " => " + annJson);

    ct++;
    out.println("<hr /><p>(" + ct + ") <xmp>" + annJson.toString(4) + "</xmp></p>");
    MediaAsset ma = getMediaAsset(annImageId, myShepherd);
    out.println("<p><b>" + ma + "</b></p>");

    // are we done?
    if (ct > makeNew) {
        out.println("<p><b>END</b></p>");
        i = annotArr.length() + 1;
    }
}


myShepherd.rollbackAndClose();

%>

(finished)
</body>
</html>
