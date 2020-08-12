<%@ page contentType="text/html; charset=utf-8" 
		language="java"
        import="org.ecocean.servlet.ServletUtilities,org.ecocean.*,
org.ecocean.media.*,
org.ecocean.ia.Task,

org.joda.time.DateTime,
org.ecocean.servlet.importer.ImportTask,

org.ecocean.movement.*,

java.net.URL,
java.util.Vector,
java.util.ArrayList,
org.json.JSONObject,
org.json.JSONArray,
java.util.Properties" %>

<%!
	//public Shepherd myShepherd = null;

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
    private String format(String label, User user) {
        if (user == null) return format(label, (String)null);
        return format(label, user.getDisplayName());
    }
    private String format(String label, DateTime dt) {
        if (dt == null) return format(label, (String)null);
        String out = "";
        if (label != null) out += "<span class=\"format-label\">" + label + ": </span>";
        String dts = dt.toString();
        out += "<span class=\"format-dt-date\">" + dts.substring(0,10) + "</span> ";
        out += "<span class=\"format-dt-time\">" + dts.substring(11,19) + "</span>";
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
	private String showEncounter(Encounter enc, HttpServletRequest req) {
		if (enc == null) return "<b>[none]</b>";
		String h = "<div class=\"encounter shown\"><a target=\"_new\" href=\"encounters/encounter.jsp?number=" + enc.getCatalogNumber() + "\">Encounter <b>" + enc.getCatalogNumber() + "</b></a>";
		if ((enc.getAnnotations() != null) && (enc.getAnnotations().size() > 0)) {
			h += "<div>Annotations: <i>(" + enc.getAnnotations().size() + ")</i><ol>";
			for (int i = 0 ; i < enc.getAnnotations().size() ; i++) {
				h += "<li><a href=\"obrowse.jsp?type=Annotation&id=" + enc.getAnnotations().get(i).getId() + "\">Annotation " + enc.getAnnotations().get(i).getId() + "</a></li>";
			}
			h += "</ol></div>";
		} else if(enc.getAnnotations()==null){
			h+="<div> Annotations = NULL</div>";
		} else if(enc.getAnnotations().size()==0) {
			h+="<div> Annotations are EMPTY</div>";
		}
		// Add some Occurrence and MarkedIndividual Stuff.
		h+= "<p>OccurrenceID: <a href='occurrence.jsp?number="+enc.getOccurrenceID()+"'>"+enc.getOccurrenceID()+"</a></p>";
		h+= "<p>IndividualID: <a href='obrowse.jsp?type=MarkedIndividual&id="+enc.getIndividualID()+"'>"+enc.getIndividualID()+"</a></p>";
        h+= "<p>submitterID: "+enc.getSubmitterID()+"</p>";
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

	private String showFeature(Feature f, HttpServletRequest req, Shepherd myShepherd) {
		if (f == null) return "<b>[none]</b>";
		if (shown.contains(f)) return "<div class=\"feature shown\">Feature <b>" + f.getId() + "</b></div>";
		shown.add(f);
		String h = "<div class=\"feature\">Feature <b>" + f.getId() + "</b>";
                h += "<input type=\"button\" onClick=\"toggleZoom('" + f.getId() + "')\" value=\"toggle zoom\" style=\"margin-left: 10px;\" />";
                h += "<ul>";
		h += "<li>type: <b>" + ((f.getType() == null) ? "[null] (unity)" : f.getType()) + "</b></li>";
		h += "<li>" + showMediaAsset(f.getMediaAsset(), req, myShepherd) + "</li>";
		h += "<li>" + showAnnotation(f.getAnnotation(), req, myShepherd) + "</li>";
        h += "<script>addFeature('" + f.getId() + "', " + f.getParametersAsString() + ");</script>";
		h += "<li>parameters: " + niceJson(f.getParameters()) + "</li>";
		return h + "</ul></div>";
	}
    private String getAnnotationLink(Annotation ann) {
        return "obrowse.jsp?type=Annotation&id="+ann.getId();
    }

	private String showAnnotation(Annotation ann, HttpServletRequest req, Shepherd myShepherd) {
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
		h += "<li>features: " + showFeatureList(ann.getFeatures(), req, myShepherd) + "</li>";
		h += "<li>encounter: " + showEncounter(Encounter.findByAnnotation(ann, myShepherd), req) + "</li>";
		h += "<li class=\"deprecated\">" + showMediaAsset(ann.getMediaAsset(), req, myShepherd) + "</li>";
		return h + "</ul></div>";
	}
        private String showImportTask(ImportTask itask) {
            String h = "<div><b>" + itask.getId() + "</b> " + itask.toString() + "<ul>";
            h += "<li>" + format("creator", itask.getCreator()) + "</li>";
            h += "<li>" + format("created", itask.getCreated()) + "</li>";
            h += "</ul>";
            if (Util.collectionIsEmptyOrNull(itask.getEncounters())) {
                h += "<p><i>no Encounters</i></p>";
            } else {
                h += "<p><b>Encounters:</b> <ul>";
                for (Encounter enc : itask.getEncounters()) {
                    h += "<li><a href=\"obrowse.jsp?type=Encounter&id=" + enc.getCatalogNumber() + "\">Encounter " + enc.getCatalogNumber() + "</a></li>";
                }
                h += "</ul></p>";
            }
            h += "<p><b>parameters:</b> " + niceJson(itask.getParameters()) + "</p>";
            if (Util.collectionIsEmptyOrNull(itask.getLog())) {
                h += "<p><i>empty log</i></p>";
            } else {
                h += "<p><b>log:</b> <ul style=\"font-size: 0.8em;\">";
                JSONArray larr = itask.getLogJSONArray();
                for (int i = 0 ; i < larr.length() ; i++) {
                    JSONObject jl = larr.optJSONObject(i);
                    if (jl == null) continue;
                    long d = jl.optLong("t", -1);
                    String l = jl.optString("l", "{empty}");
                    if (d > 0) {
                        DateTime dt = new DateTime(d);
                        h += "<li>" + format(null, dt) + " - " + l + "</li>";
                    } else {
                        h += "<li>" + l + "</li>";
                    }
                }
                h += "</ul></p>";
            }
            h += "</div>";
            return h;
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
        private String showMultiValue(MultiValue mv) {
            if (mv == null) return "[null]";
            return niceJson(mv.debug());
        }
	private String showMediaAssetList(ArrayList<MediaAsset> l) {
		if ((l == null) || (l.size() < 1)) return "[none]";
		return "";
	}

	private String showFeatureList(ArrayList<Feature> l, HttpServletRequest req, Shepherd myShepherd) {
		if ((l == null) || (l.size() < 1)) return "[none]";
		String h = "<ol>";
		for (int i = 0 ; i < l.size() ; i++) {
			h += "<li>" + showFeature(l.get(i), req, myShepherd) + "</li>";
		}
		return h + "</ol>";
	}

	private String showMediaAsset(MediaAsset ma, HttpServletRequest req, Shepherd myShepherd) {
		if (ma == null) return "asset: <b>[none]</b>";
		if (shown.contains(ma)) return "<div class=\"mediaasset shown\"><a href=\"obrowse.jsp?type=MediaAsset&id="+ma.getId()+"\"> MediaAsset <b>" + ma.getId() + "</b></a></div>";
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
		h += "<li>features: " + showFeatureList(ma.getFeatures(), req, myShepherd) + "</li>";
		h += "<li>safeURL(): " + ma.safeURL() + "</li>";
		h += "<li>detectionStatus: <b>" + ma.getDetectionStatus() + "</b></li>";
		h += "<li>" + format("acmId", ma.getAcmId()) + "</li>";
		h += "<li>parameters: " + niceJson(ma.getParameters()) + "</li>";
        h += "<li>hasMetadata(): " + ma.hasMetadata() + "</li>";
        //Shepherd maShepherd = Shepherd.newActiveShepherd(req, "showMediaAsset");
        //h += "<li>hasFamily(): " + ma.hasFamily(new Shepherd(req)) + "</li>";
        h += "<li>hasFamily(): " + ma.hasFamily(myShepherd) + "</li>";
        //maShepherd.rollbackAndClose();
		if ((ma.getMetadata() != null) && (ma.getMetadata().getData() != null)) {
			h += "<li><a target=\"_new\" href=\"obrowse.jsp?type=MediaAssetMetadata&id=" + ma.getId() + "\">[show Metadata]</a></li>";
		}
                if (ma.hasLabel("_original")) {
		    h += "<li><a target=\"_new\" href=\"appadmin/redoChildrenMediaAssets.jsp?id=" + ma.getId() + "\">[redo children assets]</a></li>";
                }
		return h + "</ul></div>";
	}
    private String showSurvey(Survey surv, HttpServletRequest req) {
        if (surv == null) return "(null Survey)";
        String h = "<p>[<a target=\"_new\" href=\"surveys/survey.jsp?surveyID=" + surv.getID() + "\">" + surv.getID() + "</a>] " + surv.toString() + "</p><b>SurveyTracks:</b><ul>";
        ArrayList<SurveyTrack> tracks = surv.getSurveyTracks();
        if (tracks == null) {
            h += "<li>(no Tracks)</li>";
        } else {
            for (SurveyTrack tr : tracks) {
                h += "<li>" + showSurveyTrack(tr, req) + "</li>";
            }
        }
        h += "</ul>";
        return h;
    }
    private String showSurveyTrack(SurveyTrack st, HttpServletRequest req) {
        if (st == null) return "(null SurveyTrack)";
        String h = "<p>" + st.toString() + "</p>";
        h += "<ul><b>Path:</b> <li>" + showPath(st.getPath(), req) + "</li></ul>";
        h += "<ul><b>Occurrences:</b> ";
        ArrayList<Occurrence> occs = st.getOccurrences();
        if (occs == null) {
            h += "<li>(no Occurrences)</li>";
        } else {
            for (Occurrence occ : occs) {
                h += "<li>" + showOccurrence(occ, req) + "</li>";
            }
        }
        h += "</ul>";
        return h;
    }
    private String showPath(Path p, HttpServletRequest req) {
        if (p == null) return "(no Path)";
        String h = "<p>" + p.toString() + "</p>";
        int showPts = p.getNumPointLocations();
        if (showPts > 0) {
            h += "<ul><b>PointLocations</b> ";
            if (showPts > 10) {
                h += "<i>Showing only 10 of " + showPts + "</i>";
                showPts = 10;  //just a sampling!  this can get ridiculously huge...
            }
            for (int i = 0 ; i < showPts ; i++) {
                h += "<li>" + p.getPointLocations().get(i).toString() + "</li>";
            }
            h += "</ul>";
        }
        return h;
    }
    private String showOccurrence(Occurrence occ, HttpServletRequest req) {
        if (occ == null) return "(no Occurrence)";
        return "[<a target=\"_new\" href=\"occurrence.jsp?number=" + occ.getID() + "\">" + occ.getID() + "</a>] " + occ.toString();
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
<script src="javascript/annot.js"></script>
<style>
body {
    font-family: arial, sans;
}
.img-margin {
    float: right;
    display: inline-block;
    oveflow-hidden;
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
.format-dt-date, .format-dt-time {
    font-size: 0.85em;
}
.format-dt-time {
    color: #777;
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
    width: 400px;
    height: 700px;
    overflow: hidden;
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
	position: absolute;
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
    max-width: 60%;
    overflow-x: scroll;
}
</style>

<script>
var features = {};
var zoomedId = false;
function toggleZoom(featId) {
console.log('featId=%o', featId);
    var imgEl = $('img')[0];
    if (zoomedId == featId) {
        zoomedId = false;
        unzoomFeature(imgEl);
        $('.featurebox').show();
        return;
    }
console.log('feature=%o', features[featId]);
    if (!features || !features[featId]) return;
    $('.featurebox').hide();
    zoomToFeature(imgEl, { parameters: features[featId] });
    zoomedId = featId;
}
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

Shepherd myShepherd = new Shepherd(context);
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
if (id == null && (acmid == null || (!"Annotation".equals(type) && !"MediaAsset".equals(type)))) {
	out.println(showForm());
	myShepherd.rollbackDBTransaction();
	myShepherd.closeDBTransaction();
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
} else if (type.equals("MultiValue")) {
	try {
		MultiValue mv = ((MultiValue) (myShepherd.getPM().getObjectById(myShepherd.getPM().newObjectIdInstance(MultiValue.class, id), true)));
		out.println(showMultiValue(mv));
	} catch (Exception ex) {
		out.println("<p>ERROR: " + ex.toString() + "</p>");
		ex.printStackTrace();
		needForm = true;
	}
} else if (type.equals("MediaAsset")) {
	if (id!=null&&acmid==null) {
		try {
			MediaAsset ma = ((MediaAsset) (myShepherd.getPM().getObjectById(myShepherd.getPM().newObjectIdInstance(MediaAsset.class, id), true)));
			out.println("<p>safeURL(<i>request</i>): <b>" + ma.safeURL(request) + "</b></p>");
			out.println(showMediaAsset(ma, request, myShepherd));
		} catch (Exception ex) {
			out.println("<p>ERROR: " + ex.toString() + "</p>");
			ex.printStackTrace();
			needForm = true;
		}
	}
	if (id==null&&acmid!=null) {
		try {
			ArrayList<MediaAsset> anns = myShepherd.getMediaAssetsWithACMId(acmid);
			if ((anns == null) || (anns.size() < 1)) {
				out.println("none with acmid " + acmid);
			} else {
				out.println("found acmid " + acmid);
				String allAnns = "";
				for (int i=0; i<anns.size(); i++) {
					allAnns += showMediaAsset(anns.get(i), request, myShepherd);
				}
				out.println(allAnns);
			}
		} catch (Exception e) {
			out.println("<p>ERROR: " + e.toString() + "</p>");
			needForm = true;
		}
		
	}
	

	
	

} else if (type.equals("Annotation")) {
	if (id!=null&&acmid==null) {
		try {
			Annotation ann = (Annotation) myShepherd.getPM().getObjectById(myShepherd.getPM().newObjectIdInstance(Annotation.class, id), true);
			out.println(showAnnotation(ann, request, myShepherd));
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
					allAnns += showAnnotation(anns.get(i), request, myShepherd);
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
} else if (type.equals("ImportTask")) {
	try {
		ImportTask task = ((ImportTask) (myShepherd.getPM().getObjectById(myShepherd.getPM().newObjectIdInstance(ImportTask.class, id), true)));
		out.println(showImportTask(task));
	} catch (Exception ex) {
		out.println("<p>ERROR: " + ex.toString() + "</p>");
		needForm = true;
	}
} else if (type.equals("Feature")) {
	try {
		Feature f = ((Feature) (myShepherd.getPM().getObjectById(myShepherd.getPM().newObjectIdInstance(Feature.class, id), true)));
		out.println(showFeature(f, request, myShepherd));
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
} else if (type.equals("Survey")) {
	try {
		Survey surv = ((Survey) (myShepherd.getPM().getObjectById(myShepherd.getPM().newObjectIdInstance(Survey.class, id), true)));
		out.println(showSurvey(surv, request));
	} catch (Exception ex) {
		out.println("<p>ERROR: " + ex.toString() + "</p>");
		ex.printStackTrace();
		needForm = true;
	}
} else if (type.equals("SurveyTrack")) {
	try {
		SurveyTrack st = ((SurveyTrack) (myShepherd.getPM().getObjectById(myShepherd.getPM().newObjectIdInstance(SurveyTrack.class, id), true)));
		out.println(showSurveyTrack(st, request));
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
if (!rawOutput(type)) {
%>

</body></html>

<% } %>
