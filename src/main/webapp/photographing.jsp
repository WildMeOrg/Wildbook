<%@ page contentType="text/html; charset=utf-8" language="java" import="org.ecocean.servlet.ServletUtilities,java.util.Properties, java.io.FileInputStream, java.io.File, java.io.FileNotFoundException, org.ecocean.*" %>
<%

//setup our Properties object to hold all properties


String context="context0";
context=ServletUtilities.getContext(request);

%>


<jsp:include page="header.jsp" flush="true"/>

<div class="container maincontent">

	<h2>Ideal Photo for ID</h2>
	<p>
	<h3>
		The Image should
	</h3>
		<ol>
			<li>TBD</li>
			
		</ol> 
		<h3>
			Method for photographing Grey Nurse Sharks
		</h3>
		<ol>
			<li>
				TBD
			</li>
		
		</ol>

		<h2>Examples of Good photos</h2>
		<p>
		TBD
		</p>

		<h2>Image Ownership</h2>
		<p>
			We only require these images for research purposes. The ownership of these images always remains with you. Whenever images are used in publications and/or on the website, recognition will be given to the photographer.
		</p>
	</div>


	<jsp:include page="footer.jsp" flush="true" />
