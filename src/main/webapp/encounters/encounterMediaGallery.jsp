<%@ page contentType="text/html; charset=utf-8" language="java"
         import="org.ecocean.servlet.ServletUtilities,
org.ecocean.media.*,
org.ecocean.*,
org.ecocean.identity.IBEISIA,
org.datanucleus.api.rest.orgjson.JSONObject,
org.datanucleus.api.rest.orgjson.JSONArray,
org.ecocean.servlet.ServletUtilities,org.ecocean.Util,org.ecocean.Measurement, org.ecocean.Util.*, org.ecocean.genetics.*, org.ecocean.tag.*, java.awt.Dimension, javax.jdo.Extent, javax.jdo.Query, java.io.File, java.io.FileInputStream,java.text.DecimalFormat,
java.util.*" %>

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
imageShepherd.setAction("encounterMediaGallery.jsp");
String langCode=ServletUtilities.getLanguageCode(request);
Properties encprops = ShepherdProperties.getProperties("encounter.properties", langCode,context);
String encNum="";
if(request.getParameter("encounterNumber")!=null){
	encNum=request.getParameter("encounterNumber");
}

boolean isGrid = (request.getParameter("grid")!=null);

imageShepherd.beginDBTransaction();

//String encNum = request.getParameter("encounterNumber");
String queryString=request.getParameter("queryString");
Query query=imageShepherd.getPM().newQuery(queryString);

//set ordering
if(request.getParameter("order")!=null){
	query.setOrdering(request.getParameter("order"));
}
else{
	query.setOrdering("dwcDateAddedLong descending");
}

//try to set range if available
if((request.getParameter("rangeStart")!=null)&&(request.getParameter("rangeEnd"))!=null){
	try{
		int startRange=(new Integer(request.getParameter("rangeStart"))).intValue();
		int endRange=(new Integer(request.getParameter("rangeEnd"))).intValue();
		query.setRange(startRange, endRange);
	}
	catch(Exception e){

		System.out.println("I tried to set a query range in encounterMediaGallery.jsp but failed due to the following exception.");
		e.printStackTrace();
	}
}




