<!DOCTYPE html PUBLIC "-//W3C//DTD XHTML 1.0 Transitional//EN"
        "http://www.w3.org/TR/xhtml1/DTD/xhtml1-transitional.dtd">
<%@ page contentType="text/html; charset=utf-8" language="java" import="org.joda.time.LocalDateTime,
org.joda.time.format.DateTimeFormatter,
org.joda.time.format.ISODateTimeFormat,java.net.*,
org.ecocean.grid.*,org.ecocean.media.*,org.ecocean.media.MediaAsset,
java.io.*,java.util.*, java.io.FileInputStream, java.io.File, java.io.FileNotFoundException, org.ecocean.*,org.ecocean.servlet.*,javax.jdo.*, java.lang.StringBuffer, java.util.Vector, java.util.HashMap, java.util.Iterator, java.util.HashSet, java.util.ArrayList, java.lang.NumberFormatException"%>

<%

String context="context0";
context=ServletUtilities.getContext(request);

Shepherd myShepherd=new Shepherd(context);



%>

<html>
<head>
<title>Fix Some Keywords</title>

</head>


<body>
<p>Marking "isUsed" on <em>Keywords!</em></p>

<ul>
<%

myShepherd.beginDBTransaction();

int numFixes=0;
int numIsUsedChanges=0;
int numKeywords=0;
int numAssets=0;
boolean committing=true;

Set<String> keywordNameSet=new HashSet<String>();

%><strong>committing = <%=committing%></strong><%
try{

	Iterator allAssets=myShepherd.getAllMediaAssets();

	while(allAssets.hasNext()){
		numAssets++;

		MediaAsset assy=(MediaAsset)allAssets.next();
		for (Keyword thisWord: assy.getKeywords()) {
			keywordNameSet.add(thisWord.getIndexname());
			if (thisWord==null) continue;
			Boolean oldVal = thisWord.getIsUsed();
			thisWord.setIsUsed(true);
			Boolean newVal = thisWord.getIsUsed();
			if (oldVal!=newVal) {
				numIsUsedChanges++;
				%><li>Just set used=true on keyword<%=thisWord.getReadableName()%></li><%
			}
		}

	}

	//now go through and delete unused keywords
	Iterator allKWs=myShepherd.getAllKeywords();
	while(allKWs.hasNext()){
		numKeywords++;
		Keyword kw=(Keyword)allKWs.next();
		if (!keywordNameSet.contains(kw.getIndexname())){
			numFixes++;
			if (committing) {
				myShepherd.throwAwayKeyword(kw);
				myShepherd.commitDBTransaction();
				myShepherd.beginDBTransaction();
			}
		} 
	}

}
catch (Exception e) {
	myShepherd.rollbackDBTransaction();
}
finally {
	myShepherd.closeDBTransaction();
}


%>

</ul>
<p>Done successfully: <%=numFixes %> total fixes, <%=numIsUsedChanges%> in-use changes.</p>
<p><strong><%=keywordNameSet.size() %></strong> Keywords were attached to MediaAssets</p>
<p>out of <strong><%=numKeywords %></strong> total keywords</p>
<p>in <strong><%=numAssets %></strong> total assets</p>

</body>
</html>
