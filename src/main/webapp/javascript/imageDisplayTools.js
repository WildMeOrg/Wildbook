/*
 * The goal of this file is to serve as a super lightweight image-handling library for wildbook.
 * Functions will generally take MediaAssets as input, and produce html elements.
 * maLib serves as the package object, containing the namespace
 */

var maLib = {};


/**
 * Builds an html 'figure' element displaying a MediaAsset. This element can be
 * later grabbed by PhotoSwipe to display a lightbox.
 * @param {string} maJSON - a jsonified MediaAsset
 * @param {DOM} intoElem - the element that will be populated
 *
 */
maLib.maJsonToFigureElem = function(maJson, intoElem) {
  // TODO: copy into html figure element
  var url = maLib.getUrl(maJson), w, h;
  // have to check to make sure values exist
  if ('metadata' in maJson) {
    w = maJson.metadata.width;
    h = maJson.metadata.height;
  }
  if (!url) {
    console.log('failed to parse into html this MediaAsset: '+JSON.stringify(maJson));
    return;
  }
  var wxh = w+'x'+h;
  var watermarkUrl = maLib.getChildUrl('_watermark');
  intoElem.append(
    $('<figure itemprop="associatedMedia" itemscope itemtype="http://schema.org/ImageObject" />').append(
      $('<a href="'+url+'" itemprop="contentUrl" data-size="'+wxh+'"/>').append(
        mkImg(maJson)
      )
    )
  );
  maLib.testExtraction(maJson);
  return;
}


