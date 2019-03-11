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

h3 {
    border-bottom: solid 2px #888;
}
  </style>




<div class="container maincontent">

        
        <%
          if((request.getParameter("username")!=null)&&(request.getParameter("password")!=null)){
          %>
          
          <p style="color:#FF0000;"><strong><em>Our records indicate that you have not yet accepted the Usage Agreement. Acceptance is required to use this resource. Please read the agreement below and click the "Accept" button to proceed or "Reject" to decline and return to the home page.</em></strong></p>
          <%
          }
          %>
          
          <h1>Giraffespotter - Wildbook for Giraffe User Agreement</h1>
       <p>Welcome to Giraffespotter! Please read this User Agreement. By using this web site, you accept its terms. This User Agreement applies to any web page using the <b>giraffespotter.org</b> domain, known as <b>Giraffespotter - Wildbook for Giraffe</b> ("Giraffespotter").
</p>

      <p>The Internet is an evolving medium, and we may change the terms of this User Agreement from time to time. By continuing to use any Giraffespotter sites after we post any such changes, you accept the User Agreement, as modified. We may change, restrict access to, suspend or discontinue Giraffespotter, or any portion of Giraffespotter, at any time.
      </p>

      <p>
      If you disagree with any material you find in Giraffespotter, we suggest that you respond by noting your disagreement in an email to info at giraffespotter dot org. We invite you to bring to our attention any material you believe to be factually inaccurate. Please forward a copy of the material to our webmaster along with an explanation of your disagreement.
      </p>

      <p>
      If you are an owner of intellectual property who believes your intellectual property has been improperly posted or distributed via Giraffespotter, please notify us immediately by sending email to our webmaster.
      </p>

      <p>
      A link to another Web site does not constitute an endorsement of that site (nor of any product, service or other material offered on that site) by Giraffespotter or its participants.
      </p>

      <h3>NO SOLICITING</h3>
<p>
      You agree not to use Giraffespotter to advertise or to solicit anyone to buy or sell products or services, or to make donations of any kind, without our express written approval.
</p>

      <h3>USE OF MATERIALS</h3>

<p>
      Any photographs that you submit to Giraffespotter remain YOUR intellectual property, and Giraffespotter and its participants agree not to use them for media purposes without your express permission. However, by submitting photographs and giraffe sighting data you give Giraffespotter and its participants permission to use this data for research and conservation purposes. Data, such as animal identifications, may be derived from your submissions. These data become the intellectual property of Giraffespotter and may not be published or re-used without the express permission of Giraffespotter.
</p>
<p>
      The Internet allows people throughout the world to share valuable information, ideas and creative works. To ensure continued open access to such materials, we all need to protect the rights of those who share their creations with us. Although we make Giraffespotter freely accessible, we don't intend to give up our rights, or anyone else's rights, to the materials appearing in Giraffespotter. The materials available through Giraffespotter are the property of Giraffespotter or, in the case of photographs and images, the property of individual contributors. All photographs and data are protected by copyright, trademark, and other intellectual property laws. You may not reproduce any of the materials without the prior written consent of the owner. You may not distribute copies of materials found on Giraffespotter in any form (including by email or other electronic means), without prior written permission from Giraffespotter.
</p>

<p>
      Requests for permission to use, reproduce, or distribute materials found in Giraffespotter should first be sent to info at giraffespotter dot org. Requests will be evaluated and responded to (yes or no) as quickly as possible. Our main concern is to protect intellectual property and to ensure that credit is given where credit is due. Our mission is to facilitate global cooperation within the research community, and we are working to make as much data as possible available while protecting the rights of individual contributors.
</p>

      <h3>PRIVACY</h3>
<p>
      Your personal information (name, address, email address, phone number) is available for follow up contact by users (researchers and volunteers) logging into Wildbook for research purposes. Your name and email address may be exported from our database for use in analysis by approved users. Your personal information is not otherwise sold or re-distributed beyond our system. When accessing this facility in conjunction with social media sites (e.g., Facebook and Flickr), we may access your name and email address (login) and you may choose select photo albums and photos during data submission. Other than login and photo submission for related research, we do not otherwise access your social media accounts.
</p>

      <h3>LINKING</h3>
<p>
      We welcome links to Giraffespotter. You are usually free to establish a hypertext link to any of Giraffespotter pages so long as the link does not state or imply any sponsorship of your site by Giraffespotter. Pages linking to the Library should include, to the best of your ability, factually correct information about Giraffespotter and cetaceans. In other words, please respect the scientific mission of Giraffespotter and help us ensure that only accurate information about cetaceans is disseminated.
