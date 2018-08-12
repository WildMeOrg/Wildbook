// related to Collaboration manipulation
//   note: requires jquery

var allCollab = {};

$(document).ready(function() {
	collabInit();
});

function collabInit() {
	$('.collaboration-button.new').each(function(i, el) {
		var jel = $(el);
		if (jel.data('multiuser')) {
			var title = t('collaboration', 'deniedMessage');
			title += ' ' + t('collaboration', 'clickCollaborateMessage');
			jel.click(function() { return collaborateMultiClick(this); });
			jel.attr('title', title);
			return;
		}

		var uid = jel.data('collabowner');
		var name = jel.data('collabownername');
console.log('uid=%s name=%s', uid, name);
		var title = t('collaboration', 'deniedMessage');
		if ((uid != "") && (uid != "null")) {
			if ((name == "") || (name == "null")) name = uid;
			if (!allCollab[uid]) allCollab[uid] = { name: name, count: 0 };
			allCollab[uid].count++;
			title += ' ' + t('collaboration', 'clickCollaborateMessage');
			jel.click(function() { return collaborateClick(this); });
		}
		jel.attr('title', title);
	});
}

function collaborateMultiClick(el) {
	var jel = $(el);
	var users = jel.data('multiuser').split(',');
	var p = popup();
	var h = _collaborateMultiHtml(users);
	p.append(h);
	p.show();
return;

/*
	var cancelButton = '<input type="button" value="Cancel" onClick="$(\'.popup\').remove();" />';

	var num = users.length;

	var h = '';
	if (num == 1) {
		var u = users[0].split(':');
		h += '<p><b>' + wildbookGlobals.properties.lang.collaboration.invitePromptOne.replace(/%s/g, u[1]) + '</b></p>';
		h += '<p><textarea id="collab-invite-message" placeholder="' + wildbookGlobals.properties.lang.collaboration.invitePromptOptionalMessage + '"></textarea></p>';
		h += '<p id="collab-controls"><input type="button" value="Yes" onClick="collaborateCall(\'' + u[0] + '\');" /> ' + cancelButton + '</p>';

	} else {
		h += '<p><b>' + wildbookGlobals.properties.lang.collaboration.invitePromptMultiple + ' ' + wildbookGlobals.properties.lang.collaboration.invitePromptMany + '</b></p><div id="collab-multi">';
		for (var i = 0 ; i < num ; i++) {
			var u = users[i].split(':');
			h += '<div><input type="checkbox" value="' + u[0] + '" id="collab-' + u[0] + '" /><label for="id-' + u[0] + '">' + u[1] + '</label></div>';
		}
		h += '<div><textarea id="collab-invite-message" placeholder="' + wildbookGlobals.properties.lang.collaboration.invitePromptOptionalMessage + '"></textarea></div>';
		h += '</div><p id="collab-controls"><input type="button" value="Send" onClick="collaborateCallMulti();" /> ' + cancelButton + '</p>';
	}
*/
}



function _collaborateMultiHtml(users) {
	var cancelButton = '<input type="button" value="Cancel" onClick="$(\'.popup\').remove();" />';
	if (inBlockedPage()) cancelButton = '<input type="button" value="Cancel" onClick="blockerCancel()" />';
	var num = users.length;

	var h = '';
	if (num == 1) {
		var u = users[0].split(':');
		var uclick = '<span class="user-bio-button" title="info on ' + u[1] + '" onClick="openUserBio(\'' + u[0] + '\')">' + u[1] + '</span>';
		h += '<div id="collab-response"><p><b>' + wildbookGlobals.properties.lang.collaboration.invitePromptOne.replace(/%s/g, uclick) + '</b></p>';
		h += '<p><textarea id="collab-invite-message" placeholder="' + wildbookGlobals.properties.lang.collaboration.invitePromptOptionalMessage + '"></textarea></p></div>';
		h += '<p id="collab-controls"><input type="button" value="Yes" onClick="collaborateCall(\'' + u[0] + '\');" /> ' + cancelButton + '</p>';

	} else {
		h += '<div id="collab-response"><p><b>' + wildbookGlobals.properties.lang.collaboration.invitePromptMultiple + ' ' + wildbookGlobals.properties.lang.collaboration.invitePromptMany + '</b></p><div id="collab-multi">';
		for (var i = 0 ; i < num ; i++) {
			var u = users[i].split(':');
			var uclick = '<span class="user-bio-button" title="info on ' + u[1] + '" onClick="openUserBio(\'' + u[0] + '\')">' + u[1] + '</span>';
			h += '<div><input type="checkbox" value="' + u[0] + '" id="collab-' + u[0] + '" /><label for="id-' + u[0] + '">' + uclick + '</label></div>';
		}
		h += '<div><textarea id="collab-invite-message" placeholder="' + wildbookGlobals.properties.lang.collaboration.invitePromptOptionalMessage + '"></textarea></div></div>';
		h += '</div><p id="collab-controls"><input type="button" value="Send" onClick="collaborateCallMulti();" /> ' + cancelButton + '</p>';
	}
	return h;
}


