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
java.util.regex.Matcher,
java.util.regex.Pattern,

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
NOTE: this "multi" version produces one Encounter per MediaAsset (and will not create an Encounter if already exists for that MA)
NOTE2: you must set ids[] *from within code below*

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


also, note the ID= !!!!
2016, Above the Ground, CRESLI, Great South Channel, ID=Flounder, Megaptera novaeangliae, fluke, humpback whale
2016, Above the Ground, CRESLI, Great South Channel, ID=Coral, Megaptera novaeangliae, fluke, humpback whale
2016, Above the Ground, CRESLI, Great South Channel, ID=I-Vee on right, Megaptera novaeangliae, fluke, hum
American Princess, Breezy Point, ID=NYC0040, Megaptera novaeangliae, Queens, humpback whale

	e.g. might want to add some other stuff to enc when created...
*/

        //MediaAsset ids
        int[] ids = new int[]{
-1,
-2
        };
	Shepherd myShepherd=null;
	String context = "context0";
	myShepherd = new Shepherd(context);

	//String[] ids = request.getParameterValues("id");
	//if ((ids == null) || (ids.length < 1)) throw new RuntimeException("you must have at least one MediaAsset id passed as id=VALUE");
	String species = request.getParameter("species");
	if (species == null) throw new RuntimeException("you must provide species=VALUE");

	myShepherd.beginDBTransaction();

	//ArrayList<Annotation> anns = new ArrayList<Annotation>();
	for (int i = 0 ; i < ids.length ; i++) {
		MediaAsset ma = MediaAssetFactory.load(ids[i], myShepherd);
		if (ma == null) {
			out.println("<p>no MediaAsset for <b>id=" + ids[i] + "</b></p>");
			continue;
		}
                JSONObject mdata = null;
                if (ma.getMetadata() != null) mdata = ma.getMetadata().getData();
		out.println("<p><b title=\"" + ma.toString() + "\">MediaAsset " + ids[i] + ":</b> ");
//out.println("<p>" + mdata + "</p>");
		ArrayList<Annotation> anns = ma.getAnnotations();
                Encounter found = null;
		if ((anns != null) && (anns.size() > 0)) {
			for (Annotation ann : anns) {
				found = ann.findEncounter(myShepherd);
				if (found != null) break;
			}
			if (found != null) {
				out.println("seems to be already attached to <a href=\"obrowse.jsp?type=Encounter&id=" + found.getCatalogNumber() + "\" title=\"" + found.toString() + "\" target=\"_new\">" + found.getCatalogNumber() + "</a>");
			}
		} else {
    			Annotation newAnn = new Annotation(species, ma);
			newAnn.setIsExemplar(true); //we think?  TODO maybe use "fluke" in keywords?
			anns.add(newAnn);
			out.println(newAnn + " (new)");
		}
                if (found != null) continue;  //no enc needed
	        if (anns.size() < 1) {
		    out.println(" <i>hmm... no Annotations to create Encounter with... </i>");
	        } else {
out.println("<i>" + anns + "</i>");
                    String exifKeywords = null;
                    if ((mdata != null) && (mdata.optJSONObject("exif") != null) && (mdata.getJSONObject("exif").optJSONObject("Iptc") != null)) exifKeywords = mdata.getJSONObject("exif").getJSONObject("Iptc").optString("Keywords", null);

out.println(" (" + exifKeywords + ") ");
		    Encounter enc = new Encounter(anns);
                    enc.setSubmitterID("Akopelman");
                    enc.setRecordedBy("Artie Kopelman");
                    enc.setSubmitterEmail("president@cresli.org");
                    enc.setState("approved");
                    String indivId = null;
                    if (exifKeywords != null) {
                        enc.addComments("<p class=\"exif-keywords\"><b>keywords:</b> " + exifKeywords + "</p>");
                        Pattern pat = Pattern.compile("ID=([^;,]+)");
                        Matcher m = pat.matcher(exifKeywords);
                        if (m.find()) {
                            indivId = m.group(1);
                            out.println(" [id <b>" + indivId + "</b>] ");
                        }
                    }

                    if (indivId != null) {
                        MarkedIndividual indiv = myShepherd.getOrCreateMarkedIndividual(indivId, enc);
			if (indiv != null) {
				indiv.addEncounter(enc, context);  //only needed if indiv already existed
                        	myShepherd.getPM().makePersistent(indiv);
			}
                    }

		    myShepherd.getPM().makePersistent(enc);
			System.out.println("mediaAssetsToEncountersMulti: [" + i + "] " + ids[i] + " -> " + enc);
		    out.println("<p>successfully created <a href=\"obrowse.jsp?type=Encounter&id=" + enc.getCatalogNumber() + "\" title=\"" + enc.toString() + "\" target=\"_new\">" + enc.getCatalogNumber() + "</a></p>");
	        }
                out.println("</p>");
        }

	myShepherd.commitDBTransaction();
	//myShepherd.rollbackDBTransaction();

%>


</body></html>
