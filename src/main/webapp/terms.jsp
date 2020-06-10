<%@ page contentType="text/html; charset=utf-8" language="java" import="org.ecocean.servlet.ServletUtilities,java.util.ArrayList,java.util.List,java.util.ListIterator,java.util.Properties, java.io.FileInputStream, java.io.File, java.io.FileNotFoundException, org.ecocean.*, org.apache.commons.lang3.StringEscapeUtils" %>
<%

//setup our Properties object to hold all properties
	Properties props=new Properties();
	String langCode=ServletUtilities.getLanguageCode(request);

	String context="context0";
	context=ServletUtilities.getContext(request);

	//set up the file input stream
	//FileInputStream propsInputStream=new FileInputStream(new File((new File(".")).getCanonicalPath()+"/webapps/ROOT/WEB-INF/classes/bundles/"+langCode+"/submit.properties"));
	//props.load(getClass().getResourceAsStream("/bundles/"+langCode+"/whoweare.properties"));
	props=ShepherdProperties.getProperties("whoweare.properties", langCode, context);


%>

<jsp:include page="header.jsp" flush="true" />
<style>
    p.P1 { display: none; }
</style>

<div class="container maincontent">


<h1>Kitizen Science Usage and Volunteer Agreement</h1>
<p class="Standard"><span class="T2">Please read the following prior to logging into Kitizen Science. By doing so, you agree to all of the terms and conditions of this Kitizen Science Usage and Volunteer Agreement ("Agreement"). If you do not agree with ANY of the terms or conditions contained herein, please do not use Kitizen Science. Please contact kitizenscience@gmail.com with any questions related to this Agreement.</span></p>
<p class="Standard"><span class="T2">Kitizen Science reserves the right to change, add, or remove portions of this Agreement. If you, the Subscriber, will no longer be able to meet their obligations under the Agreement or will no longer be able to use or access the Service in a useful manner, you must inform Kitizen Science directly and no longer use Kitizen Science in any fashion.</span></p>

<p class="Standard"><h2>Usage Agreement: Software and Website Terms and Conditions</h2></p>
<p class="Standard"><h3>1. Definitions</h3></p>

<p class="Standard"><span class="T2">The Kitizen Science's Wildbook (the "Library") is a suite of online informational services (the "Services") provided by Kitizen Science and Wild Me, consisting of software applications and content provided by members of Kitizen Science and Wild Me, and members of the general public. "You" or "yours" refers to each person or entity, as applicable, that subscribes to the Kitizen Science Wildbook (the "Subscriber"). </span></p>

<p class="Standard"><h3>Authorized Users</h3></p>

<p class="Standard"><span class="T2">Authorized users are those persons, and only those persons, who have been issued a user identifier and password by Kitizen Science.</span></p>

<p class="Standard"><h3>2. General</h3></p>

<p class="Standard"><span class="T2">The User Account Request Form, this Usage Agreement and any other policies relating to the use of the Kitizen Science Wildbook (collectively, this "Usage Agreement") set forth the terms and conditions that apply to your use of the Kitizen Science Wildbook. By signing and submitting the User Account Request Form to Kitizen Science, you are deemed to have agreed to comply with all of the terms and conditions of this Agreement. The right to use the Services is limited to Subscribers and Authorized Users and is not transferable to any other person or entity. You are responsible for protecting the confidentiality of your access to the Services and for complying with any guidelines relating to security measures designed to prevent unauthorized access as outlined in the Usage Agreement. You are responsible to make reasonable efforts to inform Authorized Users aware of the terms of use as outlined by this Agreement. You are not liable for actions of other users but agree to work with Kitizen Science to rectify any problems caused by Authorized Users who infringe upon the terms of the Agreement. If the Subscriber fails to comply with any material term or condition of the Usage Agreement, Kitizen Science may terminate this Agreement if the Subscriber does not cure such noncompliance.</span></p>

<p class="Standard"><span class="T2">All Subscribers are authorized to provide remote access to Services only to Authorized Users as long as reasonable security procedures are undertaken that will prevent remote access by institutions or individuals that are not Authorized Users under this Usage Agreement.
</span></p>

<p class="Standard"><h3>Restrictions</h3></p>

