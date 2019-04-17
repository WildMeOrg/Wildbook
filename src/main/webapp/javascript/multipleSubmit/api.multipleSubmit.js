
// Add function to retrieve all metadata and images and send to servlet.
multipleSubmitAPI = {

    sendData: function(callback) {
        var fd = new FormData();
        var files = document.getElementById('file-selector-input').files;
        console.log("POSTing #"+files.length+" files.");
        for (var i=0;i<files.length;i++) {
            fd.append('image-file-'+i,files[i]);
        }

        var numEncs = document.getElementById("number-encounters").value;

        fd.append("number-encounters", String(numEncs));

        for (var i=0;i<numEncs;i++) {
            fd.append('enc-data-'+i, "encounter-"+i);
        }
        var obj = {"json-data": "json-value"}; // just a test now, gonna pack it all in thar
        fd.append("json-data", JSON.stringify(obj));
        fd.append('recaptcha-checked', document.getElementById("recaptcha-checked").value);

        $.ajax({
            url: '../MultipleSubmitAPI',
            type: 'POST',
            data: fd,
            contentType: false,
            processData: false,
            success: function(result) {
                console.log("Success posting! "+JSON.stringify(result));
            },
            error: function(error) {
                console.log("Error posting! BARF! "+JSON.stringify(result));
            }
        });
    },

    getLocations: function(callback) {
        var errOb = {};
        $.ajax({
            url: '../MultipleSubmitAPI?getLocations=true',
            type: 'GET',
            dataType: 'json',
            success: function(result) {
                console.log("Success GETting location ids! ");
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

