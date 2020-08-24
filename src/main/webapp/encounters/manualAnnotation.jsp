<%@ page contentType="text/html; charset=utf-8" language="java"
     import="org.ecocean.*,
		org.ecocean.servlet.ServletUtilities,
		javax.jdo.Query,
		java.util.Iterator,
		java.util.List,
                java.util.HashMap,
		org.json.JSONObject,
		org.ecocean.media.*,
		org.ecocean.Annotation,
		java.net.URLEncoder,
		java.nio.charset.StandardCharsets,
		java.io.UnsupportedEncodingException"
%>

<%!
//Method to encode a string value using `UTF-8` encoding scheme
private static String encodeValue(String value) {
    try {
        return URLEncoder.encode(value, StandardCharsets.UTF_8.toString());
    } 
    catch (UnsupportedEncodingException ex) {
        ex.printStackTrace();
    }
    finally{return value;}
}

private String rotationInfo(MediaAsset ma) {
    if ((ma == null) || (ma.getMetadata() == null)) return null;
    HashMap<String,String> orient = ma.getMetadata().findRecurse(".*orient.*");
    if (orient == null) return null;
    for (String k : orient.keySet()) {
System.out.println("rotationInfo: " + k + "=" + orient.get(k) + " on " + ma);
        if (orient.get(k).matches(".*90.*")) return orient.get(k);
        if (orient.get(k).matches(".*270.*")) return orient.get(k);
    }
    return null;
}
%>

<jsp:include page="../header.jsp" flush="true"/>

<% int imgHeight = 500; %>

<%

String bbox = request.getParameter("bbox");
String aidparam = request.getParameter("assetId");
int assetId = -1;
try {
    assetId = Integer.parseInt(aidparam);
} 
catch (NumberFormatException nex) {}

String iaClass = request.getParameter("iaClass");
String maparam = request.getParameter("matchAgainst");
boolean matchAgainst = (maparam == null) || Util.booleanNotFalse(maparam);
String rtparam = request.getParameter("removeTrivial");
boolean removeTrivial = (rtparam == null) || Util.booleanNotFalse(rtparam);
String encounterId = request.getParameter("encounterId");
///skipping this for now cuz i dont want to deal with altering the *annot* once we change a feature (i.e. acmId etc so IA thinks is new)
String featureId = null;///request.getParameter("featureId");
String viewpoint = request.getParameter("viewpoint");
boolean save = Util.requestParameterSet(request.getParameter("save"));
boolean cloneEncounter = Util.requestParameterSet(request.getParameter("cloneEncounter"));
String added2enc="";

String clist = "";

%>

<style>
	body {
	    font-family: "src/main/webapp/encounters/manualAnnotation.jsp"arial, sans;
	}
	
	.error {
	    color: red;
	}
	
	#img-wrapper {
	    overflow: hidden;
	    height: <%=imgHeight%>px;
	    xfloat: right;
	    position: relative;
	}
	img.asset {
	    height: <%=imgHeight%>px;
	    xposition: absolute;
	}
	
	#bbox {
	    outline: dotted blue 3px;
	    border: solid 3px rgba(255,255,0,0.5);
	    position: absolute;
	}
        .featurebox {
            pointer-events: none;
	    outline: dotted green 2px;
	    border: solid 2px rgba(255,255,0,0.5);
	    position: absolute;
        }
	.axis {
	    width: 2000px;
	    height: 2000px;
	    position: absolute;
	    display: none;
	}
	
	#x-axis {
	    border-left: dotted 1px yellow;
	}
	#y-axis {
	    border-top: dotted 1px yellow;
	}
	
	.axis, #bbox {
	    pointer-events: none;
	}

