//wildbookGlobals.properties.lang.collaboration.invitePromptOne
var wildbookGlobals = {
	properties: {
		lang: {
			visualMatcher: {
zoomMessage: 'Hit <b>Z</b> to zoom in on <b>Encounter %s</b>, image %s.',
targetTitle: 'Encounter Target Image',
targetOtherImages: 'Other available target images from this encounter',
candidatesTitle: 'Candidate Encounters',
anyPigmentation: 'Any pigmentation',
anySex: 'Any sex',
anyRegion: 'Any region',
mmaCompat: 'MMA-compatible',
mmmIncompat: 'Not MMA-compatible',
searchButton: 'SEARCH',
errorFetching: 'Error fetching data',
candidatesNoMatch: 'No matching candidates.',
searching: 'searching...',
			}
		}
	}
};

var encData = false;

var candidateCriteria = {};
var currentCandidate = {};

function initVM(el, encID) {
	if (!el || !encID) return;
console.log('got encID='+encID);
	var wrapper = $('<div id="vm-wrapper" />');
	wrapper.append('<div id="vm-target-wrapper"><div class="title">' + wildbookGlobals.properties.lang.visualMatcher.targetTitle + '</div><div id="vm-target-main" class="img-wrapper" /><div id="vm-target-small" /><div id="small-note">' + wildbookGlobals.properties.lang.visualMatcher.targetOtherImages + '</div></div>');
	wrapper.append('<div id="vm-candidates-wrapper"><div class="title">' + wildbookGlobals.properties.lang.visualMatcher.candidatesTitle + '</div><div id="vm-candidates-imgs" /><div id="vm-candidates-controls" /></div>');
	el.append(wrapper);
	fetchTarget(encID);
	$('body').keydown(function(ev) { if (ev.which == 90) candidateFullZoom(); });
	$('body').keyup(function(ev) { if (ev.which == 90) $('#candidate-full-zoom').hide(); });
}


function loadTarget(data) {
	setTargetImg(data);
}


function setTargetImg(data, i) {
	if (i == undefined) i = 0;
	if (!data.images[i]) return;
	$('#vm-target-main').html('<img data-i="' + i + '" src="' + data.images[i].url + '" /><div>' + imgInfo(data, i) + '</div>');

	$('#vm-target-small').html('');
	for (var j = 0 ; j < data.images.length ; j++) {
		if (j == i) continue;
		$('#vm-target-small').append('<img data-i="' + j + '" src="' + data.images[j].url + '" title="' + imgInfo(data, j) + '" />');
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
		$('#vm-wrapper').html('<p class="error"><h2>' + wildbookGlobals.properties.lang.visualMatcher.errorFetching + '</h2>' + d.error + '</p>');
		return;
	}

	encData = d;
	encounterNumber = d.id;
	candidateCriteria.locationID = d.locationID;
	candidateCriteria.sex = d.sex;
	candidateCriteria.patterningCode = d.patterningCode;

	createControls();
	loadTarget(d);
	findCandidates();
}


function findCandidates() {
	var url = '../EncounterVMData?';
	$('#vm-candidates-imgs').html('<p><i>' + wildbookGlobals.properties.lang.visualMatcher.searching + '</i></p>');
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
		$('#vm-candidates-imgs').html('<p class="error"><h2>' + wildbookGlobals.properties.lang.visualMatcher.errorFetching + '</h2>' + data.error + '</p>');
		return;
	}
	if (!data.candidates || (data.candidates.length < 1)) {
		$('#vm-candidates-imgs').html('<p>' + wildbookGlobals.properties.lang.visualMatcher.candidatesNoMatch + '</p>');
		return;
	}

	$('#vm-candidates-imgs').html('');
	for (var c = 0 ; c < data.candidates.length ; c++) {
		if (!data.candidates[c].images || !data.candidates[c].images.length) continue;
		var h = '<div class="candidate" id="' + data.candidates[c].id + '">';
		for (var i = 0 ; i < data.candidates[c].images.length ; i++) {
			h += '<img title="' + imgInfo(data.candidates[c], i) + '" class="thumb" data-i="' + i + '" src="' + data.candidates[c].images[i].url + '" />';
		}
		//h += '<div class="info">' + [data.candidates[c].id, data.candidates[c].locationID, data.candidates[c].releaseDate].join(' | ') + '</div></div>';
		h += '<div class="info">' + imgInfo(data.candidates[c]) + '</div></div>';
		$('#vm-candidates-imgs').append(h);
	}
	$('#vm-candidates-imgs img').click(function() { candidateClick(this); });
	$('#vm-candidates-imgs img').mouseover(function() { candidateHover(this); });
}


