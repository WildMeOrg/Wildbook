<%@ page contentType="text/html; charset=utf-8" language="java"
     import="org.ecocean.*,
java.util.Collection,
java.io.IOException,
java.util.ArrayList,
java.util.Arrays,
javax.jdo.Query,
java.util.List,
java.util.Iterator,
java.util.Map,
java.util.HashMap,
java.lang.reflect.Method,
java.lang.reflect.Field,
org.json.JSONArray,
org.json.JSONObject,

org.ecocean.servlet.ServletUtilities,
org.ecocean.media.*
              "
%><%!


%><%


String sql = "SELECT \"ID\",\"ACMID\" FROM \"ANNOTATION\" WHERE \"ACMID\" IN (SELECT acmId FROM (SELECT \"ACMID\" AS acmId, COUNT(DISTINCT(\"INDIVIDUALID_OID\")) AS ct FROM \"ANNOTATION\" JOIN \"ENCOUNTER_ANNOTATIONS\" ON (\"ANNOTATION\".\"ID\" = \"ENCOUNTER_ANNOTATIONS\".\"ID_EID\") JOIN \"MARKEDINDIVIDUAL_ENCOUNTERS\" ON (\"ENCOUNTER_ANNOTATIONS\".\"CATALOGNUMBER_OID\" = \"MARKEDINDIVIDUAL_ENCOUNTERS\".\"CATALOGNUMBER_EID\") WHERE \"ACMID\" IS NOT NULL GROUP BY acmId) AS counts WHERE ct > 1) ORDER BY \"ACMID\", \"ID\";";
    String context = ServletUtilities.getContext(request);
    Shepherd myShepherd = new Shepherd(context);
    myShepherd.setAction("606.jsp");
    myShepherd.beginDBTransaction();
    Query q = myShepherd.getPM().newQuery("javax.jdo.query.SQL", sql);
    List results = (List)q.execute();
    
    Iterator it = results.iterator();
    String prev = "";
    String list = "";
    int ct = 1;
    while (it.hasNext()) {
        Object[] row = (Object[]) it.next();
        List<String> lrow = new ArrayList<String>();
        String id = (String)row[0];
        String acmId = (String)row[1];
        if (prev.equals("")) prev = acmId;
        if (!prev.equals(acmId)) {
            out.println("<div>(" + ct + ") " + prev + "<ul>" + list + "</ul></div>");
            list = "";
            prev = acmId;
            ct++;
        }
        list += "<li><a target=\"_new\" href=\"../obrowse.jsp?type=Annotation&id=" + id + "\">" + id + "</a></li>";
    }
    q.closeAll();
    myShepherd.rollbackDBTransaction();
    myShepherd.closeDBTransaction();

%>
