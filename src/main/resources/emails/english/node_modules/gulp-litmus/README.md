# [gulp](http://gulpjs.com)-litmus [![Build Status](https://travis-ci.org/jeremypeter/gulp-litmus.svg?branch=master)](https://travis-ci.org/jeremypeter/gulp-litmus)


## Install

```bash
$ npm install gulp-litmus
```


## Usage

```js
var gulp = require('gulp');
var litmus = require('gulp-litmus');

var config = {
    username: 'litmus_username',
    password: 'litmus_password',
    url: 'https://yourcompany.litmus.com',
    applications: [
        'applemail6',
        'gmailnew',
        'ffgmailnew',
        'chromegmailnew',
        'iphone4s',
    ]
}

gulp.task('default', function () {
	return gulp.src('src/email.html')
		.pipe(litmus(config))
		.pipe(gulp.dest('dist'));
});
```


## API

### litmus(config)

#### config

##### config.username

Type: `String`  
Default: ' '  
Required: `yes`

Litmus username


##### config.password

Type: `String`  
Default: ' '  
Required: `yes` 

Litmus password


##### config.url

Type: `String`  
Default: ' '  
Required: `yes` 

URL to your companies Litmus account


##### config.applications

Type: `Array`  
Default: []  
Required: `yes` 

Array of email clients to test. Can be found at https://yourcompany.litmus.com/clients.xml. The `<application_code>` tags contain the name e.g. Gmail Chrome: `<application_code> chromegmailnew </application_code>`


## Troubleshooting

If you're having issues with Litmus taking forever to load a test or the title of the test is showing up as "No Subject", it is most likely an issue with the Litmus API. You can check the [Litmus status](http://status.litmus.com) page to find out if their having any issues. If that's not the case, submit an issue and we'll look into further.

## License

MIT © [Jeremy Peter](https://github.com/jeremypeter)
