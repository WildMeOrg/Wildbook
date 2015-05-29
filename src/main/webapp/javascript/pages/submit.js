$(function() {
    var center = new google.maps.LatLng(10.8, 160.8);

    var map = null;
    var marker = null;

    function placeMarker(location) {
        if (marker!=null) {marker.setMap(null);}
        marker = new google.maps.Marker({
              position: location,
              map: map
          });

          //map.setCenter(location);

        var ne_lat_element = document.getElementById('lat');
        var ne_long_element = document.getElementById('longitude');

        ne_lat_element.value = location.lat();
        ne_long_element.value = location.lng();
    }

    function initialize() {
        var mapZoom = 3;
        if($("#map_canvas").hasClass("full_screen_map")){mapZoom=3;}

        if (marker!=null) {
            center = new google.maps.LatLng(10.8, 160.8);
        }

        map = new google.maps.Map(document.getElementById('map_canvas'), {
              zoom: mapZoom,
              center: center,
              mapTypeId: google.maps.MapTypeId.HYBRID
            });

        if (marker!=null) {
            marker.setMap(map);
        }

        //adding the fullscreen control to exit fullscreen
        var fsControlDiv = document.createElement('DIV');
        addFullscreenButton(fsControlDiv, map);
        fsControlDiv.index = 1;
        map.controls[google.maps.ControlPosition.TOP_RIGHT].push(fsControlDiv);

        google.maps.event.addListener(map, 'click', function(event) {
            placeMarker(event.latLng);
        });
    }

    function fullScreen() {
        $("#map_canvas").addClass('full_screen_map');
        $('html, body').animate({scrollTop:0}, 'slow');
        initialize();

        //hide header
        $("#header_menu").hide();

        //if(overlaysSet){overlaysSet=false;setOverlays();}
    }

    function exitFullScreen() {
        $("#header_menu").show();
        $("#map_canvas").removeClass('full_screen_map');

        initialize();
        //if(overlaysSet){overlaysSet=false;setOverlays();}
    }

    //making the exit fullscreen button
    function addFullscreenButton(controlDiv, map) {
        // Set CSS styles for the DIV containing the control
        // Setting padding to 5 px will offset the control
        // from the edge of the map
        controlDiv.style.padding = '5px';

        // Set CSS for the control border
        var controlUI = document.createElement('DIV');
        controlUI.style.backgroundColor = '#f8f8f8';
        controlUI.style.borderStyle = 'solid';
        controlUI.style.borderWidth = '1px';
        controlUI.style.borderColor = '#a9bbdf';;
        controlUI.style.boxShadow = '0 1px 3px rgba(0,0,0,0.5)';
        controlUI.style.cursor = 'pointer';
        controlUI.style.textAlign = 'center';
        controlUI.title = 'Toggle the fullscreen mode';
        controlDiv.appendChild(controlUI);

        // Set CSS for the control interior
        var controlText = document.createElement('DIV');
        controlText.style.fontSize = '12px';
        controlText.style.fontWeight = 'bold';
        controlText.style.color = '#000000';
        controlText.style.paddingLeft = '4px';
        controlText.style.paddingRight = '4px';
        controlText.style.paddingTop = '3px';
        controlText.style.paddingBottom = '2px';
        controlUI.appendChild(controlText);

        //toggle the text of the button
        if($("#map_canvas").hasClass("full_screen_map")){
            controlText.innerHTML = '<%=props.getProperty("exitFullscreen")%>';
        } else {
            controlText.innerHTML = '<%=props.getProperty("fullscreen")%>';
        }

        // Setup the click event listeners: toggle the full screen
        google.maps.event.addDomListener(controlUI, 'click', function() {
            if($("#map_canvas").hasClass("full_screen_map")) {
                exitFullScreen();
            } else {
                fullScreen();
            }
        });
    }

    google.maps.event.addDomListener(window, 'load', initialize);

    function resetMap() {
        var ne_lat_element = document.getElementById('lat');
        var ne_long_element = document.getElementById('longitude');
        ne_lat_element.value = "";
        ne_long_element.value = "";
    }

    $(window).unload(resetMap);

    //
    // Call it now on page load.
    //
    resetMap();

    $( "#datepicker" ).datetimepicker({
      changeMonth: true,
      changeYear: true,
      dateFormat: 'yy-mm-dd',
      maxDate: '+1d',
      controlType: 'select',
      alwaysSetTime: false
    });
    $( "#datepicker" ).datetimepicker( $.timepicker.regional[ "<%=langCode %>" ] );

    $( "#releasedatepicker" ).datepicker({
        changeMonth: true,
        changeYear: true,
        dateFormat: 'yy-mm-dd'
    });
    $( "#releasedatepicker" ).datepicker( $.datepicker.regional[ "<%=langCode %>" ] );
    $( "#releasedatepicker" ).datepicker( "option", "maxDate", "+1d" );


    /* //Get User
    hello.on('auth.login', function(auth){
        // Get Profile
        hello.api(auth.network + ':me', function(r){
            if(!r||r.error) {
                wildbook.showAlert(r.error.message);
                return;
            }
            document.getElementById(auth.network).innerHTML = "Get Albums from " + r.name + " at "+auth.network+"";
        });
    });
     */

    //Initiate hellojs
    /* hello.init({ facebook: {'wildme.org': '363791400412043'}}, { */ // Can base your keys off urls if the service allows/requires
    hello.init({facebook: wildbookGlobals.social.facebookAppId,
    /*             twitter: "UTEfL90bUGqXcsERcFbJRU4Ng", */
                google: "195771644717-2am21965cpsueu7u49f6dgnnmqg7nmm1.apps.googleusercontent.com",
                flickr: "d8de31bc9e774909bdcd77d0c3f7c6e2"}, {
       scope: "files, photos"/* ,
       redirect_uri : "../redirect.html" */
    });
});