</style>
<script>
var boxStart = false;
var origBbox = null;
$(document).ready(function() {
    origBbox = {
        top: $('#bbox').css('top'),
        left: $('#bbox').css('left'),
        width: $('#bbox').css('width'),
        height: $('#bbox').css('height')
    };
    
    <%
    if(iaClass!=null){
    %>

    $('#img-wrapper').on('mousemove', function(ev) {
        if (boxStart) {
            var w = Math.abs(ev.offsetX - boxStart[0]);
            var h = Math.abs(ev.offsetY - boxStart[1]);
            var x = Math.min(boxStart[0], ev.offsetX);
            var y = Math.min(boxStart[1], ev.offsetY);
            $('#bbox').css({
                left: x,
                top: y,
                width: w,
                height: h
            });
        } else {
            $('#x-axis').css('left', ev.offsetX);
            $('#y-axis').css('top', ev.offsetY);
        }
    }).on('mouseover', function(ev) {
        $('.axis').show();
    }).on('mouseout', function(ev) {
        if (boxStart) $('#bbox').css(origBbox);
        boxStart = false;
        $('.axis').hide();
    }).on('click', function(ev) {
        if (boxStart) {
            var w = Math.abs(ev.offsetX - boxStart[0]);
            var h = Math.abs(ev.offsetY - boxStart[1]);
            var x = Math.min(boxStart[0], ev.offsetX);
            var y = Math.min(boxStart[1], ev.offsetY);
            var bbox = [
                Math.floor(x / scale),
                Math.floor(y / scale),
                Math.floor(w / scale),
                Math.floor(h / scale)
            ].join(',');
            document.location.href = 'manualAnnotation.jsp' + document.location.search.replace(/bbox=[^&]+/, '') + '&bbox=' + bbox;
        } else {
            boxStart = [ev.offsetX, ev.offsetY];
            $('#bbox').css('left', ev.offsetX);
            $('#bbox').css('top', ev.offsetY);
            $('#bbox').css('width', 10);
            $('#bbox').css('height', 10);
        }
    });
    
    <%
	}
    %>
    
});
</script>
<div class="container maincontent">
<h1>Manual Annotation</h1>

<%


String context = ServletUtilities.getContext(request);
Shepherd myShepherd = new Shepherd(context);
myShepherd.setAction("manualAnnotation.jsp");
myShepherd.beginDBTransaction();

