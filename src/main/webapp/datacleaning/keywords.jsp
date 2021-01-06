<!DOCTYPE html PUBLIC "-//W3C//DTD XHTML 1.0 Transitional//EN"
        "http://www.w3.org/TR/xhtml1/DTD/xhtml1-transitional.dtd">
<%@ page contentType="text/html; charset=utf-8" language="java" import="org.joda.time.LocalDateTime,
org.joda.time.format.DateTimeFormatter,
org.joda.time.format.ISODateTimeFormat,java.net.*,
org.ecocean.grid.*,org.ecocean.media.*,org.ecocean.media.MediaAsset,
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
<p>Setting sex on Individuals.</p>

<ul>
<%

myShepherd.beginDBTransaction();

int numFixes=0;
boolean committing=false;

Keyword flukeWord = myShepherd.getKeyword("Tail Fluke");
if (flukeWord==null) out.println("Error! could not find flukeWord");
else out.println("<p>Set the "+flukeWord.getReadableName()+" to index "+flukeWord.getIndexname()+"</p>");
Keyword ldWord = myShepherd.getKeyword("Left Dorsal Fin");
if (ldWord==null) out.println("Error! could not find ldWord");
else out.println("<p>Set the "+ldWord.getReadableName()+" to index "+ldWord.getIndexname()+"</p>");
Keyword rdWord = myShepherd.getKeyword("Right Dorsal Fin");
if (rdWord==null) out.println("Error! could not find rdWord");
else out.println("<p>Set the "+rdWord.getReadableName()+" to index "+rdWord.getIndexname()+"</p>");


try{

	Iterator allEncs=myShepherd.getAllMediaAssets();

	while(allEncs.hasNext()){

		MediaAsset indy=(MediaAsset)allEncs.next();
		ArrayList<Keyword> wordsToRemove = new ArrayList<Keyword>();
		ArrayList<Keyword> wordsToAdd    = new ArrayList<Keyword>();

		for (Keyword kword: indy.getKeywords()) {
			if (kword.isDuplicateOf(flukeWord)) {
				wordsToRemove.add(kword);
				wordsToAdd.add(flukeWord);
			} else if (kword.isDuplicateOf(ldWord)) {
				wordsToRemove.add(kword);
				wordsToAdd.add(ldWord);
			} else if (kword.isDuplicateOf(rdWord)) {
				wordsToRemove.add(kword);
				wordsToAdd.add(rdWord);
			}
		}
		if (wordsToAdd.size()!=0) {
			ArrayList<Keyword> updatedWords = indy.getKeywords();
			updatedWords.removeAll(wordsToRemove);
			updatedWords.addAll(wordsToAdd);
			indy.setKeywords(updatedWords);
			numFixes++;
			if (committing) {
				myShepherd.commitDBTransaction();
				myShepherd.beginDBTransaction();
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
<p>Done successfully: <%=numFixes %> total individuals</p>

</body>
</html>
