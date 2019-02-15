<%@ page contentType="text/html; charset=utf-8" language="java"
         import="org.apache.shiro.crypto.*,org.apache.shiro.util.*,org.apache.shiro.crypto.hash.*,org.ecocean.*,org.ecocean.servlet.ServletUtilities, java.util.Properties,java.util.ArrayList" %>


<%

String context="context0";
context=ServletUtilities.getContext(request);

  
String langCode=ServletUtilities.getLanguageCode(request);
  

Properties props = new Properties();
props = ShepherdProperties.getProperties("overview.properties", langCode,context);



%>

<jsp:include page="header.jsp" flush="true"/>

  <style type="text/css">
    <!--


    .style2 {
      font-size: x-small;
      color: #000000;
    }

.watermark-overlay {
    position: fixed;
    transform: rotate(-10deg);
    font-size: 11.0em;
    text-align: center;
    z-index: -100;
    width: 100%;
    font-weight: bold;
    opacity: 0.3;
    color: rgba(255,100,0,1);
}
    -->
  </style>




<div class="container maincontent">

<h1 class="watermark-overlay">DRAFT COPY</h1>

<h1>User Access Policy for GiraffeSpotter - Wildbook for Giraffe</h1>

<p style="font-size: 1.2em; font-weight: bold;" >
This policy defines who may be given access to GiraffeSpotter and how access may be requested.
</p>

<h2>Background</h2>

<p>
<b>GiraffeSpotter - Wildbook for Giraffe</b> is privately funded and managed jointly by
<a href="https://wildme.org/">Wild Me</a> (a 501c3 non-profit organization in the United States),
<a href="https://giraffeconservation.org/">Giraffe Conservation Foundation</a>,
and
<a href="https://www.sandiegozooglobal.org/">San Diego Zoo Global</a>.
</p>

<p>
This Wildbook provides standardized research software and analytical techniques for the study of giraffe.
Access to this resource is provided free of charge to individuals or organizations selected according to the criteria of this policy.
The number of new accounts that can be provided each year is limited by:
<ul>
<li>available resources to support existing users</li>
<li>available resources for existing giraffe research efforts</li>
</ul>
</p>


<h2>Requesting access</h2>

<p>
To <b>request access to GiraffeSpotter</b>, please fill out the following form.
Someone at GiraffeSpotter will review your request and respond to the email you provide below. 
Your research objectives:
The organization you represent (if appropriate):
The Wildbook tools and data that could benefit your research:
How much data you have already collected for your research:
Your qualifications:
What you plan to do with the data: 
</p>


<h2>Request review</h2>

<p>
Requests for access to GiraffeSpotter are reviewed by the site managers within 60 days.
The managers may also request outside assistance in reviewing your application.
</p>

<p>
The managers weigh these and other criteria when reviewing a request for access:
<ul>
    <li>Your ability and willingness to contribute new giraffe data</li>
    <li>Your ability and willingness to follow the analytical techniques used on Giraffespotter</li>
    <li>Your ability and willingness to develop new techniques for giraffe research</li>
    <li>Your ability and willingness to develop new science for other fields using giraffe data</li>
    <li>Your ability and willingness to use giraffe data collaboratively</li>
    <li>Available resources to properly support you</li>

</ul>

The final decision for a request is made the site managers. Responses are provided via email.
Giraffespotter wishes to acknowledge in advance that not all valid requests for access can be approved
due to limitations on available resources.
</p>


<h2>By invitation</h2>

<p>
Giraffespotter may choose to invite appropriate individuals to participate without a formal request for access.
Invitations for access are reviewed in accordance with the criteria defined above.
</p>


</div>



    <jsp:include page="footer.jsp" flush="true"/>
