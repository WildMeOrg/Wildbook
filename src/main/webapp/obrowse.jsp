<%@ page contentType="text/html; charset=utf-8" 
		language="java"
        import="org.ecocean.servlet.ServletUtilities,org.ecocean.*,
org.ecocean.media.*,
org.ecocean.ia.Task,
java.net.URL,
java.util.ArrayList,
org.json.JSONObject,
java.util.Properties" %>
<%!
	public Shepherd myShepherd = null;

	private ArrayList<Object> shown = new ArrayList<Object>();

	private String showForm() {
		return "(form)";
	}

    private String format(String label, String value) {
        return format(label, value, null);
    }
    private String format(String label, String value, String alt) {
        String out = "";
        alt = (alt == null) ? "" : " title=\"" + alt + "\" ";
        if (label != null) out += "<span class=\"format-label\">" + label + ": </span>";
        if ((value == null) || (value.equals("none"))) {
            out += "<span " + alt + "class=\"format-value format-" + value + "\">" + value + "</span>";
        } else {
            out += "<span " + alt + "class=\"format-value\">" + value + "</span>";
        }
        return out;
    }
    private String format(String label, Boolean value) {
        String out = "";
        if (label != null) out += "<span class=\"format-label\">" + label + ": </span>";
        out += "<span class=\"format-value format-boolean format-" + value + "\">" + value + "</span>";
        return out;
    }
	private String niceJson(JSONObject j) {
		if (j == null) return format(null, "none");
		//return "<pre class=\"json\">" + j.toString().replaceAll(",", ",\n") + "</pre>";
		return "<pre class=\"json\">" + j.toString(3) + "</pre>";
	}

	private String showEncounter(Encounter enc) {
		if (enc == null) return format(null, "none");
		String h = "<div class=\"encounter shown\"><a target=\"_new\" href=\"encounters/encounter.jsp?number=" + enc.getCatalogNumber() + "\">Encounter <b>" + enc.getCatalogNumber() + "</b></a>";
		if ((enc.getAnnotations() != null) && (enc.getAnnotations().size() > 0)) {
			h += "<div>Annotations: <i>(" + enc.getAnnotations().size() + ")</i><ol>";
			for (int i = 0 ; i < enc.getAnnotations().size() ; i++) {
				h += "<li><a href=\"obrowse.jsp?type=Annotation&id=" + enc.getAnnotations().get(i).getId() + "\">Annotation " + enc.getAnnotations().get(i).getId() + "</a></li>";
			}
			h += "</ol></div>";
		}
		return h + "</div>";
	}

	private String showFeature(Feature f) {
		if (f == null) return format(null, "none");
		if (shown.contains(f)) return "<div class=\"feature shown\">" + format("Feature", f.getId(), f.toString()) + "</div>";
		shown.add(f);
		String h = "<div class=\"feature\">Feature <b>" + f.getId() + "</b><ul>";
		h += "<li>type: <b>" + ((f.getType() == null) ? "[null] (unity)" : f.getType()) + "</b></li>";
		h += "<li>" + showMediaAsset(f.getMediaAsset()) + "</li>";
		h += "<li>" + showAnnotation(f.getAnnotation()) + "</li>";
                h += "<script>addFeature('" + f.getId() + "', " + f.getParametersAsString() + ");</script>";
		h += "<li>parameters: " + niceJson(f.getParameters()) + "</li>";
		return h + "</ul></div>";
	}

	private String showAnnotation(Annotation ann) {
		if (ann == null) return "annotation: <b>[none]</b>";
		if (shown.contains(ann)) return "<div class=\"annotation shown\">Annotation <b>" + ann.getId() + "</b></div>";
		shown.add(ann);
		String vp = ann.getViewpoint();
                if (!Annotation.isValidViewpoint(vp)) vp = "<span title=\"INVALID viewpoint value\" style=\"background-color: #F88; font-size: 0.8em; padding: 0 8px;\">" + vp + "</span>";
		String h = "<div class=\"annotation\">Annotation <b>" + ann.getId() + "</b><ul>";
		h += "<li>" + format("iaClass", ann.getIAClass()) + "</li>";
		h += "<li>" + format("viewpoint", vp) + "</li>";
		h += "<li>" + format("acmId", ann.getAcmId()) + "</li>";
		h += "<li>" + format("matchAgainst", ann.getMatchAgainst()) + "</li>";
		h += "<li>" + format("identificationStatus", ann.getIdentificationStatus()) + "</li>";
                h += "<li>" + format("AoI", ann.getIsOfInterest()) + "</li>";
		h += "<li>features: " + showFeatureList(ann.getFeatures()) + "</li>";
		h += "<li>encounter: " + showEncounter(Encounter.findByAnnotation(ann, myShepherd)) + "</li>";
		h += "<li class=\"deprecated\">" + showMediaAsset(ann.getMediaAsset()) + "</li>";
		return h + "</ul></div>";
	}

        private String showTask(Task task) {
            String h = "<div><b>" + task.getId() + "</b> " + task.toString() + "<ul>";
            Task parent = task.getParent();
            if (parent == null) {
                h += "<li><i class=\"format-value format-none\">No parent</i></li>";
            } else {
                h += "<li><b>Parent: <a href=\"?type=Task&id=" + parent.getId() + "\">" + parent.getId() + "</a></b> <span class=\"quiet\">" + parent.toString() + "</span>";
                if (parent.numChildren() > 1) {  //must be > 1 cuz we need siblings
                    h += "<ol>";
                    for (Task kid : parent.getChildren()) {
                        if (kid.equals(task)) continue;
                        h += "<li><a title=\"sibling\" href=\"?type=Task&id=" + kid.getId() + "\">" + kid.getId() + "</a> <span class=\"quiet\">" + kid.toString() + "</span></li>";
                    }
                    h += "</ol>";
                }
                h += "</li>";
            }
            h += "<li><b>" + task.numChildren() + " children</b> Task(s)";
            if (task.numChildren() > 0) {
                h += "<ol>";
                for (Task kid : task.getChildren()) {
                    h += "<li><a href=\"?type=Task&id=" + kid.getId() + "\">" + kid.getId() + "</a> <span class=\"quiet\">" + kid.toString() + "</span></li>";
                }
                h += "</ol>";
            }
            h += "</li>";
            h += "<li><b>" + task.countObjectMediaAssets() + " MediaAsset</b> object(s)";
            if (task.hasObjectMediaAssets()) {
                h += "<ol>";
                for (MediaAsset ma : task.getObjectMediaAssets()) {
                    h += "<li><a href=\"?type=MediaAsset&id=" + ma.getId() + "\">" + ma.getId() + "</a> <span class=\"quiet\">" + ma.toString() + "</span></li>";
                }
                h += "</ol>";
            }
            h += "</li>";
            h += "<li><b>" + task.countObjectAnnotations() + " Annotation</b> object(s)";
            if (task.hasObjectAnnotations()) {
                h += "<ol>";
                for (Annotation ann : task.getObjectAnnotations()) {
                    h += "<li><a href=\"?type=Annotation&id=" + ann.getId() + "\">" + ann.getId() + "</a> <span class=\"quiet\">" + ann.toString() + "</span></li>";
                }
                h += "</ol>";
            }
            h += "</li>";
            h += "<li>parameters: " + niceJson(task.getParameters()) + "</li>";
            h += "<li><a target=\"_new\" href=\"iaResults.jsp?taskId=" + task.getId() + "\">iaResults</a></li>";
            h += "<li><a target=\"_new\" href=\"ia?v2&includeChildren&taskId=" + task.getId() + "\">JSON task tree</a></li>";
            h += "</ul>";
            return h;
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

	private String showFeatureList(ArrayList<Feature> l) {
		if ((l == null) || (l.size() < 1)) return format(null, "none");
		String h = "<i>(" + l.size() + ")</i><ol>";
		for (int i = 0 ; i < l.size() ; i++) {
			h += "<li>" + showFeature(l.get(i)) + "</li>";
		}
		return h + "</ol>";
	}

	private String showMediaAsset(MediaAsset ma) {
		if (ma == null) return "asset: <b>[none]</b>";
		if (shown.contains(ma)) return "<div class=\"mediaasset shown\">MediaAsset <b>" + ma.getId() + "</b></div>";
		shown.add(ma);
		String h = "<div class=\"mediaasset\"><a href=\"obrowse.jsp?type=MediaAsset&id=" + ma.getId() + "\">Media Asset <b>" + ma.getId() + "</b></a>";
                if (ma.webURL() == null) {
			h += "<div style=\"position: absolute; right: 0;\"><i><b>webURL()</b> returned null</i></div>";
		} else if (ma.webURL().toString().matches(".+.mp4$")) {
			h += "<div style=\"position: absolute; right: 0;\"><a target=\"_new\" href=\"" + ma.webURL() + "\">[link]</a><br /><video width=\"320\" controls><source src=\"" + ma.webURL() + "\" type=\"video/mp4\" /></video></div>";
		} else {
			h += "<a target=\"_new\" href=\"" + scrubUrl(ma.webURL()) + "\"><div class=\"img-margin\"><div id=\"img-wrapper\"><img onLoad=\"drawFeatures();\" title=\".webURL() " + ma.webURL() + "\" src=\"" + scrubUrl(ma.webURL()) + "\" /></div></div></a>";

		}
                h += "<ul style=\"width: 65%\">";
		h += "<li>store: <b>" + ma.getStore() + "</b></li>";
		h += "<li>labels: <b>" + showLabels(ma.getLabels()) + "</b></li>";
		h += "<li>features: " + showFeatureList(ma.getFeatures()) + "</li>";
		h += "<li>safeURL(): " + ma.safeURL() + "</li>";
		h += "<li>detectionStatus: <b>" + ma.getDetectionStatus() + "</b></li>";
		h += "<li>" + format("acmId", ma.getAcmId()) + "</li>";
		h += "<li>parameters: " + niceJson(ma.getParameters()) + "</li>";
		if ((ma.getMetadata() != null) && (ma.getMetadata().getData() != null)) {
			h += "<li><a target=\"_new\" href=\"obrowse.jsp?type=MediaAssetMetadata&id=" + ma.getId() + "\">[show Metadata]</a></li>";
		}
		return h + "</ul></div>";
	}

    private boolean rawOutput(String type) {
        if (type == null) return false;
        return type.equals("MediaAssetMetadata");
    }

    private String scrubUrl(URL u) {
        if (u == null) return (String)null;
        return u.toString().replaceAll("#", "%23");
    }

%><%

String id = request.getParameter("id");
String type = request.getParameter("type");

// IA debuggin use.. Can retrieve Annotations 
String acmid = request.getParameter("acmid");

if (!rawOutput(type)) {
%>
<html><head><title>obrowse</title>
<script src="tools/jquery/js/jquery.min.js"></script>
<style>

body {
    font-family: arial, sans;
}

.img-margin {
    float: right;
    display: inline-block;
}

.format-label {
    font-size: 0.9em;
    color: #777;
}
.format-value {
    font-weight: bold;
}
.format-null, .format-none, .format-boolean {
    font-size: 0.8em;
    color: #888;
    background-color: #DDD;
    border-radius: 3px;
    padding: 2px 4px;
}
.format-true {
    text-transform: uppercase;
    color: #FFF;
    background-color: #6B6;
}
.format-false {
    text-transform: uppercase;
    color: #FFF;
    background-color: #B66;
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

.quiet {
    color: #999;
    font-size: 0.85em;
}

pre.json {
    font-size: 0.85em;
    color: #666;
    padding: 8px 8px 8px 15px;
    border-radius: 3px;
    background-color: #EEE;
    display: inline-flex;
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
    if (bbox.theta) f.css('transform', 'rotate(' +  bbox.theta + 'rad)');
}
</script>

</head><body>
<%
}  //above skipped for MediaAssetMetadata (raw json)

String context = "context0";
if (Util.requestParameterSet(request.getParameter("evict"))) {
    org.ecocean.ShepherdPMF.getPMF(context).getDataStoreCache().evictAll();
    out.println("<p style=\"padding: 10px 0; text-align: center; background-color: #FAA;\"><b>.evictAll()</b> called on PMF data store cache.</p>");
}

myShepherd = new Shepherd(context);
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

if (type == null) type = "Encounter";
if (id == null && (acmid == null || !"Annotation".equals(type))) {
	out.println(showForm());
	return;
}


boolean needForm = false;

if (type.equals("Encounter")) {
	try {
		Encounter enc = ((Encounter) (myShepherd.getPM().getObjectById(myShepherd.getPM().newObjectIdInstance(Encounter.class, id), true)));
		out.println(showEncounter(enc));
	} catch (Exception ex) {
		out.println("<p>ERROR: " + ex.toString() + "</p>");
		needForm = true;
	}

} else if (type.equals("MediaAsset")) {
	try {
		MediaAsset ma = ((MediaAsset) (myShepherd.getPM().getObjectById(myShepherd.getPM().newObjectIdInstance(MediaAsset.class, id), true)));
		out.println("<p>safeURL(<i>request</i>): <b>" + ma.safeURL(request) + "</b></p>");
		out.println(showMediaAsset(ma));
	} catch (Exception ex) {
		out.println("<p>ERROR: " + ex.toString() + "</p>");
		needForm = true;
	}

} else if (type.equals("Annotation")) {
	if (id!=null&&acmid==null) {
		try {
			Annotation ann = (Annotation) myShepherd.getPM().getObjectById(myShepherd.getPM().newObjectIdInstance(Annotation.class, id), true);
			out.println(showAnnotation(ann));
		} catch (Exception ex) {
			out.println("<p>ERROR: " + ex.toString() + "</p>");
			needForm = true;
		}
	}
	if (id==null&&acmid!=null) {
		try {
			ArrayList<Annotation> anns = myShepherd.getAnnotationsWithACMId(acmid);
			if ((anns == null) || (anns.size() < 1)) {
				out.println("none with acmid " + acmid);
			} else {
				String allAnns = "";
				for (int i=0; i<anns.size(); i++) {
					allAnns += showAnnotation(anns.get(i));
				}
				out.println(allAnns);
			}
		} catch (Exception e) {
			out.println("<p>ERROR: " + e.toString() + "</p>");
			needForm = true;
		}
	}

} else if (type.equals("Task")) {
	try {
		Task task = ((Task) (myShepherd.getPM().getObjectById(myShepherd.getPM().newObjectIdInstance(Task.class, id), true)));
		out.println(showTask(task));
	} catch (Exception ex) {
		out.println("<p>ERROR: " + ex.toString() + "</p>");
		needForm = true;
	}

} else if (type.equals("Feature")) {
	try {
		Feature f = ((Feature) (myShepherd.getPM().getObjectById(myShepherd.getPM().newObjectIdInstance(Feature.class, id), true)));
		out.println(showFeature(f));
	} catch (Exception ex) {
		out.println("<p>ERROR: " + ex.toString() + "</p>");
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
		needForm = true;
	}

} else {
	out.println("<p>unknown type</p>>");
	needForm = true;
}


if (needForm) out.println(showForm());

myShepherd.rollbackDBTransaction();
myShepherd.closeDBTransaction();

if (!rawOutput(type)) {
%>

</body></html>

<% } %>
