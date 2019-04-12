
// Add function to retrieve all metadata and images and send to servlet.
multipleSubmitAPI = {

    sendData: function() {
        var fd = new FormData();
        var files = document.getElementById('file-selector-input').files;
        for (var i=0;i<files.length;i++) {
            fd.append('image-file-'+i,files[i]);
        }
        // (also get all the filled inputs!)
        $.ajax({
            url: '../MultipleSubmitAPI',
            type: 'POST',
            data: fd,
            contentType: false,
            processData: false,
            success: function(success) {
                console.log("Success posting! "+success);
            },
            error: function(error) {
                console.log("Error posting! BARF! "+error);
            }
        });
    },

    getLocations: function(callback) {
        var errOb = {};
        $.ajax({
            type: 'GET',
            url: '../MultipleSubmitAPI?getLocations=true',
            dataType: 'json',
            success: function(result) {
                console.log("Success GETting location ids! "+JSON.stringify(result));
                //var json = JSON.parse(result);
                callback(result);
            },
            error: function(error) {
                console.log("Error GETting! BARF! "+JSON.stringify(error));
                callback(errOb);
            } 
        });
    }
}

