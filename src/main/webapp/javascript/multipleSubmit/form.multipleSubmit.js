
// enables submission when recaptcha is checked
function recaptchaCallback() {
    console.log("recaptchaCallback triggered!");
    if (document.getElementById("file-selector-input").files.length>0) { // num files in input?
        document.getElementById("continueButton").disabled = false;
        document.getElementById("sendButton").disabled = false;
    } else {out("Please add image files to continue.");}

    document.getElementById("recaptcha-checked").value = "true";
}

// file selection and form switching
var maxBytes = 104857600; // This is 100mb but can be overridden in properties through the jsp
function updateSelected(inp) {
    // if there are no files, disable continue. If there is, enable.
    var f = '';
    if (inp.files && inp.files.length) {
        if (inp.files.length > 0) {
            if (document.getElementById("recaptcha-checked").value=="true") {
                document.getElementById("continueButton").disabled = false;
                document.getElementById("sendButton").disabled = false;
            } else {out("Please check ReCaptcha to continue.");}
        }
        var all = [];
        for (var i = 0 ; i < inp.files.length ; i++) {
            if (inp.files[i].size > maxBytes) {
                all.push('<span class="error">' + inp.files[i].name + ' (' + Math.round(inp.files[i].size / (1024*1024)) + 'MB is too big, 100MB max upload size.)</span>');
            } else {
                all.push(inp.files[i].name + ' (' + Math.round(inp.files[i].size / 1024) + 'k)');
            }
        }
        f = '<b>' + inp.files.length + ' file' + ((inp.files.length == 1) ? '' : 's') + ':</b> ' + all.join(', ');
    } else {
        f = inp.value;
    }
    document.getElementById('input-file-list').innerHTML = f;
}

function continueButtonClicked() {
    $(".form-file-selection").hide();
    $(".form-define-metadata").show();
    $("#continueButton").hide();
    $("#backButton").show();
    $("#sendButton").show();
    $(".recaptcha-box").hide();
    showSelectedMedia();
}

function backButtonClicked() {
    $(".form-file-selection").show();
    $(".form-define-metadata").hide();
    $("#continueButton").show();
    $("#backButton").hide();
    $("#sendButton").hide();
    $(".recaptcha-box").show();
    clearSelectedMedia();
}

function sendButtonClicked() {
    console.log("Clicked!");
    // SHOWTIME! Send these images off to certain doom
    multipleSubmitAPI.sendData(function(result){
        console.log(JSON.stringify(result));
    });
}

function showSelectedMedia() {
    var files = document.getElementById('file-selector-input').files;
    var imageTiles = "";
    var metadataTiles = "";
    var numEnc = document.getElementById("number-encounters").value;
    for (var i=0;i<numEnc;i++) {
        metadataTiles += multipleSubmitUI.generateMetadataTile(i);
    }
    for (var i=0;i<files.length;i++) {
        imageTiles += multipleSubmitUI.generateImageTile(files[i],i);
    }
    // TODO: This, better. You shouldn't need to iterate through twice. find a better way of appending instead of .htmling those big blobs.
    $("#metadata-tiles-main").html(metadataTiles);
    $("#image-tiles-main").html(imageTiles);
    for (var i=0;i<files.length;i++) {
        multipleSubmitUI.renderImageInBrowser(files[i],i);
    }
    $('.encDate').datepicker({
        format: 'mm/dd/yyyy',
        startDate: '-3d'
    });
    // clear leftover errors from provious page 
}

function clearSelectedMedia() {
    var metadata = document.getElementById("metadata-tiles-main");
    var images = document.getElementById("image-tiles-main");
    while (metadata.firstChild) {
        metadata.removeChild(metadata.firstChild);
    }
    while (images.firstChild) {
        images.removeChild(images.firstChild);
    }
    out("Please select some images to upload.");
}

//function listenForMouseoverAllImages() {
//    for (var i=0;i<imageNumber;i++) {
//        var elements = document.getElementsByClassName('img-input');
//        for (var j=0;j<elements.length;j++) {
//            document.getElementById(multipleSubmitUI.getImageIdForIndex(i)).onmouseover = function() {
//                this.style.visibility = "visible";
//            } 
//            elements[j].onmouseover = function() {
//                this.style.visibility = "visible";
//            }
//        }
//    }
//}

function showOverlay(index) {
    var tileDiv = document.getElementById("image-tile-div-"+index);
    $(tileDiv).addClass("tile-selected");
    var overlayDiv = document.getElementById("img-overlay-"+index);
    $(overlayDiv).addClass('img-selected');
    $("#img-"+index).addClass('img-selected');

    //if (!$(tileDiv).hasClass('img-selected')) { 
        // we don't need this yet.. overlay doesn't require focus with current options        
    //}

    overlayDiv.hidden = false;
}

function hideOverlay(index) {
    var tileDiv = document.getElementById("image-tile-div-"+index);
    $(tileDiv).removeClass("tile-selected");
    var overlayDiv = document.getElementById("img-overlay-"+index);
    $(overlayDiv).removeClass('img-selected');
    $("#img-"+index).removeClass('img-selected');
    // ignore if we have clicked this item to focus
    //if (!$(tileDiv).hasClass('img-selected')) {
    //}
    overlayDiv.hidden = true;
}

function imageTileClicked(index) {

    // gonna SIMPLIFY right now since we don't really need anything on focus

    //var tileDiv = document.getElementById(multipleSubmitUI.getImageIdForIndex(index));
    //console.log("Clicked! id="+multipleSubmitUI.getImageIdForIndex(index));
    //if ($(tileDiv).hasClass('img-selected')) {
    //    $(tileDiv).removeClass('img-selected');
    //} else {
    //    var anySelected = document.getElementsByClassName("img-selected");
    //    Array.prototype.slice.call(anySelected).forEach(function(tile) {
    //        $(tile).removeClass("img-selected");
    //    });
    //    $(tileDiv).addClass('img-selected');
    //}    
}

function showEditMetadata(index) {
    console.log("got the click! on element: "+index);
    var editDiv = document.getElementById("enc-metadata-inner-"+index);
    if (editDiv.classList.contains("edit-closed")) {
        $(editDiv).slideDown();
        editDiv.classList.remove("edit-closed");
    } else {
        $(editDiv).slideUp();
        editDiv.classList.add("edit-closed");
    }
}

function out(string) {
    let outStr = "<p class=\"out-message\"><b>"+string+"</b></p>";
    $(".action-message").html(outStr);
    setTimeout(function(){
        $(".action-message").empty();
    }, 3000)
}


// get all lang appropriate copy from properties, hold on to 
// before we populate in big ol global thingy
// just do this fast
var props;
$(document).ready(function(){
    multipleSubmitAPI.getProperties(function(result){
        console.log("----------------> RESULT:  "+JSON.stringify(result));
        props = result;
    });
});
//console.log(JSON.stringify(props));
function txt(str) {
    return props[str];
}

