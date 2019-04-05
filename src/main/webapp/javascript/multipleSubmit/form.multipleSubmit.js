
// enables submission when recaptcha is checked
function recaptchaCallback() {
    console.log("recaptchaCallback triggered!");
    document.getElementById("sendButton").disabled = false;
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

//This is the workhorse
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
        console.log("Selected files #"+i+": "+files.toString());
    }
    // TODO: This, better. You shouldn't need to iterate through twice. find a better way of appending instead of .htmling those big blobs.
    $("#metadata-tiles-main").html(metadataTiles);
    $("#image-tiles-main").html(imageTiles);
    for (var i=0;i<files.length;i++) {
        multipleSubmitUI.renderImageInBrowser(files[i],i);
    }

    // After all the encounter stuff is loaded..
    $('.encDate').datepicker({
        format: 'mm/dd/yyyy',
        startDate: '-3d'
    });

    // Listen for mousover on all added images.
    listenForMouseoverAllImages(files.length);
    
}

function listenForMouseoverAllImages(imageNumber) {
    for (var i=0;i<imageNumber;i++) {
        var classname = getImageUIIdForIndex(imageNumber);
        var elements = document.getElementsByClassName(classname);
        for (var j=0;j<elements.length;j++) {
            elements[i].onmouseover = function() {
                elements[i].style.display = "block";
            }
            elments[i].onmouseout = function() {
                elements[i].style.display = "none";
            }
        }
    }
}


/*

    TO DO: 

    1. Append image tiles one element at a time instead of all at once.

    2. Append all metadataTile, then each imageTile+image PAIR.. 

    5. think about result state- to a new jsp to wait? stick with single page?

*/

function sendButtonClicked() {

    // SHOWTIME! Send these images off to certain doom
    sendData();
}


