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
JSONObject update = null;
if ("update".equals(request.getQueryString())) {
    update = ServletUtilities.jsonFromHttpServletRequest(request);
}

String STATUS_KEY = "annotCheckStatus";
String context = ServletUtilities.getContext(request);
Shepherd myShepherd = new Shepherd(context);
myShepherd = new Shepherd("context0");

JSONObject status = SystemValue.getJSONObject(myShepherd, STATUS_KEY);
if (status == null) status = new JSONObject();

if (update != null) {
    response.setHeader("Content-type", "application/json");
    Integer assetId = update.optInt("assetId", -1);
    if (assetId > 0) {
        status.put(assetId.toString(), update);
        SystemValue.set(myShepherd, STATUS_KEY, status);
        myShepherd.commitDBTransaction();
    } else {
        update.put("error", "could not find assetId in JSON");
        myShepherd.rollbackDBTransaction();
    }
    out.println(update);

} else {


%><html><head><title>Annotation check</title>
<script src="https://ajax.googleapis.com/ajax/libs/jquery/3.3.1/jquery.min.js"></script>
<script>
function update(data, callback) {
    $.ajax({
        url: 'annotCheck.jsp?update',
        contentType: 'application/json',
        data: JSON.stringify(data),
        complete: function(d) {
            console.info('complete -> %o', d);
            if (typeof callback == 'function') callback(d.responseJSON);
        },
        dataType: 'json',
        type: 'POST'
    });
}

function markPassed(mid) {
    update({
        assetId: mid,
        passed: true
    }, function(d) {
        console.log('yes!!! %o', d);
        var div = $('#ma-' + d.assetId);
        div.addClass('passed');
        div.find('.pass-button').hide();
    });
console.log(mid);
    return true;
}

</script>
<style>
body {
    font-family: sans, arial;
}
.ma img {
    margin: 8px;
    height: 200px;
    float: left;
}
div.ma {
    clear: both;
}

.passed {
    opacity: 0.2;
}

.ma a {
    text-decoration: none;
    color: #EEE;
    background-color: #888;
    padding: 1px 8px;
    border-radius: 3px;
    margin: 2px;
}
.ma a:hover {
    background-color: #880;
}

div.ann {
    padding: 4px;
}
div.ann:hover {
    background-color: #ABF;
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

int pageSize = 30;
int startId = 0;
try { startId = Integer.parseInt(request.getParameter("startId")); } catch (Exception ex) {}
int pageNum = 0;
try { pageNum = Integer.parseInt(request.getParameter("pageNum")); } catch (Exception ex) {}

Query q = myShepherd.getPM().newQuery("javax.jdo.query.SQL", "SELECT COUNT(*) FROM \"MEDIAASSET_FEATURES\" where \"IDX\" > 0");
List results = (List)q.execute();
Long count = (Long) results.iterator().next();

long maxPages = Math.round(Math.floor(count - 0.5 / pageSize));

//out.println("<p><h1>startId = <b>" + startId + "</b></h1>");
out.println("<p><h1>");
if (pageNum > 0) out.println("<a href=\"?pageNum=" + (pageNum - 1) + "\">&lt;</a> ");
out.println("pageNum = <b>" + pageNum + "</b> <i>(pageSize = " + pageSize + ")</i>");
if (pageNum < maxPages) out.println("<a href=\"?pageNum=" + (pageNum + 1) + "\">&gt;</a> ");
out.println("</h1> (total: <b>" + count + "</b>)");
out.println("</p><hr />");


Query query = myShepherd.getPM().newQuery("select from org.ecocean.media.MediaAsset where id > " + startId + " && features.size() > 1");
query.setOrdering("id");
query.setRange(pageNum,pageSize);
Collection c = (Collection) (query.execute());
ArrayList<MediaAsset> mas = new ArrayList<MediaAsset>(c);
query.closeAll();

for (MediaAsset ma : mas) {
    JSONObject maStatus = status.optJSONObject(Integer.toString(ma.getId()));
    boolean passed = (maStatus != null);  //good enough for now?
    out.println("<div id=\"ma-" + ma.getId() + "\" class=\"ma" + (passed ? " passed" : "") + "\">");
    out.println("<img xtitle=\"" + ma.toString() + "\" src=\"" + ma.webURL() + "\" />");
    out.println("<a title=\"" + ma.toString() + "\" href=\"../obrowse.jsp?type=MediaAsset&id=" + ma.getId() + "\">MA " + ma.getId() + "</a>");
    for (Annotation ann : ma.getAnnotations()) {
        out.println("<div class=\"ann\">");
        out.println("<a title=\"" + ann.toString() + "\" href=\"../obrowse.jsp?type=Annotation&id=" + ann.getId() + "\">Ann " + ann.getId().substring(0,8) + "</a>");
        Encounter enc = ann.findEncounter(myShepherd);
        if (enc == null) {
            out.println("[no encounter]");
        } else {
            int asize = enc.getAnnotations().size();
            out.println("<a title=\"" + enc.toString() + "\" href=\"../encounters/encounter.jsp?number=" + enc.getCatalogNumber() + "\">Enc " + enc.getCatalogNumber().substring(0,8) + " <span class=\"small\">" + asize + "</span> </a>");
            if (enc.hasMarkedIndividual()) {
                out.println("<a href=\"../individuals.jsp?number=" + enc.getIndividualID() + "\">Ind " + enc.getIndividualID() + "</a>");
            } else {
                out.println("[no indiv]");
            }
        }
        out.println("</div>");
    }
    //if (!passed) out.println("<input class=\"pass-button\" type=\"button\" onClick=\"return markPassed(" +ma.getId() + ")\" value=\"mark passed\" />");
    out.println("<input class=\"pass-button\" type=\"button\" onClick=\"return markPassed(" +ma.getId() + ")\" value=\"mark passed\" />");
    out.println("</div>");
}

%>
</body></html>

<%
}  //POST if/else
%>
