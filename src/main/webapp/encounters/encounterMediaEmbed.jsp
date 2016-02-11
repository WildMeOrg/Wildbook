<%@ page contentType="text/html; charset=utf-8" language="java"
         import="org.ecocean.servlet.ServletUtilities,
org.ecocean.media.*,
org.ecocean.*,
org.ecocean.servlet.ServletUtilities,org.ecocean.Util,org.ecocean.Measurement, org.ecocean.Util.*, org.ecocean.genetics.*, org.ecocean.tag.*, java.awt.Dimension, javax.jdo.Extent, javax.jdo.Query, java.io.File, java.io.FileInputStream,java.text.DecimalFormat,
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
Extent allKeywords = imageShepherd.getPM().getExtent(Keyword.class, true);
Query kwImagesQuery = imageShepherd.getPM().newQuery(allKeywords);
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
<div id="media-div">
<%


ArrayList<Annotation> anns = imageEnc.getAnnotations();

if ((anns == null) || (anns.size() < 1)) {

%>
no media
<%

} else {



	for (Annotation ann : anns) {
///SKIPPING NON-TRIVIAL ANNOTATIONS FOR NOW!   TODO
		if (!ann.isTrivial()) continue;

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
%>
	<p><a href="encounterSpotTool.jsp?imageID=<%=ann.getMediaAsset().getId()%><%=isDorsalFin %>"><%=encprops.getProperty("matchPattern") %></a></p>

<%
		}

		out.println(ann.toHtmlElement(request, imageShepherd));
	}

}


%>
</div>
