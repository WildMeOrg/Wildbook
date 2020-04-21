<jsp:include page="header.jsp" flush="true"/>
<%@ page contentType="text/html; charset=utf-8" language="java"
import="org.ecocean.servlet.ServletUtilities,
org.ecocean.*,
java.util.Properties,
" %>

<%
String context=ServletUtilities.getContext(request);
Properties props = new Properties();
props = ShepherdProperties.getProperties("learnMore.properties", "",context);

%>



<div class="container maincontent">


<h2>Learn More</h2>

<h3 class="section-header"><%=props.getProperty("howItWorks") %></h3>
<p class="lead"><%=props.getProperty("howItWorksDescription") %></p>
<!--<img width="500px" height="*" style="max-width: 100%;" height="*" class="lazyload" src="cust/mantamatcher/img/leopard_howitworks2.jpg" data-src="cust/mantamatcher/img/leopard_howitworks2.jpg" /> -->

<h3 class="section-header"><%=props.getProperty("howItWorks1") %></h3>
<p class="lead"><%=props.getProperty("howItWorks1Description") %></p>
<img width="500px" height="*" style="max-width: 100%;" height="*" class="lazyload" src="cust/mantamatcher/img/leopard_howitworks2.jpg" data-src="cust/mantamatcher/img/leopard_howitworks2.jpg" />

<h3 class="section-header"><%=props.getProperty("howItWorks2") %></h3>
<p class="lead"><%=props.getProperty("howItWorks2Description") %></p>
<!-- <img width="500px" height="*" style="max-width: 100%;" height="*" class="lazyload" src="cust/mantamatcher/img/leopard_howitworks2.jpg" data-src="cust/mantamatcher/img/leopard_howitworks2.jpg" /> -->

<h3 class="section-header"><%=props.getProperty("howItWorks4") %></h3>
<p class="lead"><%=props.getProperty("howItWorks4Description") %></p>
<img width="500px" height="*" style="max-width: 100%;" height="*" class="lazyload" src="cust/mantamatcher/img/puppy_with_big_ears.JPG" data-src="cust/mantamatcher/img/puppy_with_big_ears.JPG" />

<br/>

</div>

<jsp:include page="footer.jsp" flush="true"/>

