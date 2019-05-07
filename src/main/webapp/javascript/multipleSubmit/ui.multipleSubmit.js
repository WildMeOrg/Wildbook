/*

These functions create the many HTML components in the page. Many are nested together. 
There are listed in decending as much at that is applicable, ie imageTile contains an imageDataOverlay which contains 
an encNumDropdown ect.

*/


multipleSubmitUI = {

    getImageIdForIndex: function(index) {
        return "img-"+String(index);
    },
    
    getImageUIIdForIndex: function(index) {
        return "img-input-"+String(index);
    },

    getEncInputClassForIndex: function(index) {
        return "enc-input-"+String(index);
    },

    generateMetadataTile: function(index) {
        var metadataTile = "";
        metadataTile += "<div class=\"encounter-tile-div col-xs-12 col-xl-12\">";
        metadataTile += "   <div>";
        metadataTile += "       <label class=\"encounter-label\">&nbsp"+txt("encounterMetadata")+" #"+(index+1)+"&nbsp</label>";
        metadataTile += "       <input class=\"show-metadata-btn\" type=\"button\" onclick=\"showEditMetadata("+index+")\" value=\""+txt("showDetails")+"\" />";
        metadataTile += "   </div>";
        metadataTile +=	"   <div id=\"enc-metadata-inner-"+index+"\" class=\"edit-closed\">";	
        metadataTile +=         "<div class=\"col-xs-12 col-md-4 col-lg-4 col-xl-4 enc-top-input\">"+multipleSubmitUI.generateLocationDropdown(index)+"</div>";
        metadataTile +=	"	     <div class=\"col-xs-12 col-md-4 col-lg-4 col-xl-4 enc-top-input\">";
        metadataTile += "           <p class=\"enc-input-label\"><small>Select Date:</small></p>";
        metadataTile += "           <input id=\"enc-date-"+index+"\" name=\"encDate\" title=\""+txt("encounterMetadata")+"\" type=\"text\" placeholder=\""+txt("enterDate")+"\" class=\"form-control encDate\" size=\"36\" />";
        metadataTile += "       </div>";
        metadataTile += "       <div class=\"col-xs-12 col-md-4 col-lg-4 col-xl-4 enc-top-input\">"+multipleSubmitUI.generateSpeciesDropdown(index)+"</div>";
        metadataTile +=	"       <p><textarea id=\"enc-comments-"+index+"\" class=\"form-control comment-box\" placeholder=\""+txt("moreInfo")+"\" rows=\"3\" cols=\"36\" /></p>";
        metadataTile +=	"   </div>";
        metadataTile += "</div>";
        return metadataTile;
    }, 

    generateImageTile: function(file, index) {
        var imageTile = "";
        imageTile += "<div id=\"image-tile-div-"+index+"\" class=\"image-tile-div col-xs-6 col-sm-4 col-md-3 col-lg-3 col-xl-3\" onclick=\"imageTileClicked("+index+")\" onmouseover=\"showOverlay("+index+")\" onmouseout=\"hideOverlay("+index+")\" >";
        imageTile += "  <input class=\"form-control img-filename-"+index+"\" type=\"hidden\" value=\""+file.name+"\" />";
        imageTile += "  <img class=\"image-element\" id=\""+multipleSubmitUI.getImageIdForIndex(index)+"\" src=\"#\" alt=\"Displaying "+file.name+"\" />";
        imageTile += multipleSubmitUI.generateImageDataOverlay(file,index);                
        imageTile += "</div>";
        //console.log("image tile: "+imageTile);
        return imageTile;
    },
                    
    generateImageDataOverlay: function(file,index) {
        var uiClass = multipleSubmitUI.getImageUIIdForIndex(index);
        var overlay = "";
        overlay += "  <div hidden id=\"img-overlay-"+index+"\" class=\"img-overlay-"+index+" img-input "+uiClass+"\" >";
        overlay += "      <label class=\""+uiClass+" img-input-label\">"+txt("file")+"  "+file.name+"</label>";
        // make a "click to focus" prompt here on hover
        overlay += multipleSubmitUI.generateEncNumDropdown(index);
        overlay += "  </div>";
        return overlay;                   
    },
    
    generateEncNumDropdown: function(index) { 
        var uiClass = multipleSubmitUI.getImageUIIdForIndex(index);
        var encDrop = "";
        encDrop += "<p class=\"img-input-label\"><small>"+txt("selectEncounter")+"</small></p>";
        encDrop += "<select id=\"enc-num-dropdown-"+index+"\" class=\"form-control "+uiClass+"\" name=\"enc-num-dropdown-"+index+"\">";
        encDrop += "    <option selected=\"selected\" value=\"0\">"+txt("encounter")+"  #1</option>";
        for (var i=1;i<multipleSubmitUI.encsDefined();i++) {
            encDrop += "<option value=\""+i+"\">Encounter #"+(i+1)+"</option>";
        }
        encDrop += "</select>";
        return encDrop;
    },

    generateSpeciesDropdown: function(index) { 
        var uiClass = multipleSubmitUI.getEncInputClassForIndex(index);
        var speciesDrop = "";
        var uiId = "spec-" + uiClass;
        speciesDrop += "<p class=\"enc-input-label\"><small>"+txt("selectSpecies")+":</small></p>";
        speciesDrop += "<select id=\""+uiId+"\" class=\"form-control "+uiClass+"\" name=\"species-dropdown-"+index+"\">";
        multipleSubmitAPI.getSpecies(function(result){
            var allSpecies = result.allSpecies
            for (var i=0;i<allSpecies.length;i++) {
                var species = allSpecies[i];
                //console.log("allSpecies? --> "+JSON.stringify(result));
                var option = document.createElement("option");
                option.text = species; 
                option.value = species;
                //console.log("Appending child for species="+species);
                if (document.getElementById(uiId)!=null) {
                    document.getElementById(uiId).appendChild(option);
                }
            }
        });
        speciesDrop += "</select>";
        return speciesDrop;
    },

    generateLocationDropdown: function(index) {
        var uiClass = multipleSubmitUI.getEncInputClassForIndex(index);
        var uiId = "loc-" + uiClass;
        var dd = "";
        dd += "<p class=\"enc-input-label\"><small>"+txt("selectLocation")+"</small></p>";
        dd += "<select id=\""+uiId+"\" class=\"form-control "+uiClass+"\" name=\"enc-num-dropdown-"+index+"\">";
        dd += "    <option selected=\"selected\" value=\"null\" disabled>"+txt("chooseLocation")+"</option>";
        multipleSubmitAPI.getLocations(function(locObj){
            var ddLocs = ""; 
            if (locObj.hasOwnProperty('locationIds')) {
                var locs = locObj.locationIds;
                //console.log("Type? "+(typeof locs));
                //console.log("----------------> locs: "+JSON.stringify(locs));
                for (var i in locs) {
                    var option = document.createElement("option");
                    option.text = locs[i];
                    option.value = locs[i];
                    if (document.getElementById(uiId)!=null) {
                        document.getElementById(uiId).appendChild(option);
                    } else {
                        ddLocs += "<option value=\""+locs[i]+"\">"+locs[i]+"</option>";
                    }
                }  
            }
        });
        dd += "</select>";
        return dd;
    },

    generateWaitingText:     function() {
        // I guess we don't necessarily need to do this in the same way because there is nothing dynamic
        // about the HTML.. stick to patter though
        var wait = "";
        wait += "<div class=\"container waiting-div pulsing\">";
        wait += "     <p class=\"results-waiting\">Please wait for results. This may take a few seconds to a few minutes depending on the size of upload.</p>";
        wait += "</div>"; 
        return wait;
    },

    generateResultPage: function(result) {
        var re = "";
        re += "<div class=\"container\">";
        console.log("Generating result page!  :  "+JSON.stringify(result));
        for (var i=0;i<result.numEncs; i++) {
            re += "<p><a target=\"_blank\" href=\"//"+baseURL()+"/encounters/encounter.jsp?number="+result[i].id+"\">Encounter #"+(i+1)+": "+result[i].id+"</a></p>";
            if (result[i]["img-names"].length>0) {
                re += "<p><small>Image List:</small></p>";
                re += "<ul>";
                for (var j=0;j<result[i]["img-names"].length;j++) {
                    re += "<li><small>"+result[i]["img-names"][j]+"</small></li>";
                }
                re += "</ul>";
            }
            re += "<br>";
        }
        re += "</div>";
        console.log("here's re = "+re); 
        return re;
    },
    
    renderImageInBrowser: function(file,id) {
        if (this.notNullOrEmptyString(String(file))) {
            var reader = new FileReader();
            reader.onload = function(e) {
                console.log("Target ID for image render: #"+multipleSubmitUI.getImageIdForIndex(id));
                $('#'+multipleSubmitUI.getImageIdForIndex(id)).attr('src', e.target.result); // This is the target.. where we want the preview
            }
            reader.readAsDataURL(file);
        }
    },
    
    notNullOrEmptyString: function(entry) {
        if (entry==undefined||entry==""||!entry) return false;
        return true; 
    }, 

    encsDefined() {
        return document.getElementById('number-encounters').value;
    }

};