var encData = false;

var candidateCriteria = {};
var currentCandidate = {};
var candidatesData = {};

function initVM(el, encID) {
	if (!el || !encID) return;
	$('body').append('<div id="match-dialog" />');
console.log('got encID='+encID);
	var wrapper = $('<div id="vm-wrapper" />');
	wrapper.append('<input id="match-button" type="button" value="' + wildbookGlobals.properties.lang.visualMatcher.matchButton + '" onClick="event.stopPropagation(); candidateUse()" />');
	wrapper.append('<div id="vm-target-wrapper"><div class="title">' + wildbookGlobals.properties.lang.visualMatcher.targetTitle + '</div><div id="vm-target-main" class="img-wrapper" /><div id="vm-target-small" /><div id="small-note">' + wildbookGlobals.properties.lang.visualMatcher.targetOtherImages + '</div></div>');
	wrapper.append('<div id="vm-candidates-wrapper"><div class="title">' + wildbookGlobals.properties.lang.visualMatcher.candidatesTitle + '<span id="cand-count"></span></div><div id="vm-candidates-imgs" /><div id="vm-candidates-controls" /></div>');
	el.append(wrapper);
	fetchTarget(encID);
	$('body').keydown(function(ev) { if (ev.which == 90) candidateFullZoom(); });
	$('body').keyup(function(ev) {
		if ((ev.which == 90) || (ev.which == 27)) closeFullZoom();
	});
}

function closeFullZoom() {
        $('#vm-wrapper').css('opacity', 1);
	$('#candidate-full-zoom').hide();
}

function loadTarget(data, imgNum) {
	setTargetImg(data, imgNum);
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

        var imgNum = 0;
	encData = d;
	encounterNumber = d.id;
	candidateCriteria.locationID = d.locationID;
	candidateCriteria.sex = d.sex;
	candidateCriteria.patterningCode = d.patterningCode;
	//candidateCriteria.mmaCompatible = d.mmaCompatible;

        for (var i = 0 ; i < encData.images.length ; i++) {
            if (encData.images[i].id == mediaAssetId) imgNum = i;
        }

	createControls();
	loadTarget(d, imgNum);
	findCandidates();
}


function findCandidates() {
	var url = '../EncounterVMData?';
        $('#cand-count').html('');
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
	candidatesData = {};
	console.log(data);
	if (data.error) {
		$('#vm-candidates-imgs').html('<p class="error"><h2>' + wildbookGlobals.properties.lang.visualMatcher.errorFetching + '</h2>' + data.error + '</p>');
		return;
	}
	if (!data.candidates || (data.candidates.length < 1)) {
		$('#vm-candidates-imgs').html('<p>' + wildbookGlobals.properties.lang.visualMatcher.candidatesNoMatch + '</p>');
		return;
	}

        if (data.maximumCandidatesReached) {
            $('#cand-count').html(' (' + data.candidates.length + '+) <i>MAX reached</i>');
        } else {
            $('#cand-count').html(' (' + data.candidates.length + ')');
        }
	data.candidates = candidatesOrder(data.candidates, 'mmaCompatible');
	$('#vm-candidates-imgs').html('');
	for (var c = 0 ; c < data.candidates.length ; c++) {
		if (!data.candidates[c].images || !data.candidates[c].images.length) continue;
		var h = '<div class="candidate" id="' + data.candidates[c].id + '">';
		for (var i = 0 ; i < data.candidates[c].images.length ; i++) {
			candidatesData[data.candidates[c].id] = data.candidates[c];
			h += '<img title="' + imgInfo(data.candidates[c], i) + '" class="thumb" data-i="' + i + '" src="' + data.candidates[c].images[i].thumbUrl + '" />';
		}
		//h += '<div class="info">' + [data.candidates[c].id, data.candidates[c].locationID, data.candidates[c].releaseDate].join(' | ') + '</div></div>';
		h += '<div class="info">' + imgInfo(data.candidates[c]) + '</div></div>';
		$('#vm-candidates-imgs').append(h);
	}
	$('#vm-candidates-imgs img').click(function(ev) { candidateClick(this, true); ev.stopPropagation(); });
	$('#vm-candidates-imgs div.candidate').click(function() { candidateEncounterClick(this.id); });
	$('#vm-candidates-imgs img').mouseover(function() { candidateHover(this); });
}


function candidateClick(el, doZoom) {
	var eid = el.parentElement.getAttribute('id');
	var i = el.getAttribute('data-i');
	currentCandidate = {
		i: i,
		src: candidatesData[eid].images[i].url
	};
	for (var k in candidatesData[eid]) {
		currentCandidate[k] = candidatesData[eid][k];
	}
	if (doZoom) candidateFullZoom();
}

