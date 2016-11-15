'use strict';
var gutil = require('gulp-util');
var es = require('event-stream');
var Litmus = require('./lib/litmus');
var cheerio = require('cheerio');
var dateFormat = require('dateformat');


function sendLitmus(options){

	if (!options) {
		throw new gutil.PluginError('gulp-litmus', 'options required');
	}

	var now = new Date();
	var date = dateFormat(now, 'yyyy-mm-dd');
	var litmus, html, $, title, finalHtml;

	return es.map(function (file, cb) {

		if (file.isNull()) {
			this.push(file);
			return cb();
		}

		if (file.isStream()) {
			this.emit('error', new gutil.PluginError('gulp-litmus', 'Streaming not supported'));
			return cb();
		}

		if (file.isBuffer()) {
			litmus = new Litmus(options);

			html = file.contents;    
	    $ = cheerio.load(html);
	    title = $('title').text().trim();

	    if (title.length === 0) { title = date; }
	    
	    // Send Litmus test
	    litmus.run(html, title);
		}

    cb(null, file);
    	
	});

}

module.exports = sendLitmus;