maLib.startIdentify = function(el) {
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


maLib.defaultCaptionFunction = function(maJson) {
  if ('url' in maJson) {return maJson.url;}
  else {return "Test caption, do not read"}
}

/*
var fig = $('<figure itemprop="associatedMedia" itemscope itemtype="http://schema.org/ImageObject"/>');
fig.append(
  $('<a href="'+url+'" itemprop="contentUrl" data-size="'+wxh+'"/>').append(
    mkImg(maJson)
  )
);
*/


maLib.cascadiaCaptionFunction = function(maJson) {
  if ('url' in maJson) {
    var partArray = maJson.url.split('/');
    partArray = partArray[partArray.length-1].split('.');
    return partArray[0];
  }
  return "Test caption, do not read";

}

maLib.blankCaptionFunction = function(maJson) {
  return "";
}

maLib.testCaptionFunction = function(maJson) {
  //return ("test caption for MediaAsset "+maJson.id);
  return "";
}


/**
 * Like the above, but with also writes a labeled html caption,
 * which = maCaptionFunction(maJson)
 *
 * @param {@function {@param {string} maJSON @returns {string}}} maCaptionFunction - a function that takes a jsonified MediaAsset and returns a caption string. This makes it convenient to have custom caption protocols for each Wildbook.
 */
maLib.maJsonToFigureElemCaption = function(maJson, intoElem, caption, maCaptionFunction) {
    if (maLib.nonImageDisplay(maJson, intoElem, caption, maCaptionFunction)) return;  // true means it is done!
  //var maCaptionFunction = typeof maCaptionFunction !== 'undefined' ?  b : ma.defaultCaptionFunction;
  caption = caption || "";
  maCaptionFunction = maCaptionFunction || maLib.blankCaptionFunction;
  caption = caption || '';

  // TODO: copy into html figure element
  var url = maLib.getUrl(maJson), w, h;

  // have to check to make sure values exist
  if ('metadata' in maJson) {
    w = maJson.metadata.width;
    h = maJson.metadata.height;
  }
  if (!url) {
    console.log('failed to parse into html this MediaAsset: '+JSON.stringify(maJson));
    return;
  }
  var wxh = w+'x'+h;
  var watermarkUrl = maLib.getChildUrl('_watermark');

  var fig = $('<figure itemprop="associatedMedia" itemscope itemtype="http://schema.org/ImageObject"/>');
  fig.append(
    $('<a href="'+url+'" itemprop="contentUrl" data-size="'+wxh+'"/>').append(
      mkImg(maJson)
    )
  );
  fig.append('<figcaption itemprop="caption description">'+caption+maCaptionFunction(maJson)+'</figcaption>');


  intoElem.append(fig);
  /*
    $('<figure itemprop="associatedMedia" itemscope itemtype="http://schema.org/ImageObject"/>').append(
      $('<a href="'+url+'" itemprop="contentUrl" data-size="'+wxh+'"/>').append(
        '<img src="'+url+'"itemprop="contentUrl" alt="Image description"/>'
      )
    )
  );*/
  maLib.testExtraction(maJson);
  return;
}

maLib.maJsonToFigureElemCaptionGrid = function(maJson, intoElem, caption, maCaptionFunction) {
  intoElem.append('<div class=\"col-md-4\"></div>');
  intoElem = intoElem.find('div.col-md-4').last();
  maLib.maJsonToFigureElemCaption(maJson, intoElem, caption, maCaptionFunction);
}



maLib.maJsonToFigureElemColCaption = function(maJson, intoElem, colSize, maCaptionFunction) {
  //var maCaptionFunction = typeof maCaptionFunction !== 'undefined' ?  b : ma.defaultCaptionFunction;
  // TODO: genericize caption
  maCaptionFunction = maCaptionFunction || maLib.cascadiaCaptionFunction;

  colSize = colSize || 6;

  // TODO: copy into html figure element
  var url = maLib.getUrl(maJson), w, h;
  // have to check to make sure values exist
  if ('metadata' in maJson) {
    w = maJson.metadata.width;
    h = maJson.metadata.height;
  }
  if (!url) {
    console.log('failed to parse into html this MediaAsset: '+JSON.stringify(maJson));
    return;
  }
  var wxh = w+'x'+h;
  var watermarkUrl = maLib.getChildUrl('_watermark');

  var fig = $('<figure itemprop="associatedMedia" itemscope itemtype="http://schema.org/ImageObject" class="col-md-'+colSize+'"/>');


  fig.append(
    $('<a href="'+url+'" itemprop="contentUrl" data-size="'+wxh+'"/>').append(
      mkImg(maJson)
    )
  );

  // make sure half-width images aren't more than 1/4 height of screen
  /*
  if (colSize==3) {
    fig.find("img").css("max-height","140px");
  }*/


  var caption = maCaptionFunction(maJson);
  fig.append('<figcaption itemprop="caption description">'+caption+'</figcaption>');

  intoElem.append(fig);
  /*
    $('<figure itemprop="associatedMedia" itemscope itemtype="http://schema.org/ImageObject"/>').append(
      $('<a href="'+url+'" itemprop="contentUrl" data-size="'+wxh+'"/>').append(
        '<img src="'+url+'"itemprop="contentUrl" alt="Image description"/>'
      )
    )
  );*/
  maLib.testExtraction(maJson);
  return;
}



/***
  SCHEMA EXAMPLE FOR HTML FIGURE:
  <figure itemprop="associatedMedia" itemscope itemtype="http://schema.org/ImageObject">
    <a href="large-image.jpg" itemprop="contentUrl" data-size="600x400">
        <img src="small-image.jpg" itemprop="thumbnail" alt="Image description" />
    </a>
    <figcaption itemprop="caption description">Image caption</figcaption>
  </figure>
 ***/




maLib.testExtraction = function(maJson) {
  var children = "[";
  for (child in maJson.children) {children += JSON.stringify(child)}
  //console.log('\nMediaAsset '+maJson.id+' stringified: '+JSON.stringify(maJson));
  var nChildren;
  try {
    nChildren = maJson.children.length
  }
  catch (e) {
    nChildren = 'undefined';
  }
  console.log('\t'+maJson.id+' has nChildren = '+nChildren);
console.log(maJson);

  console.log('\t'+maJson.id+' has child watermark url: '+maLib.getChildUrl(maJson, '_watermark'));

}



maLib.maJsonToFigureElemDisplayChild = function(maJson, intoElem, childLabel) {
  // TODO: copy into html figure element
  var url = maLib.getUrl(maJson), w, h;
  // have to check to make sure values exist
  if ('metadata' in maJson) {
    w = maJson.metadata.width;
    h = maJson.metadata.height;
  }
  if (!url) {
    console.log('failed to parse into html this MediaAsset: '+JSON.stringify(maJson));
    return;
  }
  var wxh = w+'x'+h;
  intoElem.append(
    $('<figure itemprop="associatedMedia" itemscope itemtype="http://schema.org/ImageObject" />').append(
      $('<a href="'+url+'" itemprop="contentUrl" data-size="'+wxh+'"/>').append(
        mkImg(maJson)
      )
    )
  );
  return;
}

/**
 * @param {JSON} maJson - a media asset
 * @param {string} _label - a label such as '_watermark', '_original' or '_thumbnail'
 * @return {boolean}
 */
maLib.hasLabel = function (maJson, _label) {
  if (maJson.labels != undefined) {
    for (index in maJson.labels) {
      if (maJson.labels[index] == _label) {return true;}
    }
  }
  return false
}

/**
 * BROKEN! TODO: fix this :^)
 * @param {JSON} maJson - a media asset
 * @param {string} _label - a label such as '_watermark', '_original' or '_thumbnail'
 * @return {JSON} the mediaAsset (or empty object) containing that child
 */
maLib.getChildWithLabel = function (maJson, _label) {
  for (index in maJson.children) { // remember that maJson is JSON! that's why the iterator is an index and not the child itself
    var child = maJson.children[index]
    if (maLib.hasLabel(child, _label)) return child;
  }
  return null;
}

/**
 * @param {JSON} maJson - a media asset
 * @param {string} _label - a label such as '_watermark', '_original' or '_thumbnail'
 * @return {boolean}
 */
maLib.hasChildWithLabel = function (maJson, _label) {
  return (maLib.getChildWithLabel(maJson, _label) != null);
}

/**
 * @param {JSON} maJson - a media asset
 * @param {string} _label - a label such as '_watermark', '_original' or '_thumbnail'
 * @return string - the url of the picture depicting the labeled child
 */
maLib.getChildUrl = function (maJson, _label) {
  var child = maLib.getChildWithLabel(maJson,_label);
  if (child != null && 'url' in child) {
    return (child.url);
  }
  return '';
}

/**
 * This crucial function (barely modified from PhotoSwipe's public example code) grabs an html div
 * that is especially formatted, and launches photoswipe from that div
 * @param {string} gallerySelector - selector that will grab gallery DOMs from the webpage
 */
maLib.initPhotoSwipeFromDOM = function(gallerySelector) {
  // parse slide data (url, title, size ...) from DOM elements
  // (children of gallerySelector)
  var parseThumbnailElements = function(el) {
      var thumbElements = $(el).find('figure'),
      //var thumbElements = el.childNodes,
          numNodes = thumbElements.length,
          items = [],
          figureEl,
          linkEl,
          size,
          item;

      for(var i = 0; i < numNodes; i++) {

          figureEl = thumbElements[i]; // <figure> element

          // include only element nodes
          if(figureEl.nodeType !== 1) {
              continue;
          }

          linkEl = figureEl.children[0]; // <a> element

          //size = linkEl.getAttribute('data-size').split('x');
        size = [800,600];  //fallback that hopefully we never see
        var imgEl = linkEl.children[0];
        if (imgEl && imgEl.naturalWidth && imgEl.naturalHeight) {
            size = [imgEl.naturalWidth, imgEl.naturalHeight];
        }

          // create slide object
          item = {
              src: linkEl.getAttribute('href'),
              w: parseInt(size[0], 10),
              h: parseInt(size[1], 10)
          };

          if(figureEl.children.length > 1) {
              // <figcaption> content
              item.title = figureEl.children[1].innerHTML;
          }

          if(linkEl.children.length > 0) {
              // <img> thumbnail element, retrieving thumbnail url
              item.msrc = linkEl.children[0].getAttribute('src');
          }

          item.el = figureEl; // save link to element for getThumbBoundsFn
          items.push(item);
      }

      return items;
  };

  // find nearest parent element
  var closest = function closest(el, fn) {
      return el && ( fn(el) ? el : closest(el.parentNode, fn) );
  };

  // triggers when user clicks on thumbnail
  var onThumbnailsClick = function(e) {
      e = e || window.event;
      e.preventDefault ? e.preventDefault() : e.returnValue = false;

      var eTarget = e.target || e.srcElement;

      // find root element of slide
      var clickedListItem = closest(eTarget, function(el) {
          return (el.tagName && el.tagName.toUpperCase() === 'FIGURE');
      });

      if(!clickedListItem) {
          return;
      }

      // find index of clicked item by looping through all child nodes
      // alternatively, you may define index via data- attribute
      // var clickedGallery = clickedListItem.parentNode;
      var clickedGallery = clickedListItem.closest(gallerySelector);

      //var childNodes = clickedListItem.parentNode.childNodes;
      var childNodes = $(clickedGallery).find('figure');

      var numChildNodes = childNodes.length,
          nodeIndex = 0,
          index;

      console.log('numChildNodes = '+numChildNodes);

      for (var i = 0; i < numChildNodes; i++) {
          if(childNodes[i].nodeType !== 1) {
              continue;
          }

          if(childNodes[i] === clickedListItem) {
              index = nodeIndex;
              break;
          }
          nodeIndex++;
      }

      if(index >= 0) {
          // open PhotoSwipe if valid index found
          console.log("Opening photoswipe through other avenue. Index="+index);
          openPhotoSwipe( index, clickedGallery );
      }
      return false;
  };

  // parse picture index and gallery index from URL (#&pid=1&gid=2)
  var photoswipeParseHash = function() {
    var hash = window.location.hash.substring(1),
    params = {};
    console.log('photoswipeParseHash hash = '+hash);

    if(hash.length < 5) {
      console.log('\thash length is short--returning empty parameters');
      return params;
    }

    var vars = hash.split('&');
    for (var i = 0; i < vars.length; i++) {
      if(!vars[i]) {
        continue;
      }
      var pair = vars[i].split('=');
      if(pair.length < 2) {
        continue;
      }
      params[pair[0]] = pair[1];
    }

    if(params.gid) {
        params.gid = parseInt(params.gid, 10);
    }
    return params;
  };

  var openPhotoSwipe = function(index, galleryElement, disableAnimation, fromURL) {
    var pswpElement = document.querySelectorAll('.pswp')[0],
        gallery,
        options,
        items;
    items = parseThumbnailElements(galleryElement);
    // define options (if needed)
    options = {
      // define gallery index (for URL)
      galleryUID: galleryElement.getAttribute('data-pswp-uid'),
      getThumbBoundsFn: function(index) {
        // See Options -> getThumbBoundsFn section of documentation for more info
        var thumbnail = items[index].el.getElementsByTagName('img')[0], // find thumbnail
          pageYScroll = window.pageYOffset || document.documentElement.scrollTop,
          rect = thumbnail.getBoundingClientRect();
        return {x:rect.left, y:rect.top + pageYScroll, w:rect.width};
      }
    };
    // PhotoSwipe opened from URL
    if(fromURL) {
      if(options.galleryPIDs) {
        // parse real index when custom PIDs are used
        // http://photoswipe.com/documentation/faq.html#custom-pid-in-url
        for(var j = 0; j < items.length; j++) {
          if(items[j].pid == index) {
            options.index = j;
            break;
          }
        }
      } else {
        // in URL indexes start from 1
        options.index = parseInt(index, 10) - 1;
      }
    } else {
      options.index = parseInt(index, 10);
    }
    // exit if index not found
    if( isNaN(options.index) ) {
      return;
    }
    if(disableAnimation) {
      options.showAnimationDuration = 0;
    }

    console.log("initializing photoswipe with "+items.length+" items.");
    // Pass data to PhotoSwipe and initialize it
    gallery = new PhotoSwipe( pswpElement, PhotoSwipeUI_Default, items, options);
    gallery.init();
  };

  // loop through all gallery elements and bind events
  var galleryElements = document.querySelectorAll( gallerySelector );

  for(var i = 0, l = galleryElements.length; i < l; i++) {
    galleryElements[i].setAttribute('data-pswp-uid', i+1);
    galleryElements[i].onclick = onThumbnailsClick;
  }

  // Parse URL and open gallery if it contains #&pid=3&gid=1
  var hashData = photoswipeParseHash();
  if(hashData.pid && hashData.gid) {
    console.log('\tabout to call openPhotoSwipe');
    openPhotoSwipe( hashData.pid ,  galleryElements[ hashData.gid - 1 ], true, true );
  }
};

maLib.isImage = function(maJson) {
    if (maJson.metadata && maJson.metadata.contentType) return (maJson.metadata.contentType.substring(0,6) == "image/");
    //kind of a little tricky cuz there is some legacy data with no metadata let alone mimetype, sooooooo
    var regex = new RegExp("\\.(jpe?g|png|gif)$", "i");
    if (!maJson.url) return false;
    return regex.test(maJson.url);
}

maLib.nonImageDisplay = function(maJson, intoElem, caption, maCaptionFunction) {
    if (maLib.isImage(maJson)) return false;
    if (!maJson.url) return false;
    var caption = (caption || '') + (maCaptionFunction ? maCaptionFunction(maJson) : '');
    var regexp = new RegExp("^video/(ogg|m4v|mp4|webm)$");
    if (maJson.metadata && maJson.metadata.contentType && regexp.test(maJson.metadata.contentType)) {
        intoElem.append('<div><video style="width: 100%;" controls>' +
        '<source src="' + maJson.url + '" type="' + maJson.metadata.contentType + '" />' +
        '<div><a target="_new" href="' + maJson.url + '">play video</a></div>' +
        '</video><div class="video-caption">' + caption + '</div></div>');
    } else {
        var filename = maJson.url;
        var i = filename.lastIndexOf("/");
        if (i >= 0) filename = filename.substring(i + 1);
        intoElem.append('<div style="text-align: center;"><a style="padding: 10px; background-color: #AAA; margin: 10px;" target="_new" href="' +
        maJson.url + '">open file <b>' + filename + '</b></a><div class="unknown-caption">' + caption + '</div></div>');
    }
    return true;
}




maLib.getUrl = function(maJson) {
    url = maJson.url;
    if (!url) return;
    if (!wildbookGlobals.username) {
        var wmUrl = maLib.getChildUrl(maJson, '_watermark');
        if (wmUrl) url = wmUrl;
    }
console.warn('>>>>>>>>>>>>>>>>>>>>>>>>> %o', url);
    url = wildbook.cleanUrl(url);
    return url;
}

function mkImg(maJson) {
    var url = maLib.getUrl(maJson);
    return '<img class="lazyload" id="figure-img-' + maJson.id + '" data-enh-mediaAssetId="' + maJson.id + '" src="/cust/mantamatcher/img/individual_placeholder_image.jpg" data-src="' + url + '" itemprop="contentUrl" alt="Image description"/>';
}

// execute above function

$(document).ready(function() {
  //maLib.initPhotoSwipeFromDOM('.my-gallery');
  maLib.initPhotoSwipeFromDOM('#enc-gallery');
});
