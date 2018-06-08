<html><head><title>obrowse</title>
<script src="tools/jquery/js/jquery.min.js"></script>
<style>

.img-margin {
    float: right;
    display: inline-block;
}

#img-wrapper {
    position: relative;
}
.featurebox {
    position: absolute;
    width: 100%;
    height: 100%;
    top: 0;
    left: 0;
    outline: dashed 2px rgba(255,255,0,0.8);
    box-shadow: 0 0 0 2px rgba(0,0,0,0.6);
}
.mediaasset {
	position: relative;
}
.mediaasset img {
	xposition: absolute;
	top: 0;
	xright: 20px;
	max-width: 350px;
}

.deprecated {
	color: #888;
}

</style>

<script>
var features = {};

function addFeature(id, bbox) {
    features[id] = bbox;
}

function drawFeatures() {
    for (id in features) {
        drawFeature(id);
    }
}
function drawFeature(id) {
    if (!(id in features)) return;
    var bbox = features[id];
    var el = $('#img-wrapper');
    var img = $('img')[0];
    var f = $('<div title="' + id + '" id="feature-' + id + '" class="featurebox" />');
    el.append(f);
    if (!bbox || !bbox.width) return;  //trivial annot, so leave it as whole image
    var scale = img.height / img.naturalHeight;
    f.css('width', (bbox.width * scale) + 'px');
    f.css('height', (bbox.height * scale) + 'px');
    f.css('left', (bbox.x * scale) + 'px');
    f.css('top', (bbox.y * scale) + 'px');
}
</script>

</head><body>
<%@ page contentType="text/html; charset=utf-8" 
		language="java"
        import="org.ecocean.servlet.ServletUtilities,org.ecocean.*,
