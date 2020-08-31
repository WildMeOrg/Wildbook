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
	console.log("setting popup id!");
	$(p).attr("id", "notifications-popup");
	p.append('<div id="notifications-scrollbox" class="scroll throbbing"></div>');
	p.show();

	$.ajax({
		url: wildbookGlobals.baseUrl + '/Collaborate?json=1&getNotifications=1',
		dataType: 'json',
		success: function(d) {
        console.log(d);
			p.find('.scroll').removeClass('throbbing').html(d.content);
			$('.collaboration-invite-notification input').click(function(ev) { clickApproveDeny(ev); });

			userGetNotifications();

			let closeButton = '<p><input onClick="closeNotificationPopup(this)" type="button" value="close" /></p>'

			$('#notifications-scrollbox').append(closeButton);
		},
		error: function(a,x,b) {
			p.find('.scroll').removeClass('throbbing').html('error');
		},
		type: 'GET'
	});
	
}

function closeNotificationPopup(el) {
	$('.popup').remove();
}

function userGetNotifications() {
	console.log('Getting merge notifications...');
	let json = {};
	$.ajax({
		url: wildbookGlobals.baseUrl + '/UserGetNotifications',
		type: 'POST',
		data: JSON.stringify(json),
		dataType: 'json',
		contentType: 'application/json',
		success: function(d) {
			console.log("literal response: "+d.notifications);

			let lastCollabNotification = $("#notifications-scrollbox").find('.collaboration-invite-notification').last();

			let notificationsArr = d.notifications;
			for (let i=0;i<notificationsArr.length;i++) {
				let thisNote = notificationsArr[i];
				console.log(JSON.stringify(thisNote));
				let notificationHTML = '<div class="merge-notification" id="merge-'+thisNote.taskId+'">';
				
				//notificationHTML += '		<p>Merge Notification: '+thisNotification.primaryIndividualId+'</p>';
				
				let notificationType = thisNote.notificationType;
				console.log("---> Notification Type: "+notificationType);
				let primaryName = thisNote.primaryIndividualName;

				if (notificationType=="mergePending") {
					let secondaryName = thisNote.secondaryIndividualName;
					notificationHTML += '<p>Merge of <strong>'+secondaryName+'</strong> into <strong>'+primaryName+'</strong> has been initiated.';
					notificationHTML += ' Auto completion date: <strong>'+thisNote.mergeExecutionDate+'</strong>, 2 week delayed execution.';
					notificationHTML += ' Initiated by user: <strong>'+thisNote.initiator+'</strong>';
					notificationHTML += '<input class="btn btn-sm" type="button" onclick="denyIndividualMerge(this)" value="Deny"/>';
					notificationHTML += '<input class="btn btn-sm" type="button" onclick="ignoreIndividualMerge(this)" value="Ignore"/></p>';
				}

				if (notificationType=='mergeComplete') {
					notificationHTML += '<p>Merge into <strong>'+primaryName+'</strong> was completed.';
					notificationHTML += ' Auto completion date: <strong>'+thisNote.mergeExecutionDate+'</strong>, 2 week delayed execution.';
					notificationHTML += ' Initiated by user: <strong>'+thisNote.initiator+'</strong>';
					notificationHTML += '<input class="btn btn-sm" type="button" onclick="ignoreIndividualMerge(this)" value="Dismiss"/></p>';
				}

				if (notificationType=='mergeDenied') {
					notificationHTML += '<p>A merge of <strong>'+secondaryName+'</strong> into <strong>'+primaryName+'</strong> was <i><strong>denied</strong></i>.';
					notificationHTML += 'Initiated by user: <strong>'+thisNote.initiator+'</strong>';
					notificationHTML += 'Denied by user: <strong>'+thisNote.deniedBy+'</strong>';
					notificationHTML += '<input class="btn btn-sm" type="button" onclick="ignoreIndividualMerge(this)" value="Dismiss"/></p>';
				}

				notificationHTML += '</div>';
				console.log("appending html: "+notificationHTML);
				$(notificationHTML).insertAfter(lastCollabNotification);
			}
			if (notificationsArr.length>0) {
				$('<h2>Individual Merge Notifications</h2>').insertAfter(lastCollabNotification);
			}

		},
		error: function(x,y,z) {
			console.warn('%o %o %o', x, y, z);
		}
	});
}

function denyIndividualMerge(el) {
	changeIndividualMergeState(el, "deny");
}

function ignoreIndividualMerge(el) {
	changeIndividualMergeState(el, "ignore");
}

function changeIndividualMergeState(el, action) {

	let mergeId = $(el).closest(".merge-notification").attr("id");
	mergeId = mergeId.replace('merge-', '');

	console.log("Updating merge id: "+mergeId);
	let json = {};

	json['mergeId'] = mergeId;
	json['action'] = mergeId;
	
	$.ajax({
		url: wildbookGlobals.baseUrl + '../ScheduledIndividualMergeUpdate',
		type: 'POST',
		data: JSON.stringify(json),
		dataType: 'json',
		contentType: 'application/json',
		success: function(d) {
			console.info('Success updating ScheduledIndividualMerge! Got back '+JSON.stringify(d));
		},
		error: function(x,y,z) {
			console.warn('%o %o %o', x, y, z);
		}
	});
}

function popup() {
	var p = $('<div class="popup" style="display: none;"></div>');
	$('body').append(p);
	return p;
}

//general purpose i18n
function t(which, key) {
	if (!key || !which) return '';
	if (!wildbookGlobals.properties.lang[which]) return '';
	return wildbookGlobals.properties.lang[which][key] || '';
}