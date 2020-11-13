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

ArrayList<String> problemChildren=new ArrayList<String>();

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
int numStringNull=0;


try {

	String filter="select from org.ecocean.Encounter where specificEpithet == 'macrocephalus'";
	Query q=myShepherd.getPM().newQuery(filter);
	Collection c=(Collection)q.execute();
	ArrayList<Encounter> encs=new ArrayList<Encounter>(c);
	q.closeAll();
	
	for(Encounter enc:encs){
	
		
		for(Annotation annot:enc.getAnnotations()){
			if(annot.getIAClass()!=null && !annot.getIAClass().equals("whale_fluke")){
				
				System.out.println(annot.getIAClass());
				
				annot.setIAClass(null);
				myShepherd.updateDBTransaction();
				
			}
		}
		
		
		
		/*
		for(MediaAsset ma:enc.getMedia()){
		
			if(ma.getDetectionStatus()!=null && (ma.getDetectionStatus().equals("pending") || ma.getDetectionStatus().equals("processing"))){
				ma.setDetectionStatus(null);
				myShepherd.updateDBTransaction();
				
			}
			
			if(ma.getDetectionStatus()==null || ma.getDetectionStatus().equals("pending")){
				
				numFixes++;
				ma.setDetectionStatus("complete");
				myShepherd.updateDBTransaction();
				
				if(ma.getAnnotations()!=null && ma.getAnnotations().size()>=2){
					//ma.setDetectionStatus("complete");
					//myShepherd.updateDBTransaction();
					System.out.println("Setting COMPLETE on 2+ annots");
					//umFixes++;
				}
				if(ma.getAnnotations()!=null && ma.getAnnotations().size()==1 && !ma.getAnnotations().get(0).isTrivial()){
					//ma.setDetectionStatus("complete");
					//myShepherd.updateDBTransaction();
					System.out.println("Setting COMPLETE on non-trivial annot");
					//numFixes++;
				}
				if(ma.getAnnotations()!=null && ma.getAnnotations().size()==1 && ma.getAnnotations().get(0).isTrivial() && ma.getAnnotations().get(0).getMatchAgainst()){
					//ma.setDetectionStatus("complete");
					//myShepherd.updateDBTransaction();
					System.out.println("trivial annot matchAgainst true");
					
					//ma.getAnnotations().get(0).revertToTrivial(myShepherd);
					//myShepherd.updateDBTransaction();
					//numFixes++;
				}
				
				if(ma.getAnnotations()!=null && ma.getAnnotations().size()==1 && ma.getAnnotations().get(0).isTrivial() ){
					//ma.setDetectionStatus("complete");
					//myShepherd.updateDBTransaction();
					//System.out.println("trivial annot matchAgainst true");
					
					//ma.getAnnotations().get(0).revertToTrivial(myShepherd);
					//myShepherd.updateDBTransaction();
					//numFixes++;
				}
				
				if(ma.getAnnotations()==null || ma.getAnnotations().size()==0 ){
					//ma.setDetectionStatus("complete");
					//myShepherd.updateDBTransaction();
					
					//ma.getAnnotations().get(0).revertToTrivial(myShepherd);
					//myShepherd.updateDBTransaction();
					//numFixes++;
				}
				
				
				//problemChildren.add(enc.getCatalogNumber());
				
				//ma.setDetectionStatus("complete");
				//myShepherd.updateDBTransaction();
			}

	
			
			if(ma.getDetectionStatus()==null){
				//System.out.println("ZZZZ: "+enc.getCatalogNumber());
				ma.setAcmId(null);
				myShepherd.updateDBTransaction();
				ArrayList<MediaAsset> maList = new ArrayList<MediaAsset>();
				maList.add(ma);
				IBEISIA.sendMediaAssetsNew(maList, context);
				numFixes++;
				myShepherd.updateDBTransaction();
				System.out.println("numFixes: "+numFixes);
				if(ma.getAcmId()==null || ma.getAcmId().trim().equals("")){
					if(!problemChildren.contains(enc.getCatalogNumber())){problemChildren.add(enc.getCatalogNumber());}
				}
			}
			
			
		}
		*/
		
		
		
		
	}

}
catch(Exception e){
	myShepherd.rollbackDBTransaction();
}
finally{
	myShepherd.closeDBTransaction();

}

for(String p:problemChildren){
	%>
	<li><a target="_blank" href="../encounters/encounter.jsp?number=<%=p %>"><%=p %></a></li>
	<%
}

%>

</ul>

<p>Done successfully: <%=numFixes %> fixed.</p>
<p>Done successfully: <%=numStringNull %> fstring null</p>

</body>
</html>
