var galFunc = {};

galFunc.cropPics = function(selector, ratio) {
  var image_width = $( selector ).parent().width();
  var desired_height = image_width * 1.0/ratio;
  $( selector ).height(desired_height);
  $( selector+' img').css('min-height', desired_height.toString()+'px');

  // center image vertically
  $( selector+' img').each(function(index, value) {
    var vertical_offset = ($(this).height() - desired_height)/2.0;
    $(this).css('margin-top','-'+vertical_offset.toString()+'px');
  });

  $( selector+' img').width('100%');
};

galFunc.cropInnerPics = function() {
  galFunc.cropPics('.gallery-info.active .gallery-inner .crop', 16.0/9);
};


galFunc.cropGridPics = function() {
  galFunc.cropPics('.gallery-unit .crop', 16.0/9);
};


$( document ).ready(function() {
  galFunc.cropGridPics();
});

// basic functions inspired by https://www.darklaunch.com/2013/08/06/jquery-next-prev-with-wrapping
galFunc.nextWrap = function( $elem, selector ) {
    var $next = $elem.next( selector );
    if ( ! $next.length ) {
        $next = $elem.parent().children( selector ).first();
    }
    return $next;
};
galFunc.prevWrap = function( $elem, selector ) {
    var $prev = $elem.prev( selector );
    if ( ! $prev.length ) {
        $prev = $elem.parent().children( selector ).last();
    }
    return $prev;
};