// collect every MediaAsset as JSON into the 'all' array
JSONArray all = new JSONArray();
List<String[]> captionLinks = new ArrayList<String[]>();
try {

	//we can have *more than one* encounter here, e.g. when used in thumbnailSearchResults.jsp !!
	Collection c = (Collection) (query.execute());
	ArrayList<Encounter> encs=new ArrayList<Encounter>(c);
  	int numEncs=encs.size();

  %><script>

function isGenusSpeciesSet(asset) {
	return (asset && asset.species);
}

var identTasks = [];
function startIdentify(ma) {
	//TODO this is tailored for flukebook basically.  see: Great Future Where Multiple IA Plugins Are Seemlessly Supported
	_identAjax(ma);  //default; pattern-match
	_identAjax(ma, { OC_WDTW: true });  //will do trailing edge match
}

function _identAjax(ma, opt) {
	if (!ma) return _identCallback({ success: false, error: '_identAjax called with no MediaAsset' });
	var aid = ma.annotationId;
	if (!aid) return _identCallback({ success: false, error: '_identAjax called with no annotationId on asset', asset: ma });
	var jdata = { identify: { annotationIds: [ aid ] }, enqueue: true };
	//var jdata = { identify: { annotationIds: [ aid ], limitTargetSize: 10 } };  //debugging (small set to compare against)
	if (opt) jdata.identify.opt = opt;
	jQuery.ajax({
		url: '../ia',
		type: 'POST',
		dataType: 'json',
		contentType: 'application/javascript',
		success: function(d) { _identCallback(d); },
		error: function(x,y,z) {
			console.warn('_identAjax error on %o: %o %o %o', ma, x, y, z);
			_identCallback({ success: false, error: 'error ' + x});
		},
		data: JSON.stringify(jdata)
	});
}

function _identCallback(res) {
console.log("_identCallback got %o", res);
	identTasks.push(res);
	if (identTasks.length > 1) {
console.info('completed _identAjax calls with %o', identTasks);
		var ids = '';
		for (var i = 0 ; i < identTasks.length ; i++) {
			if (identTasks[i].success) ids += '&taskId=' + identTasks[i].taskId;
		}
		if (ids) window.location.href = 'matchResultsMulti.jsp?' + ids.substring(1);
	}
}


function forceLink(el) {
	var address = el.href;
	if (address) window.location.href = address;
	el.stopPropagation();
}

/*
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
		        identify: { annotationIds: [ aid ] }
		      })
		    });
		  }
*/

  //console.log("numEncs = <%=numEncs%>");
  </script>
  <%
  for(int f=0;f<numEncs;f++){
		  Encounter enc = encs.get(f);
		  ArrayList<Annotation> anns = enc.getAnnotations();
		JSONObject iaTasks = new JSONObject();

		  if ((anns == null) || (anns.size() < 1)) {
		    %> <script>console.log('no annnotations found for encounter <%=encNum %>'); </script> <%
		  }
		  else {
		  	for (Annotation ann: anns) {
		      //String[] tasks = IBEISIA.findTaskIDsFromObjectID(ann.getId(), imageShepherd);
		      MediaAsset ma = ann.getMediaAsset();
		      String filename = ma.getFilename();
		      
		      String individualID="";
		      if(enc.getIndividualID()!=null){
		    	  individualID=encprops.getProperty("individualID")+"&nbsp;<a target=\"_blank\" style=\"color: white;\" href=\"../individuals.jsp?number="+enc.getIndividualID()+"\">"+enc.getIndividualID()+"</a><br>";
		      }
		      
		      //Start caption render JSP side
		      String[] capos=new String[1];
		      capos[0]="<p style=\"color: white;\"><em>"+filename+"</em><br>";
		      capos[0]+=individualID;
		      
		      capos[0]+=encprops.getProperty("encounter")+"&nbsp;<a target=\"_blank\" style=\"color: white;\" href=\"encounter.jsp?number="+enc.getCatalogNumber()+"\">"+enc.getCatalogNumber()+"</a><br>";
		      capos[0]+=encprops.getProperty("date")+" "+enc.getDate()+"<br>";
		      
		      capos[0]+=encprops.getProperty("location")+" "+enc.getLocation()+"<br>"+encprops.getProperty("locationID")+" "+enc.getLocationID()+"<br>"+encprops.getProperty("paredMediaAssetID")+" "+ma.getId()+"</p>";
		      captionLinks.add(capos);
		      //end caption render JSP side
		      
		      // SKIPPING NON-TRIVIAL ANNOTATIONS FOR NOW! TODO
		  		//if (!ann.isTrivial()) continue;  ///or not?

		  		
		  		if (ma != null) {
		  			JSONObject j = ma.sanitizeJson(request, new JSONObject("{\"_skipChildren\": true}"));
		  			if (j != null) {
						j.put("annotationId", ann.getId());
						if (ma.hasLabel("_frame") && (ma.getParentId() != null)) {
							if ((ann.getFeatures() == null) || (ann.getFeatures().size() < 1)) continue;
							//TODO here we skip unity feature annots.  BETTER would be to look at detectionStatus and feature type etc!
							//   also: prob should check *what* is detected. :) somewhere....
							if (ann.getFeatures().get(0).isUnity()) continue;  //assume only 1 feature !!
System.out.println("\n\n==== got detected frame! " + ma + " -> " + ann.getFeatures().get(0) + " => " + ann.getFeatures().get(0).getParametersAsString());
							j.put("extractFPS", ma.getParameters().optDouble("extractFPS",0));
							j.put("extractOffset", ma.getParameters().optDouble("extractOffset",0));
							MediaAsset p = MediaAssetFactory.load(ma.getParentId(), imageShepherd);
							if (p != null) {
		  						////j.put("videoParent", p.sanitizeJson(request, new JSONObject("{\"_skipChildren\": true}")));
								if (p.getParentId() != null) {
									MediaAsset sourceMA = MediaAssetFactory.load(p.getParentId(), imageShepherd);
									if (sourceMA != null) {
										JSONObject sj = sourceMA.sanitizeJson(request, new JSONObject("{\"_skipChildren\": true}"));
										if (sourceMA.getMetadata() != null) sj.put("metadata", Util.toggleJSONObject(sourceMA.getMetadata().getData()));
										j.put("sourceAsset", sj);
									}
								}
							}
						} else if (ma.isMimeTypeMajor("video")) {
							//note: this violates safeUrl / etc... use with caution in your branch?
							j.put("url", ma.webURL().toString());
						}
						all.put(j);
					}
		  		}
		  	} //end loop on each annotation
		  	
		  	
		  	// out.println("var assets = " + all.toString() + ";");
		    //System.out.println("All media assets as an array: "+all.toString());

		}
			out.println("<script> var iaTasks = " + iaTasks.toString() + ";</script>");
	}
}
catch(Exception e){e.printStackTrace();}
finally{
	query.closeAll();
	imageShepherd.rollbackDBTransaction();
	imageShepherd.closeDBTransaction();
}


