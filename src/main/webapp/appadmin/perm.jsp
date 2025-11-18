<%@ page contentType="text/html; charset=utf-8" language="java"
     import="org.ecocean.*,
org.ecocean.shepherd.core.Shepherd,
java.util.List,
java.util.Map,
org.json.JSONObject,
org.json.JSONArray
              "
%>

            <%
Shepherd myShepherd = new Shepherd("context0");

myShepherd.beginDBTransaction();
User loggedInUser = myShepherd.getUser(request);
User user = loggedInUser;

out.println("<p>logged in user: <b>" + loggedInUser + "</b></p>");

String userId = request.getParameter("userId");
if ((userId != null) && (loggedInUser != null)) {
    user = myShepherd.getUser(userId);
    if (user == null) user = myShepherd.getUserByUUID(userId);
}



String id = request.getParameter("id");
if (id == null) {
    out.println("<p>pass Encounter <b>?id=xxx</b> on url</p>");
} else {
    Encounter enc = myShepherd.getEncounter(id);
    if (enc == null) {
        out.println("<p>encounter unknown</p>");
    } else {
        out.println("<p>encounter: <b>" + enc +"</b></p>");
        out.println("<p>enc.submitterUser: <b>" + enc.getSubmitterUser(myShepherd) +"</b></p>");
        out.println("<hr /><p>testing against user: <b>" + user +"</b></p>");
        if (user != null) {
            out.println("<p>user has roles: <b>" + myShepherd.getAllRolesForUserAsString(user.getUsername()) + "</b></p>");
        }
        out.println("<p>enc.canUserView(): <b>" + enc.canUserView(user, myShepherd) + "</b></p>");
        out.println("<p>enc.canUserEdit(): <b>" + enc.canUserEdit(user, myShepherd) + "</b></p>");
        out.println("<p>enc.canUserAccess(): <b>" + enc.canUserAccess(user, myShepherd.getContext()) + "</b></p>");
    }
}
myShepherd.rollbackAndClose();



%>
