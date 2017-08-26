<%@ page contentType="text/html; charset=utf-8" language="java" import="org.ecocean.servlet.ServletUtilities,java.util.Properties, java.io.FileInputStream, java.io.File, java.io.FileNotFoundException, org.ecocean.*" %>
<%

//setup our Properties object to hold all properties
	
	
	String context="context0";
	context=ServletUtilities.getContext(request);
	
%>


<jsp:include page="header.jsp" flush="true"/>

<div class="container maincontent">
		  <h2>Photographing A Whale Shark</h2>
	
			
			<p>Did you know that by photographing a whale shark you can directly contribute to a global effort to better understand and protect these amazing animals? 


 The whale shark is listed as <em>vulnerable </em>to extinction  in the <em><a href="http://www.iucnredlist.org/search/details.php/19488/all">IUCN Red List of Threatened Species</a>. </em>Photographs showing the distinctive patterning and scarring on whale sharks are used to
			  uniquely identify individuals for long-term, mark-recapture analysis. Resulting population models can be used by local, regional, and international conservation and management authorities to understand the pressures on this species 
	    and to take specific action to protect them.</p>
			<p>The most important thing to remember when attempting to photograph a whale shark is to remain at least 3 meters (10 feet) away from the shark. Touching or blocking the path of a
			whale shark may negatively influence its behavior and affect scientists' ability to photograph it again in the future.</p>
			<p>The following types of photographs (or frame grabs from video), listed in order of importance, can be used to uniquely identify individual whale sharks.</p>
			<p><strong>1. Left-side spot patterning.</strong> This is the most important type of photograph to us. Notice that the photographer is perpendicular to the spot patterning area above the left pectoral fin. photographs of this area at this angle maximize our ability to use software pattern recognition algorithms to identify this animal within a catalog of thousands of images using its unique &quot;bodyprint&quot; as an identifier. </p>
			<p><img src="images/whaleshark_example.jpg" width="444" height="293" border="1" /></p>
			<p>This is how our computer database sees the natural patterning in this image:</p>
			<p><img src="images/example_processed.jpg" width="437" height="429" border="1" /> </p>
			<p>To prevent double-counting sharks where images of the same shark from different sides may be sent in separately, we only give an unidentified, unmatched shark a new number (e.g. <a href="//www.whaleshark.org/individuals.jsp?shark=A-001">A-001</a>, <a href="http://www.whaleshark.org/individuals.jsp?shark=A-002">A-002</a>, etc.) if we have a left-side pattern. For previously sighted individuals, such as <a href="http://www.whaleshark.org/individuals.jsp?shark=H-019">H-019</a> in Utila, Honduras, a pattern match looks like this in our database:</p>
			<p><img src="images/whaleshark_example_simple_match.gif" width="555" height="184" border="1" /> </p>
			<p><strong>2. Right-side spot patterning.</strong> Similar to left-side patterning, right-side patterning can be scanned into our database and used to identify a previously sighted shark if it also has a right-side pattern. However, we do not allocate new shark numbers to unmatched right-side patterns. They remain unmatched in our system until the shark is sighted again and properly identified with a left-side pattern.</p>
			<p><img src="images/whaleshark_example_right.jpg" width="449" height="443" border="1" /> </p>
			<p><strong>3. Scarring.</strong> Photographs of scarring on the head, fins, and body can also help identify previously marked individuals.</p>
			<table width="358">
				<tr><td><img src="images/whaleshark_example_scar.jpg" width="356" height="356" border="1" /></td></tr>
				<tr>
				  <td>This visible caudal (tail) fin scar helps to identify shark <a href="//www.whaleshark.org/individuals.jsp?shark=A-001">A-001</a> (&quot;Stumpy&quot;) at Ningaloo Marine Park in Western Australia. </td>
				</tr>
		</table>
			<p>Once you have your whale shark photographs or frame grabs from video, you can submit them directly to the Wildbook for Whale Sharks at:<br />
			  <a href="//www.whaleshark.org/submit.jsp">http://www.whaleshark.org/submit.jsp
            </a></p>
			<p>Using  the email address you supply in your encounter report, our database can automatically keep you informed of how your data is used and can notify you whenever an identified shark that you reported is resighted.</p>
			<p>Thank you for contributing to this global effort to protect the whale sharks! </p>
			<p>&nbsp;</p>
		
			<p>&nbsp;</p>
	</div>
	

<jsp:include page="footer.jsp" flush="true" />