// here we just transform captionLinks into the actual captions we want to pass
JSONArray captions = new JSONArray();
for (int i=0; i<captionLinks.size(); i++) {
  //String cappy = "<div class=\"match-tools\">";
  String cappy = "<div>";
  for (String subCaption : captionLinks.get(i)) {
    cappy = cappy+subCaption+"</br>";
  }
  cappy = cappy+ "</div>";
  captions.put(cappy);
}


%>


<style>
.image-enhancer-wrapper {
	cursor: -webkit-zoom-in;
	cursor: -moz-zoom-in;
}

.caption-youtube {
    padding: 1px 3px;
    background-color: rgba(255,200,200,0.5);
    display: inline-block;
    border-radius: 3px;
    margin: 5px;
	position: absolute;
	right: 5px;
    font-size: 0.8em;
    cursor: pointer !important;
}

.image-enhancer-wrapper div {
	cursor: auto;
}

.image-enhancer-feature-zoom {
    width: 100%;
    height: 100%;
    position: absolute;
    top: 0;
    left: -103%;
    outline: solid rgba(255,255,255,0.8) 8px;
    overflow: hidden;
    display: none;
}
.image-enhancer-wrapper:hover .image-enhancer-feature-zoom {
    xdisplay: block;
}

.image-enhancer-feature-wrapper {
    width: 100%;
    height: 100%;
    position: relative;
    overflow: hidden;
}

.image-enhancer-feature {
    position: absolute;
    outline: dotted rgba(255,255,0,0.5) 1px;
    cursor: pointer !important;
}
.image-enhancer-feature-focused {
    outline: dashed rgba(50,250,50,0.7) 4px;
}

.image-enhancer-feature-aoi {
    border: solid rgba(0,20,255,0.6) 3px;
}

.image-enhancer-wrapper:hover .image-enhancer-feature {
    background-color: rgba(255,255,255,0.05);
    box-shadow: 0 0 0 1px rgba(0,0,0,0.6);
}
.image-enhancer-wrapper:hover .image-enhancer-feature-focused {
    background-color: rgba(255,255,10,0.3);
    box-shadow: 0 0 0 2px rgba(0,0,0,0.6);
}


.image-enhancer-feature:hover {
    z-index: 30;
    outline: solid black 2px;
    background-color: rgba(120,255,0,0.3) !important;
}

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
  input[type="file"] {
    display:inline;
  }

</style>
<%
if(request.getParameter("encounterNumber")!=null){
%>
	<h2><%=encprops.getProperty("gallery") %></h2>
<%
}
%>

