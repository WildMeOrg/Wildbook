<!DOCTYPE html PUBLIC "-//W3C//DTD XHTML 1.0 Transitional//EN"
        "http://www.w3.org/TR/xhtml1/DTD/xhtml1-transitional.dtd">
<%@ page contentType="text/html; charset=utf-8" language="java" import="org.joda.time.LocalDateTime,
org.joda.time.format.DateTimeFormatter,
org.joda.time.format.ISODateTimeFormat,java.net.*,
org.ecocean.grid.*,
org.ecocean.media.MediaAsset,
java.io.*,java.util.*, java.io.FileInputStream, java.io.File, java.io.FileNotFoundException, org.ecocean.*,org.ecocean.servlet.*,javax.jdo.*, java.lang.StringBuffer, java.util.Vector, java.util.Iterator, java.lang.NumberFormatException"%>
<%

String context="context0";
context=ServletUtilities.getContext(request);

Shepherd myShepherd=new Shepherd(context);



%>

<html>
<head>
<title>Fix Standard Children</title>

</head>


<body>
<p>Setting up standardchildren for mediaassets.</p>

<ul>
<%

myShepherd.beginDBTransaction();

int numFixes=0;
int numOrphanAssets=0;	
int numDatalessAssets=0;	
int numDatasFixed=0;	
int numAssetsFixed=0;	
int numAssetsWithoutStore=0;	

boolean committing=false;


%><p>Committing = <%=committing%>.</p><%


try{

	Iterator allEncs=myShepherd.getAllEncounters();

	while(allEncs.hasNext()){

		Encounter enc =(Encounter) allEncs.next();

		if (("Oman Photo ID Catalog Bulk Import").equals(enc.getSubmitterName())) {

			for (Annotation ann: enc.getAnnotations()) {
				MediaAsset ma = ann.getMediaAsset();

				if (!ma.hasMetadata()) {
					out.println("<li>Metadata-free Asset! <a href=\"obrowse.jsp?type=MediaAsset&id="+ma.getId()+"\"> "+ma.getId()+" </a></li>");
					numDatalessAssets++;

					ma.setMetadata();
					if (committing) {
						myShepherd.commitDBTransaction();
						myShepherd.beginDBTransaction();
					}

					if (ma.hasMetadata()) numDatasFixed++;
				}


				if (!ma.hasFamily(myShepherd)) {
					out.println("<li>Orphaned Asset <a href=\"obrowse.jsp?type=MediaAsset&id="+ma.getId()+"\"> "+ma.getId()+" </a></li>");
					numOrphanAssets++;

					//if (committing) ma.updateStandardChildren(myShepherd);
					//else ma.updateStandardChildren();
					//if (committing) {
					//	myShepherd.commitDBTransaction();
					//	myShepherd.beginDBTransaction();
					//}

					//if (ma.getStore()==null) numAssetsWithoutStore++;

					//if (ma.hasFamily(myShepherd)) numAssetsFixed++;
				}

			}
			numFixes++;
		}
	}
	if (committing) {
		%><p>Committing now!</p><%
		myShepherd.commitDBTransaction();
		myShepherd.beginDBTransaction();
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
<p>Done successfully: <%=numFixes %> total encounters</p>
<p>Done             : <%=numOrphanAssets %> orphan assets</p>
<p>Done             : <%=numAssetsFixed %> assets fixed</p>
<p>Done             : <%=numAssetsWithoutStore %> assets with no asset stores</p>

</body>
</html>
