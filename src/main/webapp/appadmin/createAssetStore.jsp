<%@ page contentType="text/html; charset=utf-8" language="java"
     import="org.ecocean.*,
java.util.Map,
java.io.BufferedReader,
java.io.IOException,
java.io.InputStream,
java.io.InputStreamReader,
java.io.File,
org.json.JSONObject,

org.ecocean.servlet.ServletUtilities,
org.ecocean.media.*
              "
%>




<%

Shepherd myShepherd=null;
myShepherd = new Shepherd("context0");

String rootDir = getServletContext().getRealPath("/");
String baseDir = ServletUtilities.dataDir("context0", rootDir);

out.println("<xmp>");


JSONObject c = new JSONObject();

/*
//////////////// begin S3 //////////////
c.put("urlAccessible", true);
c.put("bucket", "default-bucket-name-goes-here");

//these are "optional".  if left out, then default aws credentials will be used, which is somewhere *like* /usr/share/tomcat7/.aws/credentials  (ymmv)
c.put("AWSAccessKeyId", "XXXXX");
c.put("AWSSecretAccessKey", "YYYYY");

AssetStoreConfig cfg = new AssetStoreConfig(c.toString());
S3AssetStore as = new S3AssetStore("example S3 AssetStore", cfg, true);
myShepherd.getPM().makePersistent(as);
//////////////// end S3 //////////////
*/




//////////////// begin local //////////////
LocalAssetStore as = new LocalAssetStore("example Local AssetStore", new File("/var/lib/tomcat7/some/path").toPath(), "http://example.com/path", true);
myShepherd.getPM().makePersistent(as);
//////////////// end local //////////////



 
out.println("created: " + as.toString());
out.println(as.getConfig());



%>


done.
