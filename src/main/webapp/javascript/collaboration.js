// related to Collaboration manipulation
//   note: requires jquery

var allCollab = {};

function initialize() {
console.log('ready!');

	$('.collaboration-button.new').each(function(i, el) {
		var jel = $(el);
		var uid = jel.data('collabowner');
		var name = jel.data('collabownername');
console.log('uid=%s name=%s', uid, name);
		if ((uid != "") && (uid != "null")) {
			if ((name == "") || (name == "null")) name = uid;
			if (!allCollab[uid]) allCollab[uid] = { name: name, count: 0 };
			allCollab[uid].count++;
			jel.click(function() { return collaborateClick(this); });
		}
	});

console.log(allCollab);
}


function collaborateClick(el) {
	var jel = $(el);
	var uid = jel.data('collabowner');
	if (!uid || (uid == "") || !allCollab[uid]) return;
	var name = jel.data('collabownername');

	var p = popup();

	var cancelButton = '<input type="button" value="Cancel" onClick="$(\'.popup\').remove();" />';

	var num = $.map(allCollab, function(n, i) { return i; }).length;

	var h = '';
	if (num == 1) {
		h += '<p><b>' + textCollab.invitePromptOne.replace(/%s/g, allCollab[uid].name) + '</b></p>';
		h += '<p id="collab-controls"><input type="button" value="Yes" onClick="collaborateCall(\'' + uid + '\');" /> ' + cancelButton + '</p>';

	} else {
		h += '<p><b>' + textCollab.invitePromptMany + '</b></p><div id="collab-multi">';
		h += '<div><input type="checkbox" checked value="' + uid + '" id="collab-' + uid + '" /><label for="id-' + u + '">' + allCollab[uid].name + ' (' + allCollab[uid].count + ' item' + ((allCollab[uid].count == 1) ? '' : 's') + ')</label></div>';
		h += '<p><b>' + textCollab.invitePromptManyOther + '</b></p>';
		for (var u in allCollab) {
			if (u != uid) {
				h += '<div><input type="checkbox" value="' + u + '" id="collab-' + u + '" /><label for="id-' + u + '">' + allCollab[u].name + ' (' + allCollab[u].count + ' item' + ((allCollab[u].count == 1) ? '' : 's') + ')</label></div>';
			}
		}
		h += '</div><p id="collab-controls"><input type="button" value="Send" onClick="collaborateCallMulti();" /> ' + cancelButton + '</p>';
	}

	p.append(h);
	p.show();
}


function collaborateCall(uid, callback) {
	$('#collab-controls').html('<div class="throbbing">&nbsp;</div>');
	$.ajax({
		url: wildbookGlobals.baseUrl + '/Collaborate?json=1&username=' + uid,
		dataType: 'json',
		success: function(d) { collaborateCallDone(d, callback); },
		error: function(a,x,b) { collaborateCallDone({ success: false, message: 'Error: ' + a + '/' + b }, callback); },
		type: 'GET'
	});
}


var colMultiCount = 0;
function collaborateCallMulti() {
	var sel = $('#collab-multi input:checked');
	if (sel.length < 1) return;
	$('#collab-controls').html('<div class="throbbing">&nbsp;</div>');
	colMultiCount = sel.length;
	sel.each(function(i,el) {
		$.ajax({
			url: wildbookGlobals.baseUrl + '/Collaborate?json=1&username=' + el.value,
			dataType: 'json',
			success: function(d) { collaborateCallMultiDone(d); },
			error: function(a,x,b) { collaborateCallMultiDone({ success: false, message: 'Error: ' + a + '/' + b }); },
			type: 'GET'
		});
	});
}


function collaborateCallDone(data, callback) {
	if (!callback) callback = '$(\'.popup\').remove();';
	console.log('invite sent reponse: %o', data);
	var h = '';
/*
	if (data.success) {
		h += '<p class="collaboration-success">' + data.message + '</p>';
	} else {
		h += '<p class="collaboration-failure">' + data.message + '</p>';
	}
*/
	h += '<p><input type="button" value="OK" onClick="' + callback + '" /></p>';
	$('#collab-controls div').removeClass('throbbing').html(h);
}


function collaborateCallMultiDone(data, num) {
	//if (!callback) callback = '$(\'.popup\').remove();';
	var callback = '$(\'.popup\').remove();';
	console.log('[%d] invite sent reponse: %o', colMultiCount, data);
	colMultiCount--;
	if (colMultiCount > 0) return;

	var h = '';
	if (data.success) {
		h += '<p class="collaboration-success">' + data.message + '</p>';
	} else {
		h += '<p class="collaboration-failure">' + data.message + '</p>';
	}
	h += '<p><input type="button" value="OK" onClick="' + callback + '" /></p>';
	$('#collab-controls div').removeClass('throbbing').html(h);
}




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
}



//TODO some day this should be general, i guess
function showNotifications(el) {
	var p = popup();
	p.css({width: '50%', left: '25%', top: '10%'});
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
}


function clickApproveDeny(ev) {
	var which = ev.target.getAttribute('class');
	var jel = $(ev.target);
	var uname = jel.parent().data('username');
	var p = jel.parent();
	p.html('').addClass('throbbing');
	$.ajax({
		url: wildbookGlobals.baseUrl + '/Collaborate?json=1&username=' + uname + '&approve=' + which,
		dataType: 'json',
		success: function(d) {
			if (d.success) {
				p.remove();
				updateNotificationsWidget();
			} else {
				p.removeClass('throbbing').html(d.message);
			}
		},
		error: function(a,x,b) {
			p.html('error');
		},
		type: 'GET'
	});
}


//TODO general usage???
function popup() {
	var p = $('<div class="popup" style="display: none;" />');
	$('body').append(p);
	return p;
}



