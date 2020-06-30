<%@ page contentType="text/plain; charset=utf-8" language="java"
     import="org.ecocean.*,
org.ecocean.servlet.ServletUtilities,
javax.jdo.*,
java.util.Iterator,
java.util.Arrays,
java.util.List,
java.util.ArrayList,
java.util.Set,
java.util.HashSet,
org.json.JSONArray,
org.json.JSONObject,
org.ecocean.media.*
              "
%><%

String context = ServletUtilities.getContext(request);
Shepherd myShepherd = new Shepherd(context);
myShepherd.setAction("acmIdSync.jsp");
myShepherd.beginDBTransaction();
try{
	if (request.getParameter("annotations") == null) {
	    out.println("[");
	    String sql = "SELECT DISTINCT(\"ACMID\") AS acmId FROM \"MEDIAASSET\" WHERE \"ACMID\" IS NOT NULL ORDER BY acmId; ";
	    Query q = myShepherd.getPM().newQuery("javax.jdo.query.SQL", sql);
	    List results = (List)q.execute();
	    Iterator it = results.iterator();
	    while (it.hasNext()) {
	        //Object[] fields = (Object[])it.next();
	        Object id = it.next();
	        if (it.hasNext()) {
	            out.println("\"" + id + "\", ");
	        } else {
	            out.println("\"" + id + "\" ");
	        }
	    }
	    out.println("]");
	    q.closeAll();
	
	
	} else {
	    out.println("{");
	
	    String sql = 
	        "SELECT \"ANNOTATION\".\"ACMID\" as annotAcmId, " +
	        "\"ANNOTATION\".\"MATCHAGAINST\" as annotMatchAgainst, " +
	        "concat(\"ENCOUNTER\".\"GENUS\", ' ', \"ENCOUNTER\".\"SPECIFICEPITHET\") as species from " +
	        " \"ANNOTATION\"  " +
	        "join \"ANNOTATION_FEATURES\" on (\"ANNOTATION_FEATURES\".\"ID_OID\" = \"ANNOTATION\".\"ID\") " + 
	        "join \"ENCOUNTER_ANNOTATIONS\" on (\"ANNOTATION_FEATURES\".\"ID_OID\" = \"ENCOUNTER_ANNOTATIONS\".\"ID_EID\") " +
	        "join \"ENCOUNTER\" on (\"ENCOUNTER_ANNOTATIONS\".\"CATALOGNUMBER_OID\" = \"ENCOUNTER\".\"CATALOGNUMBER\") " + 
	        " where \"ANNOTATION\".\"ACMID\" is not null " +
	        " order by \"ACMID\" ;";
	    Query q = myShepherd.getPM().newQuery("javax.jdo.query.SQL", sql);
	    List results = (List)q.execute();
	    Iterator it = results.iterator();
	    String prev = null;
	    List<Boolean> mag = new ArrayList<Boolean>();
	    List<String> spec = new ArrayList<String>();
	    int ct = 0;
	    while (it.hasNext()) {
	        Object[] f = (Object[])it.next();
	        String id = (String)f[0];
	        if (prev == null) prev = id;
	        if (!prev.equals(id)) {
	            if (ct > 0) out.print(", ");
	            out.println("\"" + prev + "\": { \"match\": " + (new JSONArray(mag)).toString() + ", \"species\": " + (new JSONArray(spec)).toString() + "}"); 
	            mag = new ArrayList<Boolean>();
	            spec = new ArrayList<String>();
	            ct++;
	            prev = id;
	        }
	        mag.add((Boolean)f[1]);
	        String sp = (String)f[2];
	        if ((sp == null) || sp.equals("") || sp.equals(" ")) {
	            spec.add(null);
	        } else {
	            spec.add(sp);
	        }
	/*
	        Object id = it.next();
	        if (it.hasNext()) {
	            out.println("\"" + id + "\", ");
	        } else {
	            out.println("\"" + id + "\" ");
	        }
	*/
	    }
	    out.println("}");
	    q.closeAll();
	}
}
catch(Exception e){
	e.printStackTrace();
}
finally{
	myShepherd.rollbackDBTransaction();
	myShepherd.closeDBTransaction();
}

%>
