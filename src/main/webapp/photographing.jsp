<%@ page contentType="text/html; charset=utf-8" language="java" import="org.ecocean.servlet.ServletUtilities,java.util.Properties, java.io.FileInputStream, java.io.File, java.io.FileNotFoundException, org.ecocean.*" %>
<%

//setup our Properties object to hold all properties
	
	
	String context="context0";
	context=ServletUtilities.getContext(request);
	
%>


<jsp:include page="header.jsp" flush="true"/>

<div class="container maincontent">
		  	<h2>Photographing A Sea Turtle</h2>
	
			<p>Thanks for submitting your images and information to help Sea Turtle research!</p>
			<p>The Internet of Turtles takes data from users and implements computer vision software to automatically detect and identify individual turtles. </p>
			<p>There are a few things to keep in mind when submitting images that can help us.</p>
			<p>Our computer vision software uses the patttern on side of the turtle's head for identification. Images where the turtles head is blocked or blurry can still be useful, but a good shot of the side pattern is best.</p></p>
			<p>The images do not need to be very high resolution, but it doesn't hurt.<p>
			<p>If you can see the pattern on the side of the turtle's head clearly, and think that you could tell if the same turtle was in another picture, the software probably can too.</p>
			
			<p><img src="images/hawksbill_example.jpg" width="444" height="293" border="1" /></p>
			<p><label>The above image from wiki commons would perform fine with matching.</label></p>
			<p>Please include as much information about the turtle in your image as you can. The most important pieces are date and location. With this and a identifiable image, we can star to track individuals and populations.</p>
			<p>Once you have chosen some photographs or frame grabs from video, you can submit them directly to the Internet of Turtles at:<br/>
			  <a href="//iot.wildbook.org/submit.jsp">http://iot.wildbook.org/submit.jsp
            </a></p>
			<p>Using the email address you supply in your encounter report, our database can automatically keep you informed of how your data is used and can notify you whenever a successfully identified turtle that you reported is resighted.</p>
			<p>Thank you for contributing! </p>
			<p>&nbsp;</p>
			<p>&nbsp;</p>
	</div>
	

<jsp:include page="footer.jsp" flush="true" />

