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

S3AssetStore s3as = new S3AssetStore("test S3", false);

JSONObject sp = new JSONObject();
	//String bucketName = "temporary-test";
	//String key        = "dorsal-fin.jpg";
sp.put("bucket", "temporary-test");
sp.put("key", "dorsal-fin.jpg");
sp.put("urlAccessible", true);
MediaAsset ma3 = s3as.create(sp);
out.println(ma3.localPath());
out.println(ma3.webURL());
ma3.cacheLocal();

%>


hello
