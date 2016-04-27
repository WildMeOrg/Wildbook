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

  function startIdentify(ma) {
	if (!ma) return;
	var aid = ma.annotationId;
    //var aid = el.getAttribute('data-id');
    //el.parentElement.innerHTML = '<i>starting identification</i>';
//console.warn('aid=%o, el=%o', aid, el); return;
    jQuery.ajax({
      url: '../ia',
      type: 'POST',
      dataType: 'json',
      contentType: 'application/javascript',
      success: function(d) {
        console.info('identify returned %o', d);
        if (d.taskID) {
		$('#image-enhancer-wrapper-' + ma.id + ' .image-enhancer-overlay-message').html('<p>sending to result page...</p>');
          window.location.href = 'matchResults.jsp?taskId=' + d.taskID;
        } else {
		$('#image-enhancer-wrapper-' + ma.id + ' .image-enhancer-overlay-message').html('<p>error starting identification</p>');
        }
      },
      error: function(x,y,z) {
		$('#image-enhancer-wrapper-' + ma.id + ' .image-enhancer-overlay-message').html('<p>error starting identification</p>');
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




JSONObject iaTasks = new JSONObject();

  if ((anns == null) || (anns.size() < 1)) {
    %> <script>console.log('no annnotations found for encounter <%=encNum %>'); </script> <%
  }
  else {
  	for (Annotation ann: anns) {
      String[] tasks = IBEISIA.findTaskIDsFromObjectID(ann.getId(), imageShepherd);
/*
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
*/

System.out.println(tasks);
	if ((tasks != null) && (tasks.length > 0)) {
		JSONArray t = new JSONArray();
/*  we only want the most recent for now!!
		for (int i = 0 ; i < tasks.length ; i++) {
			t.put(tasks[i]);
		}
*/
		t.put(tasks[tasks.length - 1]);
		iaTasks.put(ann.getId(), t);
	}

      // SKIPPING NON-TRIVIAL ANNOTATIONS FOR NOW! TODO
  		if (!ann.isTrivial()) continue;

  		MediaAsset ma = ann.getMediaAsset();
  		if (ma != null) {
  			JSONObject j = ma.sanitizeJson(request, new JSONObject());
  			if (j != null) {
				j.put("annotationId", ann.getId());
				all.put(j);
			}
  		}
  	}
  	// out.println("var assets = " + all.toString() + ";");
    //System.out.println("All media assets as an array: "+all.toString());

}
	out.println("<script> var iaTasks = " + iaTasks.toString() + ";</script>");

}
catch(Exception e){e.printStackTrace();}
finally{
	imageShepherd.rollbackDBTransaction();
	imageShepherd.commitDBTransaction();
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
<script src='http://<%=CommonConfiguration.getURLLocation(request) %>/javascript/imageUtilities.js'></script>
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



//initializes image enhancement (layers)
jQuery(document).ready(function() {
    var loggedIn = wildbookGlobals.username && (wildbookGlobals.username != "");
    var opt = {
    };

    if (loggedIn) {
        opt.debug = false;
        opt.menu = [
/*
            ['remove this image', function(enh) {
            }],
            ['replace this image', function(enh) {
            }],
*/
		['start new matching scan', function(enh) {
			//var mid = enh.imgEl.context.id.substring(11);
			var mid = enh.imgEl.data('enh-mediaassetid');
console.log('%o ?????', mid);
			imageEnhancer.message(jQuery('#image-enhancer-wrapper-' + mid), '<p>starting matching; please wait...</p>');
			startIdentify(assetById(mid), enh.imgEl);
		}],
        ];

	var ct = 1;
	for (var annId in iaTasks) {
		//we really only care about first tid now (most recent)
		var tid = iaTasks[annId][0];
		opt.menu.push([
			//'- previous scan results ' + ct,
			'- previous scan results',
			function(enh, tid) {
				console.log('enh(%o) tid(%o)', enh, tid);
				wildbook.openInTab('matchResults.jsp?taskId=' + tid);
			},
			tid
		]);
	}

	opt.menu.push(
            [
		function(enh) { return imagePopupInfoMenuItem(enh); },
		function(enh) { imagePopupInfo(enh); }
            ]
	);

/*
        if (true) {
            opt.menu.push(['set image as encounter thumbnail', function(enh) {
            }]);
        }

        opt.init = [
            function(el, enh) {
console.info(' ===========>   %o %o', el, enh);
		imageLayerKeywords(el, enh);
            },
        ];
*/
    }

    imageEnhancer.applyTo('figure img', opt);
});



function imageLayerKeywords(el, opt) {
	var mid = el.context.id.substring(11);
	var ma = assetById(mid);
console.info("############## mid=%s -> %o", mid, ma);
	if (!ma) return;
ma.xeywords = [
	{readableName: "fubar", indexname: "xxx"},
	{readableName: "fubar", indexname: "yyy"},
	{readableName: "fubar", indexname: "zzz"}
	];

	if (!ma.keywords) ma.keywords = [];
	var h = '<div class="image-enhancer-keyword-wrapper">';
	for (var i = 0 ; i < ma.keywords.length ; i++) {
		h += '<div class="image-enhancer-keyword" id="keyword-' + ma.keywords[i].indexname + '">' + ma.keywords[i].readableName + ' <span class="iek-remove" title="remove keyword">X</span></div>';
	}

	h += '<div class="iek-new-wrapper' + (ma.keywords.length ? ' iek-autohide' : '') + '">Add new keyword <div class="iek-new-form">';
h += '(form)';
	h += '</div></div>';

	h += '</div>';
	el.append(h);
}

function imagePopupInfo(obj) {
	if (!obj || !obj.imgEl || !obj.imgEl.context) return;
	var mid = obj.imgEl.context.id.substring(11);
	var ma = assetById(mid);
	if (!ma) return;
	var h = '<div>media asset id: <b>' + mid + '</b><br />';
	if (ma.metadata) {
		for (var n in ma.metadata) {
			h += n + ': <b>' + ma.metadata[n] + '</b><br />';
		}
	}
	h += '</div>';
	imageEnhancer.popup(h);
}

function imagePopupInfoMenuItem(obj) {
//console.log('MENU!!!! ----> %o', obj);
	if (!obj || !obj.imgEl || !obj.imgEl.context) return false;
	var mid = obj.imgEl.context.id.substring(11);
	var ma = assetById(mid);
	if (!ma) return false;
	return 'image info';
}
 

function assetById(mid) {
	if (!assets || (assets.length < 1)) return false;
	for (var i = 0 ; i < assets.length ; i++) {
		if (assets[i].id == mid) return assets[i];
	}
	return false;
}

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
