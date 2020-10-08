<%@ page contentType="text/html; charset=utf-8" language="java"
     import="org.ecocean.*,
java.util.ArrayList,
java.io.BufferedReader,
java.io.IOException,
java.io.InputStream,
java.io.InputStreamReader,
java.io.File,
org.json.JSONObject,
org.ecocean.servlet.ServletUtilities,

javax.jdo.Query,
javax.jdo.Extent,
java.util.Collection,


org.ecocean.media.*
              "
%>




<%

/*
	a generic tool to create Encounters from a bunch of MediaAssets.  alter if needed.
*/

    //public static String taxonomyString(String genus, String species) {
String genus = request.getParameter("genus");
String specificEpithet = request.getParameter("specificEpithet");

if ((genus == null) || (specificEpithet == null)) {
	out.println("<p>you <i>must</i> provide both <b>?<u>genus</u>=xxxxx&<u>specificEpithet</u>=yyyyy</b></p>");
	return;
}

String species = Util.taxonomyString(genus, specificEpithet);
out.println("<p>species=(<b>" + species + "</b>)</p>");

String context = ServletUtilities.getContext(request);
Shepherd myShepherd = new Shepherd(context);
myShepherd.beginDBTransaction();

Extent all = myShepherd.getPM().getExtent(MediaAsset.class, true);
Query q = myShepherd.getPM().newQuery(all, "parentId == null");
q.setOrdering("id");
Collection allMA = (Collection) (q.execute());

int count = 0;
for (Object o : allMA) {
	count++;
	////////if (count > 20) break;
	MediaAsset ma = (MediaAsset)o;
    	ArrayList<Annotation> anns = ma.getAnnotations();
	if ((anns != null) && (anns.size() > 0)) {
		out.println("<p>(" + count + ") <b><a target=\"_new\" href=\"obrowse.jsp?type=MediaAsset&id=" + ma.getId() + "\">" + ma + "</a></b> already has <b>Annotations</b>; skipping</p>");
		continue;
	}
	Annotation ann = new Annotation(species, ma);
	Encounter enc = new Encounter(ann);
	myShepherd.getPM().makePersistent(enc);
	out.println("<p>(" + count + ") " + ma + " -><br />" + ann + " -><br /><a target=\"_new\" href=\"obrowse.jsp?type=Encounter&id=" + enc.getCatalogNumber() + "\"><b>" + enc +"</a></b></p>");
	System.out.println("mediaAssetsToEncounters created " + enc + " from " + ma);
}

myShepherd.commitDBTransaction();

%>



