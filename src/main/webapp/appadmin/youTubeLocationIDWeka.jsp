<!DOCTYPE html PUBLIC "-//W3C//DTD XHTML 1.0 Transitional//EN"
        "http://www.w3.org/TR/xhtml1/DTD/xhtml1-transitional.dtd">
<%@ page contentType="text/html; charset=utf-8" language="java" import="org.joda.time.LocalDateTime,
org.joda.time.format.DateTimeFormatter,
org.joda.time.format.ISODateTimeFormat,java.net.*,
org.ecocean.grid.*,org.ecocean.ai.nmt.azure.*,
java.io.*,org.json.JSONObject,java.util.*, 
java.util.regex.Pattern,
java.util.regex.Matcher,

java.io.FileInputStream, java.io.File, java.io.FileNotFoundException, org.ecocean.*,org.ecocean.servlet.*,org.ecocean.media.*,javax.jdo.*, java.lang.StringBuffer, java.util.Vector, java.util.Iterator, java.lang.NumberFormatException"

%>

<%!
private String translateIfNotEnglish(String text){
	String shortForm=text;
	if(shortForm.length()>500){shortForm=shortForm.substring(0,499);}
	String langCode=DetectTranslate.detectLanguage(shortForm);
	if((!langCode.toLowerCase().equals("en"))&&(!langCode.toLowerCase().equals("und"))){
		//System.out.println("Translating: "+text);
		text=DetectTranslate.translateToEnglish(text).replaceAll(",", " ").replaceAll("\n", " ").replaceAll("'", "").replaceAll("\"", "").replaceAll("′","").replaceAll("’","").toLowerCase();
		//System.out.println("Translated to: "+text);
	}
	return text;
}

%>



<%!
private String removeUrl(String commentstr)
{
    String urlPattern = "((https?|ftp|gopher|telnet|file|Unsure|http):((//)|(\\\\))+[\\w\\d:#@%/;$()~_?\\+-=\\\\\\.&]*)";
    Pattern p = Pattern.compile(urlPattern,Pattern.CASE_INSENSITIVE);
    Matcher m = p.matcher(commentstr);
    int i = 0;
    while (m.find()) {
        commentstr = commentstr.replaceAll(m.group(i),"").trim();
        i++;
    }
    return commentstr;
}
%>


<%

String context="context0";
context=ServletUtilities.getContext(request);

Shepherd myShepherd=new Shepherd(context);
%>

<html>
<head>
<title>LocationID Predictor</title>

</head>


<body>


<%

myShepherd.beginDBTransaction();

int numFixes=0;

try{
	
		String filter="SELECT FROM org.ecocean.Encounter WHERE state == \"approved\" && locationID != null";
		
		
		Query query = myShepherd.getPM().newQuery(filter);
		Collection c = (Collection) (query.execute());
		ArrayList<Encounter> results=new ArrayList<Encounter>(c);
		query.closeAll();
		int numResults=results.size();
		
		String locationIDOptions="";
		List<String> locs=myShepherd.getAllLocationIDs();
		int numLocs=locs.size();
		for(int p=0;p<numLocs;p++){
			locationIDOptions+=(","+locs.get(p));
		}
		locationIDOptions=locationIDOptions.replaceFirst(",", "");
		
		%>
%		
%Encounter.locationID results analyzed <%=numResults %><br>
		<%
		

		StringBuffer sb=new StringBuffer("@RELATION WhaleSharkLocationIDPredictor\n\n@ATTRIBUTE description String\n@ATTRIBUTE class {"+locationIDOptions+"}\n\n@data\n");
		
		for(int i=0;i<numResults;i++){
			
			Encounter enc=results.get(i);
			String myDescription="";
			if(enc.getVerbatimLocality()!=null)myDescription+=enc.getVerbatimLocality();
			if(enc.getComments()!=null){
				myDescription+=(" "+enc.getComments());
			}
			myDescription = myDescription.replaceAll("https?://\\S+\\s?", "");
			
			myDescription=myDescription.replaceAll("[^A-Za-z0-9 ]", "").replace("\n", "").trim();
			
			
			//myDescription=removeUrl(myDescription);
			//myDescription=translateIfNotEnglish(myDescription);
			
			if((!myDescription.equals(""))&&(!enc.getLocationID().trim().equals(""))&&(!enc.getLocationID().trim().equals("None")))sb.append("'"+myDescription+"',"+enc.getLocationID()+"\n");
			
		
		}
		
	%>

	<pre><%=sb.toString() %></pre>
	<%
	


}
catch(Exception e){

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
