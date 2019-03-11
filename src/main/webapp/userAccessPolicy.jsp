<%@ page contentType="text/html; charset=utf-8" language="java"
         import="org.apache.shiro.crypto.*,org.apache.shiro.util.*,org.apache.shiro.crypto.hash.*,org.ecocean.*,org.ecocean.servlet.ServletUtilities, java.util.Properties,java.util.ArrayList, java.util.Map, java.util.HashMap, org.json.JSONObject, java.util.concurrent.ThreadPoolExecutor" %>


<%!
private String formDisplay(String val) {
    if (val == null) return "";
    return val;
}
%>

<%

String context=ServletUtilities.getContext(request);
String langCode=ServletUtilities.getLanguageCode(request);
Properties props = new Properties();
props = ShepherdProperties.getProperties("overview.properties", langCode,context);
%>

<jsp:include page="header.jsp" flush="true"/>

<%

String requestEmail = request.getParameter("request_email");
String requestName = request.getParameter("request_name");
String requestOrganization = request.getParameter("request_organization");
String requestObjectives = request.getParameter("request_objectives");
String requestToolsdata = request.getParameter("request_toolsdata");
String requestDatacollected = request.getParameter("request_datacollected");
String requestQualifications = request.getParameter("request_qualifications");
String requestDataplan = request.getParameter("request_dataplan");
String errorMessage = null;

if (Util.stringExists(requestEmail)) {
    boolean success = false;
    if (!Util.isValidEmailAddress(requestEmail)) {
        errorMessage = "Invalid email address";
    } else if (!Util.stringExists(requestName)) {
        errorMessage = "You must provide a name and email address";
    } else {
        success = true;
    }


    if (success) {
        try {
            ThreadPoolExecutor es = MailThreadExecutorService.getExecutorService();
            Map<String, String> tagMap = new HashMap<String, String>();
            tagMap.put("@REQUEST_EMAIL@", requestEmail);
            tagMap.put("@REQUEST_NAME@", requestName);
            tagMap.put("@REQUEST_ORGANIZATION@", requestOrganization);
            tagMap.put("@REQUEST_OBJECTIVES@", requestObjectives);
            tagMap.put("@REQUEST_TOOLSDATA@", requestToolsdata);
            tagMap.put("@REQUEST_DATACOLLECTED@", requestDatacollected);
            tagMap.put("@REQUEST_QUALIFICATIONS@", requestQualifications);
            tagMap.put("@REQUEST_DATAPLAN@", requestDataplan);
            tagMap.put("@REQUEST_IP@", ServletUtilities.getRemoteHost(request));
            tagMap.put("@REQUEST_TIMESTAMP@", Long.toString(System.currentTimeMillis()));
            String recPath = "/var/spool/WildbookQueryCache/access_request_" + System.currentTimeMillis() + ".json";  //hacktacular hard-coded location
            Util.writeToFile(new JSONObject(tagMap).toString(), recPath);
            String[] mailTo = new String[]{"test@example.com"};
            for (int i = 0 ; i < mailTo.length ; i++) {
                NotificationMailer mailer = new NotificationMailer(context, ServletUtilities.getLanguageCode(request), mailTo[i], "requestAccess", tagMap);
                mailer.setUrlScheme(request.getScheme());
                es.execute(mailer);
            }
        } catch (Exception ex) {
            out.println("<div class=\"error form-error\">Could not send email!  " + ex.toString() + "</div>");
            return;
        }
          
%>

<div style="margin: 120px 0 0 50px">
Your <b>request for access</b> has been sent!
</div>

<%
        return;
    }
}

%>

<script>
var formShown = false;
function toggleForm() {
    $('#request-access-div').slideToggle('slow');
    formShown = !formShown;
    if (formShown) {
        $('#request-access-button').val('Hide form');
    } else {
        $('#request-access-button').val('Show request form');
    }
}
<% if (errorMessage != null) { %>
$(document).ready(function() {
    toggleForm();
    document.getElementById('request-access-button').scrollIntoView();
});
<% } %>

</script>

  <style type="text/css">
    <!--


    .style2 {
      font-size: x-small;
      color: #000000;
    }

.request-form-error {
    padding: 10px 30px;
    background-color: #FDD;
    border-radius: 4px;
    margin-bottom: 13px;
}

#request-access-div {
    padding: 15px 30px;
    background-color: #CCC;
    display: none;
    border-radius: 10px;
}


#request-access textarea,
#request-access input 
{
    width: 40%;
}
#request-access textarea {
    font-size: 0.85em;
    height: 6em;
}

    -->
  </style>




<div class="container maincontent">

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
This <a target="_new" href="http://wildbook.org">Wildbook</a> provides standardized research software and analytical techniques for the study of giraffe.
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

<p>
<input type="button" value="Show request form" id="request-access-button" onClick="return toggleForm();" />
</p>

<div id="request-access-div">

<% if (errorMessage != null) out.println("<div class=\"request-form-error error\">" + errorMessage + "</div>"); %>

<form id="request-access" method="POST" action="userAccessPolicy.jsp">

<p>
<b>Your name: *</b><br />
<input type="text" name="request_name" value="<%=formDisplay(requestName)%>" />
</p>

<p>
<b>Your email address: *</b><br />
<input type="email" name="request_email" value="<%=formDisplay(requestEmail)%>" />
</p>

<p>
<b>The organization you represent (if appropriate):</b><br />
<input type="text" name="request_organization" value="<%=formDisplay(requestOrganization)%>" />
</p>

<p>
<b>Your research objectives:</b><br />
<textarea name="request_objectives"><%=formDisplay(requestObjectives)%></textarea>
</p>

<p>
<b>The Wildbook tools and data that could benefit your research:</b><br />
<textarea name="request_toolsdata"><%=formDisplay(requestToolsdata)%></textarea>
</p>

<p>
<b>How much data you have already collected for your research:</b><br />
<textarea name="request_datacollected"><%=formDisplay(requestDatacollected)%></textarea>
</p>

<p>
<b>Your qualifications:</b><br />
<textarea name="request_qualifications"><%=formDisplay(requestQualifications)%></textarea>
</p>

<p>
<b>What you plan to do with the data:</b><br />
<textarea name="request_dataplan"><%=formDisplay(requestDataplan)%></textarea>
</p>

<input type="submit" value="Send request" />

</form>
</div>


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
