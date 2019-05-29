<%@ page contentType="text/html; charset=utf-8" language="java" import="org.ecocean.servlet.ServletUtilities,java.util.Properties, java.io.FileInputStream, java.io.File, java.io.FileNotFoundException, org.ecocean.*" %>
<%

//setup our Properties object to hold all properties
	
	String context = ServletUtilities.getContext(request);
	String lang = ServletUtilities.getLanguageCode(request);
	Properties props = new Properties();
  	props = ShepherdProperties.getProperties("multipleSubmit.properties", lang, context);
	String baseUrl = CommonConfiguration.getServerURL(request, request.getContextPath());
%>


<jsp:include page="../header.jsp" flush="true"/>

<div class="container maincontent">

	<h2>Multiple Submission Instructions</h2>
	<hr>


	<div class="row"> 

		<div class="col-xs-12 col-sm-6 col-lg-6 col-xl-6">

			<h3><%=props.getProperty("headerOne")%></h3>
			<p><%=props.getProperty("instructionsOne")%></p> 
	
		</div>

		<div class="col-xs-12 col-sm-6 col-lg-6 col-xl-6"> 

			<img src="/cust/mantamatcher/img/hero_manta.jpg" />
	
		</div>
	
	</div>

	<br>
	<hr>
	<br>

	<div class="row">

		<div class="col-xs-12 col-sm-6 col-lg-6 col-xl-6"> 

			<img src="/cust/mantamatcher/img/instructionsImage2.png" />
	
		</div>

		<div class="col-xs-12 col-sm-6 col-lg-6 col-xl-6"> 

			<h3><%=props.getProperty("headerTwo")%></h3>
			<p><%=props.getProperty("instructionsTwo")%></p> 
	
		</div>
	
	</div>

	<br>
	<hr>
	<br>

	<div class="row">

		<div class="col-xs-12 col-sm-6 col-lg-6 col-xl-6"> 

			<h3><%=props.getProperty("headerThree")%></h3>
			<p><%=props.getProperty("instructionsThree")%></p>

		</div>

		<div class="col-xs-12 col-sm-6 col-lg-6 col-xl-6"> 

			<img src="/cust/mantamatcher/img/instructionsImage3.png" />
	
		</div>
	
	</div>

	<div class="row">

		<div class="col-xs-12 col-sm-12 col-lg-12 col-xl-12"> 

			<h4><a href="<%=baseUrl%>/multipleSubmit/multipleSubmit.jsp"><%= props.getProperty("returnToMultipleSubmit")%></a></h4>

		</div>
	
	</div>



</div>
	

<jsp:include page="../footer.jsp" flush="true" />

