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
<script src="https://ajax.googleapis.com/ajax/libs/jquery/2.2.2/jquery.min.js"></script>
<title>convertAnnotation</title>
</head><body>


<%

////this will convert Annotations away from pointing to MediaAssets directly to the new via-Features way

Shepherd myShepherd=null;
myShepherd = new Shepherd("context0");
String rootDir = getServletContext().getRealPath("/");
String baseDir = ServletUtilities.dataDir("context0", rootDir);

myShepherd.beginDBTransaction();

FeatureType.initAll(myShepherd);

Extent all = myShepherd.getPM().getExtent(MediaAsset.class, true);
Query q = myShepherd.getPM().newQuery(all, "uuid == null");
q.setOrdering("id");
Collection allMA = (Collection) (q.execute());


int totalAll = allMA.size();
int count = 0;

for (Object o : allMA) {
	MediaAsset ma = (MediaAsset)o;

	String newUUID = ma.generateUUIDFromId();
	ma.setUUID(newUUID);
System.out.println(count + ") " + ma.getId() + " --> " + newUUID);
	count++;
	if (count > 10000) break;
}
out.println("done.");


myShepherd.commitDBTransaction();



%>



<p>
done.
</body></html>
