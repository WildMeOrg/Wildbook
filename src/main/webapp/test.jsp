<%@ page contentType="text/html; charset=utf-8" language="java"
     import="org.ecocean.*,
java.util.Map,
java.io.BufferedReader,
java.io.IOException,
java.io.InputStream,
java.io.InputStreamReader,
java.io.File,
org.json.JSONObject,

org.ecocean.media.*
              "
%>




<%

JSONObject params = new JSONObject();
params.put("path", "/tmp/store/fluke2.jpg");

LocalAssetStore las = new LocalAssetStore("testStore", new File("/tmp/store").toPath(), "http://foo.bar/webroot/testStore", true);

MediaAsset ma = las.create(params);

out.println(ma.localPath());
//out.println(ma.webPathString());
out.println(ma.getID());

%>


hello
