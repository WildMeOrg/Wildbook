<%--
  ~ The Shepherd Project - A Mark-Recapture Framework
  ~ Copyright (C) 2014 Jason Holmberg
  ~
  ~ This program is free software; you can redistribute it and/or
  ~ modify it under the terms of the GNU General Public License
  ~ as published by the Free Software Foundation; either version 2
  ~ of the License, or (at your option) any later version.
  ~
  ~ This program is distributed in the hope that it will be useful,
  ~ but WITHOUT ANY WARRANTY; without even the implied warranty of
  ~ MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
  ~ GNU General Public License for more details.
  ~
  ~ You should have received a copy of the GNU General Public License
  ~ along with this program; if not, write to the Free Software
  ~ Foundation, Inc., 51 Franklin Street, Fifth Floor, Boston, MA  02110-1301, USA.
  --%>


<%@ page contentType="application/json; charset=utf-8" language="java"
trimDirectiveWhitespaces="true"
         import="
java.util.zip.*,
java.io.OutputStream,
org.ecocean.*,org.ecocean.social.*,
org.ecocean.servlet.ServletUtilities,java.io.File,
java.io.IOException,
java.util.*, org.ecocean.genetics.*,org.ecocean.security.Collaboration, com.google.gson.Gson
" %>

<%@include file="dataLib.jsp"%>

<%

String encsJson = "";
String blocker = "";
String context="context0";
context=ServletUtilities.getContext(request);
  //handle some cache-related security
  response.setHeader("Cache-Control", "no-cache"); //Forces caches to obtain a new copy of the page from the origin server
  response.setHeader("Cache-Control", "no-store"); //Directs caches not to store the page under any circumstance
  response.setDateHeader("Expires", 0); //Causes the proxy cache to see the page as "stale"
  response.setHeader("Pragma", "no-cache"); //HTTP 1.0 backward compatibility

  //setup data dir
  String rootWebappPath = getServletContext().getRealPath("/");
  File webappsDir = new File(rootWebappPath).getParentFile();
  File shepherdDataDir = new File(webappsDir, CommonConfiguration.getDataDirectoryName(context));
  //if(!shepherdDataDir.exists()){shepherdDataDir.mkdirs();}
  File encountersDir=new File(shepherdDataDir.getAbsolutePath()+"/encounters");
  //if(!encountersDir.exists()){encountersDir.mkdirs();}
  //File thisEncounterDir = new File(encountersDir, number);

