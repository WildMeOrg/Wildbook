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
