<!DOCTYPE html PUBLIC "-//W3C//DTD XHTML 1.0 Transitional//EN"
        "http://www.w3.org/TR/xhtml1/DTD/xhtml1-transitional.dtd">
<%@ page contentType="text/html; charset=utf-8" language="java" import="org.joda.time.LocalDateTime,
org.joda.time.format.DateTimeFormatter,
org.joda.time.format.ISODateTimeFormat,java.net.*,
org.ecocean.grid.*,
java.io.*,java.util.*, java.io.FileInputStream, java.io.File, java.io.FileNotFoundException, org.ecocean.*,org.ecocean.servlet.*,org.ecocean.media.*,javax.jdo.*, java.lang.StringBuffer, java.util.Vector, java.util.Iterator, java.lang.NumberFormatException"%>


<%!

static <K,V extends Comparable<? super V>>
SortedSet<Map.Entry<K,V>> entriesSortedByValues(Map<K,V> map) {
    SortedSet<Map.Entry<K,V>> sortedEntries = new TreeSet<Map.Entry<K,V>>(
        new Comparator<Map.Entry<K,V>>() {
            @Override public int compare(Map.Entry<K,V> e1, Map.Entry<K,V> e2) {
                int res = e2.getValue().compareTo(e1.getValue());
                return res != 0 ? res : 1;
            }
        }
    );
    sortedEntries.addAll(map.entrySet());
    return sortedEntries;
}

%>

<%

String context="context0";
context=ServletUtilities.getContext(request);

Shepherd myShepherd=new Shepherd(context);



%>

<html>
<head>
<title>Fix Some Fields</title>

</head>


<body>

<table width="100%">
<tr>
<td>
<h2>Location IDs Ranked by Number of Encounters</h2>
<ol>
<%

myShepherd.beginDBTransaction();
try{
	
	Map<String,Long> locCount = new HashMap<String,Long>();
	locCount.put(null, 0L);
	String sql = "SELECT \"LOCATIONID\" AS locId, COUNT(*) AS ct FROM \"ENCOUNTER\" GROUP BY locId ORDER BY ct desc";
	Query q = myShepherd.getPM().newQuery("javax.jdo.query.SQL", sql);
	List results = (List)q.execute();
	int c = 0;
	Iterator it = results.iterator();
	while (it.hasNext()) {
	    Object[] row = (Object[]) it.next();
	    String locId = (String)row[0];
	    long ct = (long)row[1];
	    if (!Util.stringExists(locId) || locId.toLowerCase().equals("none")|| locId.trim().equals("")) {
	        
	    } else {
	        locCount.put(locId, ct);
	        %>
	        <li><%=locId %>: <%=ct %></li>
	        <%
	    }
	}

%>
</ol>
</td>

<td>
<h2>Location IDs Ranked by Number Identified Individuals</h2>
<ol>
<%
List<String> locIDs=myShepherd.getAllLocationIDs();
TreeMap<String, Integer> results2=new TreeMap<String, Integer>();
for(String loc:locIDs){
	if(loc!=null && !loc.toCharArray().equals("none") && !loc.trim().equals("")){
		results2.put(loc,new Integer(myShepherd.getNumMarkedIndividualsSightedAtLocationID(loc)));
	}
}
SortedSet<Map.Entry<String,Integer>> entries=entriesSortedByValues(results2);
for(Map.Entry<String,Integer> entry:entries){
	%>
	<li><%=entry.getKey() %>: <%=entry.getValue() %></li>
	<% 
}
%>

</ol>

</td>
</tr>
</table>
<%
}
catch(Exception e){
	myShepherd.rollbackDBTransaction();
	%>
	<p>Reported error: <%=e.getMessage() %> <%=e.getStackTrace().toString() %></p>
	<%
	e.printStackTrace();
}
finally{
	myShepherd.rollbackDBTransaction();
	myShepherd.closeDBTransaction();

}

%>

</body>
</html>
