<%@ page contentType="text/html; charset=utf-8" language="java"
         import="com.drew.imaging.jpeg.JpegMetadataReader,com.drew.metadata.Metadata,com.drew.metadata.Tag,org.ecocean.mmutil.MediaUtilities,
javax.jdo.datastore.DataStoreCache, org.datanucleus.jdo.*,javax.jdo.Query,
org.datanucleus.api.rest.orgjson.JSONObject,
org.datanucleus.ExecutionContext,
		 org.joda.time.DateTime,org.ecocean.*,org.ecocean.social.*,org.ecocean.servlet.ServletUtilities,java.io.File, java.util.*, org.ecocean.genetics.*,org.ecocean.security.Collaboration, com.google.gson.Gson,
org.datanucleus.api.rest.RESTUtils, org.datanucleus.api.jdo.JDOPersistenceManager" %>


<%
String context = ServletUtilities.getContext(request);
//handle some cache-related security
response.setHeader("Cache-Control", "no-cache"); //Forces caches to obtain a new copy of the page from the origin server
response.setHeader("Cache-Control", "no-store"); //Directs caches not to store the page under any circumstance
response.setDateHeader("Expires", 0); //Causes the proxy cache to see the page as "stale"
response.setHeader("Pragma", "no-cache");


String langCode = ServletUtilities.getLanguageCode(request);
Properties props = new Properties();
props = ShepherdProperties.getProperties("survey.properties", langCode, context);

%>

