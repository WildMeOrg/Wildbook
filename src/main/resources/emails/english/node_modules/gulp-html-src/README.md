#gulp-html-src

Convert the HTML input to all the linked script or link/css tags

Streams are supported.  (It will emit whatever it gets - i.e. streams in, streams out; Buffer in, Buffer out)  

## Usage

#### Install
    npm install gulp-html-src --save

## Example

```js
var gulp = require('gulp');
var ghtmlSrc = require('gulp-html-src');
var uglify = require('gulp-uglify');

gulp.task('copy-js', function() {
	gulp.src('./src/*.html')
		.pipe(ghtmlSrc())
		// From this point, it's as if you'd used gulp.src() listing each of your 
		// javascript files that are in your html as <script src="..."></script>
		.pipe(uglify())
		.pipe(gulp.dest('./build/'));

});

gulp.task('copy-css', function() {
	gulp.src('./src/*.html')
		.pipe(ghtmlSrc({ presets: 'css'}))
		.pipe(gulp.dest('./build/'));
});

```

## Options

## Basic usage

`ghtmlSrc(options)`, where options is an object with the following properties.  If you pass no options, then the presets for `script` are used.


You can use the `presets` option to choose either script or css files to enumerate.

It will skip any script or link tags with data-remove="true" or data-ignore="true" set.  You can then use these tags to control what to remove when you remove the script tags from your HTML later.

### options.presets

    Type: `String`
    Default: `script`
    Possible Values: `script`, `css`

### options.includeHtmlInOutput

    Type: `bool`
    Default: `false`
	

By default the original HTML file (that probably came from gulp.src()) is swallowed by `gulp-html-src`, and it only emits the matching script or css files.  However, if you want to keep the HTML in the stream, then set this option to true.  One use of this could be to blindly copy the HTML and all references to a destination directory.


## Advanced usage

The following options are not used for the "normal" cases, but could be useful if you want to do something more advanced (for instance select all images out of an HTML file).  These options individually override the presets, so it's perfectly fine to specify `presets:'script'`, and then override the `selector` to be `'img'`, leaving the `getFileName` function as the default.   

### options.selector

    Type: `String`
    Default: For presets == `script`, `script:not([data-ignore=true], [data-remove=true])`, for presets == `css`, `link[type="text/css"][rel=stylesheet]:not([data-ignore=true], [data-remove=true])`
 

This is the [cheerio](https://github.com/cheeriojs/cheerio) selector (basically a jQuery selector) for the elements to select.  See `options.getFileName` for how this element is converted to a filename.

### options.getFileName

     Type: `function`
     Arguments: `element` - a single element selected by the `selector`
     Returns: The name of the file to open (relative to the HTML file)

This function is called with each matching element from the `selector`, and returns the relative filename.  For `presets == script`, this is

```js
function getFileName(node) {
	return node.attr('src');
}
```



