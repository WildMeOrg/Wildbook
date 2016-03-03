
/*
NOTE: some of this makes the assumption jquery is loaded too... meh.  was trying to avoid that.

be sure to include:
  <script src="https://sdk.amazonaws.com/js/aws-sdk-2.2.33.min.js"></script>
  <script src="tools/flow.min.js"></script>


https://docs.aws.amazon.com/AWSJavaScriptSDK/guide/browser-examples.html#Amazon_S3
"Uploading a local file using the File API"

also this looks interesting to load thumbnails and **exif** on the client
https://github.com/blueimp/JavaScript-Load-Image
also - https://code.flickr.net/2012/06/01/parsing-exif-client-side-using-javascript-2/

*/


var uploaderFlow;
var uploaderS3Bucket;
var forceLocal = (document.location.search == '?forceLocal');
var mediaAssetSetId = false;
var randomPrefix = Math.floor(Math.random() * 100000);  //this is only used for filenames when we dont get a mediaAssetSetId -- which is hopefully never
var keyToFilename = {};
var pendingUpload = -1;

//TODO we should make this more generic wrt elements and events
function uploaderInit(completionCallback) {

    if (useS3Direct()) {
        $('#uptype').html('S3-direct');
        console.info("uploader is using direct-to-s3 uploading to bucket %s", wildbookGlobals.uploader.s3_bucket);
		AWS.config.credentials = {
			accessKeyId: wildbookGlobals.uploader.s3_accessKeyId,
			secretAccessKey: wildbookGlobals.uploader.s3_secretAccessKey
  		};
  		AWS.config.region = wildbookGlobals.uploader.s3_region;
		uploaderS3Bucket = new AWS.S3({params: {Bucket: wildbookGlobals.uploader.s3_bucket}});

		document.getElementById('upload-button').addEventListener('click', function(ev) {
                        document.getElementById('upcontrols').style.display = 'none';
			var files = document.getElementById('file-chooser').files;
                        pendingUpload = files.length;
			for (var i = 0 ; i < files.length ; i++) {
				var params = {
					Key: filenameToKey(files[i].name),
					ContentType: files[i].type,
					Body: files[i]
				};
				var mgr = uploaderS3Bucket.upload(params, function(err, data) {
                                        var dkey = data.key || data.Key;  //weirdly the case changes on the K for multipart! grrr
					var el = findElement(dkey, -1);
console.info('complete? err=%o data=%o', err, data);
					if (err) {
						updateProgress(el, -1, err, 'rgba(250,120,100,0.3)');
                                                pendingUpload--;
                                                if (pendingUpload == 0) completionCallback();
					} else {
						updateProgress(el, -1, 'completed', 'rgba(200,250,180,0.3)');
                                                pendingUpload--;
                                                if (pendingUpload == 0) completionCallback();
					}
				});
				mgr.on('httpUploadProgress', function(data) {
//console.info('progress? %o', data);
//console.log('%o %o', data.key, data.size);
					var el = findElement(data.key, data.total);
					var p = ((data.loaded / data.total) * 100) + '%';
					updateProgress(el, p, 'uploading');
				}, false);
			}
  		}, false);


	} else {
        $('#uptype').html('server local');
            console.info("uploader is using uploading direct to host (not S3)");
		flow = new Flow({
  			target:'ResumableUpload',
			forceChunkSize: true,
  			query: { mediaAssetSetId: mediaAssetSetId },
			testChunks: false,
		});
		document.getElementById('upload-button').addEventListener('click', function(ev) {
			var files = flow.files;
//console.log('files --> %o', files);
                        pendingUpload = files.length;
                        for (var i = 0 ; i < files.length ; i++) {
//console.log('%d %o', i, files[i]);
                            filenameToKey(files[i].name);
                        }
                        document.getElementById('upcontrols').style.display = 'none';
                        pendingUpload = document.getElementById('file-chooser').length;
			flow.upload();
		}, false);

		flow.assignBrowse(document.getElementById('file-chooser'));
		//flow.assignDrop(document.getElementById('dropTarget'));

		flow.on('fileAdded', function(file, event){
    			console.log('added %o %o', file, event);
		});
		flow.on('fileProgress', function(file, chunk){
			var el = findElement(file.name, file.size);
			var p = ((file._prevUploadedSize / file.size) * 100) + '%';
			updateProgress(el, p, 'uploading');
    			console.log('progress %o %o', file._prevUploadedSize, file);
		});
		flow.on('fileSuccess', function(file,message){
			var el = findElement(file.name, file.size);
			updateProgress(el, -1, 'completed', 'rgba(200,250,180,0.3)');
    			console.log('success %o %o', file, message);
                        pendingUpload--;
                        if (pendingUpload == 0) completionCallback();
		});
		flow.on('fileError', function(file, message){
    			console.log('error %o %o', file, message);
                        pendingUpload--;
                        if (pendingUpload == 0) completionCallback();
		});

	}
}


