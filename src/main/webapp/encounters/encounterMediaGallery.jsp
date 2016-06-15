<%@ page contentType="text/html; charset=utf-8" language="java"
         import="org.ecocean.servlet.ServletUtilities,
org.ecocean.media.*,
org.ecocean.*,
org.datanucleus.api.rest.orgjson.JSONObject,
org.datanucleus.api.rest.orgjson.JSONArray,
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
Shepherd imageShepherd = new Shepherd(context);
imageShepherd.beginDBTransaction();
String encNum = request.getParameter("encounterNumber");


// collect every MediaAsset as JSON into the 'all' array
JSONArray all = new JSONArray();
try {

  String langCode=ServletUtilities.getLanguageCode(request);
  Properties encprops = new Properties();
  encprops = ShepherdProperties.getProperties("encounter.properties", langCode,context);
  Encounter enc = imageShepherd.getEncounter(encNum);
  ArrayList<Annotation> anns = enc.getAnnotations();

  if ((anns == null) || (anns.size() < 1)) {
    %> <script>console.log('no annnotations found for encounter <%=encNum %>'); </script> <%
  }
  else {
  	for (Annotation ann : anns) {
      // SKIPPING NON-TRIVIAL ANNOTATIONS FOR NOW! TODO
  		//if (!ann.isTrivial()) continue;  //ok, i lied... we have to let them thru now cuz IA makes non-trivial annotations... way TODO

  		MediaAsset ma = ann.getMediaAsset();
  		if (ma != null) {
  			JSONObject j = ma.sanitizeJson(request, new JSONObject());
  			if (j != null) all.put(j);
  		}
  	}
  	// out.println("var assets = " + all.toString() + ";");
    System.out.println("All media assets as an array: "+all.toString());

}

}
catch(Exception e){e.printStackTrace();}
finally{
	imageShepherd.rollbackDBTransaction();
	imageShepherd.commitDBTransaction();
}

%>

<h2>Gallery</h2>
<div class="my-gallery" id="enc-gallery" itemscope itemtype="http://schema.org/ImageGallery"> </div>
<script src='http://<%=CommonConfiguration.getURLLocation(request) %>/javascript/imageDisplayTools.js'></script>
<script>

  // Load each photo into photoswipe: '.my-gallery' above is grabbed by imageDisplayTools.initPhotoSwipeFromDOM,
  // so here we load .my-gallery with all of the MediaAssets --- done with maJsonToFigureElem.
  var assets = <%=all.toString()%>;
  assets.forEach( function(elem) {
    maLib.maJsonToFigureElemCaption(elem, $('#enc-gallery'));
  });

</script>
<jsp:include page="../photoswipe/photoswipeTemplate.jsp" flush="true"/>
