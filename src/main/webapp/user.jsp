<%@ page contentType="text/html; charset=utf-8" language="java"
         import="org.ecocean.servlet.ServletUtilities,
org.ecocean.*,
javax.jdo.Query,
java.util.List,
java.util.ArrayList,
java.util.Collection,
org.ecocean.media.MediaAsset
" %>
<%

String context = ServletUtilities.getContext(request);
Shepherd myShepherd = new Shepherd(context);
myShepherd.setAction("user.jsp");
boolean admin = request.isUserInRole("admin");
String uid = request.getParameter("id");
User requestUser = AccessControl.getUser(request, myShepherd);
User user = null;
if (uid != null) user = myShepherd.getUserByUUID(uid);
if (user == null) user = requestUser;
if (user == null) {
    response.sendError(404, "Not found");
    return;
}

boolean thisIsMe = user.equals(requestUser);

////TODO i18n of all text here!!!!!!!!!!!!!!!

//////////// TODO bail (when? after showing the name?) as security requires!
%>
<jsp:include page="header.jsp" flush="true"/>
<style>
.user-profile-image {
    max-width: 200px;
    max-height: 200px;
}
.user-profile-image-blank {
    background-color: #9DF;
    width: 200px;
    height: 200px;
}

.user-org {
    width: 150px;
    height: 220px;
    overflow: hidden;
    display: inline-block;
    margin: 8px;
    cursor: pointer;
}
.org-logo {
    max-width: 150px;
    max-height: 150px;
}
.org-logo-blank {
    background-color: #AEF;
    width: 150px;
    height: 150px;
}

.org-title {
    text-align: center;
    font-size: 1.1em;
    line-height: 1em;
    font-weight: bold;
}

.dim {
    color: #BBB;
}
</style>

<div class="container maincontent">
<h1><%=user.getDisplayName()%></h1>

<%
//ugh! we gotta move user profile image from SinglePhotoVideo!
if (user.getUserImage() == null) { %>
    <div class="user-profile-image user-profile-image-blank"></div>
<%
} else {
    String profileUrl = "/" + CommonConfiguration.getDataDirectoryName(context) + "/users/" + user.getUsername() + "/" + user.getUserImage().getFilename();
%>
    <div class="user-profile-image">
        <img src="<%=profileUrl%>" />
    </div>
<%
}

if ((user.getOrganizations() == null) || (user.getOrganizations().size() < 1)) {
    out.println("<h2 class=\"dim\">No organizational affiliations</h2>");

} else {
    out.println("<h2>Organizational affiliations</h2>");
    for (Organization org : user.getOrganizations()) { %>

<div title="<%=org.getName()%><%=((org.getDescription() == null) ? "" : " | " + org.getDescription())%>" onClick="window.location.href='org.jsp?id=<%=org.getId()%>';" class="user-org">
<%
MediaAsset logo = org.getLogo();
if (logo == null) {
%>
<div class="org-logo org-logo-blank">
    <img />
<% } else { %>
<div class="org-logo">
    <img src="<%=logo.safeURL()%>" />
<% } %>
</div>

<div class="org-title"><%=org.getName()%></div>

</div>

<%
    }
}

/////// TODO when to show activity?  to who?  configure via commonConfig ???

admin = false;  thisIsMe = false;
if (thisIsMe || admin) {
    out.println("<h2>Activity</h2>");

    String jdoql = "SELECT FROM org.ecocean.Encounter WHERE (submitters.contains(u) || informOthers.contains(u) || photographers.contains(u)) && u.uuid == '" + user.getUUID() + "' VARIABLES org.ecocean.User u";
    //out.println(jdoql);
    Query query = myShepherd.getPM().newQuery(jdoql);
    query.setOrdering("dateInMilliseconds desc");
    Collection c = (Collection) (query.execute());
    List<Encounter> encs = new ArrayList<Encounter>(c);
    query.closeAll();
    if (encs.size() < 1) {
        out.println("<h3 class=\"dim\">No Encounters</h3>");
    } else {
        out.println("<h3>Encounters</h3><ul>");
    }
    for (Encounter enc : encs) {
%>
        <li><%=enc.getCatalogNumber()%></li>

<%
    }
    out.println("</ul>");


    jdoql = "SELECT FROM org.ecocean.Occurrence WHERE (submitters.contains(u) || informOthers.contains(u)) && u.uuid == '" + user.getUUID() + "' VARIABLES org.ecocean.User u";
    //out.println(jdoql);
    query = myShepherd.getPM().newQuery(jdoql);
    query.setOrdering("millis desc");
    c = (Collection) (query.execute());
    List<Occurrence> occs = new ArrayList<Occurrence>(c);
    query.closeAll();
    if (occs.size() < 1) {
        out.println("<h3 class=\"dim\">No Occurrences</h3>");
    } else {
        out.println("<h3>Occurrences</h3><ul>");
    }
    for (Occurrence occ : occs) {
%>
        <li><%=occ%></li>

<%
    }
    out.println("</ul>");


}
%>

</div>
<jsp:include page="footer.jsp" flush="true"/>