wildbook.submit = (function() {
    function toggleImage(photo) {
        var name = "socialphoto_" + photo.data("id");

        if (photo.hasClass("selected")) {
            photo.removeClass("selected");

            $("#encounterForm input[name='" + name + "']").remove();
        } else {
            photo.addClass("selected");

            var input = $("<input>").attr("type", "hidden").attr("name", name).attr("class", "social-photo-input").val(photo.data("source"));
            $("#encounterForm").append(input);

//            reader = new FileReader();
//            reader.onload = function (event) {
//                $("#encounterForm input[name=' + name + ']").val(event.target.result);
//            };
//            reader.readAsDataURL(photo.data("source"));
        }
    }

    function buildPhotoThumbnail(item, index) {
        var photo = $("<img>");
        photo.addClass("socialphoto");
        photo.attr("src", item.thumbnail);
        if (item.name) {
            photo.attr("title", item.name);
        }

        photo.data("id", index);
        photo.data("source", item.images[0].source);

        photo.click(function() {
            toggleImage(photo);
        });

        $("#socialphotos").append(photo);
    }

    function getPhotos(network, id) {
        $("#socialphotos").empty();

        hello( network ).api('me/album', {
            id: id,
            limit:10
        }, function(resp){
            if(resp.error){
                wildbook.showAlert(resp.error.message);
                return;
            }
            else if(!resp.data || resp.data.length === 0) {
                wildbook.showAlert("There are no photos in this album");
                return;
            }

            for (var ii = 0; ii < resp.data.length; ii++) {
                buildPhotoThumbnail(resp.data[ii], ii);
            }
        });
    }

    return {
        showUploadBox: function() {
            $("#submitsocialmedia").addClass("hidden");
            $("#submitupload").removeClass("hidden");
        },

        getAlbums: function(network) {
            $("#submitsocialmedia").removeClass("hidden");
            $("#submitupload").addClass("hidden");

            $("#socialalbums").empty();
            $("#socialphotos").empty();
            //
            // Setting force:false means we'll only trigger auth flow if the user is not
            // already signed in with the correct credentials
            //
            hello(network).login({force:false}, function(auth) {
                // Get albums
                hello.api(network + ':me/albums', function(resp) {
                    if(!resp || resp.error) {
                        wildbook.showAlert("Could not open albums from " + network + ": " + resp.error.message);
                        return;
                    } else if(!resp.data || resp.data.length === 0) {
                        wildbook.showAlert("There does not appear to be any photo albums in your account");
                        return
                    }

                    // Build buttons with the albums
                    $.each(resp.data, function() {
                        var button = $('<button>').text(this.name).prop("title", this.name)
                                                  .addClass("btn btn-block btn-primary");
                        var id = this.id;
                        button.click(function() {
                            getPhotos(network, id);
                        });

                        $('#socialalbums').append(button);
                    });
                });
            }, function(ex) {
                wildbook.showAlert(ex.error.message);
            });
        }
    };
})();
