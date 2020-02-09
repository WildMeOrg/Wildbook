<%@ page contentType="text/html; charset=utf-8" language="java"
     import="org.ecocean.*,
org.ecocean.servlet.ServletUtilities,
org.json.JSONObject,
org.ecocean.media.*
              "
%><% int imgHeight = 2000; %>
<html>
<head><title>Manual Annotation</title>
<script src="../tools/jquery/js/jquery.min.js"></script>
<style>
body {
    font-family: arial, sans;
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
});
</script>
</head>
<body>
<h1>Manual Annotation</h1>

<%
String bbox = request.getParameter("bbox");
String aidparam = request.getParameter("assetId");
int assetId = -1;
try {
    assetId = Integer.parseInt(aidparam);
} catch (NumberFormatException nex) {}
String iaClass = request.getParameter("iaClass");
String maparam = request.getParameter("matchAgainst");
boolean matchAgainst = (maparam == null) || Util.booleanNotFalse(maparam);
String rtparam = request.getParameter("removeTrivial");
boolean removeTrivial = (rtparam == null) || Util.booleanNotFalse(rtparam);
String encounterId = request.getParameter("encounterId");
String featureId = request.getParameter("featureId");
String viewpoint = request.getParameter("viewpoint");
boolean save = Util.requestParameterSet(request.getParameter("save"));
boolean cloneEncounter = Util.requestParameterSet(request.getParameter("cloneEncounter"));

String context = ServletUtilities.getContext(request);
Shepherd myShepherd = new Shepherd(context);
myShepherd.beginDBTransaction();

Feature ft = null;
MediaAsset ma = null;
int[] xywh = null;

if (featureId != null) {
    try {
        ft = ((Feature) (myShepherd.getPM().getObjectById(myShepherd.getPM().newObjectIdInstance(Feature.class, featureId), true)));
    } catch (Exception ex) {}
    if (ft == null) {
        out.println("<p class=\"error\">Invalid <b>featureId=" + featureId + "</b></p>");
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
    return;
}

Encounter enc = null;
if (encounterId != null) {
    enc = myShepherd.getEncounter(encounterId);
    if (enc == null) {
        out.println("<p class=\"error\">Invalid <b>encounterId=" + encounterId + "</b></p>");
        return;
    }
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

%>

<script>scale = <%=scale%>;</script>


<p>
MediaAsset <b><a title="<%=ma.toString()%>" target="_new" href="../obrowse.jsp?type=MediaAsset&id=<%=ma.getId()%>"><%=ma.getId()%></a></b>
</p>

<p>
matchAgainst = <b><%=matchAgainst%></b>;
viewpoint = <b><%=viewpoint%></b>;
iaClass = <b><%=iaClass%></b>
</p>

<p>
(<%=xywh[0]%>,
<%=xywh[1]%>)
<%=xywh[2]%>x<%=xywh[3]%>
</p>

<p>
<% if (ft != null) { %>
editing/altering <b>Feature <%=ft.getId()%></b>
<% } else if (enc == null) { %>
<i>will <b>not attach (or clone)</b> to any Encounter</i>
<% } else if (cloneEncounter) { %>
will <i>clone</i> <b><a target="_new" href="../obrowse.jsp?type=Encounter&id=<%=enc.getCatalogNumber()%>">Encounter <%=enc.getCatalogNumber()%></a></b> and attach to clone
<% } else { %>
attaching to <b><a target="_new" href="../obrowse.jsp?type=Encounter&id=<%=enc.getCatalogNumber()%>">Encounter <%=enc.getCatalogNumber()%></a></b>
<% } %>
</p>

<p>
will <%=(removeTrivial ? "<b>remove</b>" : "<i>not</i> remove")%> trivial Annotation
</p>
<%
if (save) {
    FeatureType.initAll(myShepherd);
    JSONObject fparams = new JSONObject();
    fparams.put("x", xywh[0]);
    fparams.put("y", xywh[1]);
    fparams.put("width", xywh[2]);
    fparams.put("height", xywh[3]);
    fparams.put("_manualAnnotation", System.currentTimeMillis());

    if (ft != null) {
        JSONObject rev = ft.getParameters();
        if (rev == null) rev = new JSONObject();
        //Annotation ann = ft.getAnnotation();
        Annotation ann = myShepherd.getAnnotation(ft.getAnnotation().getId());
        rev.put("_annotationAcmId", ann.getAcmId());
        fparams.put("_previousRevision", rev);
        ft.setParametersAsString(fparams.toString());
        ft.setRevision();
        ann.setAcmId(null);
        out.println("<p>Altered <a href=\"../obrowse.jsp?type=Feature&id=" + ft.getId() + "\">Feature " + ft.getId() + "</a> (MediaAsset " + ma.getId() + " acmId reset null)</p>");
        System.out.println("manualAnnotation: altered " + ft);

    } else {

    FeatureType.initAll(myShepherd);
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
            encMsg = clone.toString() + " cloned from " + enc.toString();
        } else {
            enc.addAnnotation(ann);
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

<p>Created
<b><a href="../obrowse.jsp?type=Annotation&id=<%=ann.getId()%>" target="_new">Annotation <%=ann.getId()%></a></b><br />
and
<b><a href="../obrowse.jsp?type=Feature&id=<%=ft.getId()%>" target="_new">Feature <%=ft.getId()%></a></b>
</p>

<%
}  //non-Feature-mod

} else {
    myShepherd.rollbackDBTransaction();
%>

<h2><a href="manualAnnotation.jsp?<%=request.getQueryString()%>&save">SAVE</a></h2>


<div id="img-wrapper">
    <div class="axis" id="x-axis"></div>
    <div class="axis" id="y-axis"></div>
    <img class="asset" src="<%=ma.webURL()%>" />
    <div style="left: <%=(xywh[0] * scale)%>px; top: <%=(xywh[1] * scale)%>px; width: <%=(xywh[2] * scale)%>px; height: <%=(xywh[3] * scale)%>px;" id="bbox"></div>
</div>

<% } %>

</body></html>
