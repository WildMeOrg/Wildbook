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


int count = 0;
int convCount = 0;

for (Object o : allAnn) {
	Annotation ann = (Annotation)o;
	System.out.println(count + ") " + ann);
	ArrayList<Feature> feats = ann.getFeatures();
	if ((feats == null) || (feats.size() < 1)) {
		Feature newF = ann.migrateToFeatures();
		out.println("<p>" + count + ") <b><a href=\"obrowse.jsp?type=Annotation&id=" + ann.getId() + "\" target=\"_new\">" + ann.getId() + "</a>: no features</b>");
		out.println(" [add feature " + newF + "]");
		out.println("</p>");
		convCount++;
	} else {
		out.println("<p style=\"color: #555;\">" + count + ") " + ann.getId() + " <b>has " + feats.size() + " feature(s)</b>:");
		for (Feature f : feats) {
			out.println(f);
		}
		out.println("</p>");
	}

	count++;
	if (convCount > 7) break;
}


myShepherd.commitDBTransaction();



%>



<p>
done.
