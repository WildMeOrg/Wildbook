
// Add function to retrieve all metadata and images and send to servlet. 
function sendData() {
    var fd = new FormData();
    var files = $('#file-selector-input').files;
    for (var i=0;i<files.length;i++) {
        fd.append('image-file-'+i,files[i]);
    }

    // (also get all the filled inputs!)

    $.ajax({
        url: '../MultipleSubmit',
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
}