<div class="my-gallery" id="enc-gallery" itemscope itemtype="http://schema.org/ImageGallery"> </div>
<script src='//<%=CommonConfiguration.getURLLocation(request) %>/javascript/imageDisplayTools.js'></script>



<script>

  // Load each photo into photoswipe: '.my-gallery' above is grabbed by imageDisplayTools.initPhotoSwipeFromDOM,
  // so here we load .my-gallery with all of the MediaAssets --- done with maJsonToFigureElem.
  var assets = <%=all.toString()%>;
  var captions = <%=captions.toString()%>
  captions.forEach( function(elem) {
    console.log("caption here: "+elem);
  })

  //
  var removeAsset = function(maId) {
    if (confirm("Are you sure you want to remove this image from the encounter? The image will not be deleted from the database, and this action is reversible.")) {
      $.ajax({
        url: '../MediaAssetAttach',
        type: 'POST',
        dataType: 'json',
        contentType: "application/json",
        data: JSON.stringify({"detach":"true","EncounterID":"<%=encNum%>","MediaAssetID":maId}),
        success: function(d) {
          console.info("I detached MediaAsset "+maId+" from encounter <%=encNum%>");
          $('#image-enhancer-wrapper-' + maId).closest('figure').remove();  //TODO fix this to find it with annotation id now!
/*
          $('#remove'+maId).prev('figure').remove();
          $('#remove'+maId).after('<p style=\"text-align:center;\"><i>Image removed from encounter.</i></p>');
          $('#remove'+maId).remove();
*/
        },
        error: function(x,y,z) {
          console.warn("failed to MediaAssetDetach");
          console.warn('%o %o %o', x, y, z);
        }
      });
    }
  }


  assets.forEach( function(elem, index) {
    var assetId = elem['id'];
    console.log("EMG asset "+index+" id: "+assetId);
    if (<%=isGrid%>) {
      maLib.maJsonToFigureElemCaptionGrid(elem, $('#enc-gallery'), captions[index], maLib.testCaptionFunction)
    } else {
      maLib.maJsonToFigureElemCaption(elem, $('#enc-gallery'), captions[index]);
    }

/*   now added to image hamburger menu
    var removeAssetLink = "<p id=\"remove"+assetId+"\" style=\"text-align:right\"> <a title=\"Remove above image from encounter\" href=\"\" onclick=\"removeAsset("+assetId+")\">Remove image from encounter</a></p>";

    $('#enc-gallery').append(removeAssetLink);
*/
  });



// h/t https://stackoverflow.com/a/12692647
$(window).resize(function() {
	if (this.resizeTO) clearTimeout(this.resizeTO);
  this.resizeTO = setTimeout(function() {
      $(this).trigger('resizeEnd');
  }, 500);
});

$(window).on('resizeEnd', function(ev) {
	checkImageEnhancerResize();
});

//initializes image enhancement (layers)
jQuery(document).ready(function() {
	doImageEnhancer('figure img');
});

