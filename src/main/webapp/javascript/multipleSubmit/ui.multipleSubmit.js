multipleSubmitUI = {

    getIdForIndex: function(index) {
        return "img-"+String(index);
    }, 

    generateMetadataTile: function(file) {

        // iterate through files and return HTML for each one. 
        var metadataTile = "";
        metadataTile += "<div class=\"encounter-tile-div col-xs-12 col-xl-12\">";
        metadataTile += "   <p>Show/Hideable Tab, just looks like date and location when hidden. Other metadata?</p>";
        metadataTile += "   <input id=\"encDate\" type=\"date\" name=\"encDate\" required value=\"Date\">"
        metadataTile += "   <input id=\"encLocation\" type=\"text\" name=\"encLocation\" required value=\"Location\">" 
        metadataTile +=	"   <div class=\"input-group datePicker\" data-provide=\"datepicker\">";
	    metadataTile +=	"	    <input name=\"datePicker\" title=\"Sighting Date/Time\" type=\"text\" class=\"form-control\"/>";
	    metadataTile += "		<span class=\"input-group-addon\">";
	    metadataTile +=	"		    <span class=\"glyphicon glyphicon-th\"></span>";
	    metadataTile +=	"		</span>";
	    metadataTile +=	"	</div>";
        metadataTile += "<br/>";
        metadataTile += "</div>";

        // call another function to do basic validation. 

        return metadataTile;
    }, 

    generateImageTile: function(file, index) {
        var imageTile = "";
        imageTile += "<div class=\"image-tile-div col-xs-6 col-sm-4 col-md-3 col-lg-3 col-xl-3\">";
        imageTile += "  <label class=\"image-filename\">Here is "+file.name+"</label>";
        imageTile += "  <img class=\"image-element\" id=\""+multipleSubmitUI.getIdForIndex(index)+"\" src=\"#\" alt=\"Displaying "+file.name+"\" />";
        imageTile += "</div>";

        console.log("image tile: "+imageTile);
        return imageTile;
    },

    renderImageInBrowser: function(file,id) {
        if (this.notNullOrEmptyString(String(file))) {
            var reader = new FileReader();
            reader.onload = function(e) {
                console.log("Target ID for image render: #"+multipleSubmitUI.getIdForIndex(id));
                $('#'+multipleSubmitUI.getIdForIndex(id)).attr('src', e.target.result); // This is the target.. where we want the preview
            }
            reader.readAsDataURL(file);
        }
    }, 

    notNullOrEmptyString: function(entry) {
        if (entry==undefined||entry==""||!entry) return false;
        return true; 
    }

};