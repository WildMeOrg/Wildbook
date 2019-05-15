<%@ page contentType="text/html; charset=utf-8" language="java" import="org.ecocean.servlet.ServletUtilities,java.util.Properties, java.io.FileInputStream, java.io.File, java.io.FileNotFoundException, org.ecocean.*" %>
<%

//setup our Properties object to hold all properties
	
	String context = ServletUtilities.getContext(request);
	String lang = ServletUtilities.getLanguageCode(request);
	Properties props = new Properties();
  	props = ShepherdProperties.getProperties("instructionsMultiSubmit.properties", lang, context);

%>


<jsp:include page="../header.jsp" flush="true"/>

<div class="container maincontent">

	<h2>Multiple Submission Instructions</h2>
	<hr>


	<div class="row"> 

		<div class="col-xs-12 col-sm-6 col-lg-6 col-xl-6">

			<p>Instructions part one.. what encounters are, necessary data.</p> 
	
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

			<img src="/cust/mantamatcher/img/hero_manta.jpg" />
	
		</div>

		<div class="col-xs-12 col-sm-6 col-lg-6 col-xl-6"> 

			<p>Instructions part two.. Entering data for encounter.</p> 
	
		</div>
	
	</div>

	<br>
	<hr>
	<br>

	<div class="row">

		<div class="col-xs-12 col-sm-6 col-lg-6 col-xl-6"> 

			<p>Instructions part three.. Hiding images and sending data.</p>

		</div>

		<div class="col-xs-12 col-sm-6 col-lg-6 col-xl-6"> 

			<img src="/cust/mantamatcher/img/hero_manta.jpg" />
	
		</div>
	
	</div>

</div>
	

<jsp:include page="../footer.jsp" flush="true" />

