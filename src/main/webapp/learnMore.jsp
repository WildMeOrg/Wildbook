<jsp:include page="header.jsp" flush="true"/>
<%@ page contentType="text/html; charset=utf-8" language="java"
import="org.ecocean.servlet.ServletUtilities,
org.ecocean.*,
java.util.Properties

" %>

<%
String context=ServletUtilities.getContext(request);
Properties props = new Properties();
props = ShepherdProperties.getProperties("learnMore.properties", "en",context);

%>



<div class="container maincontent">


<h2>Learn More About Wildbook</h2>

<h3 class="section-header"><%=props.getProperty("howItWorks1") %></h3>
<p class="lead"><%=props.getProperty("howItWorks1Description") %></p>

<img width="500px" height="*" style="max-width: 100%;" height="*" class="img-responsive center-block" src="cust/mantamatcher/img/learn_dog_pack.jpg" />

<h3 class="section-header"><%=props.getProperty("howItWorks2") %></h3>
<p class="lead"><%=props.getProperty("howItWorks2Description") %></p>
<img width="500px" height="*" style="max-width: 100%;" height="*" class="img-responsive center-block" src="cust/mantamatcher/img/leopard_howitworks2.jpg" />

<h3 class="section-header"><%=props.getProperty("howItWorks4") %></h3>
<p class="lead"><%=props.getProperty("howItWorks4Description") %></p>

<br>
<h4><%=props.getProperty("gotoWildbook") %> <a href="http://www.wildbook.org">http://www.wildbook.org</a></h4>

<br/>

</div>

<jsp:include page="footer.jsp" flush="true"/>

