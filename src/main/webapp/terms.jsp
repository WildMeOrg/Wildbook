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

<div class="container maincontent">


<p class="P5"><h1>Terms and Conditions</h1></p>
<p class="P1"> </p>
<p class="Standard"><h2>Usage Agreement</h2></p>
<p class="P2"> </p>
<p class="Standard"><span class="T2">Subscribers: Please read the following Usage Agreement prior to logging into Kitizen Science. By logging into Kitizen Science, you agree to all of the terms and conditions of this Kitizen Science Usage Agreement ("Usage Agreement"), including the terms, conditions, and notices contained in the "Usage" section of this Usage Agreement. If you do not agree with ANY of the terms or conditions contained herein, please do not use Kitizen Science. Please contact webmaster at kitizenscience@gmail.com with any questions related to this agreement.</span></p>
<p class="P1"> </p>
<p class="Standard"><span class="T2">Kitizen Science reserves the right to change, modify, add or remove portions of this Usage Agreement or the terms or conditions contained herein. If the Subscriber deems that they will no longer be able to meet their obligations under the Usage Agreement or that they will no longer be able to use or access the Service in a useful manner they must inform Kitizen Science directly and no longer use Kitizen Science in any manner.</span></p>
<p class="P2"> </p>
<p class="Standard"><h2>1. Definitions</h2></p>
<p class="P2"> </p>
<p class="Standard"><span class="T2">The Kitizen Science's Wildbook (the "Library") is a suite of online informational services (the "Services") provided by Kitizen Science and Wild Me, consisting of software applications and content provided by members of Kitizen Science and Wild Me, and members of the general public. "You" or "yours" refers to each person or entity, as applicable, that subscribes to the Kitizen Science Wildbook (the "Subscriber"). </span></p>
<p class="P2"> </p>
<p class="Standard"><h2>Authorized Users</h2></p>
<p class="P2"> </p>
<p class="Standard"><span class="T2">Authorized users are those persons, and only those persons, who have been issued a user identifier and password by Kitizen Science.</span></p>
<p class="P2"> </p>
<p class="Standard"><h2>2. General</h2></p>
<p class="P2"> </p>
<p class="Standard"><span class="T2">The User Account Request Form, this Usage Agreement and any other policies relating to the use of the Kitizen Science Wildbook (collectively, this "Agreement") set forth the terms and conditions that apply to your use of the Kitizen Science Wildbook. By signing and submitting the User Account Request Form to Kitizen Science, you are deemed to have agreed to comply with all of the terms and conditions of this Agreement. The right to use the Services is limited to Subscribers and Authorized Users and is not transferable to any other person or entity. You are responsible for protecting the confidentiality of your access to the Services and for complying with any </span><span class="T2">guidelines relating to security measures designed to prevent unauthorized access as outlined in the Usage Agreement. You are responsible to make reasonable efforts to inform Authorized Users aware of the terms of use as outlined by this agreement. You are not liable for actions of other users but agree to work with Kitizen Science to rectify any problems caused by Authorized Users who infringe upon the terms of the Agreement. If the Subscriber fails to comply with any material term or condition of the Usage Agreement, Kitizen Science may terminate this Agreement if the Subscriber does not cure such noncompliance.</span></p>
<p class="P1"> </p>
<p class="Standard"><span class="T2">All Subscribers are authorized to provide remote access to Services only to Authorized Users as long as reasonable security procedures are undertaken that will prevent remote access by institutions or individuals that are not Authorized Users under this Usage Agreement.</span></p>
<p class="P1"> </p>
<p class="Standard"><h2>Restrictions</h2></p>
<p class="P1"> </p>
<p class="Standard"><span class="T2">Any use of Kitizen Science or Wild Me other than as specifically authorized herein, without the express prior written permission of Kitizen Science and Wild Me is prohibited.  No other rights are implied with respect to Kitizen Science or Wild Me.  You shall not use Kitizen Science or Wild Me to engage in any of the following prohibited activities:</span></p>
<p class="P1"> </p><ul><li><p class="P6" style="margin-left:1.27cm;"><span style="display:block;float:left;min-width:0.635cm;"></span><span class="T2">aggregation, hosting, posting, copying, distribution, public performance or public display of any Kitizen Science or Wild Me;</span><span class="odfLiEnd"/> </p></li></ul><p class="P1"> </p><ul><li><p class="P6" style="margin-left:1.27cm;"><span style="display:block;float:left;min-width:0.635cm;"></span><span class="T2">resale, sublicense, commercial use or exploitation of the Kitizen Science or Wild Me;</span><span class="odfLiEnd"/> </p></li></ul><p class="P1"> </p><ul><li><p class="P6" style="margin-left:1.27cm;"><span style="display:block;float:left;min-width:0.635cm;"></span><span class="T2">modifying or otherwise making any derivative works or uses of the Kitizen Science or Wild Me;</span><span class="odfLiEnd"/> </p></li></ul><p class="P1"> </p><ul><li><p class="P6" style="margin-left:1.27cm;"><span style="display:block;float:left;min-width:0.635cm;"></span><span class="T2">scraping or otherwise using any data mining, robots or similar data gathering or extraction methods on or in connection with the Kitizen Science or Wild Me;</span><span class="odfLiEnd"/> </p></li></ul><p class="P1"> </p><ul><li><p class="P6" style="margin-left:1.27cm;"><span style="display:block;float:left;min-width:0.635cm;"></span><span class="T2">downloading of any portion of Kitizen Science or Wild Me; </span><span class="odfLiEnd"/> </p></li></ul><p class="P1"> </p><ul><li><p class="P6" style="margin-left:1.27cm;"><span style="display:block;float:left;min-width:0.635cm;"></span><span class="T2">uploading any content that (i) may create a risk of harm or any other loss or damage to any person or property; (ii) contains intentionally inaccurate data, (iii) may constitute or contribute to a crime or a tort; (iv) includes any data that is illegal, unlawful, harmful, pornographic, defamatory, infringing, or invasive of personal privacy or publicity rights; (iv) contains any data that you does not have a right to upload </span><span class="T2">into Kitizen Science or Wild Me, or (vi) constitutes information governed by HIPAA. </span><span class="odfLiEnd"/> </p></li></ul><p class="P1"> </p><ul><li><p class="P6" style="margin-left:1.27cm;"><span style="display:block;float:left;min-width:0.635cm;"></span><span class="T2">attempts to gain unauthorized access to, test the vulnerability of or disrupt Kitizen Science or Wild Me or distribute spam or malware;</span><span class="odfLiEnd"/> </p></li></ul><p class="P1"> </p><ul><li><p class="P6" style="margin-left:1.27cm;"><span style="display:block;float:left;min-width:0.635cm;"></span><span class="T2">any use of Kitizen Science or Wild Me (i) for unlawful, misleading, fraudulent, illegal or unauthorized purposes, (ii) that violates the rights of others, or (iii) that would cause Kitizen Science or Wild Me to be out of compliance with applicable law; </span><span class="odfLiEnd"/> </p></li></ul><p class="P1"> </p><ul><li><p class="P6" style="margin-left:1.27cm;"><span style="display:block;float:left;min-width:0.635cm;"></span><span class="T2">any use of Kitizen Science or Wild Me other than for their intended purposes, including any use intended to work around technical limitations of Kitizen Science or Wild Me or that would harm Kitizen Science or Wild Me or impair anyone else’s use of them.</span><span class="odfLiEnd"/> </p></li></ul><p class="P2"> </p>
<p class="Standard"><h2>3. Usage</h2></p>
<p class="P2"> </p>
<p class="Standard"><span class="T2">Your use of the Services constitutes your agreement to all of the terms, conditions and notices below in addition to the general terms and conditions contained in this Agreement. If you do not agree with these provisions, please do not use the Services and request an immediate termination of your account.</span></p>
<p class="P2"> </p>
<p class="Standard"><h2>In General</h2></p>
<p class="P2"> </p>
<p class="Standard"><span class="T2">As a condition of using the Services you agree to abide by all applicable local, Provincial, national and international laws and regulations relevant to the use of the Services including, without limitation, any applicable privacy legislation and policies. In addition, you warrant that you will not use the Services for any purpose that is unlawful or prohibited by this Agreement (including, without limitation, any use that infringes another’s copyright rights). You may not use the Services in any manner that could damage, disable, overburden or impair the Web site or any user of the Web site, or interfere with any other party’s use of the Services. You will not use the Services or content of the Kitizen Science Wildbook to accumulate data for or promote a cat photo-identification library that may in any way be deemed by Kitizen Science to be competitive with its own efforts.</span></p>
<p class="P2"> </p>
<p class="Standard"><h2>Good Faith Data Collection, Reporting, Sharing, and Collaboration</h2></p>
<p class="P2"> </p>
<p class="Standard"><span class="T2">As a Subscriber, you have the right to submit photographs, data, and content to Kitizen Science. The Kitizen Science Wildbook is a community resource, and its content may be used for a variety of research purposes. In all cases, you will submit content and encounter reports as completely and as accurately as possible, obtaining permission (if not yours) to use any </span><span class="T2">copyrighted materials before submitting them to the Library. In addition, you will not submit any content that uses language or imagery (verbal or visual) that is deemed as offensive by anyone for any reason. Kitizen Science reserves the right to edit your content to enforce this.</span></p>
<p class="P1"> </p>
<p class="Standard"><span class="T2">While you retain the copyright to your photographic data, you are agreeing to assign in perpetuity to Kitizen Science a license to use, reuse, publish, republish, distribute, disseminate, sell, license, and otherwise use in any manner at the Kitizen Science's sole discretion any photograph or video submitted to Kitizen Science. This use, reuse, publication, republication, distribution, license, and/or sale of the subject matter may be performed in conjunction with any media, presently known or unknown, including but not limited to the Internet, magazines, television, academic journals, books, videos, DVDs, and other electronic media.</span></p>
<p class="P3"> </p>
<p class="Standard"><span class="T2">No requests for content "hiding" from other Subscribers will be honored by Kitizen Science, though our security system does prevent content "tampering" and protects your critical data. Additionally, you agree not to compete with users of the Wildbook from other regions or to allow others to compete through your access. The Wildbook is for collaboration, and users must respect the individual research interests of others outside their region of interest.</span></p>
<p class="P2"> </p>
<p class="Standard"><h2>Publications Using Data from the Library</h2></p>
<p class="P2"> </p>
<p class="Standard"><span class="T2">In any case where the content or Services of the Kitizen Science Wildbook are used in any way to contribute to any publication (online or print), Subscribers must make a good faith effort to include a visible acknowledgment of Kitizen Science in their publications. Furthermore, any Subscriber taking copyrighted content directly from the Wildbook and using it in any publication or medium must first obtain the written consent of the appropriate copyright holder(s) and comply with all national and international copyright laws.</span></p>
<p class="P2"> </p>
<p class="Standard"><h2>Copyright and Trademark Protection</h2></p>
<p class="P2"> </p>
<p class="Standard"><span class="T4">All materials contained on the Services (including, without limitation, the Web site’s "look and feel," layout, data, design, text, software, images, graphics, video and audio content (the "Content") are the property of Kitizen Science or the individual contributors of the content ("the Owner"), and their rights are protected by copyright, trademark and other intellectual property laws and international treaties. You may not reproduce any of these materials without the prior written consent of the owner. You may not distribute copies of materials found on this web site in any form (including by email or other electronic means) without prior written permission from the owner.</span></p>
<p class="P2"> </p>
<p class="Standard"><h2>Downloading Materials</h2></p>
<p class="P2"> </p>
<p class="Standard"><span class="T4">You may not publish, copy, automatically browse or download, display, distribute, post, transmit, perform, modify, create derivative works from or sell any Materials that you did not personally submit, information, products or services obtained from the Services in any form or by any means, including, without limitation, electronic, mechanical, photocopying, recording or otherwise, except as expressly permitted under applicable law or as described in this Usage Agreement. You also may not engage in systematic retrieval of data or other content or Materials from the Services to create or compile, directly or indirectly, a collection, compilation, database or directory. Nor may you “mirror” on your own site or any other server any Material contained on the Services, including, without limitation, the Services’ home page or result pages without the express and written consent of Kitizen Science and Wild Me. Use of the content and Materials on the Services for any purpose not expressly permitted by this Usage Agreement is prohibited.</span></p>
<p class="P2"> </p>
<p class="Standard"><h2>Third Party Web Sites</h2></p>
<p class="P2"> </p>
<p class="Standard"><span class="T4">Hyperlinks to other Internet resources are provided for your convenience. Kitizen Science has selected these resources as having some value and pertinence, but such resources’ development and maintenance are not under the direction of Kitizen Science. Thus, the content, accuracy, opinions expressed and other links provided by these resources are neither verified by Kitizen Science editors nor endorsed by Kitizen Science. Because Kitizen Science has no control over such Websites and resources, you acknowledge and agree that Kitizen Science is not responsible for the availability of such external Web sites or resources. In addition, you acknowledge and agree that Kitizen Science does not endorse and is not responsible or liable for any content, advertising, products or other materials on or available from such Websites or resources. Furthermore, you acknowledge and agree that Kitizen Science and Wild Me will not be liable, directly or indirectly, for any damage or loss caused by the use of any such content, products or materials.</span></p>
<p class="P2"> </p>
<p class="Standard"><h2>4. Intellectual Property Rights</h2></p>
<p class="P2"> </p>
<p class="Standard"><span class="T4">You acknowledge that the Services contain copyrighted material, trademarks, and other proprietary information owned by Kitizen Science and individual contributors to Wildbook, and that your subscription does not confer on you any right, title or interest in or to the Services, the related documentation or the intellectual property rights relating thereto other than the rights you retain on the material that you directly submitted to the Wildbook. Unauthorized copying of any portion of the Services may constitute an infringement of applicable copyright, trademark or other intellectual property laws or international treaties and may result in litigation </span><span class="T4">under applicable copyright, trademark or other intellectual property laws or international treaties and loss of privileges granted pursuant to this Agreement.</span></p>
<p class="P2"> </p>
<p class="Standard"><h2>5. Account and Security</h2></p>
<p class="P2"> </p>
<p class="Standard"><span class="T4">You are responsible for maintaining the confidentiality of your method of accessing the Services.</span></p>
<p class="P2"> </p>
<p class="Standard"><h2>6. Disclaimer of Warranty; Limitation of Liability</h2></p>
<p class="P2"> </p>
<p class="Standard"><span class="T4">YOU EXPRESSLY AGREE THAT USE OF THE SERVICES IS AT YOUR SOLE RISK. NEITHER WILD ME USA, KITIZEN SCIENCE, ITS AFFILIATES NOR ANY OF THEIR RESPECTIVE EMPLOYEES, AGENTS, THIRD PARTY CONTENT PROVIDERS OR LICENSORS WARRANT THAT THE SERVICES WILL BE AVAILABLE AT ANY PARTICULAR TIME, UNINTERRUPTED, OR ERROR FREE; NOR DO THEY MAKE ANY WARRANTY AS TO THE RESULTS THAT MAY BE OBTAINED FROM USE OF THE SERVICES, OR AS TO THE ACCURACY, RELIABILITY OR CONTENT OF ANY INFORMATION OR SERVICE PROVIDED THROUGH THE SERVICES. THE SERVICES ARE PROVIDED ON AN "AS IS" BASIS WITHOUT WARRANTIES OF ANY KIND, EITHER EXPRESS OR IMPLIED, INCLUDING BUT NOT LIMITED TO WARRANTIES OF TITLE OR IMPLIED WARRANTIES OF MERCHANTABILITY OR FITNESS FOR A PARTICULAR PURPOSE, OTHER THAN THOSE WARRANTIES WHICH ARE IMPLIED BY AND INCAPABLE OF EXCLUSION, RESTRICTION OR MODIFICATION UNDER APPLICABLE LAW.</span></p>
<p class="P4"> </p>
<p class="Standard"><span class="T4">IN NO EVENT SHALL WILD ME OR KITIZEN SCIENCE BE LIABLE TO YOU OR ANY OTHER PERSON FOR LOSS OF BUSINESS OR PROFITS, OR FOR ANY INDIRECT, INCIDENTAL OR CONSEQUENTIAL DAMAGES ARISING OUT OF THE USE OF, OR INABILITY TO USE, THE SERVICES, EVEN IF WILD ME OR KITIZEN SCIENCE WAS PREVIOUSLY ADVISED OF THE POSSIBILITY OF SUCH DAMAGES, OR FOR ANY OTHER CLAIM BY A SUBSCRIBER, AUTHORIZED USER, OR ANY OTHER PERSON. THIS WARRANTY GIVES YOU SPECIFIC LEGAL RIGHTS, AND YOU MAY ALSO HAVE OTHER RIGHTS WHICH VARY BY LOCATION.</span></p>
<p class="P4"> </p>
<p class="Standard"><span class="T4">In the event any claim relating to the performance or nonperformance by Kitizen Science or Wild Me pursuant to this Agreement, or in any other way concerning the Services, is made by a Subscriber or Authorized User, the actual damages to which such Subscriber or Authorized User may be entitled shall be limited to the lesser of the fees paid by the Subscriber or Authorized User for the Services or One US Dollar (US $1).</span></p>
<p class="P2"> </p>
<p class="Standard"><h2>7. Indemnification</h2></p>
<p class="P2"> </p>
<p class="Standard"><span class="T4">To the maximum extent permitted by law, you agree to defend, indemnify and hold harmless Kitizen Science, Wild Me, its affiliates and their respective </span><span class="T4">directors, officers, employees and agents from and against any and all claims and expenses, including attorneys' fees, arising out of the use or unauthorized copying of the Services or any of their content, the violation of this Agreement or any applicable laws or regulations, or arising out of your violation of any rights of a user.</span></p>
<p class="P2"> </p>
<p class="Standard"><h2>8. Term and Termination of Agreement</h2></p>
<p class="P2"> </p>
<p class="Standard"><span class="T4">Either party shall have the right to terminate this Agreement at any time by providing notice of termination to the other party in accordance with the Subscription Form. In the event of termination of this Agreement by either party, you shall have no claims against Kitizen Science, Wild Me, its affiliates, or any individual contributors to the Wildbook. Termination of this Agreement automatically terminates your license to use the Services, any content or any other materials contained therein.</span></p>
<p class="P2"> </p>
<p class="Standard"><h2>9. Miscellaneous</h2></p>
<p class="P2"> </p>
<p class="Standard"><span class="T4">This Agreement is entire and complete, and no representations, warranties, agreements or covenants, express or implied, of any kind or character whatsoever have been made by either party hereto to the other, except as expressly set forth in this Agreement. Except as provided herein, this Agreement may not be modified or changed unless the same shall be in writing and signed by an authorized officer of the party to be bound thereby.</span></p>
<p class="P4"> </p>
<p class="Standard"><span class="T4">You may not assign any of your rights or delegate any of your obligations under this Agreement without Kitizen Science's prior written consent. If any provision of this Agreement is held to be overly broad in scope or duration by a court of competent jurisdiction such provision shall be deemed modified to the broadest extent permitted under applicable law. If any provision of this Agreement shall be held to be illegal, invalid or unenforceable by a court of competent jurisdiction, the validity, legality and enforceability of the remaining provisions shall not, in any way, be affected or impaired thereby. No waiver by either party of any breach or default hereunder shall be deemed to be a waiver of any preceding or subsequent breach or default. The section headings used herein are for convenience only and shall not be given any legal import.</span></p>
<p class="P4"> </p>
<p class="Standard"><span class="T4">The provisions of Sections 4, 6, 7 and 8 shall survive termination of this Agreement.</span></p>
<p class="P4"> </p>
<p class="Standard"><span class="T4">YOU WILL BE ASKED TO READ, UNDERSTAND AND AGREE TO BE BOUND BY ALL OF THE TERMS OF THIS AGREEMENT BEFORE BEING ISSUED AN ACCOUNT.</span></p>
<p class="Standard"> </p></body></html>
</div>

<jsp:include page="footer.jsp" flush="true" />