function collaborateClick(el) {
	var jel = $(el);
	var uid = jel.data('collabowner');
	if (!uid || (uid == "") || !allCollab[uid]) return;
	var name = jel.data('collabownername');
	var h = _collaborateHtml(uid, name);
	var p = popup();
	p.append(h);
	p.show();
}


function _collaborateHtml(uid, name) {
	var cancelButton = '<input type="button" value="Cancel" onClick="$(\'.popup\').remove();" />';
	if (inBlockedPage()) cancelButton = '<input type="button" value="Cancel" onClick="blockerCancel()" />';
	var num = $.map(allCollab, function(n, i) { return i; }).length;
	if (num < 1) {
		allCollab[uid] = { name: name };
		num = 1;
	}

	var h = '';
	if (num == 1) {
		var uclick = '<span class="user-bio-button" title="info on ' + allCollab[uid].name + '" onClick="openUserBio(\'' + uid + '\')">' + allCollab[uid].name + '</span>';
		//h += '<div id="collab-response"><p><b>' + wildbookGlobals.properties.lang.collaboration.invitePromptOne.replace(/%s/g, allCollab[uid].name) + '</b></p>';
		h += '<div id="collab-response"><p><b>' + wildbookGlobals.properties.lang.collaboration.invitePromptOne.replace(/%s/g, uclick) + '</b></p>';
		h += '<p><textarea id="collab-invite-message" placeholder="' + wildbookGlobals.properties.lang.collaboration.invitePromptOptionalMessage + '"></textarea></p></div>';
		h += '<p id="collab-controls"><input type="button" value="Yes" onClick="collaborateCall(\'' + uid + '\');" /> ' + cancelButton + '</p>';

	} else {
		h += '<div id="collab-response"><p><b>' + wildbookGlobals.properties.lang.collaboration.invitePromptMany + '</b></p><div id="collab-multi">';
		var uclick = '<span class="user-bio-button" title="info on ' + allCollab[uid].name + '" onClick="openUserBio(\'' + uid + '\')">' + allCollab[uid].name + '</span>';
		//h += '<div><input type="checkbox" checked value="' + uid + '" id="collab-' + uid + '" /><label for="id-' + u + '">' + allCollab[uid].name + ' (' + allCollab[uid].count + ' item' + ((allCollab[uid].count == 1) ? '' : 's') + ')</label></div>';
		h += '<div><input type="checkbox" checked value="' + uid + '" id="collab-' + uid + '" /><label for="id-' + u + '">' + uclick + ' (' + allCollab[uid].count + ' item' + ((allCollab[uid].count == 1) ? '' : 's') + ')</label></div>';
		h += '<p><b>' + wildbookGlobals.properties.lang.collaboration.invitePromptManyOther + '</b></p>';
		for (var u in allCollab) {
			if (u != uid) {
				uclick = '<span class="user-bio-button" title="info on ' + allCollab[uid].name + '" onClick="openUserBio(\'' + uid + '\')">' + allCollab[uid].name + '</span>';
				h += '<div><input type="checkbox" value="' + u + '" id="collab-' + u + '" /><label for="id-' + u + '">' + uclick + ' (' + allCollab[u].count + ' item' + ((allCollab[u].count == 1) ? '' : 's') + ')</label></div>';
			}
		}
		h += '<div><textarea id="collab-invite-message" placeholder="' + wildbookGlobals.properties.lang.collaboration.invitePromptOptionalMessage + '"></textarea></div>';
		h += '</div></div><p id="collab-controls"><input type="button" value="Send" onClick="collaborateCallMulti();" /> ' + cancelButton + '</p>';
	}
	return h;
}