function doImageEnhancer(sel) {
    var loggedIn = wildbookGlobals.username && (wildbookGlobals.username != "");
    var opt = {
    };

    if (loggedIn) {
        opt.debug = false;
        opt.menu = [
           <%
           if(!encNum.equals("")){
        	%>
            ['remove this image', function(enh) {
		removeAsset(enh.imgEl.prop('id').substring(11));
            }],
            <%
    		}
            %>

/*
            ['replace this image', function(enh) {
            }],
*/
	];

	if (wildbook.iaEnabled()) {  //TODO (the usual) needs to be genericized for IA plugin support (which doesnt yet exist)
		opt.menu.push(['start new matching scan', function(enh) {
		    var mid = imageEnhancer.mediaAssetIdFromElement(enh.imgEl);
		    var aid = imageEnhancer.annotationIdFromElement(enh.imgEl);
                    var ma = assetByAnnotationId(aid);
      		    if (!isGenusSpeciesSet(ma)) {
        		imageEnhancer.popup("You need full taxonomic classification to start identification!");
        		return;
      		    }
		    imageEnhancer.message(jQuery('#image-enhancer-wrapper-' + mid + ':' + aid), '<p>starting matching; please wait...</p>');
		    startIdentify(ma, enh.imgEl);  //this asset should now be annotationly correct
		}]);
	}


        opt.menu.push(['use visual matcher', function(enh) {
	    var mid = imageEnhancer.mediaAssetIdFromElement(enh.imgEl);
	    var aid = imageEnhancer.annotationIdFromElement(enh.imgEl);
            var ma = assetByAnnotationId(aid);
      	    if (!isGenusSpeciesSet(ma)) {
                imageEnhancer.popup("You need full taxonomic classification to use Visual Matcher!");
                return;
            }
            window.location.href = 'encounterVM.jsp?number=' + encounterNumberFromElement(enh.imgEl) + '&mediaAssetId=' + mid;
        }]);

/*   we dont really like the old tasks showing up in menu. so there.
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
*/
<%
if((CommonConfiguration.getProperty("useSpotPatternRecognition", context)!=null)&&(CommonConfiguration.getProperty("useSpotPatternRecognition", context).equals("true"))){
%>
	opt.menu.push(
            [
		'spot mapping',
		function(enh) {
			if (!enh || !enh.imgEl || !enh.imgEl.context) {
				alert('could not determine id');
				return;
			}
			var mid = imageEnhancer.mediaAssetIdFromElement(enh.imgEl);
			wildbook.openInTab('encounterSpotTool.jsp?imageID=' + mid);
		}
            ],
            [
		function(enh) { return imagePopupInfoMenuItem(enh); },
		function(enh) { imagePopupInfo(enh); }
            ]
	);
	<%
    }
	%>
	

/*
        if (true) {
            opt.menu.push(['set image as encounter thumbnail', function(enh) {
            }]);
        }
*/

        opt.init = [
	    function(el, enh) { enhancerDisplayAnnots(el, enh); },
            function(el, enh) {
console.info(' ===========>   %o %o', el, enh);
		imageLayerKeywords(el, enh);
            }
        ];

    }  //end if-logged-in

	if (!opt.init) opt.init = []; //maybe created if logged in?

	opt.init.push(
		//function(el, enh) { enhancerDisplayAnnots(el, enh); },  //TODO fix for scaled/watermark image
		function(el, enh) { enhancerCaption(el, enh); }
	);

	opt.callback = function() {
		$('.image-enhancer-keyword-wrapper').on('click', function(ev) { ev.stopPropagation(); });
	};
    imageEnhancer.applyTo(sel, opt);
}

function enhancerCaption(el, opt) {
	var mid = imageEnhancer.mediaAssetIdFromElement(el.context);
	var ma = assetById(mid);
console.warn("====== enhancerCaption %o ", ma);
	if (!ma || !ma.sourceAsset || !ma.sourceAsset.store.type == 'YouTube') return;
	var title = ma.sourceAsset.filename || '';
	if (ma.sourceAsset.metadata && ma.sourceAsset.metadata.basic) {
		title = ma.sourceAsset.metadata.basic.title || 'Untitled';
		title += ' [from ' + (ma.sourceAsset.metadata.basic.author_name || 'Unknown source') + ']';
	}
	var time;
	if ((ma.extractFPS > 0) && (ma.extractOffset != undefined)) {
		time = (1 / ma.extractFPS) * ma.extractOffset - 2;  //rewind a couple seconds
		if (t < 4) {
			time = false;
		} else {
			time = Math.floor(time / 60) + 'm' + (time % 60) + 's';
		}
	}
	var tlink = (time ? '#t=' + time : '');
	var timeDisp = (time ? 'At approx <b>' + time + '</b> in ' : '');
console.info(timeDisp);

	var ycap = $('<div title="' + title + '" class="caption-youtube">' + timeDisp + ' YouTube video</div>');
	ycap.on('click', function(ev) {
		var link = 'https://www.youtube.com/watch?v=' + ma.sourceAsset.filename + tlink;
		ev.stopPropagation();
		wildbook.openInTab(link);
	});
	$(el).append(ycap);
}