function candidateEncounterClick(eid) {
	candidateClick(document.getElementById(eid).children[0]);
	candidateMakeActive(currentCandidate.id);
}

function candidateMakeActive(eid) {
	$('#vm-candidates-imgs .selected').removeClass('selected');
	$('#' + eid).addClass('selected').append($('#match-button'));
	$('#match-button').show();
}

function candidateHover(el) {
	var eid = el.parentElement.getAttribute('id');
	var i = el.getAttribute('data-i');
	currentCandidate = {
		i: i,
		src: candidatesData[eid].images[i].url
	};
	for (var k in candidatesData[eid]) {
		currentCandidate[k] = candidatesData[eid][k];
	}
	$('#zoom-message').html(wildbookGlobals.properties.lang.visualMatcher.zoomMessage.replace('%s', currentCandidate.id).replace('%s', currentCandidate.i));
}


function candidateFullZoom() {
	if (!currentCandidate.src) return;
	$('#candidate-full-zoom').html('<img src="' + $('#vm-target-main img').attr('src') + '" /><img src="' + currentCandidate.src + '" /><div class="close-button" onClick="closeFullZoom()">close</div><div class="use-button" onClick="candidateUse()">use match</div>').show();
	candidateMakeActive(currentCandidate.id);
        $('#vm-wrapper').css('opacity', 0.2);
}


function candidateUse() {
	if (!currentCandidate) return;
	var h = '<div><form action="../EncounterVMData" method="post">';
	if (nullIndividual(encData.individualID) && nullIndividual(currentCandidate.individualID)) {
		h += '<p>' + wildbookGlobals.properties.lang.visualMatcher.matchNew + '</p><div><input name="matchID" style="margin-right: 15px;" /><input type="submit" value="OK" />';
		h += '<input type="hidden" name="number" value="' + encounterNumber + '" />';
		h += '<input type="hidden" name="candidate_number" value="' + currentCandidate.id + '" />';
	} else if (nullIndividual(encData.individualID)) {
		h += '<p>' + wildbookGlobals.properties.lang.visualMatcher.matchCandidate.replace('%s', currentCandidate.individualID) + '<p><div><input name="matchID" type="hidden" value="' + currentCandidate.individualID + '" /><input type="submit" value="OK" />';
		h += '<input type="hidden" name="number" value="' + encounterNumber + '" />';
	} else if (nullIndividual(currentCandidate.individualID)) {
		h += '<p>' + wildbookGlobals.properties.lang.visualMatcher.matchTarget.replace('%s', encData.individualID) + '<p><div><input name="matchID" type="hidden" value="' + encData.individualID + '" /><input type="submit" value="OK" />';
		h += '<input type="hidden" name="number" value="' + currentCandidate.id + '" />';
	} else {
		h += '<p>' + wildbookGlobals.properties.lang.visualMatcher.matchConflict + '</p>';
	}
	h += ' <input style="margin-left: 25px;" type="button" value="Cancel" onClick="$(\'#match-dialog\').hide()" /></form></div>';

	$('#match-dialog').html(h).show();
}


function imgInfo(data, i) {
	var info = data.id;
	if (!nullIndividual(data.individualID)) info += ' (' + data.individualID + ')';
	info += ': ';
	if (data.locationID && (data.locationID != 'None') && (data.locationID != '')) info += data.locationID + ' ';

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

/*  MMA is mantamatcher specific, so disabling here
	var mmaSelTrue = '';
	var mmaSelFalse = '';
	if (candidateCriteria.mmaCompatible) {
		mmaSelTrue = 'selected';
	} else if (candidateCriteria.mmaCompatible != undefined) {
		mmaSelFalse = 'selected';
	}

	h += '<select name="mmaCompatible"><option value="">' + wildbookGlobals.properties.lang.visualMatcher.anyMma + '</option><option ' + mmaSelTrue + ' value="true">' + wildbookGlobals.properties.lang.visualMatcher.mmaCompat + '</option><option ' + mmaSelFalse + ' value="false">' + wildbookGlobals.properties.lang.visualMatcher.mmaIncompat + '</option></select>';
*/

	h += '<input type="button" style="padding: 2px 5px; margin: 0 10px;" value="' + wildbookGlobals.properties.lang.visualMatcher.searchButton + '" onClick="candidateSearch()" />';

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


function nullIndividual(iid) {
	return !(iid && (iid != '') && (iid != 'Unassigned'));
}

function candidatesOrder(carr, field) {  //TODO fieldSSSSS
	return carr.sort(function(a,b) { return _csort(a,b,field); });
}

function _csort(a, b, field) {
	if (field == 'mmaCompatible') {
		if (a.mmaCompatible) return 1;
		return -1;
	}
	return 0;
}




