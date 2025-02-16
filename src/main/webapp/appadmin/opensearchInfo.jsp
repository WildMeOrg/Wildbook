<!DOCTYPE html PUBLIC "-//W3C//DTD XHTML 1.0 Transitional//EN"
        "http://www.w3.org/TR/xhtml1/DTD/xhtml1-transitional.dtd">
<%@ page contentType="text/html; charset=utf-8" language="java" import="org.joda.time.LocalDateTime,
org.opensearch.client.Request,
java.util.List, java.util.ArrayList,
org.json.JSONObject,
org.ecocean.*
"%>
<%@ page import="org.ecocean.shepherd.core.Shepherd" %>

<%!

private String wrap(final String input, int len) {
    List<String> strings = new ArrayList<String>();
    int index = 0;
    while (index < input.length()) {
        strings.add(input.substring(index, Math.min(index + len, input.length())));
        index += len;
    }
    return String.join("\n", strings);
}

%>
<style>
h2 {
    margin-top: 2em;
    padding: 20px;
    background-color: #AAA;
}
</style>

<%

Shepherd myShepherd = new Shepherd(request);
OpenSearch os = new OpenSearch();

out.println("<p>SEARCH_SCROLL_TIME=" + os.SEARCH_SCROLL_TIME + "<br />");
out.println("SEARCH_PIT_TIME=" + os.SEARCH_PIT_TIME + "<br />");
out.println("BACKGROUND_DELAY_MINUTES=" + os.BACKGROUND_DELAY_MINUTES + "<br />");
out.println("BACKGROUND_SLICE_SIZE=" + os.BACKGROUND_SLICE_SIZE + "</p>");
out.println("BACKGROUND_PERMISSIONS_MINUTES=" + os.BACKGROUND_PERMISSIONS_MINUTES + "<br />");
out.println("BACKGROUND_PERMISSIONS_MAX_FORCE_MINUTES=" + os.BACKGROUND_PERMISSIONS_MAX_FORCE_MINUTES + "</p>");

out.println("<p>active indexing: <i>foreground</i>=" + String.valueOf(os.indexingActiveForeground()));
out.println(" / <i>background</i>=" + String.valueOf(os.indexingActiveBackground()) + "</p>");

Request req = new Request("GET", "_cat/indices?v");
//req.setJsonEntity(query.toString());
String rtn = os.getRestResponse(req);
%>
<textarea style="height: 10em; width: 100em;">
<%
out.println(wrap(rtn, 123));
%>
</textarea>
<%

for (String indexName : OpenSearch.VALID_INDICES) {
    out.println("<h2>" + indexName + "</h2>");
    req = new Request("GET", indexName + "/_mappings");
    JSONObject res = new JSONObject(os.getRestResponse(req));
%>
<h3><%=indexName%> mapping</h3>
<textarea style="height: 30em; width: 100em;">
<%
out.println(res.toString(4));
%>
</textarea>
<%

res = os.getSettings(indexName);
%>
<h3><%=indexName%> settings</h3>
<textarea style="height: 30em; width: 100em;">
<%
out.println(res.toString(4));
%>
</textarea>
<%


req = new Request("GET", indexName + "/_search?pretty=true&q=*:*&size=1");
res = new JSONObject(os.getRestResponse(req));
%>
<h3><%=indexName%> doc example</h3>
<textarea style="height: 30em; width: 100em;">
<%
out.println(res.toString(4));
%>
</textarea>
<%

}



myShepherd.rollbackAndClose();



%>
