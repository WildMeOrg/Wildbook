<%@ page contentType="text/html; charset=utf-8" language="java"
         import="com.drew.imaging.jpeg.JpegMetadataReader,com.drew.metadata.Metadata,com.drew.metadata.Tag,org.ecocean.mmutil.MediaUtilities,
javax.jdo.datastore.DataStoreCache, org.datanucleus.jdo.*,javax.jdo.Query,
org.datanucleus.api.rest.orgjson.JSONObject,
org.datanucleus.ExecutionContext,
		 org.joda.time.DateTime,org.ecocean.*,org.ecocean.social.*,org.ecocean.servlet.ServletUtilities,java.io.File, java.util.*, org.ecocean.genetics.*,org.ecocean.security.Collaboration, com.google.gson.Gson,
org.datanucleus.api.rest.RESTUtils, org.datanucleus.api.jdo.JDOPersistenceManager, org.ecocean.Survey, org.ecocean.Encounter, org.ecocean.movement.*"

 %>



<%
String date = "03241985";
String id = "Charles Winchester Stewart";

%>

<jsp:include page="header.jsp" flush="true"/>	

<div class="container maincontent">

	<div class="row">


		<div class="col-xs-12 col-sm-6">	
			<h2>Col 1</h2>
			<h3><%=date%></h3>
		</div>
		
		<div class="col-xs-12 col-sm-6">
			<h2>Col 2</h2>
			<h3><%=id%></h3>
		</div>


	</div>

	<div class="row">


		<div class="col-xs-12 col-sm-6">
			<h2>Col 3</h2>
		</div>
	
		<div class="col-xs-12 col-sm-6">
			<h2>Col 4</h2>
		</div>


	</div>
</div>


<jsp:include page="footer.jsp" flush="true"/>