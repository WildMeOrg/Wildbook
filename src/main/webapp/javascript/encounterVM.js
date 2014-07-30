
var encData = false;

var candidateCriteria = {};
var currentCandidate = {};

function initVM(el, encID) {
	if (!el || !encID) return;
console.log('got encID='+encID);
	var wrapper = $('<div id="vm-wrapper" />');
	wrapper.append('<div id="vm-target-wrapper"><div id="vm-target-main" class="img-wrapper" /><div id="vm-target-small" /></div>');
	wrapper.append('<div id="vm-candidates-wrapper"><div id="vm-candidates-imgs" /><div id="vm-candidates-controls" /></div>');
	el.append(wrapper);
	fetchTarget(encID);
	createControls();
	$('body').keydown(function(ev) { if (ev.which == 90) candidateFullZoom(); });
	$('body').keyup(function(ev) { if (ev.which == 90) $('#candidate-full-zoom').hide(); });
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
	encounterNumber = d.id;
	candidateCriteria.locationID = d.locationID;
	candidateCriteria.sex = d.sex;
	candidateCriteria.patterningCode = d.patterningCode;

	loadTarget(d);
	findCandidates();
}


function findCandidates() {
	var url = '../EncounterVMData?';
	$('#vm-candidates-imgs').html('<p><i>searching...</i></p>');
	$.each(candidateCriteria, function(key, val) {
		if ((candidateCriteria[key] != '') && (candidateCriteria[key] != 'undefined') && (candidateCriteria[key] != 'None') && (candidateCriteria[key] != undefined)) url += key + '=' + candidateCriteria[key] + '&';
	});
	url += 'candidates=1&number=' + encounterNumber;
console.log(url);

	$.ajax({
		url: url,
		success: function(d) { gotCandidates(d); },
		error: function(a,b,c) { gotCandidates({error: a+' '+b+' '+c}); },
		dataType: 'json'
	});
}


function gotCandidates(data) {
	console.log(data);
	if (data.error) {
		$('#vm-candidates-imgs').html('<p class="error"><h2>Error fetching data</h2>' + data.error + '</p>');
		return;
	}
	if (!data.candidates || (data.candidates.length < 1)) {
		$('#vm-candidates-imgs').html('<p>no matching candidates</p>');
		return;
	}

	$('#vm-candidates-imgs').html('<div id="candidate-zoom"><img /><div id="close" onClick="candidateZoomClose()">close</div><div id="use" onClick="candidateZoomUse()">use this</div></div>');
	for (var c = 0 ; c < data.candidates.length ; c++) {
		if (!data.candidates[c].images || !data.candidates[c].images.length) continue;
		var h = '<div class="candidate" id="' + data.candidates[c].id + '">';
		for (var i = 0 ; i < data.candidates[c].images.length ; i++) {
			h += '<img class="thumb" data-i="' + i + '" src="' + data.candidates[c].images[i].url + '" />';
		}
		h += '<div class="info">' + [data.candidates[c].id, data.candidates[c].locationID, data.candidates[c].releaseDate].join(' | ') + '</div></div>';
		$('#vm-candidates-imgs').append(h);
	}
	$('#vm-candidates-imgs img').click(function() { candidateClick(this); });
}


function candidateClick(el) {
	$('#candidate-zoom img').attr('src', el.src);
	$('#candidate-zoom').show();
	currentCandidate = {
		i: el.getAttribute('data-i'),
		eid: el.parentElement.getAttribute('id'),
		src: el.src
	};
console.log(currentCandidate);
}


function candidateZoomClose() {
	$('#candidate-zoom').hide();
}

function candidateZoomUse() {
}


function candidateFullZoom() {
	if (!currentCandidate.src) return;
	$('#candidate-full-zoom').html('<img src="' + $('#vm-target-main img').attr('src') + '" /><img src="' + currentCandidate.src + '" /><div class="close-button" onClick="$(\'#candidate-full-zoom\').hide()">close</div>').show();
}



function niceKeywords(keywords) {
	if (!keywords) return '';
	var k = [];
	for (var i = 0 ; i < keywords.length ; i++) {
		k.push(keywords[i].readableName);
	}
	return k.join(', ');
}


function createControls() {
	var h = '<select name="patterningCode"><option value="">Any pigmentation</option>';
	for (var i = 0 ; i < patterningCodes.length ; i++) {
		var sel = (candidateCriteria.patterningCode == patterningCodes[i] ? ' selected' : '');
		h += '<option' + sel + '>' + patterningCodes[i] + '</option>';
	}
	h += '</select>';

	var sex = ['unknown', 'female', 'male'];
	h += '<select name="sex">';
	for (var i = 0 ; i < sex.length ; i++) {
		var sel = (candidateCriteria.sex == sex[i] ? ' selected' : '');
		h += '<option' + sel + '>' + sex[i] + '</option>';
	}
	h += '</select>';

	h += '<select name="locationID"><option value="">Any region</option>';
	for (var i = 0 ; i < regions.length ; i++) {
		var sel = (candidateCriteria.locationID == regions[i] ? ' selected' : '');
		h += '<option' + sel + '>' + regions[i] + '</option>';
	}
	h += '</select>';

	h += '<select name="mma-compat"><option>MMA-compatible</option><option>not MMA-compatible</option></select>';

	h += '<input type="button" value="SEARCH" onClick="candidateSearch()" />';

	$('#vm-candidates-controls').html(h);
}


function candidateSearch() {
	$('#vm-candidates-controls select').each(function(i, el) {
		var jel = $(el);
		candidateCriteria[jel.attr('name')] = jel.val();
	});
console.log(candidateCriteria);
	findCandidates();
}


