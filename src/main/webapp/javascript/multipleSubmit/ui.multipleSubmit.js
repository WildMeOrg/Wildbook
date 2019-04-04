multipleSubmitUI = {

    getIdForIndex: function(index) {
        return "img-"+String(index);
    }, 

    generateMetadataTile: function(file) {

        var metadataTile = "<p><Metadata UI/p>";

        // iterate through files and return HTML for each one. 
        // call another function to do basic validation. 

        return metadataTile;
    }, 

    generateImageTile: function(file, index) {

        var imageTile = "";
        imageTile += "<div class=\"col-xs-6 col-sm-4 col-md-3 col-lg-3 col-xl-3\">";
        imageTile += "<img id=\""+multipleSubmitUI.getIdForIndex(index)+"\" src=\"#\" alt=\"Displaying "+file+"\" />";
        imageTile += "<label>Here is an image.</label>";
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


        //function readURL(input) {

            //if (input.files && input.files[0]) {
            //var reader = new FileReader();
            //reader.onload = function(e) {
                //$('#blah').attr('src', e.target.result);
            //}
            //reader.readAsDataURL(input.files[0]);
            //}
            //}

            //$("#imgInp").change(function() {
                //readURL(this);
            //});
            
            //<script src="https://ajax.googleapis.com/ajax/libs/jquery/2.1.1/jquery.min.js"></script>
            //<form id="form1" runat="server">
            //<input type='file' id="imgInp" />
            //<img id="blah" src="#" alt="your image" />
            //</form>
        //}

    }, 

    notNullOrEmptyString: function(entry) {
        if (entry==undefined||entry==""||!entry) return false;
        return true; 
    }

};
