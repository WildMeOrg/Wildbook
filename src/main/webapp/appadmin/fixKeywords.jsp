<!DOCTYPE html PUBLIC "-//W3C//DTD XHTML 1.0 Transitional//EN"
        "http://www.w3.org/TR/xhtml1/DTD/xhtml1-transitional.dtd">
<%@ page contentType="text/html; charset=utf-8" language="java" import="org.joda.time.LocalDateTime,
org.joda.time.format.DateTimeFormatter,
org.joda.time.format.ISODateTimeFormat,java.net.*,
org.ecocean.grid.*,org.ecocean.media.*,org.ecocean.media.MediaAsset,
java.io.*,java.util.*, java.io.FileInputStream, java.io.File, java.io.FileNotFoundException, org.ecocean.*,org.ecocean.servlet.*,javax.jdo.*, java.lang.StringBuffer, java.util.Vector, java.util.HashMap, java.util.Iterator, java.util.HashSet, java.util.ArrayList, java.lang.NumberFormatException"%>


<%!
// methods for this fix

// lots of custom logic here. Given a keyword, returns the String that should become the readableName of the cleaned version of that keyword. e.g. "right-dorsal" -> "Right Dorsal Fin"
String cleanKeywordName(String oldName) {
	if (oldName==null) return null;

	if (oldName.equals("Right-Dorsal")) return "Right Dorsal Fin";
	if (oldName.equals("Left-Dorsal")) return "Left Dorsal Fin";
	if (oldName.equals("fluke")) return "Tail Fluke";

	return oldName;
}

void incrementMap(Map<String,Integer> map, String key) {
	if (map.containsKey(key)) {
		Integer count = map.get(key);
		map.put(key, count+1);
	} else {
		map.put(key, 1);
	}
}

// this function updates the defaultKeywords map and the duplicateCounts map in light of a new thisKw, and returns the appropriate default keyword
Keyword updateKeywordMaps(Map<String,Keyword> defaultKeywords, Map<String,Integer> duplicateCounts, Keyword thisKw, Shepherd myShepherd) {
	if (thisKw==null) return null;
	String kwName = cleanKeywordName(thisKw.getReadableName());
	if (defaultKeywords.containsKey(kwName)) {
		Keyword defaultKw = defaultKeywords.get(kwName);
		if (!defaultKw.getIndexname().equals(thisKw.getIndexname())) incrementMap(duplicateCounts, kwName);
		return defaultKw;
	} else {

		Keyword newDefault = thisKw;
		if (!thisKw.getReadableName().equals(kwName)) { // gets default from shepherd if this is not the default
			newDefault = myShepherd.getOrCreateKeyword(kwName);
		}

		defaultKeywords.put(kwName, newDefault);

		return newDefault;
	}

}

void updateKeywords(Map<String,Keyword> keywordMap, Keyword kw) {
	String name = kw.getReadableName();
	if (keywordMap.containsKey(name)) return;
	keywordMap.put(name, kw);
}



%>


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
<p>Fixing some <em>Keywords!</em></p>

<ul>
<%

myShepherd.beginDBTransaction();

int numFixes=0;
int numAssets=0;
boolean committing=false;
%><strong>committing = <%=committing%></strong><%

List<String> allWords = new ArrayList<String>();
Set<String> uniqueWords = new HashSet<String>();
// duplicatedKwNames should show how many unique keywords have the same name
Map<String,Integer> duplicatedKwNames = new HashMap<String,Integer>();
Map<String,Keyword> defaultKeywords = new HashMap<String, Keyword>();


try{

	Iterator allAssets=myShepherd.getAllMediaAssets();

	while(allAssets.hasNext()){
		numAssets++;

		MediaAsset assy=(MediaAsset)allAssets.next();
		ArrayList<Keyword> wordsToRemove = new ArrayList<Keyword>();
		ArrayList<Keyword> wordsToAdd    = new ArrayList<Keyword>();

		for (Keyword thisWord: assy.getKeywords()) {

			if (thisWord==null) continue;
			//Keyword replacementWord

			String name = thisWord.getReadableName();
			uniqueWords.add(name);

			Keyword replacementWord = updateKeywordMaps(defaultKeywords, duplicatedKwNames, thisWord, myShepherd);

			if (!thisWord.getIndexname().equals(replacementWord.getIndexname())) {
				wordsToAdd.add(replacementWord);
				wordsToRemove.add(thisWord);
			}

		}
		if (wordsToAdd.size()!=0) {
			ArrayList<Keyword> updatedWords = assy.getKeywords();
			updatedWords.removeAll(wordsToRemove);
			updatedWords.addAll(wordsToAdd);
			assy.setKeywords(updatedWords);
			numFixes++;
			if (committing) {
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


// now uniqueWords will contain only singletons (duplicatedKwNames doesn't contain singletons)
uniqueWords.removeAll(duplicatedKwNames.keySet());

%>

</ul>
<p>Done successfully: <%=numFixes %> total fixes</p>
<p>num assets: <%=numAssets %> total</p>

<p><h2>Duplicated keyword counts: </h2>

	<note><em>There are <%=duplicatedKwNames.size() %> total words</em></note>
	<ul>

	<%
	for (String word: duplicatedKwNames.keySet()) {
	%><li><%= word %>: <%=duplicatedKwNames.get(word)  %></li><%
	}
	%>
</ul></p>


<p><h2>Unique words: </h2><ul>
	<%
	//uniqueWords.removeAll(duplicatedKwNames.keySet());

	for (String word: uniqueWords) {
	%><li><%= word %></li><%
	}
	%>
</ul></p>


</body>
</html>
