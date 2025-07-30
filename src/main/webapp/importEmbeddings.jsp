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
    "annot_uuid": "a4606e5c-6227-4599-bac5-54e3ec081d6c",
    "embedding": [
      0.5188146829605103,
      0.6490556597709656,
*/

private Feature makeFeature(JSONArray bbox) {
    JSONObject params = new JSONObject();
    params.put("x", bbox.getDouble(0));
    params.put("y", bbox.getDouble(1));
    params.put("width", bbox.getDouble(2));
    params.put("height", bbox.getDouble(3));
    return new Feature("org.ecocean.boundingBox", params);
}

private String fixTaxonomy(String orig) {
    if ((orig == null) || (orig.length() < 1)) return orig;
    String rtn = orig.replaceAll("_", " ");
    return rtn.substring(0, 1).toUpperCase() + rtn.substring(1);
}

private Embedding findEmbedding(JSONArray arr, String id) {
    JSONArray embArr = null;
    for (int i = 0 ; i < arr.length() ; i++) {
        JSONObject jemb = arr.optJSONObject(i);
        if (jemb.optString("annot_uuid", null).equals(id)) {
            embArr = jemb.optJSONArray("embedding");
            i = arr.length() + 1;
        }
    }
    if (embArr == null) throw new RuntimeException("could not find embedding array for id=" + id);
    Embedding emb = new Embedding(null, "miewID", null, embArr);
    return emb;
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
FeatureType.initAll(myShepherd);

int ct = 0;
for (int i = 0 ; i < annotArr.length() ; i++) {
    JSONObject annJson = annotArr.optJSONObject(i);
    if (annJson == null) throw new RuntimeException("no json object at i=" + i);
    String annId = annJson.optString("uuid", null);
    Annotation ann = myShepherd.getAnnotation(annId);
    if (ann != null) {
        System.out.println("importEmbeddings.jsp: skipping ann id=" + annId + "; exists in db");
        continue;
    }

    String annImageId = annJson.optString("image_uuid", null);
    String annTx = fixTaxonomy(annJson.optString("species", null));
    String annName = annJson.optString("name", null);
    String annViewpoint = annJson.optString("viewpoint", null);
    JSONArray bboxArr = annJson.optJSONArray("bbox");
    double annTheta = annJson.optDouble("theta", 0.0d);
    if ((annId == null) || (bboxArr == null) || (annImageId == null) || (annName == null) || (annViewpoint == null) || (annTx == null)) throw new RuntimeException("invalid json at i=" + i + " => " + annJson);

    MediaAsset ma = getMediaAsset(annImageId, myShepherd);

    Feature ft = makeFeature(bboxArr);
    ma.addFeature(ft);
    ann = new Annotation(annTx, ft);
    ann.setId(annId);
    ann.setAcmId(annId);
    ann.setViewpoint(annViewpoint);
    ann.setTheta(annTheta);

    Embedding embV2 = findEmbedding(embeddingV2Arr, annId);
    embV2.setMethodVersion("v2");
    embV2.setAnnotation(ann);
    Embedding embV3 = findEmbedding(embeddingV3Arr, annId);
    embV3.setMethodVersion("v3");
    embV3.setAnnotation(ann);

    MarkedIndividual indiv = null;
    if (annName != null) {
        indiv = myShepherd.getMarkedIndividual(annName);
        if (indiv == null) {
            indiv = new MarkedIndividual();
            indiv.setId(annName);
        }
    }

    Encounter enc = new Encounter(false);
    enc.addAnnotation(ann);
    if (indiv != null) enc.setIndividual(indiv);
    enc.setTaxonomyFromString(annTx);

    myShepherd.getPM().makePersistent(ma);
    myShepherd.getPM().makePersistent(ann);
    myShepherd.getPM().makePersistent(enc);
    if (indiv != null) myShepherd.getPM().makePersistent(indiv);

    ct++;
    out.println("<hr /><p>(" + ct + ")</p>");
    out.println("<p><a target=\"_blank\" href=\"/obrowse.jsp?type=MediaAsset&id=" + ma.getId() + "\"><b>" + ma + "</b></a></p>");
    out.println("<p><b>" + ann + "</b></p>");
    out.println("<p><b>" + ft + "</b></p>");
    out.println("<p><b>" + ann.getEmbeddings() + "</b></p>");

    // are we done?
    if (ct >= makeNew) {
        out.println("<p><b>END</b></p>");
        i = annotArr.length() + 1;
    }
}


myShepherd.commitDBTransaction();
myShepherd.closeDBTransaction();

%>

(finished)
</body>
</html>
