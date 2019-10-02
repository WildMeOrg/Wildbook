<%@ page contentType="text/html; charset=utf-8" language="java"
     import="org.ecocean.*,
java.util.List,
java.util.Collection,
java.util.ArrayList,
org.ecocean.servlet.ServletUtilities,
javax.jdo.*,
org.ecocean.media.*
              "
%>
<%

String context = ServletUtilities.getContext(request);
Shepherd myShepherd = new Shepherd(context);
myShepherd.setAction("org.jsp");
myShepherd.beginDBTransaction();

String oid = request.getParameter("id");
String uid = request.getParameter("uid");

if ((oid == null) && (uid == null)) {  //show all
    Query q = myShepherd.getPM().newQuery("SELECT FROM org.ecocean.Organization WHERE parent == null");
    q.setOrdering("name");
    Collection c = (Collection) (q.execute());
    List<Organization> orgs = new ArrayList<Organization>(c);
    q.closeAll();
    if (orgs.size() < 1) {
        out.println("<p>no Organizations</p>");
    } else {
        out.println("<ul>");
        for (Organization org : orgs) {
            out.print("<li><a title=\"" + org.getId() + "\" href=\"org.jsp?id=" + org.getId() + "\">" + org.getName() + "</a> (");
            out.println(org.numMembers() + " members, " + org.numChildren() + " sub-orgs)</li>");
        }
        out.println("</ul>");
    }

} else if (oid != null) {
    Organization org = ((Organization) (myShepherd.getPM().getObjectById(myShepherd.getPM().newObjectIdInstance(Organization.class, oid), true)));
    if (org == null) {
        out.println("<p>unknown Organization id=" + oid + "</p>");
    } else {
        if (org.getLogo() != null) out.println("<img style=\"max-height: 200px; margin-right: 30%; float: right;\" src=\"" + org.getLogo().safeURL(request) + "\" />");
        out.println("<h1>" + org.getName() + "</h1>");
        if (org.getUrl() != null) {
            out.println("<p>url: <b><a href=\"" + org.getUrl() + "\">" + org.getUrl() + "</a></b></p>");
        } else {
            out.println("<p>url: <i>none</i></p>");
        }
        out.println("<p>description: <b>" + org.getDescription() + "</b></p>");
        out.println("<p>getIndividualNameKey: <b>" + org.getIndividualNameKey() + "</b></p>");
        if (org.numMembers() < 1) {
            out.println("<p><i>no members</i></p>");
        } else {
            out.println("<p><b>Members</b><ol>");
            for (User member : org.getMembers()) {
                out.println("<li><a href=\"org.jsp?uid=" + member.getUUID() + "\" title=\"" + member.getUUID() + " / " + member.getUsername() + "\">" + member.getFullName() + "</a></li>");
            }
            out.println("</ol></p>");
        }

        if (org.getParent() != null) {
            out.println("<p>Parent org: <a href=\"org.jsp?id=" + org.getParent().getId() + "\" title=\"" + org.getParent().getId() + "\">" + org.getParent().getName() + "</a></p>");
        }

        if (org.numChildren() < 1) {
            out.println("<p><i>no sub-orgs</i></p>");
        } else {
            out.println("<p><b>Sub-orgs</b><ol>");
            for (Organization sub : org.getChildren()) {
                out.println("<li><a href=\"org.jsp?id=" + sub.getId() + "\" title=\"" + sub.getId() + "\">" + sub.getName() + "</a></li>");
            }
            out.println("</ol></p>");
        }
    }

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
}
myShepherd.rollbackAndClose();

%>
