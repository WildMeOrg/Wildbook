
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
    $("#recaptcha-div").hide();
    showSelectedMedia();
}

function backButtonClicked() {
    $(".form-file-selection").show();
    $(".form-define-metadata").hide();
    $("#continueButton").show();
    $("#backButton").hide();
    $("#sendButton").hide();
    $("#recaptcha-div").show();
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
    document.getElementsByClassName("action-message")[0].innerHTML = "";
    let files = document.getElementById('file-selector-input').files;
    let imageTiles = "";
    let metadataTiles = "";
    let numEnc = document.getElementById("number-encounters").value;
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

    multipleSubmitUI.updateFileCounters();

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

function toggleEncImages(index) {
    let allImageTiles = document.getElementsByClassName("image-tile-div");
    for (let i=0;i<allImageTiles.length;i++) {
        var selectEl = allImageTiles[i].querySelector(".enc-num-dropdown");
        if (selectEl.options[selectEl.selectedIndex].value==index) {
            let disp = allImageTiles[i].style.display;
            if (disp!=="none") {
                document.getElementById("hide-enc-images-btn-"+index).classList.add("hidden-input");
                document.getElementById("show-enc-images-btn-"+index).classList.remove("hidden-input");
                $(allImageTiles[i]).fadeOut();
            } else {
                document.getElementById("show-enc-images-btn-"+index).classList.add("hidden-input");
                document.getElementById("hide-enc-images-btn-"+index).classList.remove("hidden-input");
                $(allImageTiles[i]).fadeIn();
            }
        } 
    }
}

// flip on all borders for imgs that have this enc selected..
function toggleImageHighlights(state,index) {
    var allImageTiles = document.getElementsByClassName("image-tile-div");
    for (var i=0;i<allImageTiles.length;i++) {
        var imgEl = allImageTiles[i].querySelector(".image-element");
        var selectedEnc = $("#enc-num-dropdown-"+i+" :selected").val();
        //console.log("Selected enc: "+JSON.stringify(selectedEnc));
        if (selectedEnc!=null&&selectedEnc==index) {
            //console.log("adding borderCol: "+borderCol+"  and state: "+state);
            if (state=="on") {
                multipleSubmitUI.addEncounterLabel(imgEl, selectedEnc);
            }
            if (state=="off") {
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
    // the counter on the show/hide buttons
    multipleSubmitUI.updateFileCounters(value);
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

function updateSummary(index) {
    document.getElementById("no-details-"+index).classList.add("hidden-input");
    let dateField = document.getElementById("enc-date-"+index);
    let locationField = document.getElementById("loc-enc-input-"+index);
    let speciesField = document.getElementById("spec-enc-input-"+index);
    let commentField = document.getElementById("enc-comments-"+index);

    console.log("dateField = "+dateField.value+" locationField = "+locationField.value+" speciesField = "+speciesField.value+" commentField = "+commentField.value);
    
    let commentsSummary = document.getElementById("summary-comments-"+index).getElementsByClassName("it-value")[0];
    console.log("comment innerHTML: "+commentsSummary.innerHTML);
    if (commentField.value!=""&&commentField.value!="...") {
        commentsSummary.innerHTML = commentField.value.substring(0, 18) + "...";
        commentsSummary.parentElement.classList.remove("hidden-input");
    }
    
    let dateSummary = document.getElementById("summary-date-"+index).getElementsByClassName("it-value")[0];
    console.log("dateSummary.innerHTML: "+dateSummary.innerHTML+" aadnnndd dateField.value : "+dateField.value);
    if (dateField.value!=dateSummary.innerHTML&&dateField.value!=undefined) {
        dateSummary.innerHTML = dateField.value;
        dateSummary.parentElement.classList.remove("hidden-input");
    }
    
    let locationSummary = document.getElementById("summary-location-"+index).getElementsByClassName("it-value")[0];
    console.log("loc innerHTML: "+locationSummary.innerHTML);
    console.log("loc field typeof : "+(typeof locationField.value));
    if (locationField.value!="null"&&locationField.value!=null) {
        locationSummary.innerHTML = locationField.value;
        locationSummary.parentElement.classList.remove("hidden-input");
    }

    let speciesSummary = document.getElementById("summary-species-"+index).getElementsByClassName("it-value")[0];
    console.log("species innerHTML: "+speciesSummary.innerHTML);
    if (speciesField.value!="null") {
        speciesSummary.innerHTML = speciesField.value;
        speciesSummary.parentElement.classList.remove("hidden-input");
    }
}

function numImagesForEnc(encNum) {
    let allImageTiles = document.getElementsByClassName("image-tile-div");
    //let numEnc = document.getElementById("number-encounters").value;
    let numImgs = 0;
    for (let i=0;i<allImageTiles.length;i++) {
        var selectedEnc = $("#enc-num-dropdown-"+i+" :selected").val();
        if (encNum==selectedEnc) {
            numImgs++;
        }
    }
    return numImgs; 
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

window.onload = $(function() {
    $('[data-toggle="tooltip"]').tooltip();
    // this is all for drag and drop file addition
    let fileDropArea = document.getElementById("file-drop-area");
    fileDropArea.addEventListener("dragenter",preventDefaultAndPropagation, false);
    fileDropArea.addEventListener("dragleave",preventDefaultAndPropagation, false);
    fileDropArea.addEventListener("dragover",preventDefaultAndPropagation, false);
    fileDropArea.addEventListener("drop",preventDefaultAndPropagation, false);
    fileDropArea.addEventListener("drop",addFilesFromDragAndDrop, false);
})

var preventDefaultAndPropagation = function(e){
    e.preventDefault();
    e.stopPropagation();
}

var addFilesFromDragAndDrop = function(e) {
    let data = e.dataTransfer;
    if (data.files!=null&&data.files.length>0) {
        let fileInput = document.getElementById("file-selector-input");
        fileInput.files = data.files;
        updateSelected(fileInput);
        console.log("drag n drop got "+fileInput.files.length+" files");
    }
}

