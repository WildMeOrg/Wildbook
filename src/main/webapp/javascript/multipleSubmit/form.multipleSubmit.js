
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
var maxUploadBytes = 250 * (1024*1024);
function updateSelected(inp) {
    var f = '';
    let totalBytes = 0;
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
                all.push('<span class="file-list-name">' + inp.files[i].name + ' (' + Math.round(inp.files[i].size / (1024*1024)) + 'MB is too big, 100MB max file size.)</span>');
            } else {
                all.push(inp.files[i].name + ' (' + Math.round(inp.files[i].size / 1024) + 'k)&nbsp&nbsp');
                totalBytes += inp.files[i].size;
            }
        }
        f = '<b>' + inp.files.length + ' file' + ((inp.files.length == 1) ? '' : 's') + ':</b> ' + all.join(', ');
        f += '<br><b>'+txt('totalSize')+" "+(totalBytes/(1024*1024)).toFixed(2)+' MB</b>';
        if (totalBytes>maxUploadBytes) {
            f += '<br><b class=\"total-size-exceeded\">'+txt('totalSizeExceeded')+'</b>';
            f += '<br><b class=\"total-size-exceeded\">'+txt('recommendedMaxSize')+" "+(maxUploadBytes/(1024*1024)).toFixed(2)+' MB</b>';
        }
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
    $(".recaptcha-div").hide();
    // clear text in #instructionsBody, add alternate
    $("#instructionsBody").empty();
    $("#instructionsBody").text(txt("instructionsCurate")); 
    showSelectedMedia();
}

function backButtonClicked() {
    $(".form-file-selection").show();
    $(".form-define-metadata").hide();
    $("#continueButton").show();
    $("#backButton").hide();
    $("#sendButton").hide();
    $(".recaptcha-div").show();
    $("#gallery-header").show();
    // clear text in #instructionsBody, replace original  
    // clear show/hide buttons from curation screen
    $("#instructionsBody").empty();
    $("#gallery-header").empty();
    $("#instructionsBody").text(txt("instructionsBody"));
    clearSelectedMedia();
}

function sendButtonClicked() {
    let shouldProceed = false;
    console.log("Clicked!");
    if (hasRequiredFields()||document.getElementById("sendButton").classList.contains("missing-fields-confirmed")) {
        document.getElementById("missing-data-message").innerHTML = ""; // y no work?
        document.getElementById("sendButton").classList.remove("missing-fields-confirmed");
        shouldProceed = true; 
    } else {
        document.getElementById("sendButton").classList.add("missing-fields-confirmed");
    }
    // SHOWTIME! Send these images off to certain doom, then show result
    if (shouldProceed==true) {
        $("#metadata-tiles-main").hide();
        $("#image-tiles-main").hide();
        $("#gallery-header").hide();
        $("#results-main").html(multipleSubmitUI.generateWaitingText());
        setInterval(function(){
            $(".pulsing").fadeIn(350).delay(1200).fadeOut(350);
        });
        multipleSubmitAPI.sendData(function(result){    
            $("#results-main").html(multipleSubmitUI.generateResultPage(result));
            $(".nav-buttons").empty();
            $(".form-spacer").remove();  
        });
    }
}

