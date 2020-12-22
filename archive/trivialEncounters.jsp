<%@ page contentType="text/html; charset=utf-8" language="java"
     import="org.ecocean.*,
java.util.Collection,
java.io.IOException,
java.util.ArrayList,
javax.jdo.Query,
java.util.List,
java.util.Map,
org.json.JSONObject,

org.ecocean.servlet.ServletUtilities,
org.ecocean.media.*
              "

%><%
String context = ServletUtilities.getContext(request);
Shepherd myShepherd = new Shepherd(context);
myShepherd.beginDBTransaction();
String annId = request.getParameter("annId");
if (annId != null) {
    JSONObject rtn = new JSONObject();
    rtn.put("annId", annId);
    Annotation ann = myShepherd.getAnnotation(annId);
    if (ann == null) {
        rtn.put("success", false);
        rtn.put("error", "unknown id");
        myShepherd.rollbackDBTransaction();
    } else {
        boolean setTo = !ann.getMatchAgainst();
        ann.setMatchAgainst(setTo);
        myShepherd.commitDBTransaction();
        System.out.println("trivialEncounters: set matchAgainst=" + setTo + " on " + annId);
        rtn.put("matchAgainstSetTo", setTo);
        rtn.put("success", true);
    }
    response.setContentType("text/plain");
    out.println(rtn.toString());
    return;
}
%>
<html><head><title>Encounters with trivial Annotations</title>
<script src="https://ajax.googleapis.com/ajax/libs/jquery/3.3.1/jquery.min.js"></script>
<script>
function toggleAjax(aid) {
    $.ajax({
        url: 'trivialEncounters.jsp?annId=' + aid,
        contentType: 'application/json',
        complete: function(d) {
            console.info('complete -> %o', d);
            if (!d || !d.responseJSON || !d.responseJSON.success) alert('error');
        },
        dataType: 'json',
        type: 'GET'
    });
}


$(document).ready(function() {
    $('.ann').on('click', function(ev) {
        if (ev.target.tagName == 'A') return;
        toggleMatchAgainst(ev.currentTarget.id);
    });
});


function toggleMatchAgainst(aid) {
    var jel = $('#' + aid);
    if (!jel.length) return;
    toggleAjax(aid);
    var ma = jel.hasClass('matchagainst-true');
    jel.removeClass('matchagainst-true').removeClass('matchagainst-false');
    ma = !ma;
    jel.addClass('matchagainst-' + ma);
}
</script>
<style>
body {
    font-family: sans, arial;
}

.passed {
    opacity: 0.2;
}

div.ann {
    margin: 4px;
    display: inline-block;
    position: relative;
}
div.ann:hover {
    outline: solid 3px blue;
    opacity: 1.0;
}

div.ann img {
    max-height: 200px;
    max-width: 200px;
    min-width: 150px;
    min-height: 150px;
}

div.matchagainst-false {
    outline: solid 3px red;
    opacity: 0.3;
}

.caption {
    position: absolute;
    bottom: 2px;
    left: 0;
    width: 100%;
    background-color: rgba(255,255,255,0.5);
    font-size: 0.8em;
    color: black;
}

.caption a {
    text-decoration: none;
    color: black;
    cursor: pointer;
}
.caption a:hover {
    background-color: white;
}

.yes {
    float: right;
    background-color: #FF3;
    padding: 0 10px;
}

.matchagainst-false .yes {
    display: none;
}

.small {
    font-size: 0.8em;
    border-radius: 4px;
    padding: 2px 6px;
    background-color: 888;
}
</style>
</head>

<body><%

    String sql = "SELECT FROM org.ecocean.Encounter WHERE this.annotations.contains(ann) && ann.acmId == null VARIABLES org.ecocean.Annotation ann";
    Query query = myShepherd.getPM().newQuery(sql);
    Collection c = (Collection)query.execute();
    ArrayList<Encounter> encs = new ArrayList<Encounter>(c);
    query.closeAll();

    for (Encounter enc : encs) {
        for (Annotation ann : enc.getAnnotations()) {
            if (ann.getAcmId() != null) continue;
%><div id="<%=ann.getId()%>" class="ann matchagainst-<%=ann.getMatchAgainst()%>">
    <img src="<%=ann.getMediaAsset().safeURL()%>" />
    <div class="caption">
        <a target="_new" href="../obrowse.jsp?type=Annotation&id=<%=ann.getId()%>"><%=ann.getId().substring(0,8)%></a>
        <a target="_new" href="../encounters/encounter.jsp?number=<%=enc.getCatalogNumber()%>"><%=(enc.hasMarkedIndividual() ? enc.getIndividualID() : "<i>unid</i>")%></a>
        <span class="yes">&#x2714;</span>
    </div>
</div><%
        }
    }
    myShepherd.rollbackDBTransaction();
%>

</body></html>
