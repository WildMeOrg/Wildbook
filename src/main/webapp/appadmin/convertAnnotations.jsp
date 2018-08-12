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

Extent all = myShepherd.getPM().getExtent(Annotation.class, true);
Query q = myShepherd.getPM().newQuery(all);
q.setOrdering("id");
Collection allAnn = (Collection) (q.execute());


int totalAll = allAnn.size();
int count = 0;
int convCount = 0;

for (Object o : allAnn) {
	Annotation ann = (Annotation)o;
	System.out.println(count + ":" + convCount + ") [" + totalAll + "] " + ann);
	ArrayList<Feature> feats = ann.getFeatures();
	int fct = 0;
	boolean needsMigration = true;
	//really an annotations should only have linking feature(s) on it; so this is kinda a silly check
	if ((feats != null) && (feats.size() > 0)) {
		fct = feats.size();
		for (Feature ft : feats) {
			if (ft.isUnity() || ft.isType("org.ecocean.boundingBox")) {
				needsMigration = false;
				break;
			}
		}
	}
	if (needsMigration) {
		Feature newF = ann.migrateToFeatures();
		out.println("<p>" + count + ") <b><a href=\"obrowse.jsp?type=Annotation&id=" + ann.getId() + "\" target=\"_new\">" + ann.getId() + "</a>: was " + fct + " feature(s)</b>");
		out.println(" [add feature " + newF + "]");
		out.println("</p>");
		convCount++;
	} else {
		out.println("<p class=\"already\" style=\"color: #777;\">" + count + ") " + ann.getId() + " <b>has " + fct + " feature(s) including migrated one</b>:");
		for (Feature f : feats) {
			out.println(f);
		}
		out.println("</p>");
	}

	count++;
	if (convCount > 1000) break;
}


myShepherd.commitDBTransaction();



%>



<p>
done.
</body></html>