function hasRequiredFields() {
    let msg = "";
    let numEncs = multipleSubmitUI.encsDefined();
    for (let i=0;i<numEncs;i++) {   
        let date = document.getElementById("enc-date-"+i).value;
        let location = document.getElementById("loc-enc-input-"+i).value;
        console.log("Date: "+date+" Location: "+location);
        if (!multipleSubmitUI.hasVal(date)||!multipleSubmitUI.hasVal(location)) {
            msg += "<p class=\"missing-info\">"+txt("encounter")+" "+(i+1)+" "+txt("missingInformation")+"</p>";
            if (!multipleSubmitUI.hasVal(date)) {
                msg += "<p>"+txt("dateField")+"</p>";
            }
            if (!multipleSubmitUI.hasVal(location)) {
                msg += "<p>"+txt("locationField")+"</p>";
            }
        }
    }
    if (multipleSubmitUI.hasVal(msg)) {
        msg += "<p class=\"missing-info\">"+txt("askContinue")+"</p>";
        msg += "<p class=\"missing-info\">"+txt("continueAgain")+"</p>";
        document.getElementById("missing-data-message").innerHTML = msg;
        return false;
    }
    return true;
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
    //let fileList = document.getElementById('file-list-container');
    //fileList.innerHTML = "";
    //fileList.outerHTML = "";
    let fileList = document.getElementById('input-file-list');
    fileList.innerHTML = "";
    document.getElementsByClassName("action-message")[0].innerHTML = "";
    let files = document.getElementById('file-selector-input').files;
    let imageTiles = "";
    let metadataTiles = "";
    let numEncs = document.getElementById("number-encounters").value;
    let gallery = multipleSubmitUI.generateGalleryComponent(numEncs);
    for (let i=0;i<numEncs;i++) {
        metadataTiles += multipleSubmitUI.generateMetadataTile(i);
    }
    for (let i=0;i<files.length;i++) {
        imageTiles += multipleSubmitUI.generateImageTile(files[i],i);
    }

    $("#metadata-tiles-main").html(metadataTiles);
    $("#gallery-header").html(gallery);
    $("#image-tiles-main").html(imageTiles);
    
    for (var i=0;i<files.length;i++) {
        multipleSubmitUI.renderImageInBrowser(files[i],i);
    }
    for (var i=0;i<numEncs;i++) {
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

    $(".encDate").datepicker({
        format: 'mm/dd/yyyy',
        startDate: '-3d'
    });

    multipleSubmitUI.generateAssociatedImageList();
    multipleSubmitUI.updateFileCounters();
}

function getFileFromFilename(fileName) {
    let files = document.getElementById('file-selector-input').files;
    for (let i=0;i<files.length;i++) {
        if (files[i].name==fileName) {
            return files[i];
        }
    }
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

function showOverlay(index) {
    var tileDiv = document.getElementById("image-tile-div-"+index);
    $(tileDiv).addClass("tile-selected");
    var overlayDiv = document.getElementById("img-overlay-"+index);
    $(overlayDiv).addClass('img-selected');
    $("#img-"+index).addClass('img-selected');

    overlayDiv.hidden = false;
}

function hideOverlay(index) {
    var tileDiv = document.getElementById("image-tile-div-"+index);
    $(tileDiv).removeClass("tile-selected");
    var overlayDiv = document.getElementById("img-overlay-"+index);
    $(overlayDiv).removeClass('img-selected');
    $("#img-"+index).removeClass('img-selected');
    overlayDiv.hidden = true;
}

function showEditMetadata(index) {
    //console.log("got the click! on element: "+index);
    var editDiv = document.getElementById("enc-metadata-inner-"+index);
    if (editDiv.classList.contains("edit-closed")) {
        $(editDiv).slideDown();
        editDiv.classList.remove("edit-closed");
        //toggleImageHighlights("on",index);
    } else {
        $(editDiv).slideUp();
        editDiv.classList.add("edit-closed");
        //toggleImageHighlights("off",index);
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

function toggleAllCuratedEncImages(showHide) {
    console.log("toggling curated... showHide== "+showHide);
    let allImageTiles = document.getElementsByClassName("image-tile-div"); 
    let numEncs = multipleSubmitUI.encsDefined();
    for (let i=0;i<allImageTiles.length;i++) { 
        let selectEl = allImageTiles[i].querySelector(".enc-num-dropdown");
        let selectEnc = selectEl.options[selectEl.selectedIndex].value;
        let isNum = new RegExp('^\\d+$');
        console.log("selectEnc is ="+selectEnc);
        if (isNum.test(selectEnc)||selectEnc=="ignored") {
            let disp = allImageTiles[i].style.display;
            if (disp=="none"&&showHide==true) {
                $(allImageTiles[i]).fadeIn();
            }
            if (disp!="none"&&showHide==false) {
                $(allImageTiles[i]).fadeOut();
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
        if (selectedEnc!=null&&(selectedEnc==index||selectedEnc=="ignored")) {
            //console.log("adding borderCol: "+borderCol+"  and state: "+state);
            if (state=="on") {
                multipleSubmitUI.addEncounterLabel(imgEl, selectedEnc);
            }
        }
        if (state=="off"||selectedEnc=="unassigned") {
            multipleSubmitUI.removeEncounterLabel(imgEl); 
        }
    }
}

function highlightOnEdit(index) {
    var encSelected = document.getElementById("enc-num-dropdown-"+index);
    var value = encSelected.options[encSelected.    selectedIndex].value;
    //var editDiv = document.getElementById("enc-metadata-inner-"+value);

    toggleImageHighlights("on",value);

    multipleSubmitUI.updateFileCounters();
    multipleSubmitUI.refreshAssociatedImageList();
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

function txt(str) {
    if (multipleSubmitUI.hasVal(props[str])) {return props[str]};
    return null;
}

function baseURL() {
    var urlOb = new URL(window.location.href).host.toString();
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

function getEncImageList(index) {
    //console.log("getting image list for encNum = "+index);
    let arr = [];
    let imgs = document.getElementsByClassName("image-tile-div");
    for (let i=0;i<imgs.length;i++) {
        let dropdown = imgs[i].querySelector(".img-input").querySelector(".enc-num-dropdown"); // blech
        if (dropdown.options[dropdown.selectedIndex].value==index) {
            //console.log("adding filename : "+imgs[i].querySelector(".img-filename").value);
            arr.push(imgs[i].querySelector(".img-filename").value);
        }
    }
    return arr;     
}

function generateImageThumbnail(fileName, index) {
    multipleSubmitUI.generateImageThumbnail(fileName, index);
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

if (window.location.href.endsWith("multipleSubmit.jsp")) {
    console.log("---> Current location is multipleSubmit.jsp");
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
}

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

