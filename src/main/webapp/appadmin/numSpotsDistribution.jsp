<!DOCTYPE html PUBLIC "-//W3C//DTD XHTML 1.0 Transitional//EN"
        "http://www.w3.org/TR/xhtml1/DTD/xhtml1-transitional.dtd">
<%@ page contentType="text/html; charset=utf-8" language="java" import="org.joda.time.LocalDateTime,
org.joda.time.format.DateTimeFormatter,
org.joda.time.format.ISODateTimeFormat,java.net.*,
org.ecocean.grid.*,
java.io.*,java.util.*, java.io.FileInputStream, java.io.File, java.io.FileNotFoundException, org.ecocean.*,org.ecocean.servlet.*,org.ecocean.media.*,javax.jdo.*, java.lang.StringBuffer, java.util.Vector, java.util.Iterator, java.lang.NumberFormatException"%>

<%

String context="context0";
context=ServletUtilities.getContext(request);

Shepherd myShepherd=new Shepherd(context);
myShepherd.setAction("numSPotsDistribution.jsp");


%>

<html>
<head>
<title>Num Spots Distributions</title>

</head>


<body>

<ul>
<%

HashMap<Integer,Integer> spotMap=new HashMap<Integer,Integer>();

myShepherd.beginDBTransaction();
try{
	
    List<Encounter> encs=null;
    String filter="SELECT FROM org.ecocean.Encounter WHERE spots != null || rightSpots != null";  
    Query query=myShepherd.getPM().newQuery(filter);
    Collection c = (Collection) (query.execute());
    encs=new ArrayList<Encounter>(c);
    query.closeAll();
    int numEncounters=encs.size();
    for(int i=0;i<numEncounters;i++){
    	Encounter enc=encs.get(i);
    	if(enc.getSpots()!=null && enc.getSpots().size()>0){
    		int numSpots=enc.getSpots().size();
    		Integer nSpots=new Integer(numSpots);
    		if(spotMap.containsKey(nSpots)){
    			Integer prevSpots=spotMap.get(nSpots);
    			prevSpots++;
    			spotMap.put(nSpots, prevSpots);
    		}
    		else{
    			spotMap.put(nSpots, new Integer(1));
    		}
    	}
    	else if(enc.getRightSpots()!=null && enc.getRightSpots().size()>0){
    		int numSpots=enc.getRightSpots().size();
    		Integer nSpots=new Integer(numSpots);
    		if(spotMap.containsKey(nSpots)){
    			Integer prevSpots=spotMap.get(nSpots);
    			prevSpots++;
    			spotMap.put(nSpots, prevSpots);
    		}
    		else{
    			spotMap.put(nSpots, new Integer(1));
    		}
    	}

    }
 
    StringBuffer sb=new StringBuffer();
    %>
    
    <pre>
    <%
    for(int i=1;i<100;i++){
    	Integer iter=new Integer(i);
    	int val=0;
    	if(spotMap.get(iter)!=null){
			val=spotMap.get(iter).intValue();
    	}
    	sb.append(i+","+val+"\n");
    	
    }
    %>
    <%=sb.toString() %>
    </pre>
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

</ul>

</body>
</html>
