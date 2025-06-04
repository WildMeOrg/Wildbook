<%@ page contentType="text/html; charset=utf-8" language="java" import="org.ecocean.servlet.ServletUtilities,java.util.Properties, java.io.FileInputStream, java.io.File, java.io.FileNotFoundException, org.ecocean.*" %>
<%

//setup our Properties object to hold all properties
	
	
	String context="context0";
	context=ServletUtilities.getContext(request);
	
%>


<jsp:include page="header.jsp" flush="true"/>

<style>
.examples p {
	text-align: center;
}
.examples img {
	width: 60%;
}
.iucn {
	display: none;
	background-color: #CCC;
	padding: 15px;
	border-radius: 10px;
	margin: 20px 5px;
}
.iucn h2 {
	font-size: 1.2em;
}
</style>

<div class="container maincontent">
<h1>Photographing a giraffe</h1>
<p>
Did you know that by photographing a giraffe you can directly contribute to an Africa-wide effort to better understand and protect these amazing animals? Giraffe as one species are listed as vulnerable to extinction on
<a target="_new" href="http://www.iucnredlist.org/details/9194/0">The IUCN Red List of Threatened Species</a>.
Photographs showing the distinctive neck and body pattern of giraffe are used to uniquely identify individuals for long-term, mark-recapture analysis. Resulting population models can be used by local, regional, and international conservation and management authorities to understand the pressures on their populations and to take specific action to protect them.

<div class="iucn">

<h2><a target="_new" href="http://www.iucnredlist.org/details/9194/0">Giraffa camelopardalis (Giraffe)</a></h2>
www.iucnredlist.org
Taxonomic Notes: The IUCN SSC Giraffe and Okapi Specialist Group (GOSG) currently recognizes a single species, Giraffa camelopardalis. Nine subspecies of Giraffes are ...
</div>

<p>
Here are some examples of photographs (or frame grabs from video) that can be used to identify giraffe:
</p>

<div class="examples">
<p>
	<img src="images/photog-example-1.jpg" />
</p>
<p>
	<img src="images/photog-example-2.jpg" />
</p>
<p>
	<img src="images/photog-example-3.jpg" />
</p>
<p>
	<img src="images/photog-example-4.jpg" />
</p>
<p>
	<img src="images/photog-example-5.jpg" />
</p>
</div>
			<p>&nbsp;</p>
	</div>
	

<jsp:include page="footer.jsp" flush="true" />

