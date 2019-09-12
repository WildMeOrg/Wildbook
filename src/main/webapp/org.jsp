<%@ page contentType="text/html; charset=utf-8" language="java"
         import="org.ecocean.servlet.ServletUtilities,
org.ecocean.*,
javax.jdo.Query,
java.util.List,
java.util.ArrayList,
java.util.Collection,
org.json.JSONObject, org.json.JSONArray,
org.ecocean.media.MediaAsset
" %>
<%


////TODO i18n of all text here!!!!!!!!!!!!!!!

String context = ServletUtilities.getContext(request);
Shepherd myShepherd = new Shepherd(context);

String oid = request.getParameter("id");
String uid = request.getParameter("uid");
User thisUser = AccessControl.getUser(request, myShepherd);

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


.org-li {
    width: 38%;
    padding: 2px 6px;
    margin: 3px 0;
    background-color: #EEE;
    cursor: pointer;
}
.org-li:hover {
    background-color: #CCC;
}

.org-name {
    font-weight: bold;
}

.org-ct {
    float: right;
    font-size: 0.8em;
    color: white;
    padding: 0 4px;
    border-radius: 3px;
    margin: 3px;
}

.org-memct {
    background-color: #0AD;
}
.org-kidct {
    background-color: #0DA;
}

.dim {
    color: #BBB;
}
</style>

<%

JSONArray orgsArr = new JSONArray();
boolean singleMode = false;

if ((oid == null) && (uid == null)) {  //show all
    Query q = myShepherd.getPM().newQuery("SELECT FROM org.ecocean.Organization WHERE parent == null");
    q.setOrdering("name");
    Collection c = (Collection) (q.execute());
    List<Organization> orgs = new ArrayList<Organization>(c);
    q.closeAll();
    if (orgs.size() > 0) {
        for (Organization org : orgs) {
            JSONObject jo = org.toJSONObject(true);
            jo.put("canManage", org.canManage(thisUser, myShepherd));
            orgsArr.put(jo);
        }
    }

} else if (oid != null) {
    singleMode = true;
    Organization org = ((Organization) (myShepherd.getPM().getObjectById(myShepherd.getPM().newObjectIdInstance(Organization.class, oid), true)));
    if (org != null) {
        JSONObject jo = org.toJSONObject(true);
        jo.put("canManage", org.canManage(thisUser, myShepherd));
        orgsArr.put(jo);
    }

/*
} else if (uid != null) {
    User user = myShepherd.getUserByUUID(uid);
    if (user == null) {
        out.println("<p>unknown User id=" + uid + "</p>");
    } else {
        out.println("<h1>" + user.getFullName() + " / username=<i>" + user.getUsername() + "</i></h1>");
        out.println("<b><a href=\"user.jsp?id=" + user.getUUID() + "\">profile</a></b>");
        List<Organization> orgs = user.getOrganizations();
        if ((orgs == null) || (orgs.size() < 1)) {
            out.println("<p><i>no orgs</i></p>");
        } else {
            out.println("<p><b>orgs</b><ol>");
            for (Organization org : orgs) {
                out.println("<li><a href=\"org.jsp?id=" + org.getId() + "\" title=\"" + org.getId() + "\">" + org.getName() + "</a></li>");
            }
            out.println("</ol></p>");
        }
    }

*/
}


%>

<script type="text/javascript">
var orgs = <%=orgsArr.toString(4)%>;
var singleMode = <%=singleMode%>;
$(document).ready(function() {
    displayOrgs();
});

function displayOrgs() {
    if (!orgs || !orgs.length) {
        $('.maincontent').append('<h2>No match</h2>');
        return;
    }

    if (singleMode) {
        $('.maincontent').append(singleFullEl(orgs[0]));
    } else {
        $('.maincontent').append(listOrgsEl(orgs));
        $('.org-li').on('click', function(ev) { window.location.href = 'org.jsp?id=' + ev.currentTarget.id; });
    }
}
    
function singleFullEl(org) {
    return '<xmp>' + JSON.stringify(org, 4) + '</xmp>';
}

function listOrgsEl(orgs) {
    var l = '<div class="org-list">';
    for (var i = 0 ; i < orgs.length ; i++) {
        l += singleListEl(orgs[i]);
    }
    l += '</div>';
    return l;
}

function singleListEl(org) {
    var h = '<div id="' + org.id + '" class="org-li' + (org.canManage ? ' org-manage' : '') + '" title="created: ' + new Date(org.created).toLocaleString() + ' | modified: ' + new Date(org.modified).toLocaleString() + ' | id: ' + org.id+ '">';
    h += '<span class="org-name">' + org.name + '</span>';
    if (org.children && org.children.length) h += '<span class="org-ct org-kidct">' + org.children.length + ' sub</span>';
    if (org.members && org.members.length) h += '<span class="org-ct org-memct">' + org.members.length + ' mem</span>';
    h += '</div>';
    return h;
}

</script>

<div class="container maincontent">
</div>

<jsp:include page="footer.jsp" flush="true"/>

