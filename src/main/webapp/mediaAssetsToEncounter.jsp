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
java.util.List,
java.util.ArrayList,
org.json.JSONObject,
javax.jdo.Query,
javax.jdo.Extent,
java.util.HashMap,

org.ecocean.Annotation,
org.ecocean.media.*
              "
%>

<%!
%>

<html><head>
<title>MediaAssets -&gt; Encounter</title>
</head><body>


<%
/*
	this is pretty much hardcoded to A Kopelmans exif data...  modify as needed!  here is example of exif we are maybe using:

exif. --

Iptc: {
Keywords: "2014;CRESLI;CRESLI/Viking Fleet 2014 Great South Channel trip;GSC;Great South Channel;Megaptera novaeangliae;Viking Fleet;humpback whale",
Copyright Notice: "A. H. Kopelman for CRESLI",
By-line: "A.H. KOPELMAN",
Application Record Version: "4",
Time Created: "142014",
Date Created: "Mon Aug 11 00:00:00 PDT 2014",
Digital Time Created: "142014"
},

	e.g. might want to add some other stuff to enc when created...
*/

	Shepherd myShepherd=null;
	myShepherd = new Shepherd("context0");

	String idList = "";
	String[] ids = request.getParameterValues("id");
	if ((ids == null) || (ids.length < 1)) throw new RuntimeException("you must have at least one MediaAsset id passed as id=VALUE");
	String species = request.getParameter("species");
	if (species == null) throw new RuntimeException("you must provide species=VALUE");

	myShepherd.beginDBTransaction();

	ArrayList<Annotation> anns = new ArrayList<Annotation>();
	for (int i = 0 ; i < ids.length ; i++) {
		MediaAsset ma = MediaAssetFactory.load(Integer.parseInt(ids[i]), myShepherd);
		if (ma == null) {
			out.println("<p>no MediaAsset for <b>id=" + ids[i] + "</b></p>");
			continue;
		}
		out.println("<p><b title=\"" + ma.toString() + "\">MediaAsset " + ids[i] + ":</b> ");
		ArrayList<Annotation> hasAnns = ma.getAnnotations();
		if ((hasAnns != null) && (hasAnns.size() > 0)) {
			Encounter found = null;
			for (Annotation ann : hasAnns) {
				found = ann.findEncounter(myShepherd);
				if (found != null) break;
			}
			if (found != null) {
				out.println("seems to be already attached to <a href=\"obrowse.jsp?type=Encounter&id=" + found.getCatalogNumber() + "\" title=\"" + found.toString() + "\" target=\"_new\">" + found.getCatalogNumber() + "</a></p>");
			} else {
				anns.addAll(hasAnns);
				out.println(hasAnns + "</p>");
			}
		} else {
    			Annotation newAnn = new Annotation(species, ma);
			anns.add(newAnn);
			out.println(newAnn + " (new)</p>");
		}
		idList += ids[i] + " ";
	}

	if (anns.size() < 1) {
		out.println("<p>hmm... no Annotations to create Encounter with... </p>");
	} else {
		Encounter enc = new Encounter(anns);
		myShepherd.getPM().makePersistent(enc);
		out.println("<p>successfully created <a href=\"obrowse.jsp?type=Encounter&id=" + enc.getCatalogNumber() + "\" title=\"" + enc.toString() + "\" target=\"_new\">" + enc.getCatalogNumber() + "</a></p>");
		System.out.println("mediaAssetsToEncounter: " + idList + "--> " + enc.getCatalogNumber());
	}

	myShepherd.commitDBTransaction();

%>


</body></html>
