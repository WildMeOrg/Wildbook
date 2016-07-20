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
  function isGenusSpeciesSet() {
    var check = <%=((enc.getGenus()!=null)&&(enc.getSpecificEpithet()!=null))%>;
    console.log("isGenusSpeciesSet() = "+check);
    return check;
  }

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
        identify: { annotationIds: [ aid ] }
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

      // SKIPPING NON-TRIVIAL ANNOTATIONS FOR NOW! TODO
  		//if (!ann.isTrivial()) continue;  ///or not?

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

.image-enhancer-wrapper div {
	cursor: auto;
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

<h2>Gallery</h2>

<div class="my-gallery" id="enc-gallery" itemscope itemtype="http://schema.org/ImageGallery"> </div>
<script src='http://<%=CommonConfiguration.getURLLocation(request) %>/javascript/imageDisplayTools.js'></script>
<script src='http://<%=CommonConfiguration.getURLLocation(request) %>/javascript/imageUtilities.js'></script>

<div id="add-image-zone" class="bc4">
  <h2 style="text-align:left">Add image to Encounter</h2>
<div class="flow-box bc4" style="text-align:center" >

  <div id="file-activity" style="display:none"></div>

  <div id="updone"></div>

  <div id="upcontrols">
  	<input type="file" id="file-chooser" multiple accept="audio/*,video/*,image/*" onChange="return filesChanged(this)" />
    <div id="flowbuttons">
    	<button id="upload-button" class="btn btn-primary" style="display:none">begin upload</button>
      <button id="reselect-button" class="btn btn-primary" style="display:none">choose a different image</button>
    </div>
  </div>



</div>
</div>

<script src="../tools/flow.min.js"></script>
<style>

div#add-image-zone {
  background-color: #e8e8e8;
  margin-bottom: 8px;
  padding: 13px;
}
div#add-image-zone h2 {

}

div.flow-box {
}

div#file-activity {
	font-family: sans;
  padding-top: 8px;
	padding-bottom: 8px;
	margin: 0px;
	min-height: 20px;
  border-radius: 5px;
}
div.file-item {
	position: relative;
	background-color: #DDD;
	border-radius: 3px;
	margin: 2px;
}

div.file-item div {
	display: inline-block;
	padding: 3px 7px;
}
.file-size {
	width: 10%;
}

.file-bar {
	position: absolute;
	width: 0;
	height: 100%;
	padding: 0 !important;
	left: 0;
	border-radius: 3px;
	background-color: rgba(100,100,100,0.3);
}

#flowbuttons {
  width: 100%;
  margin-left:2px;
  margin-right:2px;
}
#flowbuttons button {
  width:48.5%;
}

#flowbuttons button:first-child {
  float: left;
  margin-right: 2%;
}
#flowbuttons button:last-child {
  float: right
  margin-left: 2%;
}

#upcontrols {
  width: 100%;
  padding-top: 8px;
  padding-bottom: 8px;
}

</style>




