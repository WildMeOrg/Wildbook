
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
    // SHOWTIME! Send these images off to certain doom, then show result
    $("#metadata-tiles-main").hide();
    $("#image-tiles-main").hide();
    $("#results-main").html(multipleSubmitUI.generateWaitingText());
    setInterval(function(){
        $(".pulsing").fadeIn(350).delay(1200).fadeOut(350);
    });
    multipleSubmitAPI.sendData(function(result){    
        $("#results-main").html(multipleSubmitUI.generateResultPage(result));
        $(".nav-buttons").empty();
        $("#input-file-list").remove();
        $(".form-spacer").remove();  
    });
}

// first 7 encs are colorblind friendly.
var safeColors = [
                  "#d53e4f",
                  "#fc8d59",
                  "#fee08b",
                  "#ffffbf",
                  "#e6f598",
                  "#99d594",
                  "#3288bd"
                            ];

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
    $("#metadata-tiles-main").html(metadataTiles);
    $("#image-tiles-main").html(imageTiles);
    for (var i=0;i<files.length;i++) {
        multipleSubmitUI.renderImageInBrowser(files[i],i);
    }
    for (var i=0;i<numEnc;i++) {
        var color;
        if (i<safeColors.length) {
            color = safeColors[i];;
        } else {
            let randCol = randomColor();
            safeColors.push(randCol);
            color = randCol
        }
        document.getElementById("encounter-label-"+i).style.backgroundColor = color;
    }
    $('.encDate').datepicker({
        format: 'mm/dd/yyyy',
        startDate: '-3d'
    });
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
        toggleImageHighlights("on",index);
    } else {
        $(editDiv).slideUp();
        editDiv.classList.add("edit-closed");
        toggleImageHighlights("off",index);
    }
}

// flip on all borders for imgs that have this enc selected..
function toggleImageHighlights(state,index) {
    var allImageTiles = document.getElementsByClassName("image-tile-div");
    for (var i=0;i<allImageTiles.length;i++) {
        var imgEl = allImageTiles[i].querySelector(".image-element");
        var selectedEnc = $("#enc-num-dropdown-"+i+" :selected").val();
        console.log("Selected enc: "+JSON.stringify(selectedEnc));
        if (selectedEnc!=null&&selectedEnc==index) {
            //console.log("adding borderCol: "+borderCol+"  and state: "+state);
            if (state=="on") {
                //add some label with enc-num
                multipleSubmitUI.addEncounterLabel(imgEl, selectedEnc);
            }
            if (state=="off") {
                //remove said label with enc-num
                multipleSubmitUI.removeEncounterLabel(imgEl, selectedEnc);
            }
        }
    }
}

// triggered by onchange img encounter select.. only add highlight if already editing
function highlightOnEdit(index) {
    //console.log("get enc number onchange for state="+state+" and index="+index);
    console.log("get enc number onchange for index="+index);
    var encSelected = document.getElementById("enc-num-dropdown-"+index);
    var value = encSelected.options[encSelected.selectedIndex].value;
    var editDiv = document.getElementById("enc-metadata-inner-"+value);
    if (!editDiv.classList.contains("edit-closed")) {
        toggleImageHighlights("on",value);
    } else {
        toggleImageHighlights("off",value);
    }
}

function out(string) {
    let outStr = "<p class=\"out-message\"><b>"+string+"</b></p>";
    $(".action-message").empty();
    $(".action-message").html(outStr);
    //setTimeout(function(){
    //    $(".action-message").empty();
    //}, 3000)
}

// get all lang appropriate copy from properties, hold on to 
// before we populate in big ol global thingy
// just do this fast
var props;
$(document).ready(function(){
    multipleSubmitAPI.getProperties(function(result){
        //console.log("----------------> RESULT:  "+JSON.stringify(result));
        props = result;
    });
});
//console.log(JSON.stringify(props));
function txt(str) {
    if (multipleSubmitUI.hasVal(props[str])) {return props[str]};
    return null;
}

function baseURL() {
    var urlOb = new URL(window.location.href).host.toString();
    //console.log("urlOb "+urlOb);
    return urlOb;
}

function randomColor() {
    var hexCode = "#";
    var chars = [0,1,2,3,4,5,6,7,8,9,'a','b','c','d','e','f'];
    while (hexCode.length<7) {
        hexCode += chars[Math.floor(Math.random()*16)];
    }
    return hexCode;
}

//recalculate label position after window resize, with delay so event dont fire like crazy
var recalcDelay;
window.onresize = function() {
    //let numEncs = document.getElementById("number-encounters").value;
    clearTimeout(recalcDelay);
    recalcDelay = setTimeout(function() {
        let allImageTiles = document.getElementsByClassName("image-tile-div");
        for (let i=0;i<allImageTiles.length;i++) {
            let imgEl = allImageTiles[i].querySelector(".image-element");;
            if (imgEl.parentNode.getElementsByClassName("chosen-enc-label")!=undefined&&imgEl.parentNode.getElementsByClassName("chosen-enc-label").length>0) {
                let selectedEnc = $("#enc-num-dropdown-"+i+" :selected").val();
                multipleSubmitUI.addEncounterLabel(imgEl, selectedEnc);
            }    
        }
    },300);
};