function useS3Direct() {
    return (!forceLocal && wildbookGlobals && wildbookGlobals.uploader && (wildbookGlobals.uploader.type == 's3direct'));
}


function requestMediaAssetSet(callback) {
    $.ajax({
        url: 'MediaAssetCreate?requestMediaAssetSet',
        type: 'GET',
        dataType: 'json',
        success: function(d) {
            console.info('success got MediaAssetSet: %o -> %s', d, d.mediaAssetSetId);
            mediaAssetSetId = d.mediaAssetSetId;
            callback(d);
        },
        error: function(a,b,c) {
            console.log('error getting MediaAssetSet: %o %o %o', a,b,c);
            alert('error getting Set ID');
        },
    });
}

/*
{
"MediaAssetCreate": [
	{
    	"setId":"567d00b5-b44e-485a-9d77-10987f6dd3e6",
      "assets": [
        {"bucket": "flukebook-dev-upload-tmp", "key": "567d00b5-b44e-485a-9d77-10987f6dd3e6/11854-r043-4f25.jpg"},
        {"bucket": "abc", "key": "xyz"}
        ]
    }
]
}*/

function createMediaAssets(setId, bucket, keys, callback) {
    var assetData = [];
    for (var i = 0 ; i < keys.length ; i++) {
        assetData.push({bucket: bucket, key: keys[i]});
    }
    $.ajax({
        url: 'MediaAssetCreate',
        type: 'POST',
        data: JSON.stringify({
            MediaAssetCreate: [{
                setId: setId,
                assets: assetData
            }]
        }),
        dataType: 'json',
        success: function(d) {
            if (d.success && d.sets) {
                console.info('successfully created MediaAssets: %o', d.sets);
                callback(d);
            } else {
                console.log('error creating MediaAssets: %o', d);
                alert('error saving on server');
                callback(d);
            }
        },
        error: function(a,b,c) {
            console.log('error creating MediaAssets: %o %o %o', a,b,c);
            alert('error saving on server');
            callback({error: a});
        },
    });
}


function filenameToKey(fname) {
    var key = fname;
    if (useS3Direct()) key = (mediaAssetSetId || randomPrefix) + '/' + fname;
    keyToFilename[key] = fname;
console.info('key = %s', key);
    return key;
}

function findElement(key, size) {
        var name = keyToFilename[key];
        if (!name) {
            console.warn('could not find filename for key %o; bailing!', key);
            return false;
        }
	var items = document.getElementsByClassName('file-item');
	for (var i = 0 ; i < items.length ; i++) {
		if ((name == items[i].getAttribute('data-name')) && ((size < 0) || (size == items[i].getAttribute('data-size')))) return items[i];
	}
	return false;
}

function getOffset(name, size) {
	var files = document.getElementById('file-chooser').files;
	for (var i = 0 ; i < files.length ; i++) {
console.warn('%o %o', size, files[i].size);
console.warn('%o %o', name, files[i].name);
		if ((size == files[i].size) &&  (name == files[i].name)) return i;
	}
	return -1;
}


function finfo(o) {
	console.info('%o', o);
}

function filesChanged(f) {
	var h = '';
	for (var i = 0 ; i < f.files.length ; i++) {
		h += '<div class="file-item" id="file-item-' + i + '" data-i="' + i + '" data-name="' + f.files[i].name + '" data-size="' + f.files[i].size + '"><div class="file-name">' + f.files[i].name + '</div><div class="file-size">' + niceSize(f.files[i].size) + '</div><div class="file-status"></div><div class="file-bar"></div></div>';
	}
	document.getElementById('file-activity').innerHTML = h;
}

function updateProgress(el, width, status, bg) {
	if (!el) return;
	var els = el.children;
	if (width < 0) {  //special, means 100%
		els[3].style.width = '100%';
	} else if (width) {
		els[3].style.width = width;
	}
	if (status) els[2].innerHTML = status;
	if (bg) els[3].style.backgroundColor = bg;
}

function niceSize(s) {
	if (s < 1024) return s + 'b';
	if (s < 1024*1024) return Math.floor(s/1024) + 'k';
	return Math.floor(s/(1024*1024) * 10) / 10 + 'M';
}