function enhancerDisplayAnnots(el, opt) {
    if (opt.skipDisplayAnnots) return;
    //var mid = imageEnhancer.mediaAssetIdFromElement(el.context);
    var aid = imageEnhancer.annotationIdFromElement(el.context);
console.warn('foocontext --> %o', aid);
    if (!aid) return;
    var ma = assetByAnnotationId(aid);
console.warn("====== enhancerDisplayAnnots %o ", ma);
    if (!ma || !ma.features || !ma.annotationId) return;
    var featwrap = $('<div class="image-enhancer-feature-wrapper" />');
    featwrap.data('enhancerScale', el.data('enhancerScale'));
    el.append(featwrap);
    var featzoom = $('<div class="image-enhancer-feature-zoom" />');
    featzoom.css('background-image', 'url(' + ma.url + ')');
    el.append(featzoom);
    var ord = featureSortOrder(ma.features);
    for (var i = 0 ; i < ord.length ; i++) {
        enhancerDisplayFeature(featwrap, opt, ma.annotationId, ma.features[ord[i]], i);
    }
}

//this sorts features such that smallest (by area) come earlier(?) so that they will lie on top of larger ones
function featureSortOrder(feat) {
    var ord = new Array();
    for (var i = 0 ; i < feat.length ; i++) {
        var area = 0;
        if (feat[i] && feat[i].parameters && feat[i].parameters.width && feat[i].parameters.height) {
            area = feat[i].parameters.width * feat[i].parameters.height;
        }
        ord.push({i: i, area: area});
    }
    ord.sort(function(a,b) { return (b.area - a.area); });  //reverse numerical sort on area
    //now we need to return an array of the .i values (offset into original array)
    var rtn = new Array();
    for (var i = 0 ; i < ord.length ; i++) {
        rtn.push(ord[i].i);
    }
    return rtn;
}

function enhancerDisplayFeature(el, opt, focusAnnId, feat, zdelta) {
    if (!feat.type) return;  //unity, skip
    if (!feat.parameters) return; //wtf???
    //TODO other than boundingBox
    var scale = el.data('enhancerScale') || 1;
console.log('FEAT!!!!!!!!!!!!!!! scale=%o feat=%o', scale, feat);
    var focused = (feat.annotationId == focusAnnId);
    var fel = $('<div title="Annot" style="z-index: ' + (31 + (zdelta||0)) + ';" class="image-enhancer-feature" />');

    var tooltip;
    if (feat.individualId) {
        tooltip = 'Name: <b>' + feat.individualId + '</b>';
    } else {
        tooltip = '<i>Unnamed individual</i>';
    }
    if (feat.encounterId) {
        tooltip += '<br />Enc ' + feat.encounterId.substr(-8);
        fel.data('encounterId', feat.encounterId);
    }
    if (focused) tooltip = '<i style="color: #840;">this encounter</i>';

    fel.prop('id', feat.id);
    if (feat.annotationIsOfInterest) {
        fel.addClass('image-enhancer-feature-aoi');
        tooltip += '<br /><i style="color: #280; font-size: 0.8em;">Annotation of Interest</i>';
    }
    if (focused) fel.addClass('image-enhancer-feature-focused');
    fel.prop('data-tooltip', tooltip);
    fel.css({
        left: feat.parameters.x * scale,
        top: feat.parameters.y * scale,
        width: feat.parameters.width * scale,
        height: feat.parameters.height * scale
    });
    fel.tooltip({ content: function() { return $(this).prop('data-tooltip'); } });
    fel.on('click', function(ev) {
        ev.stopPropagation();
        var encId = $(this).data('encounterId');
        if (!inGalleryMode() && (encId == encounterNumber)) return;  //clicking on "this" encounter when in encounter.jsp
        document.body.innerHTML = '';
        window.location.href = 'encounter.jsp?number=' + encId;
    });
    if (feat.parameters.theta) fel.css('transform', 'rotate(' + feat.parameters.theta + 'rad)');
    el.append(fel);
}

