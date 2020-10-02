function getText(propsName) {
    let translations = {};
    let json = {};
    json['propsName'] = propsName;
    $.ajax({
        url: wildbookGlobals.baseUrl + '/TranslationsGet',
        type: 'POST',
        data: JSON.stringify(json),
        dataType: 'json',
        contentType: 'application/json',
        async: false,
        success: function(d) {
            console.log('Got Javascript translations object for properties file '+propsName+'.');
            console.log("Translations? "+JSON.stringify(d));
            translations = d;
            return translations;
        },
        error: function(x,y,z) {
            console.log('Failed to get Javascript translations object for properties file '+propsName+'.');
            console.warn('%o %o %o', x, y, z);
        }
    });
    return translations;
}