<script>

  var keyToFilename = {};
  var filenames = [];
  var pendingUpload = -1;

  $("button#add-image").click(function(){$(".flow-box").show()})


  console.info("uploader is using uploading direct to host (not S3)");
  var flow = new Flow({
    target:'../ResumableUpload',
    forceChunkSize: true,
    testChunks: false,
  });

  flow.assignBrowse(document.getElementById('file-chooser'));

  flow.on('fileAdded', function(file, event){
    $('#file-activity').show();
    console.log('added %o %o', file, event);
  });
  flow.on('fileProgress', function(file, chunk){
    var el = findElement(file.name, file.size);
    var p = ((file._prevUploadedSize / file.size) * 100) + '%';
    updateProgress(el, p, 'uploading');
    console.log('progress %o %o', file._prevUploadedSize, file);
  });
  flow.on('fileSuccess', function(file,message){
    var el = findElement(file.name, file.size);
    updateProgress(el, -1, 'completed', 'rgba(200,250,180,0.3)');
    console.log('success %o %o', file, message);
    console.log('filename: '+file.name);
    filenames.push(file.name);
    pendingUpload--;
    if (pendingUpload == 0) uploadFinished();
  });
  flow.on('fileError', function(file, message){
    console.log('error %o %o', file, message);
    pendingUpload--;
    if (pendingUpload == 0) uploadFinished();
  });

  document.getElementById('upload-button').addEventListener('click', function(ev) {
    var files = flow.files;
    pendingUpload = files.length;
    for (var i = 0 ; i < files.length ; i++) {
        filenameToKey(files[i].name);
    }
    document.getElementById('upcontrols').style.display = 'none';
    console.log('#pendingUpload='+pendingUpload);
    flow.upload();
  }, false);

  document.getElementById('reselect-button').addEventListener('click', function(ev) {
    var files = flow.files;
    for (var i = 0 ; i < files.length ; i++) {
        console.info('flow.js removing file '+files[i].name);
        $("#file-item-"+i).hide();
        flow.removeFile(files[i]);
    }
    document.getElementById('upload-button').style.display = 'none';
    document.getElementById('reselect-button').style.display = 'none';
    document.getElementById('file-activity').style.display = 'none';
    $('#file-chooser').show();
    pendingUpload = flow.files.length;
    console.log('#pendingUpload='+pendingUpload);
  }, false);


  function filesChanged(f) {
  	var h = '';
  	for (var i = 0 ; i < f.files.length ; i++) {
  		h += '<div class="file-item" id="file-item-' + i + '" data-i="' + i + '" data-name="' + f.files[i].name + '" data-size="' + f.files[i].size + '"><div class="file-name">' + f.files[i].name + '</div><div class="file-size">' + niceSize(f.files[i].size) + '</div><div class="file-status"></div><div class="file-bar"></div></div>';
  	}
  	document.getElementById('file-activity').innerHTML = h;
    $('#file-chooser').hide();
    $('#upload-button').show();
    $('#reselect-button').show();
  }
  function niceSize(s) {
  	if (s < 1024) return s + 'b';
  	if (s < 1024*1024) return Math.floor(s/1024) + 'k';
  	return Math.floor(s/(1024*1024) * 10) / 10 + 'M';
  }
  function updateProgress(el, width, status, bg) {
  	if (!el) {console.info("quick return");return;}
  	var els = el.children;
  	if (width < 0) {  //special, means 100%
  		els[3].style.width = '100%';
  	} else if (width) {
  		els[3].style.width = width;
  	}
  	if (status) els[2].innerHTML = status;
  	if (bg) els[3].style.backgroundColor = bg;
  }
  function filenameToKey(fname) {
      var key = fname;
      keyToFilename[key] = fname;
      console.info('key = %s', key);
      return key;
  }

  function findElement(key, size) {
          var name = keyToFilename[key];
          if (!name) {
              console.warn('could not find filename for key %o; bailing!', key);
              return false;
          }
  	var items = document.getElementsByClassName('file-item');
  	for (var i = 0 ; i < items.length ; i++) {
  		if ((name == items[i].getAttribute('data-name')) && ((size < 0) || (size == items[i].getAttribute('data-size')))) return items[i];
  	}
  	return false;
  }
  function uploadFinished() {
  	document.getElementById('updone').innerHTML = '<i>Upload complete. Refresh page to see new image.</i>';
    console.log("upload finished.");
    console.log('upload finished. Files added: '+filenames);

    if (filenames.length > 0) {
      console.log("creating mediaAsset for filename "+filenames[0]);
      $.ajax({
        url: '../MediaAssetCreate',
        type: 'POST',
        dataType: 'json',
        contentType: 'application/javascript',
        data: JSON.stringify({
          "MediaAssetCreate": [
            {"assets": [
               {"filename": filenames[0] }
              ]
            }
          ]
        }),
        success: function(d) {
          console.info('Success! Got back '+JSON.stringify(d));
          var maId = d.withoutSet[0].id;
          console.info('parsed id = '+maId);

          var ajaxData = {"attach":"true","EncounterID":"<%=encNum%>","MediaAssetID":maId};
          var ajaxDataString = JSON.stringify(ajaxData);
          console.info("ajaxDataString="+ajaxDataString);


          $.ajax({
            url: '../MediaAssetAttach',
            type: 'POST',
            dataType: 'json',
            contentType: "application/json",
            data: ajaxDataString,
            success: function(d) {
              console.info("I attached MediaAsset "+maId+" to encounter <%=encNum%>");
            },
            error: function(x,y,z) {
              console.warn("failed to MediaAssetAttach");
              console.warn('%o %o %o', x, y, z);
            }
          });

        },
        error: function(x,y,z) {
          console.warn('%o %o %o', x, y, z);
        },
      });

    }
  }


  </script>

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
          $('#image-enhancer-wrapper-' + maId).closest('figure').remove();
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
    maLib.maJsonToFigureElemCaption(elem, $('#enc-gallery'), captions[index]);

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
            ['remove this image', function(enh) {
		removeAsset(enh.imgEl.prop('id').substring(11));
            }],
/*
            ['replace this image', function(enh) {
            }],
*/
	];

	if (wildbook.iaEnabled()) {
		opt.menu.push(['start new matching scan', function(enh) {
      if (isGenusSpeciesSet()) {
        i!mageEnhancer.popup("You need full taxonomic classification to start identification!");
        return;
      }
			//var mid = enh.imgEl.context.id.substring(11);
			var mid = enh.imgEl.data('enh-mediaassetid');
      console.log('%o ?????', mid);
			imageEnhancer.message(jQuery('#image-enhancer-wrapper-' + mid), '<p>starting matching; please wait...</p>');
			startIdentify(assetById(mid), enh.imgEl);
		}]);
	}

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
*/

        opt.init = [
            function(el, enh) {
console.info(' ===========>   %o %o', el, enh);
		imageLayerKeywords(el, enh);
            },
        ];

    }

	opt.callback = function() {
		$('.image-enhancer-keyword-wrapper').on('click', function(ev) { ev.stopPropagation(); });
	};

    imageEnhancer.applyTo(sel, opt);
}


function checkImageEnhancerResize() {
	var needUpdate = false;
	$('.image-enhancer-wrapper').each(function(i,el) {
		var imgW = $('#figure-img-' + el.id.substring(23)).width();
		var wrapW = $(el).width();
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
	var mid = wrapper.prop('id').substring(23);
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
				//the reality is we prob only have one, mid so we save that to update the menu of
				var mainMid = false;
				if (d.results) {
					for (var mid in d.results) {
						if (!mainMid) mainMid = mid;
						assetById(mid).keywords = [];
						for (var id in d.results[mid]) {
							assetById(mid).keywords.push({
								indexname: id,
								readableName: d.results[mid][id]
							});
						}
					}
				}
				if (mainMid) {
					$('#image-enhancer-wrapper-' + mainMid + ' .image-enhancer-keyword-wrapper').remove();
					imageLayerKeywords($('#image-enhancer-wrapper-' + mainMid), { _mid: mainMid });
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

function imageLayerKeywords(el, opt) {
	var mid;
	if (opt && opt._mid) {  //hack!
		mid = opt._mid;
	} else {
 		mid = el.context.id.substring(11);
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