function checkImageEnhancerResize() {
//TODO update enhancerScale when this happens!
	var needUpdate = false;
	$('.image-enhancer-wrapper').each(function(i,el) {
            var jel = $(el);
            var imgEl = jel.parent().find('img:first');
//console.log('wtf: %o', el);
//console.log('wtf: %o %o', imgEl, imgEl.width());
		var imgW = imgEl.width();
		var wrapW = jel.width();
//console.warn('%o -> %o vs %o', el.id, imgW, wrapW);
		if (imgW && wrapW && (imgW != wrapW)) needUpdate = true;
	});
	if (needUpdate) doImageEnhancer('figure img');
}


var popupStartTime = 0;
function addNewKeyword(el) {
	console.warn(el);
	var jel = $(el);
	var wrapper = jel.closest('.image-enhancer-wrapper');
	if (!wrapper.length) {
		console.error("could not find MediaAsset id from closest wrapper");
		return;
	}
	var mid = imageEnhancer.mediaAssetIdFromElement(wrapper);
	if (!assetById(mid)) {
		console.error("could not find MediaAsset byId(%o)", mid);
		return;
	}

	var val = jel.val();

	var data = { onMediaAssets: { assetIds: [ mid ] } };

	if (el.id == 'keyword-new') {
		if (val == '') return;
		//imageEnhancer.popup('Adding new keyword <b>' + val + '</b> to this image.');
		data.onMediaAssets.newAdd = [ val ];
	} else if (jel.hasClass('iek-remove')) {
		var kid = jel.parent().prop('id').substring(8);
		//imageEnhancer.popup('Removing keyword <b>' + wildbookGlobals.keywords[kid] + '</b> from this image.');
		data.onMediaAssets.remove = [ kid ];
	} else {
		var name = wildbookGlobals.keywords[val] || '';
		//imageEnhancer.popup('Adding keyword <b>' + name + '</b> to this image.');
		data.onMediaAssets.add = [ val ];
	}
console.info(data);

	popupStartTime = new Date().getTime();
	$.ajax({
		url: wildbookGlobals.baseUrl + '/RestKeyword',
		data: JSON.stringify(data),
		contentType: 'application/javascript',
		success: function(d) {
console.info(d);
			if (d.success) {
/*
				var elapsed = new Date().getTime() - popupStartTime;
				if (elapsed > 6000) {
					$('.image-enhancer-popup').remove();
				} else {
					window.setTimeout(function() { $('.image-enhancer-popup').remove(); }, 6000 - elapsed);
				}
*/
				if (d.newKeywords) {
					for (var id in d.newKeywords) {
						wildbookGlobals.keywords[id] = d.newKeywords[id];
					}
				}
				var mainMid = false;
				if (d.results) {
					for (var mid in d.results) {
                                            refreshKeywordsForMediaAsset(mid, d.results[mid]);
					}
				}
			} else {
				var msg = d.error || 'ERROR could not make change';
				$('.popup-content').append('<p class="error">' + msg + '</p>');
			}
		},
		error: function(x,a,b) {
			console.error('%o %o %o', x, a, b);
			$('.popup-content').append('<p class="error">ERROR making change: ' + b + '</p>');
		},
		type: 'POST',
		dataType: 'json'
	});
	return false;
}

/*
{
    "success": true,
    "newKeywords": {
        "ff808181557f843f01557f843f280000": "foo"
    },
    "results": {
        "82091": {
            "518aca4e5113bfc8015113bfe77b000e": "2A",
            "518aca4e5113bfc8015113bfe4fd000b": "2C",
            "518aca4e46618bcd0146915d7a120016": "Left-Dorsal",
            "518aca4e430bec5501430bf2fc190001": "fluke",
            "ff808181557f843f01557f843f280000": "foo"
        }
    }
}
*/

