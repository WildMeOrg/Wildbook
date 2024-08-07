<!DOCTYPE html PUBLIC "-//W3C//DTD XHTML 1.0 Transitional//EN"
        "http://www.w3.org/TR/xhtml1/DTD/xhtml1-transitional.dtd">
<%@ page contentType="text/html; charset=utf-8" language="java" import="org.joda.time.LocalDateTime,
org.opensearch.client.Request,
java.util.List, java.util.ArrayList,
org.json.JSONObject,
org.ecocean.*
"%>

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

<%

Shepherd myShepherd = new Shepherd(request);
OpenSearch os = new OpenSearch();

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


req = new Request("GET", "encounter/_mappings");
JSONObject res = new JSONObject(os.getRestResponse(req));
%>
<h3>Encounter mapping</h3>
<textarea style="height: 30em; width: 100em;">
<%
out.println(res.toString(4));
%>
</textarea>
<%

req = new Request("GET", "encounter/_settings");
res = new JSONObject(os.getRestResponse(req));
%>
<h3>Encounter settings</h3>
<textarea style="height: 30em; width: 100em;">
<%
out.println(res.toString(4));
%>
</textarea>
<%


req = new Request("GET", "encounter/_search?pretty=true&q=*:*&size=1");
res = new JSONObject(os.getRestResponse(req));
%>
<h3>Encounter example</h3>
<textarea style="height: 30em; width: 100em;">
<%
out.println(res.toString(4));
%>
</textarea>
<%



myShepherd.rollbackAndClose();


myShepherd.rollbackAndClose();



%>
