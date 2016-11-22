<%@ page contentType="text/html; charset=utf-8" language="java"
         import="org.ecocean.servlet.ServletUtilities,
org.ecocean.media.*,
org.ecocean.*,
org.datanucleus.api.rest.orgjson.JSONObject,
org.datanucleus.api.rest.orgjson.JSONArray,org.datanucleus.api.rest.orgjson.JSONException,
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
myShepherd.setAction("individualMediaGallery.jsp");
imageShepherd.beginDBTransaction();
String encNum = request.getParameter("encounterNumber");
String indID = request.getParameter("individualID");


// collect every MediaAsset as JSON into the 'all' array
JSONArray all = new JSONArray();
try {

  MarkedIndividual sharky = imageShepherd.getMarkedIndividual(indID);
  Encounter[] galleryEncs = sharky.getDateSortedEncounters();

  String langCode=ServletUtilities.getLanguageCode(request);
  Properties encprops = new Properties();
  encprops = ShepherdProperties.getProperties("encounter.properties", langCode,context);

  for (Encounter enc : galleryEncs) {

  //Encounter enc = imageShepherd.getEncounter(encNum);
  ArrayList<Annotation> anns = enc.getAnnotations();

  if ((anns == null) || (anns.size() < 1)) {
    %> <script>console.log('no annnotations found for encounter <%=encNum %>'); </script> <%
  }
  else {
  	for (Annotation ann : anns) {
      // SKIPPING NON-TRIVIAL ANNOTATIONS FOR NOW! TODO
  		//if (!ann.isTrivial()) continue;

  		MediaAsset ma = ann.getMediaAsset();
  		if (ma != null) {
  			JSONObject j = ma.sanitizeJson(request, new JSONObject());
  			if (j != null) all.put(j);
  		}
  	}
  	// out.println("var assets = " + all.toString() + ";");

}
}

System.out.println("individualMediaGallery: All media assets as an array: "+all.toString());
%> <script>console.log('all MAs: <%=all.toString() %>'); </script> <%


// IF we have at least 4 images, we need to make sure image 2 has a SMALLER height than image 3, or else image 4 will go under 3, not 2.

if (all.length()>3) {

  try {
    
    int height1 = all.getJSONObject(1).getJSONObject("metadata").getInt("height");
    int height2 = all.getJSONObject(2).getJSONObject("metadata").getInt("height");
    if (height1 > height2) {
      JSONObject temp = all.getJSONObject(1);
      all.put(1,all.getJSONObject(2));
      all.put(2, temp);
      System.out.println("flipped images!");
    }

  }
  catch (JSONException e) {
    e.printStackTrace();
  }
}


}
catch(Exception e){e.printStackTrace();}
finally{
	imageShepherd.rollbackDBTransaction();
	imageShepherd.closeDBTransaction();
}

%>

<!--<h2>Gallery</h2>-->
<div class='row' id='ind-gallery'>
<div class="my-gallery" id="enc-gallery" itemscope itemtype="http://schema.org/ImageGallery"> </div>

<script src='//<%=CommonConfiguration.getURLLocation(request) %>/javascript/imageDisplayTools.js'></script>

<style>
  .my-gallery>figure {
    padding-left: 5px;
    padding-right: 5px;
    margin-bottom: 5px;
  }
</style>


<script>

  // Load each photo into photoswipe: '.my-gallery' above is grabbed by imageDisplayTools.initPhotoSwipeFromDOM,
  // so here we load .my-gallery with all of the MediaAssets --- done with maJsonToFigureElem.
  var assets = <%=all.toString()%>;
  var unseenImages = false;
  for (var index in assets) {
    var elem = assets[index];
    if (index==0) {
      maLib.maJsonToFigureElemColCaption(elem, $('#enc-gallery'), 6);
      first = false;
    } else if (index<5){
      maLib.maJsonToFigureElemColCaption(elem, $('#enc-gallery'), 3);
    } else {
      unseenImages = true;
      break;
    }
  }

  // TODO: fix this link, Bink.
  if (unseenImages) {
    console.log('shalom!');
    // link to individual thumbnail search results
    var thumbGalleryLink = '//<%=CommonConfiguration.getURLLocation(request) %>/individualThumbnailSearchResults.jsp?individualID=<%=indID%>';

    $('#ind-gallery').append('<p style="text-align:right;">Most recent images shown. <a href="'+thumbGalleryLink+'"><em>See all images.</em></a></p>');
  }

  assets.forEach( function(elem) {

  });

</script>
</div>
<jsp:include page="/photoswipe/photoswipeTemplate.jsp" flush="true"/>