<p class="Standard"><span class="T2">Any use of Kitizen Science or Wild Me other than as specifically authorized herein, without the express prior written permission of Kitizen Science and Wild Me is prohibited.  No other rights are implied with respect to Kitizen Science or Wild Me.  You shall not use Kitizen Science or Wild Me to engage in any of the following prohibited activities:</span></p>
<p class="P1"> </p>
<ul>
	<li>
		<p class="P6" style="margin-left:1.27cm;">
			<span style="display:block;float:left;min-width:0.635cm;"></span>
			<span class="T2">aggregation, hosting, posting, copying, distribution, public performance or public display of any Kitizen Science or Wild Me;</span>
			<span class="odfLiEnd"/> 
		</p>
	</li>
</ul>
<p class="P1"> </p>
<ul>
	<li>
		<p class="P6" style="margin-left:1.27cm;">
			<span style="display:block;float:left;min-width:0.635cm;"></span>
			<span class="T2">resale, sublicense, commercial use or exploitation of the Kitizen Science or Wild Me;</span>
			<span class="odfLiEnd"/> 
		</p>
	</li>
</ul>
<p class="P1"> </p>
<ul>
	<li>
		<p class="P6" style="margin-left:1.27cm;">
			<span style="display:block;float:left;min-width:0.635cm;"></span>
			<span class="T2">modifying or otherwise making any derivative works or uses of the Kitizen Science or Wild Me;</span>
			<span class="odfLiEnd"/> 
		</p>
	</li>
</ul>
<p class="P1"> </p>
<ul>
	<li>
		<p class="P6" style="margin-left:1.27cm;">
			<span style="display:block;float:left;min-width:0.635cm;"></span>
			<span class="T2">scraping or otherwise using any data mining, robots or similar data gathering or extraction methods on or in connection with the Kitizen Science or Wild Me;</span>
			<span class="odfLiEnd"/> 
		</p>
	</li>
</ul>
<p class="P1"> </p>
<ul>
	<li>
		<p class="P6" style="margin-left:1.27cm;">
			<span style="display:block;float:left;min-width:0.635cm;"></span>
			<span class="T2">downloading of any portion of Kitizen Science or Wild Me;</span>
			<span class="odfLiEnd"/> 
		</p>
	</li>
</ul>
<p class="P1"> </p>
<ul>
	<li>
		<p class="P6" style="margin-left:1.27cm;">
			<span style="display:block;float:left;min-width:0.635cm;"></span>
			<span class="T2">uploading any content that (i) may create a risk of harm or any other loss or damage to any person or property; (ii) contains intentionally inaccurate data, (iii) may constitute or contribute to a crime or a tort; (iv) includes any data that is illegal, unlawful, harmful, pornographic, defamatory, infringing, or invasive of personal privacy or publicity rights; (iv) contains any data that you does not have a right to upload into Kitizen Science or Wild Me, or (vi) constitutes information governed by HIPAA.</span>
			<span class="odfLiEnd"/> 
		</p>
	</li>
</ul>
<p class="P1"> </p>
<ul>
	<li>
		<p class="P6" style="margin-left:1.27cm;">
			<span style="display:block;float:left;min-width:0.635cm;"></span>
			<span class="T2">attempts to gain unauthorized access to, test the vulnerability of or disrupt Kitizen Science or Wild Me or distribute spam or malware;</span>
			<span class="odfLiEnd"/> 
		</p>
	</li>
</ul>
<p class="P1"> </p>
<ul>
	<li>
		<p class="P6" style="margin-left:1.27cm;">
			<span style="display:block;float:left;min-width:0.635cm;"></span>
			<span class="T2">any use of Kitizen Science or Wild Me (i) for unlawful, misleading, fraudulent, illegal or unauthorized purposes, (ii) that violates the rights of others, or (iii) that would cause Kitizen Science or Wild Me to be out of compliance with applicable law;</span>
			<span class="odfLiEnd"/> 
		</p>
	</li>
</ul>
<p class="P1"> </p>
<ul>
	<li>
		<p class="P6" style="margin-left:1.27cm;">
			<span style="display:block;float:left;min-width:0.635cm;"></span>
			<span class="T2">any use of Kitizen Science or Wild Me other than for their intended purposes, including any use intended to work around technical limitations of Kitizen Science or Wild Me or that would harm Kitizen Science or Wild Me or impair anyone else’s use of them.</span>
			<span class="odfLiEnd"/> 
		</p>
	</li>
