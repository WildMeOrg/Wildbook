<%@ page contentType="text/plain; charset=utf-8" language="java"
     import="org.ecocean.*,
java.util.Map,
java.io.BufferedReader,
java.io.IOException,
java.io.InputStream,
java.io.InputStreamReader,
java.io.File,
java.net.URL,
org.json.JSONObject,

org.ecocean.servlet.ServletUtilities,
org.ecocean.media.*
              "
%>
<%@ page import="org.ecocean.shepherd.core.Shepherd" %>


<%

/////////////////////// NOTE: this is a work-in-progress; alpha at best
///////////////////////  more development needed before being used in production

Shepherd myShepherd = new Shepherd("context0");
myShepherd.beginDBTransaction();

AssetStore astore = AssetStore.getDefault(myShepherd);

String id = request.getParameter("id");


MediaAsset ma = myShepherd.getMediaAsset(id);

if (ma == null) throw new RuntimeException("bad id " + id);
if (ma.getStore().getId() == astore.getId()) throw new RuntimeException(ma + " is already in " + astore);


JSONObject params = ma.getParameters();
String imgUrl = params.optString("media_url_https", null);
out.println(imgUrl);

if (imgUrl == null) {
    // toplevel "tweet" asset, so we just kinda move the store and bail
    ma.setStore(astore);

} else {
    // bring the image local
    File tmpFile = new File("/tmp/detwitter-" + id);
    try {
        URLAssetStore.fetchFileFromURL(new URL(imgUrl), tmpFile);
    } catch (Exception ex) {
        ex.printStackTrace();
        throw ex;
    }
    out.println(tmpFile);

    String filename = params.optString("id", "twitter-" + id);
    String dirId = Util.generateUUID();
    String path = Encounter.subdir(dirId) + File.separator + filename;
    out.println(path);
    params.put("path", path);
    params.put("userFilename", filename);
    // FIXME if this script is used, MediaAsset.setStore() must be re-enabled; it is currently commented out
    ma.setStore(astore);
    ma.setParameters(params);
    ma.copyIn(tmpFile);
    ma.updateMetadata();
/// make standard children FIXME
}

out.println(ma);

myShepherd.commitDBTransaction();
myShepherd.closeDBTransaction();

%>
