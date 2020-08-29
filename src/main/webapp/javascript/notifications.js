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
	p.append('<div id="notifications-scrollbox" class="scroll throbbing" />');
	p.show();

	$.ajax({
		url: wildbookGlobals.baseUrl + '/Collaborate?json=1&getNotifications=1',
		dataType: 'json',
		success: function(d) {
        console.log(d);
			p.find('.scroll').removeClass('throbbing').html(d.content);
			$('.collaboration-invite-notification input').click(function(ev) { clickApproveDeny(ev); });
			addMergeNotifications()
		},
		error: function(a,x,b) {
			p.find('.scroll').removeClass('throbbing').html('error');
		},
		type: 'GET'
	});
	
	
	
}

function addMergeNotifications() {
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
				let thisNotification = notificationsArr[i];
				console.log(JSON.stringify(thisNotification));
				let notificationHTML = '<div class="merge-notification" id="merge-'+thisNotification.taskId+'">';
				notificationHTML += '		<p>Merge Notification: '+thisNotification.primaryIndividualId+'</p>';
				
				//seperate methods for different types
				
				notificationHTML += '</div><br>';
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