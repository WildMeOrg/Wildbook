multipleSubmitUI = {

    getImageIdForIndex: function(index) {
        return "img-"+String(index);
    },
    
    getImageUIIdForIndex: function(index) {
        return "img-input-"+String(index);
    },

    generateMetadataTile: function(index) {

        // iterate through files and return HTML for each one

        // create a div to be position absolute that contains your inputs!
        var metadataTile = "";
        metadataTile += "<div id=\"encounter-metadata-"+index+"\" class=\"encounter-tile-div col-xs-12 col-xl-12\">";
        metadataTile += "   <p>Show/Hideable Tab, just looks like date and location when hidden. Other metadata?</p>";
        metadataTile += "   <input id=\"encLocation\" type=\"text\" name=\"encLocation\" required placeholder=\"Enter Location\">";
	    metadataTile +=	"	<input name=\"encDate\" title=\"Sighting Date/Time\" type=\"text\" placeholder=\"Enter Date\" class=\"encDate\"/>";
        metadataTile += "   <label>&nbsp;</label>";   
        metadataTile += "   <br/>";
        metadataTile += "</div>";

        // call another function to do basic validation. 
        return metadataTile;
    }, 

    generateImageTile: function(file, index) {
        var imageTile = "";
        imageTile += "<div class=\"image-tile-div col-xs-6 col-sm-4 col-md-3 col-lg-3 col-xl-3\">";
        imageTile += "  <label class=\"image-overlay image-filename\">File: "+file.name+"</label>";
        imageTile += "  <img class=\"image-element\" id=\""+multipleSubmitUI.getImageIdForIndex(index)+"\" src=\"#\" alt=\"Displaying "+file.name+"\" />";
        imageTile += "  <div class=\"img-input\">";
        imageTile += "      <p class=\" "+multipleSubmitUI.getImageUIIdForIndex(index)+"\">Does it show?</p>";
        imageTile += "      <input class=\" "+multipleSubmitUI.getImageUIIdForIndex(index)+"\" placeholder=\"Encounter Number\" />";
        imageTile += "  </div>"; 
        imageTile += "</div>";

        console.log("image tile: "+imageTile);
        return imageTile;
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
    } 

};