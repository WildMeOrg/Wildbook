<%@ page contentType="text/html; charset=utf-8" language="java"
         import="org.ecocean.servlet.ServletUtilities,org.ecocean.*,
org.ecocean.media.*,
org.json.JSONObject,
java.net.URL,
java.util.ArrayList,
java.util.Properties,org.slf4j.Logger,org.slf4j.LoggerFactory" %>
<%

if (AccessControl.isAnonymous(request)) {
    response.sendError(401, "Denied");
    return;
}
String context = ServletUtilities.getContext(request);
Shepherd myShepherd = new Shepherd(context);
myShepherd.setAction("individualGallery.jsp");
myShepherd.beginDBTransaction();

User user = myShepherd.getUser(request);

boolean admin = (user.hasRoleByName("admin", myShepherd) || user.hasRoleByName("super_volunteer", myShepherd));
String id = request.getParameter("id");
String skipEncId = request.getParameter("subject");
  //handle some cache-related security
  response.setHeader("Cache-Control", "no-cache"); //Forces caches to obtain a new copy of the page from the origin server
  response.setHeader("Cache-Control", "no-store"); //Directs caches not to store the page under any circumstance
  response.setDateHeader("Expires", 0); //Causes the proxy cache to see the page as "stale"
  response.setHeader("Pragma", "no-cache"); //HTTP 1.0 backward compatibility

MarkedIndividual indiv = myShepherd.getMarkedIndividualQuiet(id);
if (indiv == null) {
    out.println("<h1>unknown id</h1>");
    return;
}


int matchPhoto = -1;
boolean secondary = Util.requestParameterSet(request.getParameter("secondary"));
try {
    matchPhoto = Integer.parseInt(request.getParameter("matchPhotoAssetId"));
} catch (Exception ex) {}

if (admin && (matchPhoto > 0)) {
    SystemValue.set(myShepherd, "MatchPhoto" + (secondary ? "2" : "") + "_" + id, matchPhoto);
    out.println("{\"success\": true, \"assetId\": " + matchPhoto + "}");
    myShepherd.commitDBTransaction();
    return;
}

%>
<script type="text/javascript">

var imgData = {};
var zscale = 1;

function imgLoaded(el) {
    var id = el.id.substring(4);
console.log('asset id=%o', id);
    var imgEl = $(el);
    if (!imgEl.length) return;
    if (!imgData[id] || !imgData[id].bbox) {
        imgEl.css({
            width: '100%',
            top: 0,
            left: 0
        });
        imgEl.show();
        imgEl.panzoom({maxScale:9}).on('panzoomend', function(ev, panzoom, matrix, changed) {
            if (!changed) return $(ev.currentTarget).panzoom('zoom');
        });
        return;
    }
    var wrapper = imgEl.parent();
    wrapper.css('background-color', '#FFF');
    var ow = imgData[id].origWidth;
    var oh = imgData[id].origHeight;
    var iw = imgEl[0].naturalWidth;
    var ih = imgEl[0].naturalHeight;
    var ww = wrapper.width();
    var wh = wrapper.height();
    for (var i = 0 ; i < imgData[id].bbox.length ; i++) {
        imgData[id].bbox[i] *= iw / ow;
    }
    //var padding = ww * 0.05;
    var padding = imgData[id].bbox[2] * 0.3;
    var ratio = ww / (imgData[id].bbox[2] + padding);
    if ((wh / (imgData[id].bbox[3] + padding)) < ratio) ratio = wh / (imgData[id].bbox[3] + padding);
console.log('img=%dx%d / wrapper=%dx%d / box=%dx%d / padding=%d', iw, ih, ww, wh, imgData[id].bbox[2], imgData[id].bbox[3], padding);
console.log('%.f', ratio);
/*
	var dx = (ww / 2) - ((imgData[id].bbox[2] + padding) * ratio / 2);
	var dy = (wh / 2) - ((imgData[id].bbox[3] + padding) * ratio / 2);
console.log('dx, dy %f, %f', dx, dy);
*/

        imgEl.css({
            width: '100%',
            top: 0,
            left: 0
        });

imgEl.panzoom({maxScale:20})
    .on('zoomstart panzoomstart panstart', function(ev) {
//console.log('start----- %o', ev);
        $('#wrapper-' + ev.target.id.substring(4) + ' .canvas-box').hide();
    })
    .on('zoomend panzoomend', function(ev, panzoom, matrix, changed) {
        adjustBox(ev.target.id.substring(4));
        if (!changed) {
            var rtn = $(ev.currentTarget).panzoom('zoom');
            adjustBox(ev.target.id.substring(4));
            return rtn;
        }
    });

zscale = ww / ow;
var yscale = wh / oh;
var px = -(imgData[id].bbox[0] * zscale) + (ww / 2) - (imgData[id].bbox[2] * zscale / 2);
var py = -(imgData[id].bbox[1] * yscale) + (wh / 2) - (imgData[id].bbox[3] * yscale / 2);

var zz = 2.2 * ww / imgData[id].bbox[2];
if (zz < 1) zz = 1;
console.info('[ zz = %f ]  px, py = %f,%f (zscale %f, yscale %f)', zz, px, py, zscale, yscale);
imgEl.panzoom('pan', zz * px, zz * py);
imgEl.panzoom('zoom', zz);

	imgEl.show();

        var box = $('<canvas width="' + ow + '" height="' + oh + '" class="canvas-box"></canvas>');
        box.css({
            transformOrigin: '50% 50%',
            xopacity: 0.5,
            xleft: imgData[id].bbox[0] * zscale + 'px',
            xtop: imgData[id].bbox[1] * zscale + 'px',
            left: 0, top: 0,
            width: '100%',
            xheight: wh + 'px'
        });
        var ctx = box[0].getContext('2d');
        ctx.strokeStyle = '#bff223';
        ctx.lineWidth = 5;
        ctx.setLineDash([10, 4]);
        ctx.beginPath();
        //ctx.rect(imgData[id].bbox[0] * zscale, imgData[id].bbox[1] * zscale, imgData[id].bbox[2] * zscale, imgData[id].bbox[3] * zscale);
        ctx.rect(imgData[id].bbox[0] - (padding/2), imgData[id].bbox[1] - (padding/2), imgData[id].bbox[2] + padding, imgData[id].bbox[3] + padding);
        ctx.stroke();
        box.hide();
        wrapper.append(box);
        adjustBox(id);
}