org.ecocean.media.*,
java.util.Vector,
java.util.ArrayList,
org.json.JSONObject,
java.util.Properties" %>
<%!

	public Shepherd myShepherd = null;

	private ArrayList<Object> shown = new ArrayList<Object>();

	private String showForm() {
		return "(form)";
	}

	private String niceJson(JSONObject j) {
		if (j == null) return "<b>[none]</b>";
		return "<pre class=\"json\">" + j.toString().replaceAll(",", ",\n") + "</pre>";
	}

	private String showEncounter(Encounter enc, HttpServletRequest req) {
		if (enc == null) return "<b>[none]</b>";
		String h = "<div class=\"encounter shown\"><a target=\"_new\" href=\"encounters/encounter.jsp?number=" + enc.getCatalogNumber() + "\">Encounter <b>" + enc.getCatalogNumber() + "</b></a>";
		if ((enc.getAnnotations() != null) && (enc.getAnnotations().size() > 0)) {
			h += "<div>Annotations:<ul>";
			for (int i = 0 ; i < enc.getAnnotations().size() ; i++) {
				Annotation ann = enc.getAnnotations().get(i);
				if (ann!=null) {
					h += "<li><a href=\"obrowse.jsp?type=Annotation&id=" + enc.getAnnotations().get(i).getId() + "\">Annotation " + enc.getAnnotations().get(i).getId() + "</a></li>";
				} else {
					h += "<li>NULL Annotation!</li>";
				}
			}
			h += "</ul></div>";
		} else if(enc.getAnnotations()==null){
			h+="<div> Annotations = NULL</div>";
		} else if(enc.getAnnotations().size()==0) {
			h+="<div> Annotations are EMPTY</div>";
		}
		// Add some Occurrence and MarkedIndividual Stuff.
		h+= "<p>OccurrenceID: <a href='occurrence.jsp?number="+enc.getOccurrenceID()+"'>"+enc.getOccurrenceID()+"</a></p>";
		h+= "<p>IndividualID: <a href='obrowse.jsp?type=MarkedIndividual&id="+enc.getIndividualID()+"'>"+enc.getIndividualID()+"</a></p>";
		h+= "<p>webUrl: <a href="+enc.getWebUrl(req)+">"+enc.getWebUrl(req)+"</a></p>";
		return h + "</div>";
	}

	private String showMarkedIndividual(MarkedIndividual ind, HttpServletRequest req) {
		if (ind == null) return "<b>[none]</b>";
		String h = "<div class=\"individual shown\"><a target=\"_new\" href=\"individuals.jsp?number=" + ind.getIndividualID() + "\">Individual <b>" + ind.getIndividualID() + "</b></a>";
		h += "<p>Nickname: "+ind.getNickName()+"</p>";
		h += "<p>Sex: "+ind.getSex()+"</p>";
		h += "<p>Taxonomy: "+ind.getSpecificEpithet()+"</p>";
		h += "<p>webUrl: <a href="+ind.getWebUrl(req)+">"+ind.getWebUrl(req)+"</a></p>";

		Vector encs = ind.getEncounters();
		if ((encs != null) && (encs.size() > 0)) {
			h += "<div>Encounters:<ul>";
			for (int i = 0 ; i < encs.size() ; i++) {
				Encounter enc = (Encounter) encs.get(i);
				if (enc!=null) {
					h += "<li><a href=\"obrowse.jsp?type=Encounter&id=" + enc.getCatalogNumber() + "\">Encounter " + enc.getCatalogNumber() + "</a>";
					h+= showEncounter(enc, req);
					h+= "</li>";	
				} else {
					h += "<li>NULL Emcpimter!</li>";
				}
			}
			h += "</ul></div>";
		}
		h += "</div>";
		return h;
	}

	private String showFeature(Feature f, HttpServletRequest req) {
		if (f == null) return "<b>[none]</b>";
		if (shown.contains(f)) return "<div class=\"feature shown\">Feature <b>" + f.getId() + "</b></div>";
		shown.add(f);
		String h = "<div class=\"feature\">Feature <b>" + f.getId() + "</b><ul>";
		h += "<li>type: <b>" + ((f.getType() == null) ? "[null] (unity)" : f.getType()) + "</b></li>";
		h += "<li>" + showMediaAsset(f.getMediaAsset(), req) + "</li>";
		h += "<li>" + showAnnotation(f.getAnnotation(), req) + "</li>";
		h += "<li>" + showMediaAsset(f.getMediaAsset()) + "</li>";
		h += "<li>" + showAnnotation(f.getAnnotation()) + "</li>";
        h += "<script>addFeature('" + f.getId() + "', " + f.getParametersAsString() + ");</script>";
		h += "<li>parameters: " + niceJson(f.getParameters()) + "</li>";
		return h + "</ul></div>";
	}

	private String showAnnotation(Annotation ann, HttpServletRequest req) {
		if (ann == null) return "annotation: <b>[none]</b>";
		if (shown.contains(ann)) return "<div class=\"annotation shown\">Annotation <b>" + ann.getId() + "</b></div>";
		shown.add(ann);
		String h = "<div class=\"annotation\">Annotation <b>" + ann.getId() + "</b><ul>";
		h += "<li>species: <b>" + ((ann.getSpecies() == null) ? "[null]" : ann.getSpecies()) + "</b></li>";
		h += "<li>features: " + showFeatureList(ann.getFeatures(), req) + "</li>";
		h += "<li>encounter: " + showEncounter(Encounter.findByAnnotation(ann, myShepherd), req) + "</li>";
		h += "<li class=\"deprecated\">" + showMediaAsset(ann.getMediaAsset(), req) + "</li>";
		return h + "</ul></div>";
	}

	private String showLabels(ArrayList<String> l) {
		if ((l == null) || (l.size() < 1)) return "[none]";
		String h = "<ul>";
		for (int i = 0 ; i < l.size() ; i++) {
			h += "<li>" + l.get(i) + "</li>";
		}
		return h + "</ul>";
	}

	private String showMediaAssetList(ArrayList<MediaAsset> l) {
		if ((l == null) || (l.size() < 1)) return "[none]";
		return "";
	}

	private String showFeatureList(ArrayList<Feature> l, HttpServletRequest req) {
		if ((l == null) || (l.size() < 1)) return "[none]";
		String h = "<ul>";
		for (int i = 0 ; i < l.size() ; i++) {
			h += "<li>" + showFeature(l.get(i), req) + "</li>";
		}
		return h + "</ul>";
	}

	private String showMediaAsset(MediaAsset ma, HttpServletRequest req) {
		if (ma == null) return "asset: <b>[none]</b>";
		if (shown.contains(ma)) return "<div class=\"mediaasset shown\"><a href=\"obrowse.jsp?type=MediaAsset&id="+ma.getId()+"\"> MediaAsset <b>" + ma.getId() + "</b></a></div>";
		shown.add(ma);
		String h = "<div class=\"mediaasset\">MediaAsset <b>" + ma.getId() + "</b>";
        if (ma.webURL() == null) {
			h += "<div style=\"position: absolute; right: 0;\"><i><b>webURL()</b> returned null</i></div>";
		} else if (ma.webURL().toString().matches(".+.mp4$")) {
			h += "<div style=\"position: absolute; right: 0;\"><a target=\"_new\" href=\"" + ma.webURL() + "\">[link]</a><br /><video width=\"320\" controls><source src=\"" + ma.webURL() + "\" type=\"video/mp4\" /></video></div>";
		} else {
			h += "<a target=\"_new\" href=\"" + ma.webURL() + "\"><div class=\"img-margin\"><div id=\"img-wrapper\"><img onLoad=\"drawFeatures();\" title=\".webURL() " + ma.webURL() + "\" src=\"" + ma.webURL() + "\" /></div></div></a>";

		}
                h += "<ul style=\"width: 65%\">";
		h += "<li>store: <b>" + ma.getStore() + "</b></li>";
		h += "<li>labels: <b>" + showLabels(ma.getLabels()) + "</b></li>";
		h += "<li>features: " + showFeatureList(ma.getFeatures(), req) + "</li>";
		h += "<li>safeURL(): " + ma.safeURL() + "</li>";
		h += "<li>parameters: " + niceJson(ma.getParameters()) + "</li>";
		if ((ma.getMetadata() != null) && (ma.getMetadata().getData() != null)) {
			h += "<li><a target=\"_new\" href=\"obrowse.jsp?type=MediaAssetMetadata&id=" + ma.getId() + "\">[show Metadata]</a></li>";
		}
		return h + "</ul></div>";
	}