</ul>
<p class="Standard"><h3>3. Usage</h3></p>

<p class="Standard"><span class="T2">Your use of the Services constitutes your agreement to all of the terms, conditions and notices below in addition to the general terms and conditions contained in this Agreement. If you do not agree with these provisions, please do not use the Services and request an immediate termination of your account.
</span></p>

<p class="Standard"><h3>In General</h3></p>

<p class="Standard"><span class="T2">As a condition of using the Services you agree to abide by all applicable local, Provincial, national and international laws and regulations relevant to the use of the Services including, without limitation, any applicable privacy legislation and policies. In addition, you warrant that you will not use the Services for any purpose that is unlawful or prohibited by this Agreement (including, without limitation, any use that infringes another’s copyright rights). You may not use the Services in any manner that could damage, disable, overburden or impair the Web site or any user of the Web site, or interfere with any other party’s use of the Services. You will not use the Services or content of the Kitizen Science Wildbook to accumulate data for or promote a cat photo identification library that may in any way be deemed by Kitizen Science to be competitive with its own efforts.</span></p>

<p class="Standard"><h3>Good Faith Data Collection, Reporting, Sharing, and Collaboration</h3></p>

<p class="Standard"><span class="T2">As a Subscriber, you have the right to submit photographs, data, and content to Kitizen Science. In all cases, you will submit content and encounter reports as completely and as accurately as possible, obtaining permission (if not yours) to use any copyrighted materials before submitting them to the Library. In addition, you will not submit any content that uses language or imagery (verbal or visual) that is deemed as offensive by anyone for any reason. Kitizen Science reserves the right to edit your content to enforce this.</span></p>

<p class="Standard"><span class="T2">While you retain the copyright to your photographic data, you are agreeing to assign in perpetuity to Kitizen Science a license to use, reuse, publish, republish, distribute, disseminate, sell, license, and otherwise use in any manner at the Kitizen Science's sole discretion any photograph or video submitted to Kitizen Science. This use, reuse, publication, republication, distribution, license, and/or sale of the subject matter may be performed in conjunction with any media, presently known or unknown, including but not limited to the Internet, magazines, television, academic journals, books, videos, DVDs, and other electronic media.</span></p>

<p class="Standard"><span class="T2">No requests for content "hiding" from other Subscribers will be honored by Kitizen Science, though our security system does prevent content "tampering" and protects your critical data. Additionally, you agree not to compete with users of the Wildbook from other regions or to allow others to compete through your access. The Wildbook is for collaboration, and users must respect the individual research interests of others outside their region of interest.</span></p>

<p class="Standard"><h3>Publications Using Data from the Library</h3></p>

<p class="Standard"><span class="T2">In any case where the content or Services of the Kitizen Science Wildbook are used in any way to contribute to any publication (online or print), Subscribers must make a good faith effort to include a visible acknowledgment of Kitizen Science in their publications. Furthermore, any Subscriber taking copyrighted content directly from the Wildbook and using it in any publication or medium must first obtain the written consent of the appropriate copyright holder(s) and comply with all national and international copyright laws.</span></p>

<p class="Standard"><h3>Copyright and Trademark Protection</h3></p>

