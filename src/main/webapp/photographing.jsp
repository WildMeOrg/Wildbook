<%@ page contentType="text/html; charset=utf-8" language="java" import="org.ecocean.servlet.ServletUtilities,java.util.Properties, java.io.FileInputStream, java.io.File, java.io.FileNotFoundException, org.ecocean.*" %>
<%

//setup our Properties object to hold all properties
	
	
	String context="context0";
	context=ServletUtilities.getContext(request);
	
%>


<jsp:include page="header.jsp" flush="true"/>

<div class="container maincontent">
		  <h2>How to photograph Fire salamanders</h2>
	<p>Identifying distinct individuals is essential to increase the accuracy and completeness of population counts. We are
	interested in the black and yellow dorsal pattern on the back of the individuals and in the tail pattern of larvae as
	these are unique to each individual. Full body pictures will help us more easily to identify the individual. As fire
	salamanders are mainly nocturnal, good light conditions are also helpful. If you have more than one picture, no problem
	at all; this might even help us to identify the individual more easily. Although we prefer full body pictures from above of
	the individual, pictures from the side might help us to identify the sex of the individual.</p>
	<ol>
		<li>
			Of one or more distinguishable individuals
			<ul>
				<li>
					Black and yellow back/dorsal pattern of fire salamanders
				</li>
				<li>
					Tail pattern of larvae (preferentially from both sides)
				</li>
			</ul>
		</li>
		<li>
			Relatively large with decent resolution
		</li>
		<li>
			In focus and not blurry
		</li>
	</ol>
	<div class="row">
		<div class="col-xs-12 col-sm-6 col-md-4 col-lg-4">
			<img src="images/howtophotograph/photo_salamander_1.jpeg" alt="Perfect dog pic" width="100%" />
		</div>
		<div class="col-xs-12 col-sm-6 col-md-4 col-lg-4">
			<img src="images/howtophotograph/photo_salamander_2.jpeg" alt="Perfect dog pic" width="100%" />
		</div>
		<div class="col-xs-12 col-sm-6 col-md-4 col-lg-4">
			<img src="images/howtophotograph/photo_salamander_3.jpeg" alt="Perfect dog pic" width="100%" />
		</div>
	</div>
	
	</div>
	

<jsp:include page="footer.jsp" flush="true" />