//setup our Properties object to hold all properties
  Properties props = new Properties();
  //String langCode = "en";
  String langCode=ServletUtilities.getLanguageCode(request);



  //load our variables for the submit page

 // props.load(getClass().getResourceAsStream("/bundles/" + langCode + "/individuals.properties"));
  props = ShepherdProperties.getProperties("individuals.properties", langCode,context);

	Properties collabProps = new Properties();
 	collabProps=ShepherdProperties.getProperties("collaboration.properties", langCode, context);
  
	
  String markedIndividualTypeCaps = props.getProperty("markedIndividualTypeCaps");
  String nickname = props.getProperty("nickname");
  String nicknamer = props.getProperty("nicknamer");
  String alternateID = props.getProperty("alternateID");
  String sex = props.getProperty("sex");
  String setsex = props.getProperty("setsex");
  String numencounters = props.getProperty("numencounters");
  String encnumber = props.getProperty("number");
  String dataTypes = props.getProperty("dataTypes");
  String date = props.getProperty("date");
  String size = props.getProperty("size");
  String spots = props.getProperty("spots");
  String location = props.getProperty("location");
  String mapping = props.getProperty("mapping");
  String mappingnote = props.getProperty("mappingnote");
  String setAlternateID = props.getProperty("setAlternateID");
  String setNickname = props.getProperty("setNickname");
  String unknown = props.getProperty("unknown");
  String noGPS = props.getProperty("noGPS");
  String update = props.getProperty("update");
  String additionalDataFiles = props.getProperty("additionalDataFiles");
  String delete = props.getProperty("delete");
  String none = props.getProperty("none");
  String addDataFile = props.getProperty("addDataFile");
  String sendFile = props.getProperty("sendFile");
  String researcherComments = props.getProperty("researcherComments");
  String edit = props.getProperty("edit");
  String matchingRecord = props.getProperty("matchingRecord");
  String tryAgain = props.getProperty("tryAgain");
  String addComments = props.getProperty("addComments");
  String record = props.getProperty("record");
  String getRecord = props.getProperty("getRecord");
  String allEncounters = props.getProperty("allEncounters");
  String allIndividuals = props.getProperty("allIndividuals");

  String name = request.getParameter("number").trim();
  Shepherd myShepherd = new Shepherd(context);


	List<Collaboration> collabs = Collaboration.collaborationsForCurrentUser(request);


  myShepherd.beginDBTransaction();
  try {
    if (myShepherd.isMarkedIndividual(name)) {


      MarkedIndividual sharky = myShepherd.getMarkedIndividual(name);
      boolean isOwner = ServletUtilities.isUserAuthorizedForIndividual(sharky, request);

/*
String altID="";
if(sharky.getAlternateID()!=null){
	altID=sharky.getAlternateID();
}

*/
  //f (isOwner && CommonConfiguration.isCatalogEditable(context))


    Encounter[] dateSortedEncs = sharky.getDateSortedEncounters();

			ArrayList<HashMap> myEncs = new ArrayList<HashMap>();

    int total = dateSortedEncs.length;
    for (int i = 0; i < total; i++) {
	HashMap henc = new HashMap();
      Encounter enc = dateSortedEncs[i];
      
				boolean visible = true; //enc.canUserAccess(request);  ///TODO technically we dont need this encounter-level locking!!!
        Vector encImages = enc.getAdditionalImageNames();
        String imgName = "";
        
							//String encSubdir = thisEnc.subdir();
          imgName = "/"+CommonConfiguration.getDataDirectoryName(context)+"/encounters/" + enc.subdir() + "/thumb.jpg";

	henc.put("visible", visible);
        henc.put("thumbUrl", imgName);
	henc.put("date", enc.getDate());
    	henc.put("location", enc.getLocation());
	if ((enc.getMedia()!=null) && (enc.getMedia().size()>0)) henc.put("hasImages", true);
   	if ((enc.getTissueSamples()!=null) && (enc.getTissueSamples().size()>0)) henc.put("hasTissueSamples", true);
   	if (enc.hasMeasurements()) henc.put("hasMeasurements", true);
	henc.put("catalogNumber", enc.getEncounterNumber());
 	henc.put("alternateID", enc.getAlternateID());
	henc.put("sex", enc.getSex());

	ArrayList<String> occ = new ArrayList<String>();
    if(myShepherd.getOccurrenceForEncounter(enc.getCatalogNumber())!=null){
    	Occurrence thisOccur=myShepherd.getOccurrenceForEncounter(enc.getCatalogNumber());
    	ArrayList<String> otherOccurs=thisOccur.getMarkedIndividualNamesForThisOccurrence();
    	if(otherOccurs!=null){
    		int numOtherOccurs=otherOccurs.size();
    		for(int j=0;j<numOtherOccurs;j++){
    			String thisName=otherOccurs.get(j);
    			if(!thisName.equals(sharky.getIndividualID())) occ.add(thisName);
    		}
    	}
    }

	henc.put("occurrences", occ);

	henc.put("behavior", enc.getBehavior());

System.out.println(henc);
	myEncs.add(henc);

    } //end for

			sendAsJson(response, request, myEncs);

	}
} catch(Exception e){
}
  finally{
	  myShepherd.rollbackDBTransaction();
	  myShepherd.closeDBTransaction();
  }

%>
