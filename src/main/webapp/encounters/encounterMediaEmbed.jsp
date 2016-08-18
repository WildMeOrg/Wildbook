<%@ page contentType="text/html; charset=utf-8" language="java"
         import="org.ecocean.servlet.ServletUtilities,
org.ecocean.media.*,
org.ecocean.*,
org.ecocean.identity.IBEISIA,
org.ecocean.servlet.ServletUtilities,org.ecocean.Util,org.ecocean.Measurement, org.ecocean.Util.*, org.ecocean.genetics.*, org.ecocean.tag.*, org.datanucleus.api.rest.orgjson.JSONObject, org.datanucleus.api.rest.orgjson.JSONArray, java.awt.Dimension, javax.jdo.Extent, javax.jdo.Query, java.io.File, java.io.FileInputStream,java.text.DecimalFormat,
java.util.*" %>
<%@ taglib uri="http://www.sunwesttek.com/di" prefix="di" %>
<%--
  ~ The Shepherd Project - A Mark-Recapture Framework
  ~ Copyright (C) 2011 Jason Holmberg
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

<%
String context="context0";
context=ServletUtilities.getContext(request);


//get the encounter number
String imageEncNum = request.getParameter("encounterNumber");

//set up the JDO pieces and Shepherd
Shepherd imageShepherd = new Shepherd(context);
imageShepherd.beginDBTransaction();

try{

//Extent allKeywords = imageShepherd.getPM().getExtent(Keyword.class, true);
//Query kwImagesQuery = imageShepherd.getPM().newQuery(allKeywords);
/*
boolean haveRendered = false;

//let's set up references to our file system components
String rootWebappPath = getServletContext().getRealPath("/");
File webappsDir = new File(rootWebappPath).getParentFile();
File shepherdDataDir = new File(webappsDir, CommonConfiguration.getDataDirectoryName(context));
File encountersDir=new File(shepherdDataDir.getAbsolutePath()+"/encounters");
*/


//handle translation
//String langCode = "en";
String langCode=ServletUtilities.getLanguageCode(request);


//let's load encounters.properties
Properties encprops = new Properties();
//encprops.load(getClass().getResourceAsStream("/bundles/" + langCode + "/encounter.properties"));
encprops=ShepherdProperties.getProperties("encounter.properties", langCode,context);


//String baseDir = ServletUtilities.dataDir(context, rootWebappPath);
Encounter imageEnc = imageShepherd.getEncounter(imageEncNum);
//File thisEncounterDir = new File(imageEnc.dir(baseDir));
//String encUrlDir = "/" + CommonConfiguration.getDataDirectoryName(context) + imageEnc.dir("");


%>

<script>
function startIdentify(el) {
	var aid = el.getAttribute('data-id');
	el.parentElement.innerHTML = '<i>starting identification</i>';
	jQuery.ajax({
		url: '/ia',
		type: 'POST',
		dataType: 'json',
		contentType: 'application/javascript',
		success: function(d) {
			console.info('identify returned %o', d);
			if (d.taskID) {
				window.location.href = 'matchResults.jsp?taskId=' + d.taskID;
			} else {
				alert('error starting identification');
			}
		},
		error: function(x,y,z) {
			alert('error starting identification');
			console.warn('%o %o %o', x, y, z);
		},
		data: JSON.stringify({
			identify: { annotationIds: [ aid ] }
		})
	});
}

</script>

<div id="media-div">
<%


ArrayList<Annotation> anns = imageEnc.getAnnotations();

if ((anns == null) || (anns.size() < 1)) {

%>
no media
<%

} else {

  %>
  <p>Here is the beginning of encounterMediaEmbed!</p>
  <%




	for (Annotation ann : anns) {
///SKIPPING NON-TRIVIAL ANNOTATIONS FOR NOW!   TODO
		if (!ann.isTrivial()) continue;

%>
	<p style="display: none"><a href="encounterSpotTool.jsp?imageID=<%=ann.getMediaAsset().getId()%><%=isDorsalFin %>"><%=encprops.getProperty("matchPattern") %></a></p>

<style>
	#match-tools {
		padding: 5px 15px;
		display: inline-block;
		background-color: #DDD;
		margin: 4px;
		border-radius: 4px;
	}
	#match-tools a {
		cursor: pointer;
		display: block;
	}
</style>
	<div id="match-tools">
		<div><a data-id="<%=ann.getId()%>" onClick="startIdentify(this)">Match this image</a></div>
<%
	String[] tasks = IBEISIA.findTaskIDsFromObjectID(ann.getId(), imageShepherd);
	if ((tasks != null) && (tasks.length > 0)) {
		for (int i = 0 ; i < tasks.length ; i++) {
			out.println("<a target=\"_new\" href=\"matchResults.jsp?taskId=" + tasks[i] + "\">" + (i+1) + ") previous match results</a>");
		}
	}
%>
	</div>
<%
//hacky menu, for now.  TODO break this out as part of toHtmlElement so it is part of image suite
		if (CommonConfiguration.useSpotPatternRecognition(context)) {
			String isDorsalFin="";
			String genusSpecies="";
			if ((imageEnc.getGenus()!=null) && (imageEnc.getSpecificEpithet()!=null)) genusSpecies=imageEnc.getGenus()+imageEnc.getSpecificEpithet();
			if((genusSpecies.equals("Physetermacrocephalus"))||(genusSpecies.equals("Megapteranovaeangliae"))){
				isDorsalFin="&isDorsalFin=false";
			}
			else if(genusSpecies.equals("Tursiopstruncatus")){
				isDorsalFin="&isDorsalFin=true";
			}
      MediaAsset me = ann.getMediaAsset();
      int meId = me.getId();
      JSONObject j = me.sanitizeJson(request, new JSONObject());
      System.out.println("JSON string: "+j.toString());

      %>
    	<p>< Media Asset Property Dump:
        <ul>
          <li> ID: <%=me.getId()%> </li>
          <li> UUID: <%=me.getUUID()%></li>
          <li> parametersAsString: <%=me.getParametersAsString()%></li>
          <li> localPath: <%=me.localPath()%></li>
          <li> width: <%=me.getWidth()%></li>
          <li> height: <%=me.getHeight()%></li>
          <li> JSON: <%=j%></li>
        </ul>
       <a href="encounterSpotTool.jsp?imageID=<%=me.getId()%><%=isDorsalFin %>"></br><%=encprops.getProperty("matchPattern") %></a></p></br></br></br>
      <%
		}

		out.println(ann.toHtmlElement(request, imageShepherd));
	}

}

}
catch(Exception e){e.printStackTrace();}
finally{
	imageShepherd.rollbackDBTransaction();
	imageShepherd.commitDBTransaction();
}

%>
</div>
