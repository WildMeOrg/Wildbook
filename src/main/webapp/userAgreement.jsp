<%@ page contentType="text/html; charset=utf-8" language="java"
         import="org.apache.shiro.crypto.*,org.apache.shiro.util.*,org.apache.shiro.crypto.hash.*,org.ecocean.*,org.ecocean.servlet.ServletUtilities, java.util.Properties,java.util.ArrayList" %>


<%

String context="context0";
context=ServletUtilities.getContext(request);

  

  //Shepherd myShepherd = new Shepherd(context);
  //myShepherd.setAction("userAgreement.jsp");
  
  	

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
          
          <h1>Amphibian and Reptile Wildbook User Agreement</h1>
          <p>Welcome to Amphibian and Reptile Wildbook! Please read this Visitor Agreement. By using this web site, you accept its terms. This Visitor Agreement applies to any web page using the following domains, which are collectively known as the "Amphibian and Reptile Wildbook" or in shortened form "Wildbook":
            <ul><li>amphibian-reptile.wildbook.org</li></ul> 
          </p>
      <p>The Internet is an evolving medium, and we may change the terms of this Visitor Agreement from time to time. By continuing to use any Amphibian and Reptile Wildbook sites after we post any such changes, you accept the Visitor Agreement, as modified. We may change, restrict access to, suspend or discontinue Amphibian and Reptile Wildbook, or any portion of Amphibian and Reptile Wildbook, at any time.
      </p>
      <p>
      If you disagree with any material you find in Amphibian and Reptile Wildbook, we suggest that you respond by noting your disagreement in an email to webmaster at amphibian-reptile dot wildbook dot org. We invite you to bring to our attention any material you believe to be factually inaccurate. Please forward a copy of the material to our webmaster along with an explanation of your disagreement.
      </p>
      <p>
      If you are an owner of intellectual property who believes your intellectual property has been improperly posted or distributed via Amphibian and Reptile Wildbook, please notify us immediately by sending email to our webmaster.
      </p>
      <p>
      A link to another Web site does not constitute an endorsement of that site (nor of any product, service or other material offered on that site) by Amphibian and Reptile Wildbook or its participants.
      </p>
      <h3>NO SOLICITING</h3>
      You agree not to use Amphibian and Reptile Wildbook to advertise or to solicit anyone to buy or sell products or services, or to make donations of any kind, without our express written approval.
      <h3>USE OF MATERIALS</h3>
      Any photographs that you submit to Amphibian and Reptile Wildbook remain YOUR intellectual property, and Amphibian and Reptile Wildbook and its participants agree not to use them for media purposes without your express permission. However, by submitting photographs and amphibian or reptile sighting data you give Wild Me and its participants permission to use this data for research and conservation purposes. Data, such as animal identifications, may be derived from your submissions. These data become the intellectual property of Amphibian and Reptile Wildbook and may not be published or re-used without the express permission of Amphibian and Reptile Wildbook.
      The Internet allows people throughout the world to share valuable information, ideas and creative works. To ensure continued open access to such materials, we all need to protect the rights of those who share their creations with us. Although we make Amphibian and Reptile Wildbook freely accessible, we don't intend to give up our rights, or anyone else's rights, to the materials appearing in Amphibian and Reptile Wildbook. The materials available through Amphibian and Reptile Wildbook are the property of Wild Me or, in the case of photographs and images, the property of individual contributors. All photographs and data are protected by copyright, trademark, and other intellectual property laws. You may not reproduce any of the materials without the prior written consent of the owner. You may not distribute copies of materials found on Amphibian and Reptile Wildbook in any form (including by email or other electronic means), without prior written permission from Wild Me.
      Requests for permission to use, reproduce, or distribute materials found in Amphibian and Reptile Wildbook should first be sent to webmaster at amphibian-reptile dot wildbook dot org. Requests will be evaluated and responded to (yes or no) as quickly as possible. Our main concern is to protect intellectual property and to ensure that credit is given where credit is due. Our mission is to facilitate global cooperation within the research community, and we are working to make as much data as possible available while protecting the rights of individual contributors.
      <h3>Privacy</h3>
      Your personal information (name, address, email address, phone number) is available for follow up contact by users (researchers and volunteers) logging into Wildbook for research purposes. Your name and email address may be exported from our database for use in analysis by approved users. Your personal information is not otherwise sold or re-distributed beyond our system. When accessing this facility in conjunction with social media sites (e.g., Facebook and Flickr), we may access your name and email address (login) and you may choose select photo albums and photos during data submission. Other than login and photo submission for related research, we do not otherwise access your social media accounts.
      <h3>LINKING</h3>
      We welcome links to Amphibian and Reptile Wildbook. You are usually free to establish a hypertext link to any of Amphibian and Reptile Wildbook pages so long as the link does not state or imply any sponsorship of your site by Amphibian and Reptile Wildbook. Pages linking to the Library should include, to the best of your ability, factually correct information about Amphibian and Reptile Wildbook and amphibians or reptiles. In other words, please respect the scientific mission of Amphibian and Reptile Wildbook and help us ensure that only accurate information about amphibians or reptiles is disseminated.
      <h3>FRAMING</h3>
      <p>No Framing. Without the prior written permission of Amphibian and Reptile Wildbook, you may not frame any of the content in Amphibian and Reptile Wildbook, or incorporate into another Web site or other service any intellectual property of Amphibian and Reptile Wildbook or its data contributors. Requests for permission to frame our content may be sent to: webmaster at amphibian-reptile dot wildbook dot org.
      </p>
      <h3>DISCLAIMER OF WARRANTIES AND LIABILITY</h3>
      <p>We work hard to make Amphibian and Reptile Wildbook interesting and informative, but we cannot guarantee that our users will always find everything to their liking. Please read this Disclaimer carefully before using Amphibian and Reptile Wildbook.
      </p>
      <p>YOU AGREE THAT YOUR USE OF Amphibian and Reptile Wildbook IS AT YOUR SOLE RISK. BECAUSE OF THE NUMBER OF POSSIBLE SOURCES OF INFORMATION AVAILABLE THROUGHOUT, AND THE INHERENT HAZARDS AND UNCERTAINTIES OF ELECTRONIC DISTRIBUTION, THERE MAY BE DELAYS, OMISSIONS, INACCURACIES OR OTHER PROBLEMS WITH SUCH INFORMATION. IF YOU RELY ON ANY Wildbook MATERIAL, YOU DO SO AT YOUR OWN RISK. YOU UNDERSTAND THAT YOU ARE SOLELY RESPONSIBLE FOR ANY DAMAGE TO YOUR COMPUTER SYSTEM OR LOSS OF DATA THAT RESULTS FROM ANY MATERIAL AND/OR DATA DOWNLOADED FROM OR OTHERWISE PROVIDED THROUGH Amphibian and Reptile Wildbook. Amphibian and Reptile Wildbook IS PROVIDED TO YOU AS IS, WITH ALL FAULTS, AND AS AVAILABLE. UNDER NO CIRCUMSTANCES SHALL THE PARTICIPANTS, PROGRAMMERS, AND CONSULTANTS IN Amphibian and Reptile Wildbook BE LIABLE TO YOU OR ANYONE ELSE FOR ANY DAMAGES ARISING OUT OF USE OF Amphibian and Reptile Wildbook, INCLUDING, WITHOUT LIMITATION, LIABILITY FOR CONSEQUENTIAL, SPECIAL, INCIDENTAL, INDIRECT OR SIMILAR DAMAGES, EVEN IF WE ARE ADVISED BEFOREHAND OF THE POSSIBILITY OF SUCH DAMAGES. (BECAUSE SOME STATES DO NOT ALLOW THE EXCLUSION OR LIMITATION OF CERTAIN CATEGORIES OF DAMAGES, THE ABOVE LIMITATION MAY NOT APPLY TO YOU. IN SUCH STATES, THE LIABILITY OF WILD ME AND ITS STAFF AND AFFILIATES IS LIMITED TO THE FULLEST EXTENT PERMITTED BY SUCH STATE LAW.)
      </p>
      <h3>USER ACCOUNTS</h3>
      <p>Amphibian and Reptile Wildbook staff does its best to ensure that information we post to Amphibian and Reptile Wildbook is timely, accurate, and scientifically valuable. To obtain access to certain services of Amphibian and Reptile Wildbook, you may be given an opportunity to register with Amphibian and Reptile Wildbook. As part of any such registration process, you will be provided an user name and a password. You agree that the information you supply during that registration process will be accurate and complete and that you will not register under the name of, or attempt to enter Amphibian and Reptile Wildbook under the name of, another person. You will be responsible for preserving the confidentiality of your password, sharing it with no one else without express permission, and will notify the staff of Amphibian and Reptile Wildbook of any known or suspected unauthorized use of your account. You agree to indemnify, defend and hold harmless Wild Me, its affiliates and participants, and their officers, directors, employees, agents, licensors and suppliers, from and against any and all losses, expenses, damages and costs (including reasonable attorneys' fees) resulting from any violation of this Visitor Agreement or any activity related to your account (including negligent or wrongful conduct) by you or any other person accessing Amphibian and Reptile Wildbook using your account.
      </p>
      <h3>MISCELLANEOUS </h3>
      <p>In the event that any portion of this Visitor Agreement is found to be invalid or unenforceable for any reason, such invalidity or unenforceability shall not affect the enforceability or validity of any other portion of this Visitor Agreement, which shall remain in full force and effect and be construed as if the invalid or unenforceable portion were not part of the Visitor Agreement.
      </p>
      <p>
      By using Amphibian and Reptile Wildbook, you agree to abide by the terms of this Visitor Agreement.
      </p>
      <p>We hope you enjoy using Amphibian and Reptile Wildbook, and we welcome suggestions for improvements.
      </p>
      <p>
      Thanks for your support!
      </p>
      <p>Last updated: June 16, 2015
      </p>
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
        </div>

    <jsp:include page="footer.jsp" flush="true"/>
