
<%@ page contentType="text/xml; charset=utf-8" language="java" import="java.util.Properties, 
java.io.FileInputStream, java.io.File, java.io.FileNotFoundException, org.ecocean.*,org.ecocean.servlet.*,javax.jdo.*, java.lang.StringBuffer, java.util.Vector, java.util.Iterator, 
java.lang.NumberFormatException,
org.datanucleus.api.rest.orgjson.JSONObject,
org.datanucleus.api.rest.orgjson.JSONArray,
org.datanucleus.api.rest.orgjson.JSONException,
org.ecocean.media.MediaAsset,
java.util.*
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

int maxSize=9999999;
if(request.getParameter("maxSize")!=null){
	try{
		maxSize=(new Integer(request.getParameter("maxSize"))).intValue();
	}
	catch(Exception e){}
}

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
		//Keyword word=myShepherd.getKeyword(indexName);
		
		String filter="SELECT FROM org.ecocean.media.MediaAsset WHERE keywords.contains(word0) && word0.indexname == \""+indexName+"\" VARIABLES org.ecocean.Keyword word0";
		
		if(indexName.equals("nofilter")){
			filter="SELECT FROM org.ecocean.media.MediaAsset WHERE parentId == null";
			
		}
		
		Query query=myShepherd.getPM().newQuery(filter);
		Collection c=(Collection)query.execute();
		List<MediaAsset> photos=new ArrayList<MediaAsset>(c);
		
		
		//List<SinglePhotoVideo> photos=myShepherd.getAllSinglePhotoVideosWithKeyword(word);
		int numPhotos=photos.size();
		JSONArray all = new JSONArray();
		for(int i=0;((i<numPhotos)&&(i<maxSize));i++){
			MediaAsset spv=photos.get(i);
			//String baseDir = ServletUtilities.dataDir(context, rootWebappPath);
			String encNum="";
			String individualID="";
			
			//try to find the corresponding encounter number
			String encFilter="SELECT FROM org.ecocean.Encounter WHERE catalogNumber != null && annotations.contains(photo0) && photo0.features.contains(feat0) && feat0.asset.uuid == \""+spv.getUUID()+"\" VARIABLES org.ecocean.Annotation photo0;org.ecocean.media.Feature feat0";
			Query encQuery=myShepherd.getPM().newQuery(encFilter);
			Collection d=(Collection)encQuery.execute();
					if((d!=null)&&(d.size()>0)){
						ArrayList<Encounter> encs=new ArrayList<Encounter>(d);
						Encounter enc=encs.get(0);
						encNum=enc.getCatalogNumber();
						if(enc.getIndividualID()!=null){
							individualID=enc.getIndividualID();
						}
						
					}
			encQuery.closeAll();
			
			//String path =  "http://www.whaleshark.org"+ encUrlDir + "/" + spv.getFilename();
	        String path = spv.webURL().toString();
			
			JSONObject jsonKW = new JSONObject();
			jsonKW.put("url", path);
			jsonKW.put("uuid", spv.getUUID());
			jsonKW.put("correspondingEncounterNumber", encNum);
			if(!individualID.equals("")){
				jsonKW.put("individualID", individualID);	
			}
			
			JSONArray keywords = new JSONArray();
			ArrayList<Keyword> myWords=spv.getKeywords();
			int numWords=myWords.size();
			for(int f=0;f<numWords;f++){
				Keyword thisWord=myWords.get(f);
				keywords.put(thisWord.getIndexname());
			}
			jsonKW.put("keywords", keywords);
			all.put(jsonKW);
			
		}
		
		returnMe.put("images", all);
		%>
		<%=returnMe.toString(2) %>
		<%
		query.closeAll();
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


