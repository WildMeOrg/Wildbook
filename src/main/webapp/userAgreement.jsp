<%@ page contentType="text/html; charset=utf-8" language="java"
         import="org.apache.shiro.crypto.*,org.apache.shiro.util.*,org.apache.shiro.crypto.hash.*,org.ecocean.*,org.ecocean.servlet.ServletUtilities, java.util.Properties,java.util.ArrayList" %>


<%

String context="context0";
context=ServletUtilities.getContext(request);

  

  //Shepherd myShepherd = new Shepherd(context);
  
  	

//setup our Properties object to hold all properties

  //language setup
  //String langCode = "en";
  String langCode=ServletUtilities.getLanguageCode(request);
  

  Properties props = new Properties();
  //props.load(getClass().getResourceAsStream("/bundles/" + langCode + "/overview.properties"));
  props = ShepherdProperties.getProperties("overview.properties", langCode,context);



%>

<jsp:include page="header.jsp" flush="true"/>

  <style type="text/css">
    <!--


    .style2 {
      font-size: x-small;
      color: #000000;
    }

    -->
  </style>




<div class="container maincontent">
        
        <%
          if((request.getParameter("username")!=null)&&(request.getParameter("password")!=null)){
          %>
          
          <p style="color:#FF0000;"><strong><em>Our records indicate that you have not yet accepted the Usage Agreement. Acceptance is required to use this resource. Please read the agreement below and click the "Accept" button to proceed or "Reject" to decline and return to the home page.</em></strong></p>
          <%
          }
          %>
        <br />
        <br />
          <h1>Example User Agreement</h1>
          <div>
            <p>The following EXAMPLE policy applies only to individuals with login access to this Wildbook .</p>
          </div>
          <h2><a name="usage_agreement" id="usage_agreement">Usage Agreement</a></h2>
          <div>
            <p>Subscribers: Please read the following Usage Agreement prior to logging in to Wildbook. By logging into Wildbook, you agree to all of the terms and conditions of this Wildbook Usage Agreement (&ldquo;Usage Agreement&rdquo;), including the terms, conditions, and notices contained in the &ldquo;Usage&rdquo; section of this Usage Agreement. If you do not agree with ANY of the terms or conditions contained herein, please do not use Wildbook. Please contact webmaster at Wildbook dot org with any questions related to this agreement.</p>
            <p>Wild Me reserves the right to change, modify, add or remove portions of this Usage Agreement or the terms or conditions contained herein. However, subscribers are to be notified 30 days prior to the changes taking place. If the Subscriber deems that they will no longer be able to meet their obligations under the Usage Agreement or that they will no longer be able to use or access the Service in a useful manner they must inform Wild Me directly and no longer use Wildbook in any manner.</p>
          </div>
          <h3><a name="definitions" id="definitions">1. Definitions.</a></h3>
          <div>
            <p>The Wildbook Wildbook (the &ldquo;Wildbook&rdquo;) is a suite of online informational services (the &ldquo;Services&rdquo;) provided by Wild Me, consisting of software applications and content provided by members of Wild Me, members of the general public, and governmental management agencies. &ldquo;You&rdquo; or &ldquo;yours&rdquo; refers to each person or entity, as applicable, that subscribes to the Wildbook Wildbook (the &ldquo;Subscriber&rdquo;).</p>
          </div>
          <h4><a name="a_authorized_users" id="a_authorized_users">1.a Authorized Users</a></h4>
          <div>
            <p>Authorized users are those persons, and only those persons, who have been issued a user identifier and password by Wild Me.</p>
          </div>
          <h3><a name="general" id="general">2. General.</a></h3>
          <div>
            <p>The User Account Request Form, this Usage Agreement and any other policies relating to the use of the Wildbook (collectively, this &ldquo;Agreement&rdquo;) set forth the terms and conditions that apply to your use of the Wildbook. By signing and submitting the User Account Request Form to Wild Me, you are deemed to have agreed to comply with all of the terms and conditions of this Agreement. The right to use the Services is limited to Subscribers and Authorized Users and is not transferable to any other person or entity. You are responsible for protecting the confidentiality of your access to the Services and for complying with any guidelines relating to security measures designed to prevent unauthorized access as outlined in the Usage Agreement. You are responsible to make reasonable efforts to inform Authorized Users aware of the terms of use as outlined by this agreement. You are not liable for actions of other users but agree to work with Wild Me to rectify any problems caused by Authorized Users who infringe upon the terms of the Agreement. If the Subscriber fails to comply with any material term or condition of the Usage Agreement, Wild Me may terminate this Agreement upon written notice if the Subscriber does not cure such noncompliance within sixty (60) days of receiving written notice of the breach.</p>
            <p>All Subscribers are authorized to provide remote access to Services only to Authorized Users as long as reasonable security procedures are undertaken that will prevent remote access by institutions or individuals that are not Authorized Users under this Usage Agreement.</p>
          </div>
          <h3><a name="usage" id="usage">3. Usage.</a></h3>
          <div>
            <p>Your use of the Services constitutes your agreement to all of the terms, conditions, and notices below in addition to the general terms and conditions contained in this Agreement. If you do not agree with these provisions, please do not use the Services and request an immediate termination of your account.</p>
          </div>
          <h4><a name="in_general" id="in_general">In General</a></h4>
          <div>
            <p>As a condition of using the Services you agree to abide by all applicable local, Provincial, national and international laws and regulations relevant to the use of the Services including, without limitation, any applicable privacy legislation and policies. In addition, you warrant that you will not use the Services for any purpose that is unlawful or prohibited by this Agreement (including, without limitation, any use that infringes another&rsquo;s copyright rights). You may not use the Services in any manner that could damage, disable, overburden or impair the Web site or any user of the Web site, or interfere with any other party&rsquo;s use of the Services. You will not use the Services or content of the Wildbook Wildbook to accumulate data for or promote a wildlife photo-identification library that may in any way be deemed by Wild Me to be competitive with its own efforts.</p>
          </div>
          <h4><a name="good_faith_data_collection_reporting_sharing_and_collaboration" id="good_faith_data_collection_reporting_sharing_and_collaboration">Good Faith Data Collection, Reporting, Sharing, and Collaboration</a></h4>
          <div>
            <p>As a Subscriber, you have the right to submit photographs, data, and content to Wildbook. The Wildbook Wildbook is a community resource, and its content is used by a number of different individuals and agencies for a variety of research and conservation purposes. In all cases, you will submit content and encounter reports as completely and as accurately as possible, obtaining permission to use any copyrighted materials before submitting them to the Library. In addition, you will not submit any content that uses language or imagery (verbal or visual) that is deemed as offensive by anyone for any reason. Wild Me reserves the right to edit your content to enforce this.</p>
            <p>While you retain the copyright to your photographic data, you are agreeing to share this information with other Subscribers and to a limited extent with the general public. No requests for content &ldquo;hiding&rdquo; from other Subscribers will be honored by Wild Me, though our security system does prevent content &ldquo;tampering&rdquo; and protects your critical data. Additionally, you agree not to compete with users of the Wildbook from other regions or to allow others to compete through your access. The Wildbook is for collaboration, and users must respect the individual research interests of others outside their region of interest.</p>
          </div>
          <h4><a name="publications_using_data_from_the_wildbook" id="publications_using_data_from_the_wildbook">Publications Using Data from the Wildbook</a></h4>
          <div>
            <p>In any case where the content or Services of the Wildbook Wildbook are used in any way to contribute to any publication (online or print), Subscribers must make a good faith effort to include a visible acknowledgment of Wildbook in their publications. Furthermore, any Subscriber taking copyrighted content directly from the Wildbook and using it in any publication or medium must first obtain the written consent of the appropriate copyright holder(s) (such as encounter photographers) and comply with all national and international copyright laws.</p>
          </div>
          <h4><a name="copyright_and_trademark_protection" id="copyright_and_trademark_protection">Copyright and Trademark Protection</a></h4>
          <div>
            <p>All materials contained on the Services (including, without limitation, the Web site&rsquo;s &ldquo;look and feel,&rdquo; layout, data, design, text, software, images, graphics, video and audio content (the &ldquo;Content&rdquo;) are the property of Wild Me or the individual contributors of the content (&ldquo;the Owner&rdquo;), and their rights are protected by copyright, trademark and other intellectual property laws and international treaties. You may not reproduce any of these materials without the prior written consent of the owner. You may not distribute copies of materials found on this web site in any form (including by email or other electronic means) without prior written permission from the owner.</p>
          </div>
          <h4><a name="downloading_materials" id="downloading_materials">Downloading Materials</a></h4>
          <div>
            <p>You may not publish, copy, automatically browse or download, display, distribute, post, transmit, perform, modify, create derivative works from or sell any Materials that you did not personally submit, information, products or services obtained from the Services in any form or by any means, including, without limitation, electronic, mechanical, photocopying, recording or otherwise, except as expressly permitted under applicable law or as described in this Usage Agreement. You also may not engage in systematic retrieval of data or other content or Materials from the Services to create or compile, directly or indirectly, a collection, compilation, database or directory. Nor may you &ldquo;mirror&rdquo; on your own site or any other server any Material contained on the Services, including, without limitation, the Services&rsquo; home page or result pages without the express and written consent of Wild Me. Use of the content and Materials on the Services for any purpose not expressly permitted by this Usage Agreement is prohibited.</p>
          </div>
          <h4><a name="third_party_web_sites" id="third_party_web_sites">Third Party Web Sites</a></h4>
          <div>
            <p>Hyperlinks to other Internet resources are provided for your convenience. Wild Me has selected these resources as having some value and pertinence, but such resources&rsquo; development and maintenance are not under the direction of Wild Me. Thus, the content, accuracy, opinions expressed and other links provided by these resources are neither verified by Wild Me editors nor endorsed by Wild Me. Because Wild Me has no control over such Web sites and resources, you acknowledge and agree that Wild Me is not responsible for the availability of such external Web sites or resources. In addition, you acknowledge and agree that Wild Me does not endorse and is not responsible or liable for any content, advertising, products or other materials on or available from such Web sites or resources. Furthermore, you acknowledge and agree that Wild Me will not be liable, directly or indirectly, for any damage or loss caused by the use of any such content, products or materials.</p>
          </div>
          <h3><a name="intellectual_property_rights" id="intellectual_property_rights">4. Intellectual Property Rights.</a></h3>
          <div>
            <p>You acknowledge that the Services contain copyrighted material, trademarks, and other proprietary information owned by Wild Me and individual contributors to Wildbook, and that your subscription does not confer on you any right, title or interest in or to the Services, the related documentation or the intellectual property rights relating thereto other than the rights you retain on the material that you directly submitted to the Wildbook. Unauthorized copying of any portion of the Services may constitute an infringement of applicable copyright, trademark or other intellectual property laws or international treaties and may result in litigation under applicable copyright, trademark or other intellectual property laws or international treaties and loss of privileges granted pursuant to this Agreement.</p>
          </div>
          <h3><a name="account_and_security" id="account_and_security">5. Account and Security.</a></h3>
          <div>
            <p>You are responsible for maintaining the confidentiality of your method of accessing the Services.</p>
          </div>
          <h3><a name="disclaimer_of_warrantylimitation_of_liability" id="disclaimer_of_warrantylimitation_of_liability">6. Disclaimer of Warranty; Limitation of Liability.</a></h3>
          <div>
            <p>YOU EXPRESSLY AGREE THAT USE OF THE SERVICES IS AT YOUR SOLE RISK. NEITHER WILD ME USA, ITS AFFILIATES NOR ANY OF THEIR RESPECTIVE EMPLOYEES, AGENTS, THIRD PARTY CONTENT PROVIDERS OR LICENSORS WARRANT THAT THE SERVICES WILL BE AVAILABLE AT ANY PARTICULAR TIME, UNINTERRUPTED, OR ERROR FREE; NOR DO THEY MAKE ANY WARRANTY AS TO THE RESULTS THAT MAY BE OBTAINED FROM USE OF THE SERVICES, OR AS TO THE ACCURACY, RELIABILITY OR CONTENT OF ANY INFORMATION OR SERVICE PROVIDED THROUGH THE SERVICES. THE SERVICES ARE PROVIDED ON AN &ldquo;AS IS&rdquo; BASIS WITHOUT WARRANTIES OF ANY KIND, EITHER EXPRESS OR IMPLIED, INCLUDING BUT NOT LIMITED TO WARRANTIES OF TITLE OR IMPLIED WARRANTIES OF MERCHANTABILITY OR FITNESS FOR A PARTICULAR PURPOSE, OTHER THAN THOSE WARRANTIES WHICH ARE IMPLIED BY AND INCAPABLE OF EXCLUSION, RESTRICTION OR MODIFICATION UNDER APPLICABLE LAW.</p>
            <p>IN NO EVENT SHALL WILD ME BE LIABLE TO YOU OR ANY OTHER PERSON FOR LOSS OF BUSINESS OR PROFITS, OR FOR ANY INDIRECT, INCIDENTAL OR CONSEQUENTIAL DAMAGES ARISING OUT OF THE USE OF, OR INABILITY TO USE, THE SERVICES, EVEN IF WILD ME WAS PREVIOUSLY ADVISED OF THE POSSIBILITY OF SUCH DAMAGES, OR FOR ANY OTHER CLAIM BY A SUBSCRIBER, AUTHORIZED USER, OR ANY OTHER PERSON. THIS WARRANTY GIVES YOU SPECIFIC LEGAL RIGHTS, AND YOU MAY ALSO HAVE OTHER RIGHTS WHICH VARY BY LOCATION.</p>
            <p>In the event any claim relating to the performance or nonperformance by Wild Me pursuant to this Agreement, or in any other way concerning the Services, is made by a Subscriber or Authorized User, the actual damages to which such Subscriber or Authorized User may be entitled shall be limited to the lesser of the fees paid by the Subscriber or Authorized User for the Services or One US Dollar (US $1).</p>
          </div>
          <h3><a name="indemnification" id="indemnification">7. Indemnification.</a></h3>
          <div>
            <p>To the maximum extent permitted by law, you agree to defend, indemnify and hold harmless Wild Me, its affiliates and their respective directors, officers, employees and agents from and against any and all claims and expenses, including attorneys' fees, arising out of the use or unauthorized copying of the Services or any of their content, the violation of this Agreement or any applicable laws or regulations, or arising out of your violation of any rights of a user.</p>
          </div>
          <h3><a name="term_and_termination_of_agreement" id="term_and_termination_of_agreement">8. Term and Termination of Agreement.</a></h3>
          <div>
            <p>Either party shall have the right to terminate this Agreement at any time by providing notice of termination to the other party in accordance with the Subscription Form. In the event of termination of this Agreement by either party, you shall have no claims against Wild Me, its affiliates, or any individual contributors to the Wildbook. Termination of this Agreement automatically terminates your license to use the Services, any content or any other materials contained therein.</p>
          </div>
          <h3><a name="miscellaneous" id="miscellaneous">9. Miscellaneous.</a></h3>
          <div>
            <p>This Agreement is entire and complete, and no representations, warranties, agreements or covenants, express or implied, of any kind or character whatsoever have been made by either party hereto to the other, except as expressly set forth in this Agreement. Except as provided herein, this Agreement may not be modified or changed unless the same shall be in writing and signed by an authorized officer of the party to be bound thereby.</p>
            <p>You may not assign any of your rights or delegate any of your obligations under this Agreement without Wild Me's prior written consent. If any provision of this Agreement is held to be overly broad in scope or duration by a court of competent jurisdiction such provision shall be deemed modified to the broadest extent permitted under applicable law. If any provision of this Agreement shall be held to be illegal, invalid or unenforceable by a court of competent jurisdiction, the validity, legality and enforceability of the remaining provisions shall not, in any way, be affected or impaired thereby. No waiver by either party of any breach or default hereunder shall be deemed to be a waiver of any preceding or subsequent breach or default. The section headings used herein are for convenience only and shall not be given any legal import.</p>
            <p>The provisions of Sections 4, 6, 7 and 8 shall survive termination of this Agreement.</p>
            
            <%
          	if((request.getParameter("username")!=null)&&(request.getParameter("password")!=null)){
          	%>
            <p><strong>I HAVE READ AND UNDERSTAND THIS AGREEMENT AND AGREE TO BE BOUND BY ALL OF ITS TERMS.</strong></p>
          	<%
          	}
          	else{
          	%>
          	<p><strong>YOU WILL BE ASKED TO READ, UNDERSTAND AND AGREE TO BE BOUND BY ALL OF THE TERMS OF THIS AGREEMENT BEFORE BEING ISSUED AN ACCOUNT.</strong></p>
          	<%
          	
          	
          	}
          	%>
          </div>
          <h1 class="intro">&nbsp;</h1>
          
          <%
          if((request.getParameter("username")!=null)&&(request.getParameter("password")!=null)){
          %>
          <p><table cellpadding="5"><tr><td>
          	<form name="accept_agreement" action="LoginUser" method="post">
          		<input type="hidden" name="username" value="<%=request.getParameter("username")%>" />
          		<input type="hidden" name="password" value="<%=request.getParameter("password")%>" />
          		<input type="submit" id="acceptUserAgreement" name="acceptUserAgreement" value="Accept"/>
          	</form>
          </td>
          <td><form name="reject_agreement" action="index.jsp" method="get">
          		<input type="submit" name="rejectUserAgreement" value="Reject"/>
          	</form></td>
          </tr></table>
          </p>
          <%
          }
          %>
        </div>

    <jsp:include page="footer.jsp" flush="true"/>
