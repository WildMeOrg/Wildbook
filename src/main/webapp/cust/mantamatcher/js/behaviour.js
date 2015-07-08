$(function() {
	var documentHeight = window.innerHeight;
	var headerHeight = $(".page-header").outerHeight();

  checkScrollHeight();
  
  $(window).resize(function() {
    checkScrollHeight(); 
  });

  $(window).on("scroll", function() {
    checkScrollHeight();
  });

  // Setting hero to window height and hero video
	$(".hero").css("min-height", documentHeight - headerHeight - 80);
  $(".hero .embed-container").css("height", documentHeight - headerHeight - 130);

  var vimeoPlayer = document.querySelector('iframe');
  $f(vimeoPlayer).addEvent('ready', ready);

    function ready(vimeoPlayer) {

        froogaloop = $f(vimeoPlayer);

        $("#watch-movie").on("click", function(event) {
          event.stopPropagation();
          $(".hero .container").fadeOut("slow");
          $(".hero .video-wrapper").fadeIn("slow", function(){
              froogaloop.api("play");
          });
        });

        $("html").on("click", function(event) {
          event.stopPropagation();
          $(".hero .container").fadeIn("slow");
          $(".hero .video-wrapper").fadeOut("slow", function(){
            froogaloop.api("pause");
          });
        });
    }

}); // document ready

function checkScrollHeight() {
  var scrollPos = $(document).scrollTop();   
  
  if ($(window).width() < 768) {
    updateScrollPos(scrollPos);
  } else {
    $("body").removeClass("scrolled");
  }
}

function updateScrollPos(scrollPos) {
  if (scrollPos > 50) {
    $("body").addClass("scrolled");
  } else if (scrollPos < 50) {
    $("body").removeClass("scrolled");
  }
}