<p class="Standard"><span class="T4">All materials contained on the Services (including, without limitation, the Web site’s "look and feel," layout, data, design, text, software, images, graphics, video and audio content (the "Content") are the property of Kitizen Science or the individual contributors of the content ("the Owner"), and their rights are protected by copyright, trademark and other intellectual property laws and international treaties. You may not reproduce any of these materials without the prior written consent of the owner. You may not distribute copies of materials found on this web site in any form (including by email or other electronic means) without prior written permission from the owner.</span></p>

<p class="Standard"><h3>Downloading Materials</h3></p>

<p class="Standard"><span class="T4">You may not publish, copy, automatically browse or download, display, distribute, post, transmit, perform, modify, create derivative works from or sell any Materials that you did not personally submit, information, products or services obtained from the Services in any form or by any means, including, without limitation, electronic, mechanical, photocopying, recording or otherwise, except as expressly permitted under applicable law or as described in this Usage Agreement. You also may not engage in systematic retrieval of data or other content or Materials from the Services to create or compile, directly or indirectly, a collection, compilation, database or directory. Nor may you “mirror” on your own site or any other server any Material contained on the Services, including, without limitation, the Services’ home page or result pages without the express and written consent of Kitizen Science and Wild Me. Use of the content and Materials on the Services for any purpose not expressly permitted by this Usage Agreement is prohibited.</span></p>

<p class="Standard"><h3>Third Party Web Sites</h3></p>

<p class="Standard"><span class="T4">Hyperlinks to other Internet resources are provided for your convenience. Kitizen Science has selected these resources as having some value and pertinence, but such resources’ development and maintenance are not under the direction of Kitizen Science. Thus, the content, accuracy, opinions expressed and other links provided by these resources are neither verified by Kitizen Science editors nor endorsed by Kitizen Science. Because Kitizen Science has no control over such Websites and resources, you acknowledge and agree that Kitizen Science is not responsible for the availability of such external Web sites or resources. In addition, you acknowledge and agree that Kitizen Science does not endorse and is not responsible or liable for any content, advertising, products or other materials on or available from such Websites or resources. Furthermore, you acknowledge and agree that Kitizen Science and Wild Me will not be liable, directly or indirectly, for any damage or loss caused by the use of any such content, products or materials.</span></p>

<p class="Standard"><h3>4. Intellectual Property Rights</h3></p>

<p class="Standard"><span class="T4">You acknowledge that the Services contain copyrighted material, trademarks, and other proprietary information owned by Kitizen Science and individual contributors to Wildbook, and that your subscription does not confer on you any right, title or interest in or to the Services, the related documentation or the intellectual property rights relating thereto other than the rights you retain on the material that you directly submitted to the Wildbook. Unauthorized copying of any portion of the Services may constitute an infringement of applicable copyright, trademark or other intellectual property laws or international treaties and may result in litigation under applicable copyright, trademark or other intellectual property laws or international treaties and loss of privileges granted pursuant to this Agreement.</span></p>

<p class="Standard"><h3>5. Account and Security</h3></p>

<p class="Standard"><span class="T4">You are responsible for maintaining the confidentiality of your method of accessing the Services.</span></p>

<p class="Standard"><h3>6. Disclaimer of Warranty; Limitation of Liability</h3></p>

<p class="Standard"><span class="T4">You expressly agree that use of the services is at your sole risk. Neither Wild Me USA, Kitizen Science, its affiliates nor any of their respective employees, agents, third party content providers or licensors warrant that the services will be available at any particular time, uninterrupted, or error free; nor do they make any warranty as to the results that may be obtained from use of the services, or as to the accuracy, reliability or content of any information or service provided through the services. The services are provided on an "as is" basis without warranties of any kind, either express or implied, including but not limited to warranties of title or implied warranties of merchantability or fitness for a particular purpose, other than those warranties which are implied by and incapable of exclusion, restriction or modification under applicable law.</span></p>
<p class="P4"> </p>
<p class="Standard"><span class="T4">In no event shall Wild Me or Kitizen Science be liable to you or any other person for loss of business or profits, or for any indirect, incidental or consequential damages arising out of the use of, or inability to use, the services, even if Wild Me or Kitizen Science was previously advised of the possibility of such damages, or for any other claim by a Subscriber, authorized user, or any other person. This warranty gives you specific legal rights, and you may also have other rights which vary by location.</span></p>
<p class="P4"> </p>
<p class="Standard"><span class="T4">In the event any claim relating to the performance or nonperformance by Kitizen Science or Wild Me pursuant to this Agreement, or in any other way concerning the Services, is made by a Subscriber or Authorized User, the actual damages to which such Subscriber or Authorized User may be entitled shall be limited to the lesser of the fees paid by the Subscriber or Authorized User for the Services or One US Dollar (US $1).</span></p>

<p class="Standard"><h3>7. Indemnification</h3></p>

<p class="Standard"><span class="T4">To the maximum extent permitted by law, you agree to defend, indemnify and hold harmless Kitizen Science, Wild Me, its affiliates and their respective directors, officers, employees and agents from and against any and all claims and expenses, including attorneys' fees, arising out of the use or unauthorized copying of the Services or any of their content, the violation of this Agreement or any applicable laws or regulations, or arising out of your violation of any rights of a user.</span></p>

<p class="Standard"><h3>8. Term and Termination of Agreement</h3></p>

<p class="Standard"><span class="T4">Either party shall have the right to terminate this Agreement at any time by providing notice of termination to the other party in accordance with the Subscription Form. In the event of termination of this Agreement by either party, you shall have no claims against Kitizen Science, Wild Me, its affiliates, or any individual contributors to the Wildbook. Termination of this Agreement automatically terminates your license to use the Services, any content or any other materials contained therein.</span></p>

<p class="Standard"><h3>9. Miscellaneous</h3></p>

<p class="Standard"><span class="T4">This Agreement is entire and complete, and no representations, warranties, agreements or covenants, express or implied, of any kind or character whatsoever have been made by either party hereto to the other, except as expressly set forth in this Agreement. Except as provided herein, this Agreement may not be modified or changed unless the same shall be in writing and signed by an authorized officer of the party to be bound thereby.</span></p>

<p class="Standard"><span class="T4">You may not assign any of your rights or delegate any of your obligations under this Agreement without Kitizen Science's prior written consent. If any provision of this Agreement is held to be overly broad in scope or duration by a court of competent jurisdiction such provision shall be deemed modified to the broadest extent permitted under applicable law. If any provision of this Agreement shall be held to be illegal, invalid or unenforceable by a court of competent jurisdiction, the validity, legality and enforceability of the remaining provisions shall not, in any way, be affected or impaired thereby. No waiver by either party of any breach or default hereunder shall be deemed to be a waiver of any preceding or subsequent breach or default. The section headings used herein are for convenience only and shall not be given any legal import.</span></p>

<p class="Standard"><span class="T4">The provisions of Sections 4, 6, 7 and 8 shall survive termination of this Agreement.</span></p>

<p class="Standard"><span class="T4">You will be asked to read, understand and agree to be bound by all of the terms of this Agreement before being issued an account.</span></p>

<hr>

	<div id="volunteer-agreement">
		<h2>Volunteer Agreement</h2>
		<p class="Standard"><span class="T2">I wish to assist nonprofit corporation Kitizen Science in its cat population monitoring research endeavors. In consideration of being given access to Kitizen Science’s app-based photographing platform; uploading, sieving, categorizing, and processing imagery and data in Kitizen Science’s project cloud database; and any other tasks assigned in the sole discretion of Kitizen Science, I agree to the following terms and conditions:
		</span></p>
		<p class="Standard"><h3>1. Research Volunteer Terms and Conditions</h3></p>
		<p class="Standard"><span class="T2">My participation in any of Kitizen Science’s research endeavors constitutes my agreement to all of the terms, conditions and notices below. I understand that if I do not agree with these provisions, I will request an immediate termination of my status as a Volunteer.</span></p>
		<p class="Standard"><h3>2. Respect for Applicable Privacy and Property Law – Noncompetition</h3></p>
		<p class="Standard"><span class="T2">As a condition of volunteering for Kitizen Science, I agree to abide by all applicable local, provincial, state, national and international laws and regulations including, without limitation, any applicable civil and criminal legislation and policies.</span></p>
		<p class="Standard"><span class="T2">In addition, I warrant that I will not use Kitizen Science’s software, cloud access, data, or resources for any purpose that is unlawful or prohibited by this Agreement (including, without limitation, any use that infringes another’s property, privacy, or copyright rights). I will honor private property and not trespass thereon, nor intentionally or knowingly photograph lurid, sexual, violent, criminal, or obscene acts of third parties in locations where they have a reasonable expectation of privacy.</span></p>
		<p class="Standard"><span class="T2">I agree not to act in any manner that could damage, disable, overburden or impair Kitizen Science or research databases, or interfere with any other party or Volunteer’s ability to assist in Kitizen Science’s research endeavors. I agree not to accumulate data for or promote a cat photo-identification library that may in any way be deemed by Kitizen Science to be competitive with its own efforts.</span></p>
		<p class="Standard"><h3>3. Respect for Animals</h3></p>
		<p class="Standard"><span class="T2">I agree not to touch, disturb, harm, or harass any cat (or any other animal) while volunteering with Kitizen Science, and understand that my role as a field survey volunteer is only to photograph cats without making any attempt to handle them physically.</span></p>
		<p class="Standard"><h3>4. Intellectual Property Rights</h3></p>
		<p class="Standard"><span class="T2">I understand that when using its cat survey application, Kitizen Science will have the ability to track my location and the locations of my photographs using all available technologies. I hereby give full consent to such geographical monitoring and use of that information as Kitizen Science sees fit.</span></p>
		<p class="Standard"><h3>5. Release and Limitation of Liability</h3></p>
		<p class="Standard"><span class="T2">I hereby holds harmless, forever release and discharge Kitizen Science, its officers, directors, agents, volunteers, advisors and/or representatives (collectively, “Releasees”) from, and, further, agree not to sue Releasees for, any and all claims, rights, demands, actions, causes of action, expenses, loss of business or profits, actual, consequential, or incidental damages, fees, and harms of any kind that I may ever have, whether known or unknown, accrued or unaccrued, arising in any way from volunteering for Kitizen Science. This is intended as a general release conferring the greatest protection possible to Releasees.
		In the event any claim relating to the performance or nonperformance by Kitizen Science or Wild Me pursuant to this Agreement, or in any other way concerning my volunteering activities, is made by me, the actual damages to which such I may be entitled shall be limited to the lesser of the fees paid by me or One US Dollar (US $1).</span></p>
		<p class="Standard"><h3>6. Indemnification – Duty to Defend</h3></p>
		<p class="Standard"><span class="T2">To the maximum extent permitted by law, I agree to defend and indemnify Releasees from any liability, cost or expense, including penalties, losses, damages, litigation expenses, judgments, attorney’s fees, or fines, arising from or resulting from my acts or omissions.</span></p>
		<p class="Standard"><h3>7. Assumption of Risk</h3></p>
		<p class="Standard"><span class="T2">I am aware of the risk that cats pose to persons, animals, and property, including but not limited to scratches, bites, physical injury, emotional harm, passing of zoonotic diseases, and indirect harm by trying to avoid contact with a feline (e.g., falling, straining, spraining, torqueing). I assume all such risks, and any others that may result in injury to me, my property, my animals, any member of my family or the public, or any property or animals of a member of my family or the public, arising from my volunteer efforts for Kitizen Science.</span></p>
		<p class="Standard"><h3>8. No Assignment.</h3></p>
		<p class="Standard"><span class="T2">No party’s rights under this Agreement shall be assigned without the express written consent of Kitizen Science, which consent may be withheld in its sole discretion.</span></p>
		<p class="Standard"><h3>9. Integration.</h3></p>
		<p class="Standard"><span class="T2">This Agreement is entered into by me without reliance upon any statement, representation, warranty, agreement, promise, inducement, or document of any kind not expressly contained herein and may not be changed unless in writing signed by Kitizen Science.</span></p>
		<p class="Standard"><h3>10. Severability.</h3></p>
		<p class="Standard"><span class="T2">If any portion of this Agreement is held invalid or unenforceable, all remaining portions shall nevertheless remain valid and enforceable, and to the extent it can be given effect without the invalid portions.</span></p>
		<p class="Standard"><h3>11. Breach and Attorney’s Fees.</h3></p>
		<p class="Standard"><span class="T2">I agree that should I be found by a court of competent jurisdiction to have materially breached this Agreement, the prevailing party shall recover attorney’s fees and costs, including those incurred in satisfying any judgment therefrom.</span></p>
		<p class="Standard"><h3>12. Venue and Applicable Law.</h3></p>
		<p class="Standard"><span class="T2">This Agreement shall be governed by and construed according to the laws of the State of Washington. Litigation arising from breaches of this contract shall commence in King County.</span></p>
		<p class="Standard"><h3>13. Term and Termination of Agreement</h3></p>
		<p class="Standard"><span class="T2">Either party shall have the right to terminate this Agreement at any time by providing notice of termination to the other party in writing. Termination of this Agreement automatically terminates my license to use Kitizen Science’s services, access its cloud database, or any content or any other materials contained therein.</span></p>
	</div>

</div>

<jsp:include page="footer.jsp" flush="true" />