%><%

myShepherd = new Shepherd("context0");
myShepherd.setAction("obrowse.jsp");
myShepherd.beginDBTransaction();

/*
String context="context0";
context=ServletUtilities.getContext(request);

  //setup our Properties object to hold all properties
  //String langCode = "en";
  String langCode=ServletUtilities.getLanguageCode(request);
  


//set up the file input stream
  Properties props = new Properties();
  //props.load(getClass().getResourceAsStream("/bundles/" + langCode + "/login.properties"));
  props = ShepherdProperties.getProperties("login.properties", langCode,context);
*/

String id = request.getParameter("id");
String type = request.getParameter("type");
if (type == null) type = "Encounter";
if (id == null) {
	out.println(showForm());
	return;
}


boolean needForm = false;

if (type.equals("Encounter")) {
	try {
		Encounter enc = ((Encounter) (myShepherd.getPM().getObjectById(myShepherd.getPM().newObjectIdInstance(Encounter.class, id), true)));
		out.println(showEncounter(enc, request));
	} catch (Exception ex) {
		out.println("<p>ERROR: " + ex.toString() + "</p>");
		ex.printStackTrace();
		needForm = true;
	}

} else if (type.equals("MarkedIndividual")) {
	try {
		MarkedIndividual ind = ((MarkedIndividual) (myShepherd.getPM().getObjectById(myShepherd.getPM().newObjectIdInstance(MarkedIndividual.class, id), true)));
		out.println(showMarkedIndividual(ind, request));
	} catch (Exception ex) {
		out.println("<p>ERROR: " + ex.toString() + "</p>");
		ex.printStackTrace();
		needForm = true;
	}

} else if (type.equals("MediaAsset")) {
	try {
		MediaAsset ma = ((MediaAsset) (myShepherd.getPM().getObjectById(myShepherd.getPM().newObjectIdInstance(MediaAsset.class, id), true)));
		out.println("<p>safeURL(<i>request</i>): <b>" + ma.safeURL(request) + "</b></p>");
		out.println(showMediaAsset(ma, request));
	} catch (Exception ex) {
		out.println("<p>ERROR: " + ex.toString() + "</p>");
		ex.printStackTrace();
		needForm = true;
	}

} else if (type.equals("Annotation")) {
	try {
		Annotation ann = ((Annotation) (myShepherd.getPM().getObjectById(myShepherd.getPM().newObjectIdInstance(Annotation.class, id), true)));
		out.println(showAnnotation(ann, request));
	} catch (Exception ex) {
		out.println("<p>ERROR: " + ex.toString() + "</p>");
		ex.printStackTrace();
		needForm = true;
	}

} else if (type.equals("Feature")) {
	try {
		Feature f = ((Feature) (myShepherd.getPM().getObjectById(myShepherd.getPM().newObjectIdInstance(Feature.class, id), true)));
		out.println(showFeature(f, request));
	} catch (Exception ex) {
		out.println("<p>ERROR: " + ex.toString() + "</p>");
		ex.printStackTrace();
		needForm = true;
	}

} else if (type.equals("MediaAssetMetadata")) {  //note: you pass the actual MediaAsset id here
	response.setContentType("text/json");
	try {
		MediaAsset ma = ((MediaAsset) (myShepherd.getPM().getObjectById(myShepherd.getPM().newObjectIdInstance(MediaAsset.class, id), true)));
		if ((ma.getMetadata() != null) && (ma.getMetadata().getData() != null)) {
			out.println(ma.getMetadata().getData().toString());
		} else {
			out.println("<p>no <b>.metadata.data</b> on " + ma.toString() + "</p>");
		}
	} catch (Exception ex) {
		out.println("<p>ERROR: " + ex.toString() + "</p>");
		ex.printStackTrace();
		needForm = true;
	}

} else {
	out.println("<p>unknown type</p>>");
	needForm = true;
}


if (needForm) out.println(showForm());

myShepherd.rollbackDBTransaction();
myShepherd.closeDBTransaction();

%>

</body></html>