function collaborateCall(uid, callback) {
	$('#collab-controls').html('<div class="throbbing">&nbsp;</div>');
	var msg = $('#collab-invite-message').val();
	$.ajax({
		url: wildbookGlobals.baseUrl + '/Collaborate?json=1&username=' + uid + '&message=' + encodeURIComponent(msg),
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
	var msg = $('#collab-invite-message').val();
	$('#collab-controls').html('<div class="throbbing">&nbsp;</div>');
	colMultiCount = sel.length;
	sel.each(function(i,el) {
		$.ajax({
			url: wildbookGlobals.baseUrl + '/Collaborate?json=1&username=' + el.value + '&message=' + encodeURIComponent(msg),
			dataType: 'json',
			success: function(d) { collaborateCallMultiDone(d); },
			error: function(a,x,b) { collaborateCallMultiDone({ success: false, message: 'Error: ' + a + '/' + b }); },
			type: 'GET'
		});
	});
}


function collaborateCallDone(data, callback) {
	if (!callback) callback = '$(\'.popup\').remove();';
	if (inBlockedPage()) callback = 'blockerCancel()';
	console.log('invite sent reponse: %o', data);
	var h = '';
	h += '<p><input type="button" value="OK" onClick="' + callback + '" /></p>';

	$('#collab-response').html('<p class="collaboration-' + (data.success ? "success" : "failure") + '">' + data.message + '</p>');
	$('#collab-controls div').removeClass('throbbing').html(h);
}


function collaborateCallMultiDone(data, num) {
	//if (!callback) callback = '$(\'.popup\').remove();';
	var callback = '$(\'.popup\').remove();';
	console.log('[%d] invite sent reponse: %o', colMultiCount, data);
	colMultiCount--;
	if (colMultiCount > 0) return;

	var h = '';
	h += '<p><input type="button" value="OK" onClick="' + callback + '" /></p>';

	$('#collab-response').html('<p class="collaboration-' + (data.success ? "success" : "failure") + '">' + data.message + '</p>');
	$('#collab-controls div').removeClass('throbbing').html(h);
}


function inBlockedPage() {
	return document.location.href.match('encounter.jsp|occurrence.jsp|individuals.jsp');
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
}


function clickApproveDeny(ev) {
	var which = ev.target.getAttribute('class');
	var jel = $(ev.target);
	var p = jel.parent();
	var uname = p.data('username');
	p.html('&nbsp;').addClass('throbbing');
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
			p.removeClass('throbbing').html('error');
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


//general purpose i18n
function t(which, key) {
	if (!key || !which) return '';
	if (!wildbookGlobals.properties.lang[which]) return '';
	return wildbookGlobals.properties.lang[which][key] || '';
}


//TODO put this somewhere else
function openUserBio(uname) {
	var dlg = $('#popup-bio');
	if (!dlg.length) {
		dlg = $('<div id="popup-bio" />');
		$('body').append(dlg);
	}
	dlg.dialog({
		//draggable: false,
		resizable: false,
		width: 500
	});
console.log('ok?');
	$.ajax({
		url: wildbookGlobals.baseUrl + '/UserInfo?username=' + uname,
		success: function(h) { $('#popup-bio').html(h) },
		data: 'text',
		type: 'GET'
	});
}


function collabBackOrCloseButton() {
    if (!window.history || (window.history.length == 1)) {
        return '<input type="button" value="CLOSE" onClick="window.close()" />';
    } else {
        return '<input type="button" value="BACK" onClick="window.history.back()" />';
    }
}

function blockerCancel() {
    if (!window.history || (window.history.length == 1)) {
        window.close();
    } else {
        window.history.back();
    }
}

