<%@ page contentType="text/html; charset=utf-8" language="java"
     import="org.ecocean.*,
org.ecocean.servlet.ServletUtilities,
org.json.JSONObject,
org.ecocean.media.*
              "
%>
<html>
<head><title>Manual Annotation</title>
<style>
body {
    font-family: arial, sans;
}

.error {
    color: red;
}

#img-wrapper {
    width: 40%;
    float: right;
    position: relative;
}
img.asset {
    height: 300px;
    position: absolute;
}

#bbox {
    outline: dotted blue 3px;
    border: solid 3px rgba(255,255,0,0.5);
    position: absolute;
}
</style>
<script>
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
String encounterId = request.getParameter("encounterId");
String viewpoint = request.getParameter("viewpoint");
boolean save = Util.requestParameterSet(request.getParameter("save"));
boolean cloneEncounter = Util.requestParameterSet(request.getParameter("cloneEncounter"));

String context = ServletUtilities.getContext(request);
Shepherd myShepherd = new Shepherd(context);
myShepherd.beginDBTransaction();

MediaAsset ma = MediaAssetFactory.load(assetId, myShepherd);
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

int[] xywh = null;
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

if ((bbox == null) || (xywh == null)) {
    out.println("<p class=\"error\">Invalid <b>bbox=" + bbox + "</b> (should be <i>x,y,w,h</i>)</p>");
    return;
}
double scale = 300d / ma.getHeight();

%>


<div id="img-wrapper">
    <img title="<%=ma.toString()%>" class="asset" src="<%=ma.webURL()%>" />
    <div style="left: <%=(xywh[0] * scale)%>px; top: <%=(xywh[1] * scale)%>px; width: <%=(xywh[2] * scale)%>px; height: <%=(xywh[3] * scale)%>px;" id="bbox"></div>
</div>

<p>
MediaAsset <b><a target="_new" href="../obrowse.jsp?type=MediaAsset&id=<%=ma.getId()%>"><%=ma.getId()%></a></b>
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
<% if (enc == null) { %>
<i>will <b>not attach (or clone)</b> to any Encounter</i>
<% } else if (cloneEncounter) { %>
will <i>clone</i> <b><a target="_new" href="../obrowse.jsp?type=Encounter&id=<%=enc.getCatalogNumber()%>">Encounter <%=enc.getCatalogNumber()%></a></b> and attach to clone
<% } else { %>
attaching to <b><a target="_new" href="../obrowse.jsp?type=Encounter&id=<%=enc.getCatalogNumber()%>">Encounter <%=enc.getCatalogNumber()%></a></b>
<% } %>
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
    Feature ft = new Feature("org.ecocean.boundingBox", fparams);
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
    myShepherd.commitDBTransaction();
%><hr />

<p>Created
<b><a href="../obrowse.jsp?type=Annotation&id=<%=ann.getId()%>" target="_new">Annotation <%=ann.getId()%></a></b><br />
and
<b><a href="../obrowse.jsp?type=Feature&id=<%=ft.getId()%>" target="_new">Feature <%=ft.getId()%></a></b>
</p>

<%
} else {
    myShepherd.rollbackDBTransaction();
%>

<h2><a href="manualAnnotation.jsp?<%=request.getQueryString()%>&save">SAVE</a></h2>

<% } %>

</body></html>
