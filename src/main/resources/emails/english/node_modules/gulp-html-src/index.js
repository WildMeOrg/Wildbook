var fs = require('fs'),
    path = require('path'),
    url = require('url'),
	File = require('vinyl'),
    cheerio = require('cheerio'),
    through = require('through2'),
    extend = require('extend'),
    q = require('q');

module.exports = function(options) {
	var defaults = {
		presets: 'script',
		includeHtmlInOutput: false,
		createReadStream : fs.createReadStream
	};

	var presets = {
		script : {
			selector: 'script:not([data-ignore=true], [data-remove=true])',
			getFileName: function(node) { return node.attr('src'); }
		},
		css : {
			selector: 'link[rel=stylesheet]:not([data-ignore=true], [data-remove=true])',
			getFileName: function(node) { return node.attr('href'); }
		}
	};

	var selectedPresets = (options && options.presets && presets[options.presets]) ||
	                     presets[defaults.presets];

	
	options = extend({}, defaults, selectedPresets, options);
	
	var makeAbsoluteFileName = function makeAbsoluteFileName(file, fileName) {
		//return file.base + fileName; // path.join(file.base, fileName);
		return path.join(path.dirname(file.path), fileName);
	};

	var isRelative = function isRelative(path) {
		return (url.parse(path).protocol == null);
	};

	var streamToBuffer = function streamToBuffer(stream) {
		var buffers = [];
		var deferred = q.defer();
		var totalLength = 0;
		stream.on('readable', function() {
			data = stream.read();
			if (data !== null) {
				buffers.push(data);
				totalLength += data.length;
			}
		});

		stream.on('error', function(err) {
			deferred.reject(err);
		});

		stream.on('end', function() {
			deferred.resolve(Buffer.concat(buffers, totalLength));
		});

		return deferred.promise;
	};

    /**
     * Returns an array of matched files - empty if no file is found.
     * @param contents
     * @returns {Array}
     */
    var transformFile = function transformFile(contents) {
        var $ = cheerio.load(contents.toString());
        var result = [];
        $(options.selector).each(function() {
            var element = $(this);
            var fileName = options.getFileName(element);
            result.push(fileName);
        });

        return result;
    };


	var transform = function(file, enc, callback) {
		var stream = this;
		var bufferReadPromises = [];
        var fileNames;
        var files = [];
		
		if (file.isNull()) {
			// No contents - do nothing
			stream.push(file);
			callback();
		}

        if (file.isStream()) {
            streamToBuffer(file.contents)
                .then(function(contents) {

                    // Get all file names from contents of file.
                    fileNames = transformFile(contents);

                    // Iterate over found file names.
                    fileNames.forEach(function (fileName) {
                        if (isRelative(fileName)) {
                            var absoluteFileName = makeAbsoluteFileName(file, fileName);
                            stream.push(new File({
                                cwd: file.cwd,
                                base: file.base,
                                path: absoluteFileName,
                                contents: options.createReadStream(absoluteFileName)
                            }));
                        }
                    });

                    // Check if we should include HTML file.
                    if (options.includeHtmlInOutput) {
                        stream.push(file);
                    }

                    callback();
                }, function(err) {
                    stream.emit('error', err);
                });
        }

        if (file.isBuffer()) {

            // Get all file names from contents of file.
            fileNames = transformFile(file.contents);

            // Iterate over found file names.
            fileNames.forEach(function (fileName, index) {
                if (isRelative(fileName)) {
                    try	{
                        var absoluteFileName = makeAbsoluteFileName(file, fileName);
                        var readPromise = streamToBuffer(options.createReadStream(absoluteFileName))
                            .then(function(contents) {
                                files[index] = new File({
                                    cwd: file.cwd,
                                    base: file.base,
                                    path: absoluteFileName,
                                    contents: contents
                                });
                            }, function(err) {
                                stream.emit('error', err);
                            });
                        bufferReadPromises.push(readPromise);
                    }
                    catch(err) {
                        stream.emit('error', err);
                    }
                }
            });

            // Wait for all reading to be done.
            q.all(bufferReadPromises)
                .then(function() {
                    // Push all files into the stream in correct order.
                    files.forEach(function (file) {
                        stream.push(file);
                    });

                    // end of contents, no further matches for this file
                    if (options.includeHtmlInOutput) {
                        stream.push(file);
                    }
                    callback();
                });
		}
	};
	
	return through.obj(transform);
}
