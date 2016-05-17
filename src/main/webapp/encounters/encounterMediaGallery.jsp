<%@ page contentType="text/html; charset=utf-8" language="java"
         import="org.ecocean.servlet.ServletUtilities,
org.ecocean.media.*,
org.ecocean.*,
org.ecocean.identity.IBEISIA,
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
List<String[]> captionLinks = new ArrayList<String[]>();
try {

  String langCode=ServletUtilities.getLanguageCode(request);
  Properties encprops = new Properties();
  encprops = ShepherdProperties.getProperties("encounter.properties", langCode,context);
  Encounter enc = imageShepherd.getEncounter(encNum);
  ArrayList<Annotation> anns = enc.getAnnotations();
  %>
  <script>
  function startIdentify(el) {
    var aid = el.getAttribute('data-id');
    el.parentElement.innerHTML = '<i>starting identification</i>';
    jQuery.ajax({
      url: '../ia',
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
        identify: aid,
        genus: '<%=enc.getGenus()%>',
        species: '<%=enc.getSpecificEpithet()%>'
      })
    });
  }
  // because we have links within the photoswipe-opening clickable area
  function forceLink(el) {
    var address = el.href;
    if (address) {
      window.location.href = address;
    };
    el.stopPropagation();
  }
  /*
  $(".forceLink").click(function(e) {
    alert('callin!');
    e.stopPropagation();
  });
  */
  //
  </script>
<%




  if ((anns == null) || (anns.size() < 1)) {
    %> <script>console.log('no annnotations found for encounter <%=encNum %>'); </script> <%
  }
  else {
  	for (Annotation ann: anns) {
      String[] tasks = IBEISIA.findTaskIDsFromObjectID(ann.getId(), imageShepherd);
    	if (tasks != null) {
        String[] linkArray = new String[tasks.length+1];
        linkArray[0] = "<a data-id="+ann.getId()+" onClick=\"startIdentify(this)\">Match this image</a>";
    		for (int i = 0 ; i < tasks.length ; i++) {
          String linkAddr = "matchResults.jsp?taskId=" + tasks[i];
          linkArray[i+1] = "<a target=\"_new\" href=\"" + linkAddr + "\" onClick=\"forceLink(this)\">" + (i+1) + ") previous match results</a>";
          %> <script>console.log('added links: <%=linkArray[i] %>'); </script> <%
    		}
        captionLinks.add(linkArray);
    	}
      else {
        captionLinks.add(new String[]{"<a data-id="+ann.getId()+" onClick=\"startIdentify(this)\">Match this image</a>"});
        //out.println("no scan tasks here");
      }

      // SKIPPING NON-TRIVIAL ANNOTATIONS FOR NOW! TODO
  		if (!ann.isTrivial()) continue;

  		MediaAsset ma = ann.getMediaAsset();
  		if (ma != null) {
  			JSONObject j = ma.sanitizeJson(request, new JSONObject());
  			if (j != null) all.put(j);
  		}
  	}
  	// out.println("var assets = " + all.toString() + ";");
    System.out.println("encounterMediaGallery: All media assets as an array: "+all.toString());

}

}
catch(Exception e){e.printStackTrace();}
finally{
	imageShepherd.rollbackDBTransaction();
	imageShepherd.closeDBTransaction();
}

// here we just transform captionLinks into the actual captions we want to pass
JSONArray captions = new JSONArray();
for (int i=0; i<captionLinks.size(); i++) {
  String cappy = "<div class=\"match-tools\">";
  for (String subCaption : captionLinks.get(i)) {
    cappy = cappy+subCaption+"</br>";
  }
  cappy = cappy+ "</div>";
  captions.put(cappy);
}


%>
<style>
	.match-tools {
		padding: 5px 15px;
		background-color: #DDD;
		margin: 4px;
		border-radius: 4px;
    display: inline-block;
    float: right;
    width: 50%;
	}
	.match-tools a {
		cursor: pointer;
		display: block;
	}
</style>

<h2>Gallery</h2>
<div class="my-gallery" id="enc-gallery" itemscope itemtype="http://schema.org/ImageGallery"> </div>
<script src='http://<%=CommonConfiguration.getURLLocation(request) %>/javascript/imageDisplayTools.js'></script>
<script>

  // Load each photo into photoswipe: '.my-gallery' above is grabbed by imageDisplayTools.initPhotoSwipeFromDOM,
  // so here we load .my-gallery with all of the MediaAssets --- done with maJsonToFigureElem.
  var assets = <%=all.toString()%>;
  var captions = <%=captions.toString()%>
  captions.forEach( function(elem) {
    console.log("caption here: "+elem);
  })
  assets.forEach( function(elem, index) {
    maLib.maJsonToFigureElemCaption(elem, $('#enc-gallery'), captions[index]);
  });

</script>
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

<jsp:include page="../photoswipe/photoswipeTemplate.jsp" flush="true"/>
