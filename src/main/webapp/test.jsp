<%@ page contentType="text/html; charset=utf-8" language="java"
     import="org.ecocean.*,
java.util.Map,
java.io.BufferedReader,
java.io.IOException,
java.io.InputStream,
java.io.InputStreamReader,
java.io.File,

org.ecocean.media.*
              "
%>




<%


LocalAssetStore las = new LocalAssetStore("testStore", new File("/tmp/store").toPath(), "http://foo.bar/webroot/testStore", true);

MediaAsset ma = las.create("/tmp/store/fluke2.jpg", "testType");

out.println(ma.getPath());
out.println(ma.webPathString());
out.println(ma.getID());

%>


hello
