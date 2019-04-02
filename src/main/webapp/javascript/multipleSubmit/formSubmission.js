

// recaptcha and form security 


var captchaWidgetId;
function onloadCallback() {
    captchaWidgetId = grecaptcha.render('myCaptcha', {
        'sitekey' : '<%=recaptchaProps.getProperty("siteKey") %>',  // required
        'theme' : 'light'
    });
}

function sendAttempt() {
    if(($('#myCaptcha > *').length < 1)){
        $("#encounterForm").attr("action", "EncounterForm");
        submitForm();
    } else {	
        console.log('Here!');
        var recaptachaResponse = grecaptcha.getResponse( captchaWidgetId );
        console.log( 'g-recaptcha-response: ' + recaptachaResponse );
        if(!isEmpty(recaptachaResponse)) {
            $("#encounterForm").attr("action", "EncounterForm");
            if (sendSocialPhotosBackground()) return false;
            submitForm();
        }
    }
}

// make this more compact.. just a counter for files stolen from submit.jsp
var maxBytes = 104857600; // This is 100mb.. Do something else? I dunno.
function updateSelected(inp) {
    var f = '';
    if (inp.files && inp.files.length) {
        var all = [];
        for (var i = 0 ; i < inp.files.length ; i++) {
            if (inp.files[i].size > maxBytes) {
                all.push('<span class="error">' + inp.files[i].name + ' (' + Math.round(inp.files[i].size / (1024*1024)) + 'MB is too big, 100MB max upload size.)</span>');
            } else {
                all.push(inp.files[i].name + ' (' + Math.round(inp.files[i].size / 1024) + 'k)');
            }
        }
        f = '<b>' + inp.files.length + ' file' + ((inp.files.length == 1) ? '' : 's') + ':</b> ' + all.join(', ');
    } else {
        f = inp.value;
    }
    document.getElementById('input-file-list').innerHTML = f;
} 



