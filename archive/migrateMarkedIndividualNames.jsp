<%@ page contentType="text/html; charset=utf-8" language="java"
 import="org.ecocean.*,
java.util.ArrayList,
java.util.List,
java.util.Set,
java.util.Iterator,
java.util.Collection,
javax.jdo.*,
org.ecocean.media.*

          "
%>
<style>
.assigned {
    color: #AAA;
}
</style>
<%

String context = "context0";
Shepherd myShepherd = new Shepherd(context);


Extent all = null;
try {
    all = myShepherd.getPM().getExtent(Organization.class, true);
} catch (javax.jdo.JDOException x) {
    x.printStackTrace();
}
Extent encClass = myShepherd.getPM().getExtent(Organization.class, true);
Query q = myShepherd.getPM().newQuery(encClass);
Collection c = (Collection) (q.execute());
int numOrgs = c.size();
q.closeAll();
if (numOrgs < 1) {
    out.println("<p>seems you have no <i>Organizations</i>, you should probably run <b>migrate_to_organizations.pl</b> first.</p>");
    return;
}

myShepherd.beginDBTransaction();

q = myShepherd.getPM().newQuery("SELECT FROM org.ecocean.MarkedIndividual");
c = (Collection)q.execute();
Iterator it = c.iterator();

while (it.hasNext()) {
    MarkedIndividual ind = (MarkedIndividual)it.next();
    MultiValue names = ind.getNames();
    if (names != null) {
        out.println("<p class=\"assigned\"><b>" + ind.getId() + "</b>:");
        out.println(names + "</p>");
    } else {
        names = ind.setNamesFromLegacy();
        out.println("<p><b>" + ind.getId() + "</b>:");
        out.println(names + "</p>");
    }
}

q.closeAll();


myShepherd.commitDBTransaction();

%>



