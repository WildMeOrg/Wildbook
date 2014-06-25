// related to Collaboration manipulation
//   note: requires jquery

var allCollab = {};

function initialize() {
console.log('ready!');

	$('.collaboration-button').each(function(i, el) {
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


function collaborateCall(uid) {
	$('#collab-controls').html('<div class="throbbing">&nbsp;</div>');
	$.ajax({
		url: baseUrl + '/Collaborate?json=1&username=' + uid,
		dataType: 'json',
		success: function(d) { collaborateCallDone(d); },
		error: function(a,x,b) { collaborateCallDone({ success: false, message: 'Error: ' + a + '/' + b }); },
		type: 'GET'
	});
}


function collaborateCallMulti() {
	$('#collab-controls').html('<div class="throbbing">NOT YET IMPLEMENTED</div>');
	$('#collab-multi input').each(function(i,el) {
console.log(el);
	});
}


function collaborateCallDone(data) {
	console.log(data);
	var h = '';
	if (data.success) {
		h += '<p class="collaboration-success">' + data.message + '</p>';
	} else {
		h += '<p class="collaboration-failure">' + data.message + '</p>';
	}
	h += '<p><input type="button" value="OK" onClick="$(\'.popup\').remove();" /></p>';
	$('#collab-controls div').removeClass('throbbing').html(h);
}




//TODO general usage???
function popup() {
	var p = $('<div class="popup" style="display: none;" />');
	$('body').append(p);
	return p;
}



