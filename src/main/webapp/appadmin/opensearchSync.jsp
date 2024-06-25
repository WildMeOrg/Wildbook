<!DOCTYPE html PUBLIC "-//W3C//DTD XHTML 1.0 Transitional//EN"
        "http://www.w3.org/TR/xhtml1/DTD/xhtml1-transitional.dtd">
<%@ page contentType="text/html; charset=utf-8" language="java" import="org.joda.time.LocalDateTime,
org.ecocean.*
"%>


<%

Shepherd myShepherd = new Shepherd(request);
OpenSearch os = new OpenSearch();

int[] res = Encounter.opensearchSyncIndex(myShepherd);
out.println("<p>re-indexed: <b>" + res[0] + "</b></p>");
out.println("<p>removed: <b>" + res[1] + "</b></p>");

myShepherd.rollbackAndClose();

%>
