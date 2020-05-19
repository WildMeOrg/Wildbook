<%@ page contentType="text/html; charset=utf-8" language="java"
         import="org.ecocean.CommonConfiguration,java.util.Properties, org.ecocean.servlet.ServletUtilities,
org.ecocean.NoteField,
org.ecocean.Shepherd,
org.ecocean.AccessControl" %>
<%

  //setup our Properties object to hold all properties
  
  String langCode = ServletUtilities.getLanguageCode(request);

  //set up the file input stream
  //FileInputStream propsInputStream=new FileInputStream(new File((new File(".")).getCanonicalPath()+"/webapps/ROOT/WEB-INF/classes/bundles/"+langCode+"/submit.properties"));
  //props.load(propsInputStream);
  
  

  
  boolean loggedIn = !AccessControl.isAnonymous(request);
  String context=ServletUtilities.getContext(request);
Shepherd myShepherd = new Shepherd(context);
        request.setAttribute("pageTitle", "Kitizen Science &gt; Monitoring the Impact of Spay/Neuter Programs on Free-roaming Cat Populations");

%>
<jsp:include page="header.jsp" flush="true"/>
<style>
.big-button {
    background-color: #9dc327;
    border-radius: 8px;
    padding: 10px 20px;
    color: white !important;
    font-size: 1.2em;
}
</style>

<div class="container maincontent">
<%= NoteField.buildHtmlDiv("7f34d433-ecfc-452e-a466-ac0c053104f7", request, myShepherd) %>


<div class="margin-top: 20px;">&nbsp;</div>

<!--
<% if (loggedIn) { %>
<p align="center"><a class="big-button" href="queue.jsp">Proceed to Study</a></p>
<% } else { //is logged in %>
<p style="display: none;" align="center"><a class="big-button" href="register.jsp">Participate in Kitizen Science!</a></p>
<% } %>
-->


</div>


<jsp:include page="footer.jsp" flush="true" />
