
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

response.setContentType("text/csv");

Shepherd myShepherd=new Shepherd(context);
%>



<%

myShepherd.beginDBTransaction();

int numFixes=0;

try{
	
		String filter="SELECT FROM org.ecocean.Encounter WHERE (state == \"approved\" || state == \"unidentifiable\")";
		
		
		Query query = myShepherd.getPM().newQuery(filter);
		Collection c = (Collection) (query.execute());
		ArrayList<Encounter> results=new ArrayList<Encounter>(c);
		query.closeAll();
		int numResults=results.size();
		
		if(request.getParameter("results")!=null){
			try{
				int myVal=(new Integer(request.getParameter("results").trim()).intValue());
				if(myVal<numResults)numResults=myVal;
			}
			catch(Exception e){e.printStackTrace();}
		}
		
		String locationIDOptions="";
		List<String> locs=myShepherd.getAllLocationIDs();
		locs.add("null");
		int numLocs=locs.size();
		for(int p=0;p<numLocs;p++){
			locationIDOptions+=(","+locs.get(p));
		}
		locationIDOptions=locationIDOptions.replaceFirst(",", "");
		
		

		StringBuffer sb=new StringBuffer("@RELATION WhaleSharkLocationIDPredictor\n\n@ATTRIBUTE encounter String\n@ATTRIBUTE video String\n@ATTRIBUTE title String\n@ATTRIBUTE tags String\n@ATTRIBUTE description String\n@ATTRIBUTE location String\n@ATTRIBUTE class {"+locationIDOptions+"}\n\n@data\n");
		
		for(int i=0;i<numResults;i++){
			
			Encounter enc=results.get(i);
			String myDescription="";
			String title="";
			String tags ="";
			String videoURL="";
			//if(enc.getVerbatimLocality()!=null)myDescription+=enc.getVerbatimLocality();
			if(enc.getComments()!=null){
				
				String comments=enc.getComments();
				Pattern videoPattern = Pattern.compile("<p>Auto-sourced(.+?)</p>");
				Matcher m = videoPattern.matcher(comments);
				while(m.find()){
					videoURL = m.group(0).replaceAll("Auto-sourced from YouTube Parent Video: ", "").trim();
					Pattern p = Pattern.compile("href=\"(.*?)\"");
					Matcher m2 = p.matcher(videoURL);
					
					if (m2.find()) {
					    videoURL = m2.group(0).replaceAll("href=\"", "").replaceAll("\"",""); // this variable should contain the link URL
					}
				}

				
				myDescription+=enc.getComments().replaceAll("<p>Auto-sourced(.+?)</p>","");
				
				//now let's get the title
				Pattern titlePattern = Pattern.compile("<p>From YouTube video: (.+?)</p>");
				Matcher m3 = titlePattern.matcher(myDescription);
				while(m3.find()){
					title = m3.group(0).replaceAll("<p>","").replaceAll("</p>","").replaceAll("<i>","").replaceAll("</i>","").replaceAll("[^A-Za-z0-9 ]", "").replaceAll("From YouTube video","").trim();
				}
				myDescription=myDescription.replaceAll("<p>From YouTube video: (.+?)</p>","").trim();
				
				
				//now let's get tags
				Pattern tagsPattern = Pattern.compile("<p><b>tags:</b> \\[(.+?)\\]</p>");
				Matcher m4 = tagsPattern.matcher(myDescription);
				while(m4.find()){
					tags = m4.group(0).replaceAll("<p>","").replaceAll("</p>","").replaceAll("<b>","").replaceAll("</b>","").replaceAll("[^A-Za-z0-9 ]", "").replaceAll("tags","").trim();
				}
				myDescription=myDescription.replaceAll("<p><b>tags:</b> \\[(.+?)\\]</p>","").replaceAll("<p><b>tags:</b> \\[]</p>","").replaceAll("<p>"," ").replaceAll("</p>", "").replaceAll("<br>"," ").trim();
				
				
				//myDescription = myDescription.replaceAll("https?://\\S+\\s?", "");
				//myDescription=myDescription.replaceAll("[^A-Za-z0-9 ]", "").replace("\n", "").trim();
			}
			
			myDescription=myDescription.replaceAll("https?://\\S+\\s?", "").replaceAll("[^A-Za-z0-9 ]", "").replace("\n", "");

			
			String myLocation="";
			if(enc.getVerbatimLocality()!=null){
				myLocation+=enc.getVerbatimLocality().replaceAll("https?://\\S+\\s?", "").replaceAll("[^A-Za-z0-9 ]", "").replace("\n", "").trim();
			}
			
			String locationID="null";
			if((enc.getLocationID()!=null)&&(!enc.getLocationID().trim().equals(""))&&(!enc.getLocationID().trim().toLowerCase().equals("none"))){locationID=enc.getLocationID();}

			
			sb.append(enc.getCatalogNumber()+","+videoURL+",'"+title+"','"+tags+"',"+"'"+myDescription+"',"+"'"+myLocation+"',"+locationID+"\n");
			
		
		}
		
	%>

	<%=sb.toString() %>
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

