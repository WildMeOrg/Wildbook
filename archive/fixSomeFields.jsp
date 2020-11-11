<!DOCTYPE html PUBLIC "-//W3C//DTD XHTML 1.0 Transitional//EN"
        "http://www.w3.org/TR/xhtml1/DTD/xhtml1-transitional.dtd">
<%@ page contentType="text/html; charset=utf-8" language="java" import="org.joda.time.LocalDateTime,
org.joda.time.format.DateTimeFormatter,
org.joda.time.format.ISODateTimeFormat,java.net.*,org.ecocean.media.*,org.ecocean.identity.*,
org.ecocean.grid.*,
java.io.*,java.util.*, java.io.FileInputStream, java.io.File, java.io.FileNotFoundException, org.ecocean.*,org.ecocean.servlet.*,javax.jdo.*, java.lang.StringBuffer, java.util.Vector, java.util.Iterator, java.lang.NumberFormatException"%>

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
<ul>
<%

myShepherd.beginDBTransaction();

int numFixes=0;


try {

	String filter="select from org.ecocean.Encounter where specificEpithet == 'truncatus'";
	Query q=myShepherd.getPM().newQuery(filter);
	Collection c=(Collection)q.execute();
	ArrayList<Encounter> encs=new ArrayList<Encounter>(c);
	q.closeAll();
	
	for(Encounter enc:encs){
		
		for(MediaAsset ma:enc.getMedia()){
			if(ma.getDetectionStatus()!=null && (ma.getDetectionStatus().equals("pending") || ma.getDetectionStatus().equals("bad"))){
				ma.setDetectionStatus(null);
				myShepherd.updateDBTransaction();
				
			}
			if(ma.getDetectionStatus()==null && ma.getAcmId()!=null){
				ma.setAcmId(null);
				myShepherd.updateDBTransaction();
				ArrayList<MediaAsset> maList = new ArrayList<MediaAsset>();
				maList.add(ma);
				IBEISIA.sendMediaAssetsNew(maList, context);
				numFixes++;
				System.out.println("numFixes: "+numFixes);
			}
			
		}
		
	}

}
catch(Exception e){
	myShepherd.rollbackDBTransaction();
}
finally{
	myShepherd.closeDBTransaction();

}

%>

</ul>

<p>Done successfully: <%=numFixes %> fixed.</p>

</body>
</html>
