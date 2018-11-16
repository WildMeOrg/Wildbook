<%@ page contentType="text/plain; charset=utf-8" language="java"
     import="org.ecocean.*,
java.util.List,
java.io.BufferedReader,
java.io.IOException,
java.io.InputStream,
java.io.InputStreamReader,
java.io.File,
org.joda.time.DateTime,
org.json.JSONObject,
org.json.JSONArray,
com.google.api.services.youtube.model.SearchResult,
com.google.api.services.youtube.model.SearchResultSnippet,
org.ecocean.media.*,
org.ecocean.servlet.ServletUtilities,
java.util.ArrayList,
weka.core.Instance,
weka.core.Attribute,
weka.core.DenseInstance, 
org.ecocean.ai.weka.Classify,
weka.core.Instances,
org.ecocean.ai.nmt.azure.*,
org.ecocean.ai.agent.AgentUtil,
org.ecocean.ai.utilities.AIUtilities
"
%>




<%

String context = ServletUtilities.getContext(request);
YouTube.init(context);

JSONObject rtn = new JSONObject("{\"success\": false}");

/*
Shepherd myShepherd = new Shepherd("context0");
myShepherd.beginDBTransaction();
*/

//YouTubeAssetStore yts = YouTubeAssetStore.find(myShepherd);
long sinceMS = System.currentTimeMillis() - (24 * 60 * 60 * 1000);  //i.e. 24 hours ago
try {
	sinceMS = Long.parseLong(request.getParameter("since"));
} catch (Exception ex) {}
rtn.put("since", sinceMS);
rtn.put("sinceDateTime", new DateTime(sinceMS));


//WEKA ML filtering

String rootDir = getServletContext().getRealPath("/");
String dataDir = ServletUtilities.dataDir(context, rootDir);
String fullPathToClassifierFile	= Classify.getClassifierFileFullPath(dataDir);
boolean wekaAvailable = new File(fullPathToClassifierFile).exists();

ArrayList<Attribute> attributeList = new ArrayList<Attribute>(2);

Attribute merged = new Attribute("merged", true);

ArrayList<String> classVal = new ArrayList<String>();
classVal.add("good");
classVal.add("poor");

attributeList.add(merged);
attributeList.add(new Attribute("@@class@@",classVal));

Instances data = null;
Instance weka_instance = null;

if (wekaAvailable) {
    data = new Instances("TestInstances",attributeList,2);
    data.setClassIndex(data.numAttributes()-1);
    weka_instance = new DenseInstance(data.numAttributes());
    data.add(weka_instance);
    weka_instance.setDataset(data);
    //end WEKA prep for ML
}


String keyword = request.getParameter("keyword");
if (keyword == null) {
	rtn.put("error", "must supply keyword=");
} else {
	List<SearchResult> vids;
	try {
		vids = YouTube.searchByKeyword(keyword, sinceMS, context);
	} catch (Exception ex) {
		rtn.put("error", "exception thrown: " + ex.toString());
		out.println(rtn);
		return;
	}
	rtn.put("success", true);
	if ((vids == null) || (vids.size() < 1)) {
		rtn.put("count", 0);
	} else {
		rtn.put("count", vids.size());
		JSONArray varr = new JSONArray();
		for (SearchResult vid : vids) {
			
			//check the video for strings that indicate non-data videos (e.g., video games, documentaries, etc.)
			SearchResultSnippet snip=vid.getSnippet();
			boolean filterMe=false;
			
			//handle title		
			String title="";
			if((snip.getTitle()!=null)&&(!snip.getTitle().trim().equals(""))){
				title=snip.getTitle();
				String titleLang=DetectTranslate.detectLanguage(title);
				if((!titleLang.equals("unk"))&&(!titleLang.equals("en")))title=DetectTranslate.translateToEnglish(title);
			}
			
			//handle description		
			String desc="";
			if((snip.getDescription()!=null)&&(!snip.getDescription().trim().equals(""))){
				desc=snip.getDescription();
				String titleDesc=DetectTranslate.detectLanguage(desc);
				if((!titleDesc.equals("unk"))&&(!titleDesc.equals("en")))title=DetectTranslate.translateToEnglish(desc);
			}
			
			String consolidatedRemarks=title+" "+desc;
			//consolidatedRemarks=consolidatedRemarks.replaceAll(",", " ").replaceAll("\n", " ").replaceAll("'", "").replaceAll("\"", "").replaceAll("’","").replaceAll("′","").toLowerCase().replaceAll("whale shark", "whaleshark");
			consolidatedRemarks=AIUtilities.youtubePredictorPrepareString(consolidatedRemarks);

                        if (wekaAvailable) {
			    weka_instance.setValue(merged, consolidatedRemarks);
			    Double classValue=Classify.classifyWithFilteredClassifier(weka_instance, fullPathToClassifierFile);
			    if (classValue.intValue()==1) filterMe=true;
                        }

                        if (!filterMe) filterMe = AgentUtil.youtubeFilterOld(context, consolidatedRemarks);  //try old method!

			if(!filterMe)varr.put(new JSONObject(vid.toString()));
			
		}
		rtn.put("videos", varr);
	}
}


out.println(rtn);



%>




