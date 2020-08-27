function updateNotificationsWidget() {
	var n = $('#notifications');
	if (!n.length) return;
	$.ajax({
		url: wildbookGlobals.baseUrl + '/Collaborate?json=1&getNotificationsWidget=1',
		dataType: 'json',
		success: function(d) {
        console.log(d);
			if (d.success && d.content) n.html(d.content);
		},
		type: 'GET'
    });
    
    // append IndividualMerge notifications
}

function showNotifications(el) {
	var p = popup();
	p.css({width: '50%', left: '25%', top: '200px'});
	p.append('<div class="scroll throbbing" />');
	p.show();
	$.ajax({
		url: wildbookGlobals.baseUrl + '/Collaborate?json=1&getNotifications=1',
		dataType: 'json',
		success: function(d) {
        console.log(d);
			p.find('.scroll').removeClass('throbbing').html(d.content);
			$('.collaboration-invite-notification input').click(function(ev) { clickApproveDeny(ev); });
		},
		error: function(a,x,b) {
			p.find('.scroll').removeClass('throbbing').html('error');
		},
		type: 'GET'
    });
    
    // make sure individual notifications are present

}

function popup() {
	var p = $('<div class="popup" style="display: none;" />');
	$('body').append(p);
	return p;
}

//general purpose i18n
function t(which, key) {
	if (!key || !which) return '';
	if (!wildbookGlobals.properties.lang[which]) return '';
	return wildbookGlobals.properties.lang[which][key] || '';
}