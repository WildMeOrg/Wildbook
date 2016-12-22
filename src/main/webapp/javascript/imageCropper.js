var imageCropper = {};

imageCropper.cropPics = function(selector, ratio) {
  var $parent = $( selector ).first().parent();
  console.log("parent class = "+$parent.attr("class"));
  var image_width = $parent.width();
  console.log("image_width = "+image_width);
  var desired_height = image_width * 1.0/ratio;
  console.log("desired_height = "+desired_height);
  $( selector ).height(desired_height);
  $( selector+' img').css('min-height', desired_height.toString()+'px');

  // center image vertically
  $( selector+' img').each(function(index, value) {
    var vertical_offset = ($(this).height() - desired_height)/2.0;
    $(this).css('margin-top','-'+vertical_offset.toString()+'px');
  });

  $( selector+' img').width('100%');
};

imageCropper.cropInnerPics = function() {
  imageCropper.cropPics('.gallery-info.active .gallery-inner .crop', 16.0/9);
};


imageCropper.cropGridPics = function() {
  imageCropper.cropPics('.gallery-unit .crop', 16.0/9);
};

$( document ).ready(function() {
  //imageCropper.cropGridPics();
});

// basic functions inspired by https://www.darklaunch.com/2013/08/06/jquery-next-prev-with-wrapping
imageCropper.nextWrap = function( $elem, selector ) {
    var $next = $elem.next( selector );
    if ( ! $next.length ) {
        $next = $elem.parent().children( selector ).first();
    }
    return $next;
};
imageCropper.prevWrap = function( $elem, selector ) {
    var $prev = $elem.prev( selector );
    if ( ! $prev.length ) {
        $prev = $elem.parent().children( selector ).last();
    }
    return $prev;
};
