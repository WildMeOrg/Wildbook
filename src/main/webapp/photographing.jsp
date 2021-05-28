<%@ page contentType="text/html; charset=utf-8" language="java" import="org.ecocean.servlet.ServletUtilities,java.util.Properties, java.io.FileInputStream, java.io.File, java.io.FileNotFoundException, org.ecocean.*" %>
<%

//setup our Properties object to hold all properties
	
	
	String context="context0";
	context=ServletUtilities.getContext(request);
	
%>


<jsp:include page="header.jsp" flush="true"/>

<div class="container maincontent">
		  <h2>HOW TO PHOTOGRAPH</h2>
		<h2>GROUPER IMAGES</h2>

	<p>Each grouper can be identified by its coloration patterns, spots, bars and blotches. A full side on shot is most
	valuable for identification and a set of images of both sides of the fish is the ideal data submission. However, fish
	can be identified by just head and tail patterns as well.</p>
	<img src="../webapp/cust/mantamatcher/img/grouper_photo_1.png" alt="">
	<img src="../webapp/cust/mantamatcher/img/grouper_photo_2.png" alt="">
	<p>Good example images of left and right side of the same fish in barred color phase</p>
	<h2>COLOR PHASES</h2>
	<p>Nassau Grouper can be seen in four color phases, A. barred B. bicolor C. dark D. white belly</p>
	<img src="../webapp/cust/mantamatcher/img/grouper_photo_3.png" alt="">
	<p>It is possible to identify Individual grouper from all four color phases however the most valuable are barred and
	bicolor.</p>
	<p>The dark phase can only be identified if the patterns of the fish can still be seen</p>
	<img src="../webapp/cust/mantamatcher/img/grouper_photo_4.png" alt="">
	<p>This foreground image is an example of a dark phase Nassau Grouper worth submitting. Fish in left background would not
	be good to submit.</p>
	<h2>YOUR APPROACH</h2>
	<ul>
		<li>Approach fish in a calm and controlled manner.</li>
		<li>Position yourself in a suitable location unobstructed by reef and level with the fish to obtain a clear image.</li>
		<li>After you capture an image of one side; if you wait or slowly move around the front of the fish it will often shift
		positions or slowly swim away and allow you to capture the other side.</li>
		<li>Do not chase or harm the fish in anyway.</li>
	</ul>
	<h2>YOUR MOST VALUABLE IMAGES OF GROUPER WILL BE:</h2>
	<ul>
		<li>Positioned at right angles to either the left side or right side of the fish.</li>
		<li>Clear enough to show patterns and markings.</li>
		<li>Properly exposed with enough light (particularly for darker color phased fish)</li>
		<li>Capture photos of both sides of each fish if possible.</li>
		<li>High-resolution images captured from your camera or extracted from video footage.</li>
		<li>Uploaded to website in jpeg format, preferably no larger than 2Mb file size.</li>
		<li>Bicolored images are also valuable. You will choose whether it is a bicolor image upon upload.</li>
	</ul>
	<img src="../webapp/cust/mantamatcher/img/grouper_photo_5.png" alt="">
	<p>Good example of a bicolored images of a fish. Crop each fish out and submit separately.</p>
	<h2>Important Notes</h2>
	<ul>
		<li>While these are the ideal image conditions, we would like all images even if they are not super clear, high resolution
		or slightly obstructed. Head shots and tail shots should also be submitted. The fish can often still be identified from
		these.</li>
		<li>Images captured as screenshots or snapshots from video footage (eg .GoPros, DSLRs) are valuable. That is how we capture
		the bulk of our research images. It is often easier to video the fish as you move around it and then capture the images
		topside for submission.</li>
		<li>We are currently only taking photos and videos of Nassau Grouper, but please save your photos of other grouper species
		as we will be adding multiple species in the near future.</li>
		<li>Fish sometime change color phases during an encounter. If you capture different color phases of the same individual
		please submit each of these and notify that they are the same individual.</li>
		<li>Please note any other interesting features on the fish; shark bites, tags, etc.</li>
		<li>If multiple individuals are captured in one image please crop and submit them separately.</li>
	</ul>
	<img src="../webapp/cust/mantamatcher/img/grouper_photo_6.png" alt="">
	<p>Example of a head shot of the same fish changing color phase but still distinguishable as an individual.</p>
	<img src="../webapp/cust/mantamatcher/img/grouper_photo_7.png" alt="">
	<img src="../webapp/cust/mantamatcher/img/grouper_photo_8.png" alt="">
	<img src="../webapp/cust/mantamatcher/img/grouper_photo_9.png" alt="">

			
	
	</div>
	

<jsp:include page="footer.jsp" flush="true" />