function candidateClick(el) {
	currentCandidate = {
		i: el.getAttribute('data-i'),
		eid: el.parentElement.getAttribute('id'),
		src: el.src
	};
	candidateFullZoom();
}


function candidateHover(el) {
	currentCandidate = {
		i: el.getAttribute('data-i'),
		eid: el.parentElement.getAttribute('id'),
		src: el.src
	};

	$('#zoom-message').html(wildbookGlobals.properties.lang.visualMatcher.zoomMessage.replace('%s', currentCandidate.eid).replace('%s', currentCandidate.i));
}


function candidateFullZoom() {
	if (!currentCandidate.src) return;
	$('#candidate-full-zoom').html('<img src="' + $('#vm-target-main img').attr('src') + '" /><img src="' + currentCandidate.src + '" /><div class="close-button" onClick="$(\'#candidate-full-zoom\').hide()">close</div>').show();
}



function imgInfo(data, i) {
	var info = data.id + ' ';

	if (data.dateInMilliseconds) {
		var d = new Date(data.dateInMilliseconds);
		info += d.toLocaleDateString() + ' ';
	}

	if ((i != undefined) && data.images && data.images[i] && data.images[i].keywords) {
		info += '[img ' + i;
		var k = [];
		for (var j = 0 ; j < data.images[i].keywords.length ; j++) {
			k.push(data.images[i].keywords[j].readableName);
		}
		if (k.length) info += '; ' + k.join('|');
		info += ']';
	}

	return info;
}


function createControls() {
	var h = '<select name="patterningCode"><option value="">' + wildbookGlobals.properties.lang.visualMatcher.anyPigmentation + '</option>';
	for (var i = 0 ; i < patterningCodes.length ; i++) {
		var sel = (candidateCriteria.patterningCode == patterningCodes[i] ? ' selected' : '');
		h += '<option' + sel + '>' + patterningCodes[i] + '</option>';
	}
	h += '</select>';

	var sex = ['unknown', 'female', 'male'];
	h += '<select name="sex"><option value="">' + wildbookGlobals.properties.lang.visualMatcher.anySex + '</option>';
	for (var i = 0 ; i < sex.length ; i++) {
		var sel = (candidateCriteria.sex == sex[i] ? ' selected' : '');
		h += '<option' + sel + '>' + sex[i] + '</option>';
	}
	h += '</select>';

	h += '<select name="locationID"><option value="">' + wildbookGlobals.properties.lang.visualMatcher.anyRegion + '</option>';
	for (var i = 0 ; i < regions.length ; i++) {
		var sel = (candidateCriteria.locationID == regions[i] ? ' selected' : '');
		h += '<option' + sel + '>' + regions[i] + '</option>';
	}
	h += '</select>';

	h += '<select name="mmaCompatible"><option value="true">' + wildbookGlobals.properties.lang.visualMatcher.mmaCompat + '</option><option value="false">' + wildbookGlobals.properties.lang.visualMatcher.mmaIncompat + '</option></select>';

	h += '<input type="button" value="' + wildbookGlobals.properties.lang.visualMatcher.searchButton + '" onClick="candidateSearch()" />';

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


