
/*
be sure to include:
  <script src="https://sdk.amazonaws.com/js/aws-sdk-2.2.33.min.js"></script>
  <script src="tools/flow.min.js"></script>

/////Object {s3_secretAccessKey: "MWaPEpplIlHNeZspL6krTKh/muAa3l6rru5fIiMn", s3_region: "us-west-2", s3_accessKeyId: "AKIAJFVBLOTSKZ5554EA", type: "s3direct", s3_bucket: "us-west-2"}

https://docs.aws.amazon.com/AWSJavaScriptSDK/guide/browser-examples.html#Amazon_S3
"Uploading a local file using the File API"

also this looks interesting to load thumbnails and **exif** on the client
https://github.com/blueimp/JavaScript-Load-Image
also - https://code.flickr.net/2012/06/01/parsing-exif-client-side-using-javascript-2/

*/


var uploaderFlow;
var uploaderS3Bucket;
var forceLocal = (document.location.search == '?forceLocal');

//TODO we should make this more generic wrt elements and events
function uploaderInit() {

    if (!forceLocal && wildbookGlobals && wildbookGlobals.uploader && (wildbookGlobals.uploader.type == 's3direct')) {
        $('#uptype').html('S3-direct');
        console.info("uploader is using direct-to-s3 uploading to bucket %s", wildbookGlobals.uploader.s3_bucket);
		AWS.config.credentials = {
			accessKeyId: wildbookGlobals.uploader.s3_accessKeyId,
			secretAccessKey: wildbookGlobals.uploader.s3_secretAccessKey
  		};
  		AWS.config.region = wildbookGlobals.uploader.s3_region;
		uploaderS3Bucket = new AWS.S3({params: {Bucket: wildbookGlobals.uploader.s3_bucket}});

		document.getElementById('upload-button').addEventListener('click', function(ev) {
			var files = document.getElementById('file-chooser').files;
			for (var i = 0 ; i < files.length ; i++) {
				var params = {
					Key: files[i].name,
					ContentType: files[i].type,
					Body: files[i]
				};
				var mgr = uploaderS3Bucket.upload(params, function(err, data) {
					var el = findElement(data.key, -1);
console.info('complete? err=%o data=%o', err, data);
					if (err) {
						updateProgress(el, -1, err, 'rgba(250,120,100,0.3)');
					} else {
						updateProgress(el, -1, 'completed', 'rgba(200,250,180,0.3)');
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
  			//query:{upload_token:'my_token'}
			testChunks: false,
		});
		document.getElementById('upload-button').addEventListener('click', function(ev) {
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
		});
		flow.on('fileError', function(file, message){
    			console.log('error %o %o', file, message);
		});

	}
}


function findElement(name, size) {
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


