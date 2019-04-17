
// enables submission when recaptcha is checked
function recaptchaCallback() {
    console.log("recaptchaCallback triggered!");
    document.getElementById("sendButton").disabled = false;
    document.getElementById("recaptcha-checked").value = "true";
}

// file selection and form switching
var maxBytes = 104857600; // This is 100mb but can be overridden in properties through the jsp
function updateSelected(inp) {
    // if there are no files, disable continue. If there is, enable.
    var f = '';
    if (inp.files && inp.files.length) {
        if (inp.files.length > 0) {
            document.getElementById("continueButton").disabled = false;
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
    $(".recaptcha-box").show();
    showSelectedMedia();
}

function backButtonClicked() {
    $(".form-file-selection").show();
    $(".form-define-metadata").hide();
    $("#continueButton").show();
    $("#backButton").hide();
    $("#sendButton").hide();
    $(".recaptcha-box").hide();
}

function showSelectedMedia() {
    var files = document.getElementById('file-selector-input').files;
    var imageTiles = "";
    var metadataTiles = "";
    var numEnc = $('#numberEncounters').val();

    for (var i=0;i<numEnc;i++) {
        // The same every time!
        metadataTiles += multipleSubmitUI.generateMetadataTile(i);
    }

    for (var i=0;i<files.length;i++) {
        imageTiles += multipleSubmitUI.generateImageTile(files[i],i);
        // we actually want to do this for the number of encs defined in the number input..
        //console.log("Selected files #"+i+": "+files.toString());
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
    var imgDiv = document.getElementById("img-overlay-"+index);
    var tileDiv = document.getElementById("image-tile-div-"+index);
    if (!$(tileDiv).hasClass('img-selected')) { 
        // pop a label over the top that says 'click to select'
    }
    imgDiv.hidden = false;
}

function hideOverlay(index) {
    var imgDiv = document.getElementById("img-overlay-"+index);
    var tileDiv = document.getElementById("image-tile-div-"+index);
    // ignore if we have clicked this item to focus
    if (!$(tileDiv).hasClass('img-selected')) {
        imgDiv.hidden = true;
    }
}

function imageTileClicked(index) {
    var tileDiv = document.getElementById(multipleSubmitUI.getImageIdForIndex(index));
    console.log("Clicked! id="+multipleSubmitUI.getImageIdForIndex(index));
    if ($(tileDiv).hasClass('img-selected')) {
        $(tileDiv).removeClass('img-selected');
    } else {
        $(tileDiv).addClass('img-selected');
    }    
}

function showEditMetadata(index) {
    console.log("got the click! on element: "+index);
    var editDiv = document.getElementById("enc-metadata-inner-"+index);
    console.log("Where it at? --> "+editDiv);
    if (editDiv.classList.contains("edit-closed")) {
        $(editDiv).slideDown();
        editDiv.classList.remove("edit-closed");
    } else {
        $(editDiv).slideUp();
        editDiv.classList.add("edit-closed");
    }
}

/*

    TO DO: 

    1. Append image tiles one element at a time instead of all at once. <---- !!!

    2. Allow click/focus on each image edit

    3. build out enc metadata form sections w/ show hide

    4. make img-overlay inputs the correct type.. build dropdowns based on previous information.

    5. think about result state- to a new jsp to wait? stick with single page?

*/

function sendButtonClicked() {
    // SHOWTIME! Send these images off to certain doom
    multipleSubmitAPI.sendData(function(result){


    });
}
