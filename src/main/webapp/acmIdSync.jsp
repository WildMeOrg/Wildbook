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
%><%@ page import="org.ecocean.shepherd.core.Shepherd"%><%

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

			"\"ANNOTATION\".\"IACLASS\" as annotIaClass, " +

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
		List<String> iaClass = new ArrayList<String>();
	    int ct = 0;
	    while (it.hasNext()) {
	        Object[] f = (Object[])it.next();
	        String id = (String)f[0];
	        if (prev == null) prev = id;
	        if (!prev.equals(id)) {
	            if (ct > 0) out.print(", ");
	            out.println("\"" + prev + "\": { \"match\": " + (new JSONArray(mag)).toString() + ", \"species\": " + (new JSONArray(spec)).toString() +  ", \"iaClass\": " + (new JSONArray(iaClass)).toString() + "}"); 
	            mag = new ArrayList<Boolean>();
	            spec = new ArrayList<String>();
				iaClass = new ArrayList<String>();
	            ct++;
	            prev = id;
			}
			
			String thisIaClass = (String)f[1];
			if ((thisIaClass == null) || thisIaClass.equals("") || thisIaClass.equals(" ")) {
				iaClass.add(null);
			} else {
				iaClass.add(thisIaClass);
			}
			
			mag.add((Boolean)f[2]);
			
	        String sp = (String)f[3];
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