</p>

      <h3>FRAMING</h3>
      <p>No Framing. Without the prior written permission of Giraffespotter, you may not frame any of the content in Giraffespotter, or incorporate into another Web site or other service any intellectual property of Giraffespotter or its data contributors. Requests for permission to frame our content may be sent to: info at giraffespotter dot org.
      </p>

      <h3>DISCLAIMER OF WARRANTIES AND LIABILITY</h3>
      <p>We work hard to make Giraffespotter interesting and informative, but we cannot guarantee that our users will always find everything to their liking. Please read this Disclaimer carefully before using Giraffespotter.
      </p>

      <p>YOU AGREE THAT YOUR USE OF GIRAFFESPOTTER IS AT YOUR SOLE RISK. BECAUSE OF THE NUMBER OF POSSIBLE SOURCES OF INFORMATION AVAILABLE THROUGHOUT, AND THE INHERENT HAZARDS AND UNCERTAINTIES OF ELECTRONIC DISTRIBUTION, THERE MAY BE DELAYS, OMISSIONS, INACCURACIES OR OTHER PROBLEMS WITH SUCH INFORMATION. IF YOU RELY ON ANY WILDBOOK MATERIAL, YOU DO SO AT YOUR OWN RISK. YOU UNDERSTAND THAT YOU ARE SOLELY RESPONSIBLE FOR ANY DAMAGE TO YOUR COMPUTER SYSTEM OR LOSS OF DATA THAT RESULTS FROM ANY MATERIAL AND/OR DATA DOWNLOADED FROM OR OTHERWISE PROVIDED THROUGH GIRAFFESPOTTER. GIRAFFESPOTTER IS PROVIDED TO YOU AS IS, WITH ALL FAULTS, AND AS AVAILABLE. UNDER NO CIRCUMSTANCES SHALL THE PARTICIPANTS, PROGRAMMERS, AND CONSULTANTS IN GIRAFFESPOTTER BE LIABLE TO YOU OR ANYONE ELSE FOR ANY DAMAGES ARISING OUT OF USE OF GIRAFFESPOTTER, INCLUDING, WITHOUT LIMITATION, LIABILITY FOR CONSEQUENTIAL, SPECIAL, INCIDENTAL, INDIRECT OR SIMILAR DAMAGES, EVEN IF WE ARE ADVISED BEFOREHAND OF THE POSSIBILITY OF SUCH DAMAGES. (BECAUSE SOME STATES DO NOT ALLOW THE EXCLUSION OR LIMITATION OF CERTAIN CATEGORIES OF DAMAGES, THE ABOVE LIMITATION MAY NOT APPLY TO YOU. IN SUCH STATES, THE LIABILITY OF WILD ME AND ITS STAFF AND AFFILIATES IS LIMITED TO THE FULLEST EXTENT PERMITTED BY SUCH STATE LAW.)
      </p>

      <h3>USER ACCOUNTS</h3>

      <p>Giraffespotter staff does its best to ensure that information we post to Giraffespotter is timely, accurate, and scientifically valuable. To obtain access to certain services of Giraffespotter, you may be given an opportunity to register with Giraffespotter. As part of any such registration process, you will be provided an user name and a password. You agree that the information you supply during that registration process will be accurate and complete and that you will not register under the name of, or attempt to enter Giraffespotter under the name of, another person. You will be responsible for preserving the confidentiality of your password, sharing it with no one else without express permission, and will notify the staff of Giraffespotter of any known or suspected unauthorized use of your account. You agree to indemnify, defend and hold harmless Giraffespotter, its affiliates and participants, and their officers, directors, employees, agents, licensors and suppliers, from and against any and all losses, expenses, damages and costs (including reasonable attorneys' fees) resulting from any violation of this User Agreement or any activity related to your account (including negligent or wrongful conduct) by you or any other person accessing Giraffespotter using your account.
      </p>

      <h3>MISCELLANEOUS </h3>

      <p>In the event that any portion of this User Agreement is found to be invalid or unenforceable for any reason, such invalidity or unenforceability shall not affect the enforceability or validity of any other portion of this User Agreement, which shall remain in full force and effect and be construed as if the invalid or unenforceable portion were not part of the User Agreement.
      </p>

      <p>
      By using Giraffespotter, you agree to abide by the terms of this User Agreement.
      </p>

    <p>
      We hope you enjoy using Giraffespotter, and we welcome suggestions for improvements.
      </p>

      <p>
      Thanks for your support!
      </p>

      <p>
<i>Last updated: February 15, 2019</i>
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
          </div>
          
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
