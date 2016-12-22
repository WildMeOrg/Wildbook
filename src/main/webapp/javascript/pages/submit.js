$(function() {
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

    //
    // For flickr you need to register your application domain with an auth-proxy. The aut-proxy that
    // hello.js uses by default is https://auth-server.herokuapp.com/. Sign in and add your application.
    // Reference = "flickr", domain is your server's domain, and cliend id and secret are those for flickr.
    //
    //Initiate hello.js
    /* hello.init({ facebook: {'wildme.org': '363791400412043'}}, { */ // Can base your keys off urls if the service allows/requires
    hello.init({facebook: wildbook.social.apiKey('facebook'),
                google: wildbook.social.apiKey('google'),
                flickr: wildbook.social.apiKey('flickr')},
               {scope: "files,photos"}
    );

    function toggleImage(photo) {
        var name = "socialphoto_" + photo.data("id");

        if (photo.hasClass("selected")) {
            photo.removeClass("selected");

            $("#encounterForm input[name='" + name + "']").remove();
        } else {
            photo.addClass("selected");

            var input = $("<input>").attr("type", "hidden").attr("name", name).attr("class", "social-photo-input").val(photo.data("source"));
            $("#encounterForm").append(input);
        }
    }

    function buildPhotoThumbnail(item, index) {
        var photo = $("<img>");
        photo.addClass("socialphoto");
        photo.attr("src", item.thumbnail);
        if (item.name) {
            photo.attr("title", item.name);
        }

        //
        // If an array of images are presented, it seems that the zeroth one
        // is the full size one and the others are various smaller sizes.
        //
        photo.data("id", index);
        if (item.images && item.images[0]) {
            photo.data("source", item.images[0].source);
        } else {
            photo.data("source", item.source);
        }

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

    function getAlbums(network) {
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

    $.each(wildbook.social.myServices(), function() {
        var service = this.toString(); //comes out a String obect
        if (!wildbook.social.featureEnabled(service, 'images')) {
            return;
        };
        
        var button = $("<button>").attr("title", "Import from " + service).addClass("zocial").addClass("icon").addClass(service);
        button.click(function() {
            getAlbums(service);
        });

        $("#social_image_buttons").append($("<li>").append(button));
    });
});
