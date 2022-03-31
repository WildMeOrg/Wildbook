<!DOCTYPE html PUBLIC "-//W3C//DTD XHTML 1.0 Transitional//EN"
        "http://www.w3.org/TR/xhtml1/DTD/xhtml1-transitional.dtd">
<%@ page contentType="text/html; charset=utf-8" language="java" import="org.joda.time.LocalDateTime,
org.joda.time.format.DateTimeFormatter,
org.joda.time.format.ISODateTimeFormat,java.net.*,
org.ecocean.grid.*,
java.io.*,java.util.*, java.io.FileInputStream, java.io.File, java.io.FileNotFoundException, org.ecocean.*,org.ecocean.servlet.*,javax.jdo.*, java.lang.StringBuffer, java.util.Vector, java.util.Iterator, java.lang.NumberFormatException"%>

<%

String context="context0";
context=ServletUtilities.getContext(request);

	Shepherd myShepherd=new Shepherd(context);
	myShepherd.beginDBTransaction();

// pg_dump -Ft sharks > sharks.out

//pg_restore -d sharks2 /home/webadmin/sharks.out

Integer startYear=myShepherd.getEarliestSightingYear();
	Integer endYear=myShepherd.getLastSightingYear();

HashMap<String,HashMap<Integer,Integer>> locations=new HashMap<String,HashMap<Integer,Integer>>();
List<String> locationIDs=myShepherd.getAllLocationIDs();
int numLocID=locationIDs.size();
for(int q=0;q<numLocID;q++){

	HashMap<Integer,Integer> yearMatrix=new HashMap<Integer,Integer>();
	
	for(int i=startYear;i<=endYear;i++){
		
		yearMatrix.put(new Integer(i), new Integer(0));
		
	}
	locations.put(locationIDs.get(q), yearMatrix);
}
%>

<html>
<head>
<title>Cascadia Data Matrix</title>

</head>


<body>


<%



//build queries


try{


	String rootDir = getServletContext().getRealPath("/");
	String baseDir = ServletUtilities.dataDir(context, rootDir).replaceAll("dev_data_dir", "caribwhale_data_dir");
	
	
	

	Iterator allEncs=myShepherd.getAllEncounters();
	


while(allEncs.hasNext()){
	
	myShepherd.beginDBTransaction();
	Encounter enc=(Encounter)allEncs.next();
	boolean locked=false;

	if((enc.getGenus()!=null)&&(enc.getGenus().toLowerCase().equals("megaptera"))&&(enc.getAnnotations()!=null)&&(enc.getAnnotations().size()>0)&&(enc.getLocationID()!=null)){
		int thisYear=enc.getYear();
		String locID=enc.getLocationID();
		HashMap<Integer,Integer> yearMatrix=locations.get(locID);
		
		int value=yearMatrix.get(thisYear).intValue()+enc.getAnnotations().size();
		yearMatrix.put(new Integer(thisYear), new Integer(value));
		
		locations.put(locID, yearMatrix);
	
	} //end if

	
	
} //end while

String header="Region";	
for(int i=startYear;i<=endYear;i++){
	header=header+(","+i);
}
	
%>	
<%=header %><br />	
	
<%	
//and now write out the result

for(int q=0;q<numLocID;q++){
	
	String line=locationIDs.get(q);

	HashMap<Integer,Integer> yearMatrix=locations.get(locationIDs.get(q));
	
	for(int i=startYear;i<=endYear;i++){
		line=line+(","+yearMatrix.get(i).toString());
	}
	%>
	<%=line %></br>
	<%
}

	
	

} //end try
catch(Exception e){e.printStackTrace();}

myShepherd.rollbackDBTransaction();
myShepherd.closeDBTransaction();

%>


</body>
</html>
