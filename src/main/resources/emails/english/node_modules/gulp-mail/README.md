## Information

<table>
  <tr>
    <td>Package</td><td>gulp-mail</td>
  </tr>
  <tr>
    <td>Description</td>
    <td>Send mails with gulp</td>
  </tr>
</table>

Highly learnt from [gulp-mailer](https://github.com/meerkats/gulp-mailer) (not available on npm)

## Usage

### `mail(options)`

- options: [object]

```js
var mail = require('gulp-mail')
var smtpInfo = {
  auth: {
    user: 'foo@163.com',
    pass: '123456'
  },
  host: 'smtp.163.com',
  secureConnection: true,
  port: 465
}

gulp.task('mail', function() {
  return gulp.src('./mails/i-love-you.html')
    .pipe(mail({
      subject: 'Surprise!?',
      to: [
        'bar@gmail.com'
      ],
      from: 'Foo <foo@163.com>',
      smtp: smtpInfo
    }))
})
```
