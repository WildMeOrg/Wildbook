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
			<li>
				Be at right angles to the left or right hand flank (side) of the shark
			</li>
			<li>
				Be clear enough to show spots and markings on the shark’s skin
			</li>
			<li>
				Not be over exposed, making markings difficult to differentiate
			</li>
			<li>
				The strobe should be positioned to minimise “backscatter” in the image, as this gets confused with the shark’s markings
			</li>
			<li>
				Be captured in the highest resolution for you camera type
			</li>
			<li>
				Be sent to the website in J-peg format, preferably no larger than 2Mb
			</li>
		</ol> 
		<h3>
			Method for photographing Grey Nurse Sharks
		</h3>
		<ol>
			<li>
				Approach dive site in a calm and controlled manner.
			</li>
			<li>
				Position yourself in a suitable location to obtain clear images of the sharks.
			</li>
			<li>
				Be patient, remain as still as possible in order to allow the sharks to swim past you in their natural pattern.
			</li>
			<li>
				Do Not Chase or harm the shark. Refer to the Australian Department of the Environment's <a href="https://www.environment.gov.au/system/files/pages/0a19abf7-5f8d-46d1-a0ae-b11e58e47685/files/grey-nurse-code.pdf">Code of Conduct for diving with grey nurse sharks</a>.
			</li>
		</ol>

		<h2>Examples of Good photos</h2>
		<p>
			<figure>
				<img src="images/spotashark/GNS-Female1.jpg" alt="" width=80% style="display: block;margin: 0 auto;"/>
				<figcaption>a sexually immature shark&mdash;select 'unknown' for sex when uploading your image</figcaption>
			</figure>
			<figure>
				<img src="images/spotashark/GNS-JuvenileMale-PaulKrattiger.jpg" alt="" width=80% style="display: block;margin: 0 auto;"/>
				<figcaption>a juvenile male</figcaption>
			</figure>
			<figure>
				<img src="images/spotashark/GNS-MaleAdult.jpg" alt="" width=80% style="display: block;margin: 0 auto;"/>
				<figcaption>an adult male</figcaption>
			</figure>
		</p>

		<h2>Image Ownership</h2>
		<p>
			We only require these images for research purposes. The ownership of these images always remains with you. Whenever images are used in publications and/or on the website, recognition will be given to the photographer.
		</p>
	</div>


	<jsp:include page="footer.jsp" flush="true" />
