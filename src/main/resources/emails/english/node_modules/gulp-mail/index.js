// learnt from https://github.com/meerkats/gulp-mailer
// used old nodemailer@0.7.1 instead

var _ = require('underscore');
var nodemailer = require('nodemailer');
var path = require('path');
var through2 = require('through2');
var util = require('util');
var gutil = require('gulp-util');

module.exports = function (options) {

    options = _.defaults(options || {}, {
        to: null,
        from: null,
        subject: null,
        html: null,
        smtp: null
    });

    return through2.obj(function (file, enc, next) {

        var transporter = nodemailer.createTransport("SMTP", options.smtp);

        if (file.isNull()) {
            this.push(file);
            return next();
        }

        var to = options.to.join(',');
        var subject = options.subject || _subjectFromFilename(file.path);
        var html = options.html || file.contents.toString();

        return transporter.sendMail({
            from: options.from,
            to: to,
            subject: subject,
            generateTextFromHTML: true, // added
            html: html
        }, function (error, info) {

            if (error) {
                console.error(error);
                transporter.close();
                return next();
            }

            gutil.log("Send email", gutil.colors.cyan(subject), 'to',
            gutil.colors.red(to));
            transporter.close();
            next();

        });
    });
};

_subjectFromFilename = function (filename) {
    var name = path.basename(filename).replace(path.extname(filename), '');
    return util.format('[TEST] %s', name);
};