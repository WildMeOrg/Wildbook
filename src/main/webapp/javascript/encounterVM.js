
var encData = false;

function initVM(el, encID) {
	if (!el || !encID) return;
console.log('got encID='+encID);
	var wrapper = $('<div id="vm-wrapper" />');
	wrapper.append('<div id="vm-target-wrapper"><div id="vm-target-main" class="img-wrapper" /><div id="vm-target-small" /></div>');
	wrapper.append('<div id="vm-candidates-wrapper" />');
	el.append(wrapper);
	fetchTarget(encID);
}


function loadTarget(data) {
	setTargetImg(data);
}


function setTargetImg(data, i) {
	if (i == undefined) i = 0;
	if (!data.images[i]) return;
	$('#vm-target-main').html('<img data-i="' + i + '" src="' + data.images[i].url + '" /><div>' + niceKeywords(data.images[i].keywords) + '</div>');

	$('#vm-target-small').html('');
	for (var j = 0 ; j < data.images.length ; j++) {
		if (j == i) continue;
		$('#vm-target-small').append('<img data-i="' + j + '" src="' + data.images[j].url + '" title="' + niceKeywords(data.images[j].keywords) + '" />');
	}

	$('#vm-target-wrapper img').click(function() { setTargetImg(encData, this.getAttribute('data-i')); });
}

function fetchTarget(encID) {
	$.ajax({
		url: '../EncounterVMData?number=' + encID,
		success: function(d) { gotTarget(d); },
		error: function(a,b,c) { gotTarget({error: a+' '+b+' '+c}); },
		dataType: 'json'
	});
}


function gotTarget(d) {
	console.log(d);
	if (d.error) {
		$('#vm-wrapper').html('<p class="error"><h2>Error fetching data</h2>' + d.error + '</p>');
		return;
	}

	encData = d;
	loadTarget(d);
}



function niceKeywords(keywords) {
	if (!keywords) return '';
	var k = [];
	for (var i = 0 ; i < keywords.length ; i++) {
		k.push(keywords[i].readableName);
	}
	return k.join(', ');
}

