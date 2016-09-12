
<%@ page contentType="text/xml; charset=utf-8" language="java" import="java.util.Properties, 
java.io.FileInputStream, java.io.File, java.io.FileNotFoundException, org.ecocean.*,org.ecocean.servlet.*,javax.jdo.*, java.lang.StringBuffer, java.util.Vector, java.util.Iterator, 
java.lang.NumberFormatException,
org.datanucleus.api.rest.orgjson.JSONObject,
org.datanucleus.api.rest.orgjson.JSONArray,
org.datanucleus.api.rest.orgjson.JSONException,
java.util.List
"%>
<%

//set up persistence
String context="context0";
Shepherd myShepherd=new Shepherd(context);
myShepherd.setAction("getKeywordImages.jsp");

//response object
response.setContentType("application/json");
JSONArray myResultArray = new JSONArray();

//set up return JSON object
JSONObject returnMe = new JSONObject();

String rootWebappPath = getServletContext().getRealPath("/");

myShepherd.beginDBTransaction();




try{

	//no keyword set so just list keywords
	if(request.getParameter("indexName")==null){
		Iterator allSKW=myShepherd.getAllKeywords();
		JSONArray all = new JSONArray();
		while(allSKW.hasNext()){
			Keyword kw=(Keyword)allSKW.next();
			JSONObject jsonKW = new JSONObject();
			jsonKW.put("indexName", kw.getIndexname());
			jsonKW.put("readableName", kw.getReadableName());
			all.put(jsonKW);
		}
		returnMe.put("keywords", all);
		%>
		<%=returnMe.toString(2) %>
		<%
	}
	//list keywords by indexname
	else{
		
		String indexName=request.getParameter("indexName").trim();
		returnMe.put("keyword", indexName);
		Keyword word=myShepherd.getKeyword(indexName);
		List<SinglePhotoVideo> photos=myShepherd.getAllSinglePhotoVideosWithKeyword(word);
		int numPhotos=photos.size();
		JSONArray all = new JSONArray();
		for(int i=0;i<numPhotos;i++){
			SinglePhotoVideo spv=photos.get(i);
			String baseDir = ServletUtilities.dataDir(context, rootWebappPath);
			Encounter imageEnc=myShepherd.getEncounter(spv.getCorrespondingEncounterNumber());
			File thisEncounterDir = new File(imageEnc.dir(baseDir));
			String encUrlDir = "/" + CommonConfiguration.getDataDirectoryName(context) + imageEnc.dir("");

			String path =  "http://www.whaleshark.org"+ encUrlDir + "/" + spv.getFilename();
	        
			JSONObject jsonKW = new JSONObject();
			jsonKW.put("url", path);
			jsonKW.put("correspondingEncounterNumber", spv.getCorrespondingEncounterNumber());
			all.put(jsonKW);
			
		}
		
		returnMe.put("images", all);
		%>
		<%=returnMe.toString(2) %>
		<%
	}	


%>


<%
} 
catch(Exception ex) {

	System.out.println("!!!An error occurred on page. The error was:");
	ex.printStackTrace();



}

finally{
	myShepherd.rollbackDBTransaction();
	myShepherd.closeDBTransaction();
	myShepherd=null;
}
%>