try{
	String vlist = "<p> 1. Select viewpoint: <select name=\"viewpoint\" onChange=\"return pulldownUpdate(this);\"><option value=\"\">CHOOSE</option>";
	Query q = myShepherd.getPM().newQuery("javax.jdo.query.SQL", "select distinct(\"VIEWPOINT\") as v from \"ANNOTATION\" order by v");
	List results = (List)q.execute();
	Iterator it = results.iterator();
	while (it.hasNext()) {
	    String v = (String)it.next();
	    if (!Util.stringExists(v)) continue;
	    vlist += "<option" + (v.equals(viewpoint) ? " selected" : "") + ">" + v + "</option>";
	}
	vlist += "</select></p>";
	q.closeAll();
	
	if(viewpoint!=null){
		clist = "<p>2. Select annotation iaClass: <select name=\"iaClass\" onChange=\"return pulldownUpdate(this);\"><option value=\"\">CHOOSE</option>";
		Query q2 = myShepherd.getPM().newQuery("javax.jdo.query.SQL", "select distinct(\"IACLASS\") as v from \"ANNOTATION\" order by v");
		results = (List)q2.execute();
		it = results.iterator();
		while (it.hasNext()) {
		    String v = (String)it.next();
		    System.out.println("Encooded v: "+v);
		    if (!Util.stringExists(v)) continue;
		    System.out.println("v:" +v+" versus iaCLass:"+iaClass);
		    clist += "<option" + (v.equals(iaClass) ? " selected" : "") + ">" + v + "</option>";
		}
		clist += "</select></p>";
		q2.closeAll();
	}
	
	
	Feature ft = null;
	MediaAsset ma = null;
	int[] xywh = null;
	
	if (featureId != null) {
	    try {
	        ft = ((Feature) (myShepherd.getPM().getObjectById(myShepherd.getPM().newObjectIdInstance(Feature.class, featureId), true)));
	    } catch (Exception ex) {}
	    if (ft == null) {
	        out.println("<p class=\"error\">Invalid <b>featureId=" + featureId + "</b></p>");
	        myShepherd.rollbackDBTransaction();
		    myShepherd.closeDBTransaction();
	        return;
	    }
	    removeTrivial = false;
	    ma = ft.getMediaAsset();
	    ft.getParametersAsString();
	    if (ft.getParameters() != null) {
	        xywh = new int[4];
	        xywh[0] = (int)Math.round(ft.getParameters().optDouble("x", 10.0));
	        xywh[1] = (int)Math.round(ft.getParameters().optDouble("y", 10.0));
	        xywh[2] = (int)Math.round(ft.getParameters().optDouble("width", 100.0));
	        xywh[3] = (int)Math.round(ft.getParameters().optDouble("height", 100.0));
	    }
	}
	
	if (ma == null) ma = MediaAssetFactory.load(assetId, myShepherd);
	if (ma == null) {
	    out.println("<p class=\"error\">Invalid <b>assetId=" + assetId + "</b></p>");
	    myShepherd.rollbackDBTransaction();
	    myShepherd.closeDBTransaction();
	    return;
	}
	
	//ok, we now know that we have a MediaAsset
	//now let's check if we need to force Encounter cloning
	
	Encounter enc = null;
	if (encounterId != null) {
	    enc = myShepherd.getEncounter(encounterId);
	    if (enc == null) {
	        out.println("<p class=\"error\">Invalid <b>encounterId=" + encounterId + "</b></p>");
	        myShepherd.rollbackDBTransaction();
		    myShepherd.closeDBTransaction();
	        return;
	    }
	}
	
	
	//ok, we now know that we have a MediaAsset
	//now let's check if we need to force Encounter cloning
	List<Annotation> annots=ma.getAnnotations();
	//we would expect at least a trivial annotation, so if annots>=2, we know we need to clone
	if(annots.size()>1){
		cloneEncounter=true;
	}
	//if the one annot isn't trivial, then we have to clone the encounter as well
	else if(annots.size()==1 && !annots.get(0).isTrivial()){
		cloneEncounter=true;
	}
	
	
	
	if (bbox != null) {
	    String[] parts = bbox.split(",");
	    if (parts.length == 4) {
	        xywh = new int[4];
	        try {
	            for (int i = 0 ; i < parts.length ; i++) {
	                int n = Integer.parseInt(parts[i]);
	                if (n < 0) throw new NumberFormatException("hey we do not want negative numbers!");
	                xywh[i] = n;
	            }
	        } catch (NumberFormatException nex) {
	            System.out.println(nex.toString());
	            xywh = null;
	        }
	    }
	}
	
	if ((bbox == null) && (xywh == null)) {
	    xywh = new int[]{-10,-10,1,1};
	    //out.println("<p class=\"error\">Invalid <b>bbox=" + bbox + "</b> (should be <i>x,y,w,h</i>)</p>");
	    //return;
	}
	double scale = imgHeight / ma.getHeight();
        if (rotationInfo(ma) != null) scale = imgHeight / ma.getWidth();  //90deg so we have to adjust scale
	
	%>
	
	
	
	
	<p>
	MediaAsset <b><a title="<%=ma.toString()%>" target="_new" href="../obrowse.jsp?type=MediaAsset&id=<%=ma.getId()%>"><%=ma.getId()%></a></b>
	<script>scale = <%=scale%>;
        var asset = <%=ma.sanitizeJson(request, new org.datanucleus.api.rest.orgjson.JSONObject(), true, myShepherd)%>;

	function pulldownUpdate(el) {
	//console.info('%o', el.name);
	    var u = window.location.href;
	    var m = u.match(new RegExp(el.name + '=\\w+'));
	    if (!m) {  //was not (yet) in url
	        u += '&' + el.name + '=' + encodeURIComponent(el.value);
	    } else {
	console.log('m = %o', m);
	        u = u.substring(0,m.index) + el.name + '=' + encodeURIComponent(el.value) + u.substring(m.index + m[0].length);
	console.log(u);
	    }
	    window.location.href = u;
	}


        function drawFeatures() {
            if (!asset || !asset.features || !asset.features.length) return;
            for (var i = 0 ; i < asset.features.length ; i++) {
                drawFeature(document.getElementById('main-img'), asset.features[i]);
            }
        }

        function drawFeature(imgEl, ft) {
            if (!imgEl || !ft || !ft.parameters || (ft.type != 'org.ecocean.boundingBox')) return;
            var f = $('<div title="' + ft.id + '" id="feature-' + ft.id + '" class="featurebox" />');
            var scale = imgEl.height / imgEl.naturalHeight;
//console.info('mmmm scale=%f (ht=%d/%d)', scale, imgEl.height, imgEl.naturalHeight);
            if (scale == 1) return;
            imgEl.setAttribute('data-feature-drawn', true);
            f.css('width', (ft.parameters.width * scale) + 'px');
            f.css('height', (ft.parameters.height * scale) + 'px');
            f.css('left', (ft.parameters.x * scale) + 'px');
            f.css('top', (ft.parameters.y * scale) + 'px');
            if (ft.parameters.theta) f.css('transform', 'rotate(' +  ft.parameters.theta + 'rad)');
//console.info('mmmm %o', f);
            $(imgEl).parent().append(f);
        }
	</script></p>
	
	<p>
	<%
	if(!save){
	%>
	<b><%=vlist%></b>
	<%
	}
	if(!save && viewpoint!=null){
	%>
	<b><%=clist%></b>
	<%
	}
	%>
	</p>
	

	

	<%
	if (save) {
	    if (ft != null) {
	        out.println("saved(not) " + ft);
	        myShepherd.rollbackDBTransaction();
		    myShepherd.closeDBTransaction();
	        return;
	    }
	
	    FeatureType.initAll(myShepherd);
	    JSONObject fparams = new JSONObject();
	    fparams.put("x", xywh[0]);
	    fparams.put("y", xywh[1]);
	    fparams.put("width", xywh[2]);
	    fparams.put("height", xywh[3]);
	    fparams.put("_manualAnnotation", System.currentTimeMillis());
	    ft = new Feature("org.ecocean.boundingBox", fparams);
	    ma.addFeature(ft);
	    Annotation ann = new Annotation(null, ft, iaClass);
	    ann.setMatchAgainst(matchAgainst);
	    ann.setViewpoint(viewpoint);
	    String encMsg = "(no encounter)";
	    if (enc != null) {
	        if (cloneEncounter) {
	            Encounter clone = enc.cloneWithoutAnnotations();
	            clone.addAnnotation(ann);
	            clone.addComments("<p data-annot-id=\"" + ann.getId() + "\">Encounter cloned and <i>new Annotation</i> manually added by " + AccessControl.simpleUserString(request) + "</p>");
	            myShepherd.getPM().makePersistent(clone);
	            myShepherd.updateDBTransaction();
	            encMsg = clone.toString() + " cloned from " + enc.toString();
	            added2enc=clone.getCatalogNumber();
	            try {
	  
	                Occurrence occ = myShepherd.getOccurrence(enc);
	                if (occ!=null) {
	                	occ.addEncounterAndUpdateIt(clone);
		                occ.setDWCDateLastModified();
		                myShepherd.updateDBTransaction();
	                }
	                //let's create an occurrence to link these two Encounters
	                else{
	                	
	                	occ = new Occurrence(Util.generateUUID(), clone);
	                	occ.addEncounter(enc);
	                	myShepherd.getPM().makePersistent(occ);
	                	myShepherd.updateDBTransaction();
	                	
	                }
	            } catch (Exception e) {
	                e.printStackTrace();
	                myShepherd.rollbackDBTransaction();
	            }
	            
	            
	            
	        } else {
	            enc.addAnnotation(ann);
	            added2enc=enc.getCatalogNumber();
	            enc.addComments("<p data-annot-id=\"" + ann.getId() + "\"><i>new Annotation</i> manually added by " + AccessControl.simpleUserString(request) + "</p>");
	            encMsg = enc.toString();
	        }
	    }
	    System.out.println("manualAnnotation: added " + ann + " and " + ft + " to enc=" + encMsg);
	    myShepherd.getPM().makePersistent(ft);
	    myShepherd.getPM().makePersistent(ann);
	
	    if (removeTrivial) {
	        //note this will only remove (at most) ONE
	        Annotation foundTrivial = null;
	        for (Annotation a : ma.getAnnotations()) {
	            if (a.isTrivial()) foundTrivial = a;
	        }
	        if (foundTrivial == null) {
	            System.out.println("manualAnnotation: removeTrivial=true, but no trivial annot on " + ma);
	        } else {
	            foundTrivial.detachFromMediaAsset();
	            if (enc == null) {
	                System.out.println("manualAnnotation: removeTrivial detached " + foundTrivial + " (and Feature) from " + ma);
	            } else {
	                enc.removeAnnotation(foundTrivial);
	                System.out.println("manualAnnotation: removeTrivial detached " + foundTrivial + " (and Feature) from " + ma + " and " + enc);
	            }
	        }
	    }
	
	    myShepherd.commitDBTransaction();
		%><hr />
		
		<h2>Success!</h2> 
		<p>
		<b>Created <a href="../obrowse.jsp?type=Annotation&id=<%=ann.getId()%>" target="_new">Annotation <%=ann.getId()%></a> on Encounter <a href="encounter.jsp?number=<%=added2enc %>"><%=added2enc %></a>.
		</p>
		
		<%
	} 
	else {
	    myShepherd.rollbackDBTransaction();
		%>
		
	<%
	if(iaClass!=null){
	%>
	<p><b>3. Draw the new annotation bounding box below.</b></p>
	<%
	}
	%>
		
		<div id="img-wrapper">
		    <div class="axis" id="x-axis"></div>
		    <div class="axis" id="y-axis"></div>
		    <img class="asset" src="<%=ma.webURL()%>" id="main-img" onLoad="drawFeatures()" />
		    <div style="left: <%=(xywh[0] * scale)%>px; top: <%=(xywh[1] * scale)%>px; width: <%=(xywh[2] * scale)%>px; height: <%=(xywh[3] * scale)%>px;" id="bbox"></div>
		</div>
		
	<%
	if(bbox!=null){
	%>
		<p>
		(<%=xywh[0]%>,
		<%=xywh[1]%>)
		<%=xywh[2]%>x<%=xywh[3]%>
		</p>
		

		<p><b>4. Click SAVE below to complete the annotation.</b></p>
				
				
	<p>
	<% if (ft != null) { %>
	This will edit/alter <b>Feature <%=ft.getId()%>.</b>
	<% } else if (enc == null) { %>
	<i>This will <b>not attach (or clone)</b> to any Encounter.</i>
	<% } else if (cloneEncounter) { %>
	This will <i>clone</i> <b><a target="_new" href="encounter.jsp?number=<%=enc.getCatalogNumber()%>">encounter <%=enc.getCatalogNumber()%></a></b> and attach the new annotation to the clone.
	<% } else { %>
	This will attach to <b><a target="_new" href="encounter.jsp?number=<%=enc.getCatalogNumber()%>">encounter <%=enc.getCatalogNumber()%></a>.</b>
	<% } %>
	</p>
	
	<p>
	<% if (enc != null) { %>
	This will <%=(removeTrivial ? "<b>remove</b>" : "<i>not</i> remove")%> the trivial annotation.
	<% } %>
	</p>
				
				<h2><a href="manualAnnotation.jsp?<%=request.getQueryString()%>&save">SAVE</a></h2>
		
		
		
	<%
	}
	
	
	} //end else
}
catch(Exception ue){
	myShepherd.rollbackDBTransaction();
	ue.printStackTrace();
}
finally{
	myShepherd.closeDBTransaction();
}


%>
</div>

<jsp:include page="../footer.jsp" flush="true"/>
