<%@ page contentType="text/html; charset=utf-8" language="java"
     import="org.ecocean.*,
java.util.Collection,
java.io.IOException,
java.util.ArrayList,
javax.jdo.Query,
java.util.List,
java.util.Map,
org.json.JSONObject,

org.ecocean.servlet.ServletUtilities,
org.ecocean.media.*
              "

%><%

String context = ServletUtilities.getContext(request);
Shepherd myShepherd = new Shepherd(context);
myShepherd.beginDBTransaction();

Query query = myShepherd.getPM().newQuery("SELECT FROM org.ecocean.Encounter WHERE submitterID == null && submitters.size() > 0");
Collection c = (Collection) (query.execute());
List<Encounter> encs = new ArrayList<Encounter>(c);
query.closeAll();

int ct = 0;
for (Encounter enc : encs) {
    ct++;
    User u = enc.getSubmitters().get(0);
    out.println("<hr />(" + ct + ") ");
    if ((u == null) || (u.getUsername() == null)) {
        out.println("<b>user " + u + "</b> has <i>no username!</i> for " + enc.getCatalogNumber() + " (skipping)");
    } else {
        out.println(enc.getCatalogNumber() + " -> <i>" + u.getUsername() + "</i>");
        System.out.println("submitterIDFix.jsp: " + enc.getCatalogNumber() + " -> " + u.getUsername());
        enc.setSubmitterID(u.getUsername());
    }
}

myShepherd.commitDBTransaction();


%>