function adjustBox(id) {
    window.setTimeout(function() {
        var matrix = $('#img-' + id).css('transform');
        $('#wrapper-' + id + ' .canvas-box').css('transform', matrix).show();
    }, 300);
}


</script>

<style>
.img-wrapper {
    width: 48%;
    height: 650px;
    display: inline-block;
    margin: 10px 4px;
    position: relative;
    overflow: hidden;
    background-color: #DDD;
}
.gallery-img {
    position: absolute;
    max-width: none;
    display: none;
}
.gallery-box {
    pointer-events: none;
    position: absolute;
    outline: solid 2px #bff223;
}
.gallery-box-wrapper {
    pointer-events: none;
    position: absolute;
}
.canvas-box {
    pointer-events: none;
    position: absolute;
}
.img-info {
    position: absolute;
    right: 10px;
    top: 10px;
    display: inline-block;
    background-color: rgba(255,255,0,0.7);
    border-radius: 4px;
    padding: 2px 10px;
}
</style>
<jsp:include page="header.jsp" flush="true" />
<script src="tools/panzoom/jquery.panzoom.min.js"></script>

<div style="text-align: center;" class="maincontent">
<h2>Gallery for Individual <%=indiv.getDisplayName()%></h2>

<div style="margin-top: 30px;"></div>
<%
Integer mpMA = SystemValue.getInteger(myShepherd, "MatchPhoto_" + id);
Integer mp2MA = SystemValue.getInteger(myShepherd, "MatchPhoto2_" + id);

if (!Util.collectionIsEmptyOrNull(indiv.getEncounters())) for (Encounter enc : indiv.getEncounters()) {
    if ((skipEncId != null) && skipEncId.equals(enc.getCatalogNumber())) continue;
    if (!Util.collectionIsEmptyOrNull(enc.getAnnotations())) for (Annotation ann : enc.getAnnotations()) {
        MediaAsset ma = ann.getMediaAsset();
        if (ma == null) continue;
        JSONObject j = new JSONObject();
        j.put("annotationId", ann.getId());
        j.put("origWidth", ma.getWidth());
        j.put("origHeight", ma.getHeight());
        if (!ann.isTrivial()) j.put("bbox", ann.getBbox());
/*
        ArrayList<MediaAsset> kids = ma.findChildrenByLabel(myShepherd, "_mid");
        if (!Util.collectionIsEmptyOrNull(kids)) ma = kids.get(0);
*/
        URL url = ma.safeURL(myShepherd, request);
        out.println("<script> imgData[" + ma.getId() + "] = " + j.toString() + "; </script>");
%>

<div id="wrapper-<%=ma.getId()%>" class="img-wrapper">
    <img id="img-<%=ma.getId()%>" class="gallery-img" src="<%=url%>" onLoad="imgLoaded(this);" />

<% if (admin) { %>
    <div class="img-info"
        onClick="wildbook.openInTab('encounters/encounter.jsp?number=<%=enc.getCatalogNumber()%>');" title="open this encounter" style="cursor: pointer;"
><%
    if (ma.hasKeyword("MatchPhoto") || ((mpMA != null) && (ma.getId() == mpMA))) {
        out.println("<b>Match Photo</b>");
    } else if ((mp2MA != null) && (ma.getId() == mp2MA)) {
        out.println("<b style=\"color: #888;\"><i>Secondary</i> Match Photo</b>");
    } else {
        out.println("&#x2b08;");
    }
%>
</div>
<% } %>

</div>


<%
    }  //media loop
}  //enc loop
%>

<div id="gallery">
</div>

</div>


<jsp:include page="footer.jsp" flush="true" />


<%
myShepherd.rollbackDBTransaction();
%>
