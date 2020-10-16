<%@ page contentType="text/html; charset=utf-8" language="java"
         import="org.ecocean.CommonConfiguration, org.ecocean.Encounter, org.ecocean.MarkedIndividual, org.ecocean.Shepherd,org.ecocean.servlet.ServletUtilities,javax.jdo.Extent,javax.jdo.FetchPlan, javax.jdo.Query, java.util.Iterator, java.util.Properties" %>

<%
String context="context0";
context=ServletUtilities.getContext(request);
	Shepherd myShepherd = new Shepherd(context);
	myShepherd.beginDBTransaction();
	
    //setup our Properties object to hold all properties
    //String langCode = "en";
    String langCode=ServletUtilities.getLanguageCode(request);
    
   String mediaId = request.getParameter("mediaId");
   String encounterId = request.getParameter("encounterId");
   
   if (mediaId == null) {
	   mediaId = "";
   }
   if (encounterId == null) {
	   encounterId = "";
   }
%>

	<jsp:include page="header.jsp" flush="true" />
	    <div class="container maincontent">
        <form method="post" action="EncounterSetPatterningPassport" enctype="multipart/form-data">
Encounter ID
<input type="text" name="encounterId" value="<%=encounterId%>">
<p/>
Photo/Media ID
<input type="text" name="mediaId" value="<%=mediaId%>">
<p/>
Patterning Passport XML File ***
<input type="file" name="patterningPassportData">
<p/>
<input type="submit">
</form>
        </div>
        
        <jsp:include page="footer.jsp" flush="true"/>
    
<%
   
    myShepherd.rollbackDBTransaction();
    myShepherd.closeDBTransaction();
 
    myShepherd = null;
%>
