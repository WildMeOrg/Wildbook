
// Add function to retrieve all metadata and images and send to servlet.
multipleSubmitAPI = {

    sendData: function(callback) {
        var fd = new FormData();
        var json = {};
        var files = document.getElementById('file-selector-input').files;
        console.log("POSTing #"+files.length+" files.");
        json["number-images"] = files.length;

        var imgArrs = {};
        for (var i=0;i<files.length;i++) {
            // push to arrays of image numbers with the enc numbers as key
            fd.append('image-file-'+i,files[i]);
            //var imgName = files[i].name;
            var targetEnc =  document.getElementById("enc-num-dropdown-"+i).value;
            if (imgArrs.hasOwnProperty(targetEnc)) {
                imgArrs[targetEnc].push(i);
            } else {
                imgArrs[targetEnc] = [i];   
            }
        }
        json["enc-image-lists"] = imgArrs;
        var numEncs = document.getElementById("number-encounters").value;
        json["number-encounters"] = numEncs;   
        json['recaptcha-checked'] = document.getElementById("recaptcha-checked").value

        for (var i=0;i<numEncs;i++) {
            var encJson = {};
            //encJson[location] =
            encJson["date"] = document.getElementById("enc-date-"+i).value; 
            encJson["comments"] = document.getElementById("enc-comments-"+i).value;
            encJson["location"] = document.getElementById("loc-enc-input-"+i).value;
            encJson["species"] = document.getElementById("spec-enc-input-"+i).value;
            // need all image names associated with this enc. 

            json['enc-data-'+i] = encJson;
        }
        fd.append("json-data", JSON.stringify(json));
        $.ajax({
            url: '../MultipleSubmitAPI',
            type: 'POST',
            data: fd,
            contentType: false,
            processData: false,
            success: function(result) {
                console.log("Success posting! "+JSON.stringify(result));
                callback(result);
            },
            error: function(error) {
                //console.log("Error posting! BARF! "+JSON.stringify(error));
                $('#server-error').html(error);
                callback(error);
            }
        });
    },

    getLocations: function(callback) {
        multipleSubmitAPI.getData('getLocations=true', callback);
    },

    getSpecies: function(callback) {
        multipleSubmitAPI.getData('getSpecies=true', callback);
    },

    getProperties: function(callback) {
        multipleSubmitAPI.getData('getProperties=true', callback);
    },

    getData: function(getString, callback) {
        $.ajax({
            url: '../MultipleSubmitAPI?'+getString,
            type: 'GET',
            dataType: 'json',
            success: function(result) {
                console.log("Success GETting "+getString);
                //var json = JSON.parse(result);
                callback(result);
            },
            error: function(error) {
                //console.log("Error GETting! BARF! "+JSON.stringify(error));
                $('#server-error').html(error);
                callback(error);
            } 
        });

    }
}

