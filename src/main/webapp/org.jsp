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


.org-desc {
    padding: 8px;
    font-size: 0.8em;
    color: #888;
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

.org-link:hover {
    text-decoration: none;
}

.org-link {
    padding: 2px;
    margin: 0 0 0 15px;
    background-color: #EEE;
    border-radius: 3px;
    color: #999;
}


.org-logo {
    float: right;
}

.org-memct {
    background-color: #0AD;
}
.org-kidct {
    background-color: #0DA;
}

.org-user {
    padding: 3px;
    width: 30%;
}
.org-user:hover {
    background-color: #EEE;
}

.org-edit {
    cursor: pointer;
    display: inline-block;
    border-radius: 2px;
    padding: 0 3px;
    background-color: #DDD;
    color: #666;
    float: right;
    margin-left: 8px;
}
.org-edit:hover {
    background-color: #AAA;
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
        if (orgs[0].canManage) enableEdit();
    } else {
        $('.maincontent').append(listOrgsEl(orgs));
        $('.org-li').on('click', function(ev) { window.location.href = 'org.jsp?id=' + ev.currentTarget.id; });
    }
}

function enableEdit() {
    $('.org-user').each(function(i,el) {
        $(el).append('<div title="REMOVE from organization" class="org-edit org-remove">x</div>');
    });
    $('.org-members').append('<div id="user-add-form"><input type="text" style="width: 20em;" id="org-add-user" placeholder="Search user to add" /><input type="button" value="Add user" onClick="return addUser();" /></div>');
    autoUser();
}

function singleFullEl(org) {
    var h = '<div class="org-full" id="' + org.id + '">';
    if (org.url) {
        h += '<span class="org-name">' + org.name + '<a class="org-link" target="_new" href="' + org.url + '">&#8599;</a></span>';
    } else {
        h += '<span class="org-name">' + org.name + '</span>';
    }
    if (org.logo && org.logo.url) h += '<img class="org-logo" src="' + org.logo.url + '" />';
    if (org.description) h += '<div class="org-desc">' + org.description + '</div>';

    if (org.children && org.children.length) {
        h += '<div class="org-children">';
        for (var i = 0 ; i < org.children.length ; i++) {
            h += singleListEl(org.children[i]);
        }
        h += '</div>';
    }
    if (org.members && org.members.length) {
        h += '<div class="org-members">';
        for (var i = 0 ; i < org.members.length ; i++) {
            h += singleUserEl(org.members[i]);
        }
        h += '</div>';
    }
    h += '</div>';
    return h;
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


function singleUserEl(user) {
    var h = '<div id="' + user.id + '" class="org-user">';
    h += '<a target="_new" title="username: ' + (user.username || '[none]') + '" href="user.jsp?id=' + user.id + '">' + user.fullName + '</a>';
    h += '</div>';
    return h;
}


function autoUser() {
    $('#org-add-user').autocomplete({
        source: function( request, response ) {
            $.ajax({
                url: wildbookGlobals.baseUrl + '/OrganizationEdit?searchUser=' + request.term,
                type: 'GET',
                dataType: "json",
                success: function( data ) {
                        var valid = new Array();
                        for (var i = 0 ; i < data.length ; i++) {
                            if ($('.org-user#' + data[i].id).length) continue;  //this user is in group already
                            valid.push(data[i]);
                        }
                        var res = $.map(valid, function(item) {
                        var label = item.id.substr(0,8);
                        if (item.fullName) label = item.fullName + ' (' + label + ')';
                        return { label: label, value: item.id };
                    });
                    response(res);
                }
            });
        }
    });

}



function addUser() {
    var orgId = $('.org-full').attr('id');
    var userId = $('#org-add-user').val();
console.log("orgId=%s userId=%s", orgId, userId);
    if (!orgId || !userId) return false;
    $('#user-add-form').hide();
    addUserAjax(orgId, userId, function(rtn) {
console.info('rtn = %o', rtn);
        if (rtn.success) window.location.reload();
        $('#user-add-form').html('<div class="error">' + rtn.error + '</div>').show();
    });
    return true;
}

//callback gets json, which will have .success and .message or .error (and likely more?)
function addUserAjax(orgId, userId, callback) {
    $.ajax({
        url: wildbookGlobals.baseUrl + '/OrganizationEdit',
        data: JSON.stringify({ id: orgId, addUsers: [ userId ] }),
        contentType: 'application/javascript',
        dataType: 'json',
        success: function(d) {
            callback(d);
        },
        error: function(x) {
            var rtn = { success: false, _error: x, error: 'ERROR ' + x.status + ': ' + x.statusText };
            callback(rtn);
        },
        type: 'POST'
    });
}

</script>

<div class="container maincontent">
</div>

<jsp:include page="footer.jsp" flush="true"/>