function refreshKeywordsForMediaAsset(mid, data) {
    for (var i = 0 ; i < assets.length ; i++) {
        if (assets[i].id != mid) continue;
        for (var id in data.results[mid]) {
            if (!assets[i].keywords) assets[i].keywords = [];
            assets[i].keywords.push({
                indexname: id,
                readableName: data.results[mid][id]
            });
        }
    }
    $('.image-enhancer-wrapper-mid-' + mid).each(function(i,el) {   //update the ui
        $(el).find('.image-enhancer-keyword-wrapper').remove();
        imageLayerKeywords($(el), { _mid: mid });
    });
}

function imageLayerKeywords(el, opt) {
	var mid;
	if (opt && opt._mid) {  //hack!
		mid = opt._mid;
	} else {
 		mid = imageEnhancer.mediaAssetIdFromElement(el.context);
	}
	var ma = assetById(mid);
console.info("############## mid=%s -> %o", mid, ma);
	if (!ma) return;

	if (!ma.keywords) ma.keywords = [];
	var thisHas = [];
	var h = '<div class="image-enhancer-keyword-wrapper">';
	for (var i = 0 ; i < ma.keywords.length ; i++) {
		thisHas.push(ma.keywords[i].indexname);
//console.info('keyword = %o', ma.keywords[i]);
		h += '<div class="image-enhancer-keyword" id="keyword-' + ma.keywords[i].indexname + '">' + ma.keywords[i].readableName + ' <span class="iek-remove" title="remove keyword">X</span></div>';
	}

	h += '<div class="iek-new-wrapper' + (ma.keywords.length ? ' iek-autohide' : '') + '">add new keyword<div class="iek-new-form">';
	if (wildbookGlobals.keywords) {
		var hasSome = false;
		var mh = '<select onChange="return addNewKeyword(this);" style="width: 100%" class="keyword-selector"><option value="">select keyword</option>';
		for (var j in wildbookGlobals.keywords) {
			if (thisHas.indexOf(j) >= 0) continue; //dont list ones we have
			mh += '<option value="' + j + '">' + wildbookGlobals.keywords[j] + '</option>';
			hasSome = true;
		}
		mh += '</select>';
		if (hasSome) h += mh;
	}
	h += '<br /><input placeholder="or enter new" id="keyword-new" type="text" style="" onChange="return addNewKeyword(this);" />';
	h += '</div></div>';

	h += '</div>';
	el.append(h);
	el.find('.image-enhancer-keyword-wrapper').on('click', function(ev) {
		ev.stopPropagation();
	});
	el.find('.iek-remove').on('click', function(ev) {
		//ev.stopPropagation();
		addNewKeyword(ev.target);
	});
}

function imagePopupInfo(obj) {
	if (!obj || !obj.imgEl || !obj.imgEl.context) return;
	var mid = imageEnhancer.mediaAssetIdFromElement(obj.imgEl);
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
	var mid = imageEnhancer.mediaAssetIdFromElement(obj.imgEl);
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
function assetByAnnotationId(aid) {
	if (!aid || !assets || (assets.length < 1)) return false;
	for (var i = 0 ; i < assets.length ; i++) {
		if (assets[i].annotationId == aid) return assets[i];
	}
	return false;
}

function encounterNumberFromAsset(asset) {
    if (!asset || !asset.annotationId || !asset.features) return false;
    for (var i = 0 ; i < asset.features.length ; i++) {
        if (asset.features[i].annotationId == asset.annotationId) return asset.features[i].encounterId;
    }
    return false;
}

function encounterNumberFromElement(el) {  //should be img element
    var aid = imageEnhancer.annotationIdFromElement(el);
    return encounterNumberFromAsset(assetByAnnotationId(aid));
}

function inGalleryMode() {
    return (typeof(encounterNumber) == 'undefined');
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
