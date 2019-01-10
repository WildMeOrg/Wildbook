<html><head><title>Annotation check</title>
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

<body>
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
%>



<%
String context = ServletUtilities.getContext(request);
Shepherd myShepherd = new Shepherd(context);
myShepherd = new Shepherd("context0");

int pageSize = 30;
int startId = 0;
try { startId = Integer.parseInt(request.getParameter("startId")); } catch (Exception ex) {}
int pageNum = 0;
try { pageNum = Integer.parseInt(request.getParameter("pageNum")); } catch (Exception ex) {}

Query q = myShepherd.getPM().newQuery("javax.jdo.query.SQL", "SELECT COUNT(*) FROM \"MEDIAASSET_FEATURES\" where \"IDX\" > 0");
List results = (List)q.execute();
Long count = (Long) results.iterator().next();

//out.println("<p><h1>startId = <b>" + startId + "</b></h1>");
out.println("<p><h1>pageNum = <b>" + pageNum + "</b> <i>(pageSize = " + pageSize + ")</i></h1>");
out.println(" (total: <b>" + count + "</b>)");
out.println("</p><hr />");


Query query = myShepherd.getPM().newQuery("select from org.ecocean.media.MediaAsset where id > " + startId + " && features.size() > 1");
query.setOrdering("id");
query.setRange(pageNum,pageSize);
Collection c = (Collection) (query.execute());
ArrayList<MediaAsset> mas = new ArrayList<MediaAsset>(c);
query.closeAll();

for (MediaAsset ma : mas) {
    out.println("<div class=\"ma\">");
    out.println("<img xtitle=\"" + ma.toString() + "\" src=\"" + ma.webURL() + "\" />");
    out.println("<a title=\"" + ma.toString() + "\" href=\"obrowse.jsp?type=MediaAsset&id=" + ma.getId() + "\">MA " + ma.getId() + "</a>");
    for (Annotation ann : ma.getAnnotations()) {
        out.println("<div class=\"ann\">");
        out.println("<a title=\"" + ann.toString() + "\" href=\"obrowse.jsp?type=Annotation&id=" + ann.getId() + "\">Ann " + ann.getId().substring(0,8) + "</a>");
        Encounter enc = ann.findEncounter(myShepherd);
        if (enc == null) {
            out.println("[no encounter]");
        } else {
            int asize = enc.getAnnotations().size();
            out.println("<a title=\"" + enc.toString() + "\" href=\"encounters/encounter.jsp?number=" + enc.getCatalogNumber() + "\">Enc " + enc.getCatalogNumber().substring(0,8) + " <span class=\"small\">" + asize + "</span> </a>");
            if (enc.hasMarkedIndividual()) {
                out.println("<a href=\"individuals.jsp?number=" + enc.getIndividualID() + "\">Ind " + enc.getIndividualID() + "</a>");
            } else {
                out.println("[no indiv]");
            }
        }
        out.println("</div>");
    }
    out.println("</div>");
}

%>
</body></html>
