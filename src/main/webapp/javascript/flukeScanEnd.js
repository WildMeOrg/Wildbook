

var encounter = {};

//adjust this as algorithms are added. same format as colDefn will be, keyed off header row names
var columnInfo = {
    individualID: {
        label: 'indiv ID',
        //value: _getValue
        value: _indivValue
    },
    encounterID: {
        label: 'encounter ID',
        //value: _getValue,
        value: _encValue,
    },
/*
    overall_score: {
        label: 'std dev score',
        value: _getValue,
	sortFunction: function(a,b) { return parseFloat(a) - parseFloat(b); }
    },
*/
    adaboost_match: {
        label: 'metascore',
        value: _cleanFloatValue,
        sortValue: _getValue,
	sortFunction: function(a,b) { return parseFloat(a) - parseFloat(b); }
    },
    adaboost_nonmatch: {
        label: 'adaboost non-match',
        value: _cleanFloatValue,
        sortValue: _getValue,
	sortFunction: function(a,b) { return parseFloat(a) - parseFloat(b); }
    },
    score_holmbergIntersection: {
        label: 'intersec',
        value: _cleanFloatValue,
        sortValue: _getValue,
	sortFunction: function(a,b) { return parseFloat(a) - parseFloat(b); }
    },
    score_fastDTW: {
        label: 'fastDTW',
        value: _getValue,
	sortFunction: function(a,b) { return parseFloat(a) - parseFloat(b); }
    },
    score_I3S: {
        label: 'I3S',
        value: _cleanFloatValue,
        sortValue: _getValue,
	sortFunction: function(a,b) { return parseFloat(a) - parseFloat(b); }
    },
    //score_proportion: {
    //    label: 'proportion',
    //    value: _cleanFloatValue,
    //    sortValue: _getValue,
	//sortFunction: function(a,b) { return parseFloat(a) - parseFloat(b); }
    //},
    rank: {
        label: 'rank',
        value: _getValue,
	sortFunction: function(a,b) { return parseFloat(a) - parseFloat(b); }
    },
    msm: {
        label: 'MSM',
        value: _getValue,
	sortFunction: function(a,b) { return parseFloat(a) - parseFloat(b); }
    },
    swale: {
        label: 'Swale',
        value: _getValue,
	sortFunction: function(a,b) { return parseFloat(a) - parseFloat(b); }
    },
	euclidean: {
        label: 'Euc.',
        value: _getValue,
	sortFunction: function(a,b) { return parseFloat(a) - parseFloat(b); }
    },
	patterningCode: {
        label: 'PattDiff',
        value: _getValue,
	sortFunction: function(a,b) { return parseFloat(a) - parseFloat(b); }
    },
	dateDiff: {
        label: 'DaysDiff',
        value: _getValue,
	sortFunction: function(a,b) { return parseFloat(a) - parseFloat(b); }
    },
    IBEISColor: {
        label: 'IBEIS',
        value: _getValue,
	sortFunction: function(a,b) { return parseFloat(a) - parseFloat(b); }
    }
};


var colDefn = [];
$(document).ready(function() { wildbook.init(function() { init2(); }); });


function displayChart(enc1, enc2) {
console.warn('****** displayChart(%s,%s) ***********', enc1, enc2);
    $('#chart').html('<div class="note">loading chart data</div>');
    $.ajax({
        url: 'flukeScanIntersectVisualization.jsp?enc1=' + enc1 + '&enc2=' + enc2,
        type: 'GET',
        dataType: 'json',
        error: function(e) {
            var msg = '<b>Error loading chart data</b><br />' + e.status + ' ERROR: <b>' + e.statusText + '</b>';
            $('#chart .note').html(msg);
            console.error(e);
        },
        success: function(d) {
            $('#chart').html('');
console.info('success? %o', d);
            drawChart(d[0].spots, d[1].transformedSpots);
        }
    });
}

var chartInitialized = false;
var chartShownFirstTime = false;
var tableCreated = false;
function initChart() {
console.warn('chart init!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!');
    chartInitialized = true;
    if (tableCreated && !chartShownFirstTime) {
        chartShownFirstTime = true;
        displayChart(encounterNumber, flukeMatchingData[results[0]][columnInfo.encounterID.i]);
    }
}

function init() {
    //google.load('visualization', '1.1', {packages: ['line', 'corechart']});
    //google.load("visualization", "1", {packages:['line', "corechart"]});
    //google.setOnLoadCallback(initChart);

    console.log('ok %o', flukeMatchingData);
    if (!Array.isArray(flukeMatchingData)) {
        alert('do not have flukeMatchingData json');
        return;
    }
    var headerRow = flukeMatchingData.shift();
    for (var i = 0 ; i < headerRow.length ; i++) {
        if (columnInfo[headerRow[i]]) {
            columnInfo[headerRow[i]].i = i;
            columnInfo[headerRow[i]].key = headerRow[i];
            colDefn.push(columnInfo[headerRow[i]]);
        } else {
            console.warn("no columnInfo for %s", headerRow[i]);
        }
    }

    $('#result-images').append('<div class="result-image-wrapper" id="image-main" />');
    $('#result-images').append('<div class="result-image-wrapper" id="image-compare" />');
    doTable();
    tableCreated = true;

    displayImage(encounterNumber, $('#image-main'));
    displayImage(flukeMatchingData[results[0]][columnInfo.encounterID.i], $('#image-compare'));
    setImageMeta(flukeMatchingData[results[0]][columnInfo.adaboost_match.i]);

    if (chartInitialized && !chartShownFirstTime) {
        chartShownFirstTime = true;
        //displayChart(encounterNumber, flukeMatchingData[results[0]][columnInfo.encounterID.i]);
    }


    window.addEventListener("keydown", function(ev) {
        var w = ev.keyCode;
        if ((w == 38) || (w == 40)) {
            ev.preventDefault();
            nudge(w - 39);
        }
    }, false);

}





/*

$(document).keydown(function(k) {
	if ((k.which == 38) || (k.which == 40) || (k.which == 33) || (k.which == 34)) k.preventDefault();
	if (k.which == 38) return tableDn();
	if (k.which == 40) return tableUp();
	if (k.which == 33) return nudge(-howMany);
	if (k.which == 34) return nudge(howMany);
});


var xcolDefn = [
	{
		key: 'thumb',
		label: 'Thumb',
		value: _colThumb,
		nosort: true,
	},
	{
		key: 'individualID',
		label: 'ID',
		value: _colIndLink,
		//sortValue: function(o) { return o.individualID.toLowerCase(); },
	},
	{
		key: 'date',
		label: 'Date',
		value: _colEncDate,
		sortValue: _colEncDateSort,
		sortFunction: function(a,b) { return parseFloat(a) - parseFloat(b); }
	},
	{
		key: 'verbatimLocality',
		label: 'Location',
	},
	{
		key: 'locationID',
		label: 'Location ID',
	},
	{
		key: 'taxonomy',
		label: 'Taxonomy',
		value: _colTaxonomy,
	},
	{
		key: 'submitterID',
		label: 'User',
	},
	{
		key: 'creationDate',
		label: 'Created',
		value: _colCreationDate,
		sortValue: _colCreationDateSort,
	},
	{
		key: 'modified',
		label: 'Edit Date',
		value: _colModified,
		sortValue: _colModifiedSort,
	}

];

*/

var howMany = 10;
var start = 0;
var results = [];

var sortCol = -1; //by default, no sort

var sortReverse = true;

var counts = {
	total: 0,
	ided: 0,
	unid: 0,
	dailydup: 0,
};

var sTable = false;

function doTable() {
        if (columnInfo && columnInfo.rank) {  //sort by rank if we have it
            sortCol = columnInfo.rank.i;
            sortReverse = false;
        }

	sTable = new SortTable({
		data: flukeMatchingData,
		perPage: howMany,
		sliderElement: $('#results-slider'),
		columns: colDefn,
	});

	$('#results-table').addClass('tablesorter').addClass('pageableTable');
	var th = '<thead><tr>';
		for (var c = 0 ; c < colDefn.length ; c++) {
			var cls = 'ptcol-' + colDefn[c].key;
			if (!colDefn[c].nosort) {
				if (sortCol === false) { //init
					sortCol = c;
					cls += ' headerSortUp';
				}
				cls += ' header" onClick="return headerClick(event, ' + c + ');';
			}
			th += '<th class="' + cls + '">' + colDefn[c].label + '</th>';
		}
	$('#results-table').append(th + '</tr></thead>');
	for (var i = 0 ; i < howMany ; i++) {
		var r = '<tr xxxonClick="return rowClick(this);" class="clickable pageableTable-visible">';
		for (var c = 0 ; c < colDefn.length ; c++) {
			r += '<td class="ptcol-' + colDefn[c].key + ' tdw"><div></div></td>';
		}
		r += '</tr>';
		$('#results-table').append(r);
	}

        $('tr.clickable').on('click', function(ev) {
console.log(ev);
            var id = $(ev.currentTarget).data('id');
            var t = $(ev.target);
            if (t.hasClass('enc-button')) {
	        var w = window.open('encounter.jsp?number=' + id, '_blank');
                w.focus();
                return;
            } else if (t.hasClass('indiv-button')) {
                var indivID = t.parent().text();
                var i = indivID.indexOf(' IND'); //craphactacular
                if (i > -1) indivID = indivID.substring(0, i);
	        var w = window.open('../individuals.jsp?number=' + indivID, '_blank');
                w.focus();
                return;
            }
            rowClick(ev.currentTarget);
        });

/*
        $('tr.clickable').on('click', '.enc-button', function(ev) {
            ev.stopPropagation();
console.log(ev); return;
	    var w = window.open('encounter.jsp?number=' + ev.target.innerText, '_blank');
            w.focus();
        });


        $('td.ptcol-individualID').click(function(ev) {
            ev.stopPropagation();
	    var w = window.open('../individuals.jsp?number=' + ev.target.innerText, '_blank');
            w.focus();
        });
*/


	//$('.ptcol-thumb.tdw').removeClass('tdw');

	sTable.initSort();
	sTable.initValues();


	newSlice(sortCol, sortReverse);

	$('#progress').hide();
	sTable.sliderInit();
	show();
	computeCounts();
	displayCounts();

	$('#results-table').on('mousewheel', function(ev) {  //firefox? DOMMouseScroll
		if (!sTable.opts.sliderElement) return;
		ev.preventDefault();
		var delta = Math.max(-1, Math.min(1, (event.wheelDelta || -event.detail)));
		if (delta != 0) nudge(-delta);
	});

}

function setImageMeta(adaboostMatch) {
   // $('#image-meta #score').html('Metascore: <b>' + _cleanFloat(adaboostMatch) + '</b>');
}

function rowClick(el) {
	console.log(el);
	displayImage(el.getAttribute('data-id'), $('#image-compare'));
        setImageMeta($(el).find('.ptcol-adaboost_match').text());
	displayChart(encounterNumber, el.getAttribute('data-id'));
        return false;
/*
	var w = window.open('encounter.jsp?number=' + el.getAttribute('data-id'), '_blank');
	w.focus();
	return false;
*/
}

function headerClick(ev, c) {
	start = 0;
	ev.preventDefault();
	console.log(c);
	if (sortCol == c) {
		sortReverse = !sortReverse;
	} else {
		sortReverse = false;
	}
	sortCol = c;

	$('#results-table th.headerSortDown').removeClass('headerSortDown');
	$('#results-table th.headerSortUp').removeClass('headerSortUp');
	if (sortReverse) {
		$('#results-table th.ptcol-' + colDefn[c].key).addClass('headerSortUp');
	} else {
		$('#results-table th.ptcol-' + colDefn[c].key).addClass('headerSortDown');
	}
console.log('sortCol=%d sortReverse=%o', sortCol, sortReverse);
	newSlice(sortCol, sortReverse);
	show();
}


//will append to el
function addImage(enc, el) {
console.info('addImage(%o, %o)', enc, el);
    //var imgSrc = wildbookGlobals.dataUrl + '/encounters/' + enc.subdir() + '/' + enc.get('spotImageFileName');
    if (!enc.get('annotations') || (enc.get('annotations').length < 1) || !enc.get('annotations')[0].mediaAsset) {
        console.warn('addImage(%o) failed because of bad Annotations/MediaAsset');
        return;
    }
    var ma = new wildbook.Model.MediaAsset(enc.get('annotations')[0].mediaAsset);
    //var imgSrc = wildbookGlobals.dataUrl + '/encounters/' + enc.subdir() + '/extract' + enc.id + '.jpg';
    var imgSrc = ma.labelUrl('_spot', ma.labelUrl());

    el.find('.note').remove();
    el.append('<img src="' + wildbook.cleanUrl(imgSrc) + '"/>');
    var inf = {
        catalogNumber: 'Encounter ID',
        otherCatalogNumbers: 'Alternate Encounter ID',
        individualID: 'Assigned to',
        date: 'Date',
        sex: 'Sex',
        verbatimLocality: 'Location',
        locationID: 'Location ID'
    };
    var h = '<div class="image-info">';
    for (var k in inf) {
        var v = enc.get(k);
        if (k == 'date') v = enc.date();
        if ((v === false) || (v == '')) v = 'None';
        h += '<div class="image-info-' + k + '">' + inf[k] + ': <b>' + v + '</b></div>';
    }
    h += '</div>';
    el.append(h);
}

//loads (if needed) enc data and replaces existing compared image (i.e. right side)
function displayImage(encID, el) {
    el.html('<div class="note">loading ' + encID + '</div>');
    if (encounter[encID]) {
        if (encounter[encID].errorMsg) {
            el.find('.note').html(encounter[encID].errorMsg);
        } else {
            addImage(encounter[encID], el);
        }
        return;
    }
    encounter[encID] = new wildbook.Model.Encounter({catalogNumber: encID});
    encounter[encID].fetch({
        success: function() { addImage(encounter[encID], el); },
        error: function(a,b,c) {
            encounter[encID].errorMsg = '<b>' + encID + '</b><br />' + b.status + ' ERROR: ' + b.statusText;
            el.find('.note').html(encounter[encID].errorMsg);
        }
    });
}


function show() {
	$('#results-table td').html('');
	$('#results-table tbody tr').show();
	for (var i = 0 ; i < results.length ; i++) {
                var encID = flukeMatchingData[results[i]][columnInfo.encounterID.i];
		$('#results-table tbody tr')[i].title = 'Encounter ' + encID;
		$('#results-table tbody tr')[i].setAttribute('data-id', encID);
		for (var c = 0 ; c < colDefn.length ; c++) {
			$('#results-table tbody tr')[i].children[c].innerHTML = '<div>' + sTable.values[results[i]][c] + '</div>';
		}
	}
	if (results.length < howMany) {
		$('#results-slider').hide();
		for (var i = 0 ; i < (howMany - results.length) ; i++) {
			$('#results-table tbody tr')[i + results.length].style.display = 'none';
		}
	} else {
		$('#results-slider').show();
	}

	//if (sTable.opts.sliderElement) sTable.opts.sliderElement.slider('option', 'value', 100 - (start / (flukeMatchingData.length - howMany)) * 100);
	sTable.sliderSet(100 - (start / (sTable.matchesFilter.length - howMany)) * 100);
	displayPagePosition();
}

function computeCounts() {
return;
	counts.total = sTable.matchesFilter.length;
	counts.unid = 0;
	counts.ided = 0;
	counts.dailydup = 0;
	var uniq = {};

	for (var i = 0 ; i < counts.total ; i++) {
		var iid = flukeMatchingData[sTable.matchesFilter[i]].get('individualID');
		if (iid == 'Unassigned') {
			counts.unid++;
		} else {
			var k = iid + ':' + flukeMatchingData[sTable.matchesFilter[i]].get('year') + ':' + flukeMatchingData[sTable.matchesFilter[i]].get('month') + ':' + flukeMatchingData[sTable.matchesFilter[i]].get('day');
			if (!uniq[k]) {
				uniq[k] = true;
				counts.ided++;
			} else {
				counts.dailydup++;
			}
		}
	}
/*
	var k = Object.keys(uniq);
	counts.ided = k.length;
*/
}


function displayCounts() {
	for (var w in counts) {
		$('#count-' + w).html(counts[w]);
	}
}


function displayPagePosition() {
	if (sTable.matchesFilter.length < 1) {
		$('#table-info').html('<b>no matches found</b>');
		return;
	}

	var max = start + howMany;
	if (sTable.matchesFilter.length < max) max = sTable.matchesFilter.length;
	$('#table-info').html((start+1) + ' - ' + max + ' of ' + sTable.matchesFilter.length);
}


function newSlice(col, reverse) {
	results = sTable.slice(col, start, start + howMany, reverse);
}


function nudge(n) {
	start += n;
	if ((start + howMany) > sTable.matchesFilter.length) start = sTable.matchesFilter.length - howMany;
	if (start < 0) start = 0;
console.log('start -> %d', start);
	newSlice(sortCol, sortReverse);
	show();
}

function tableDn() {
	return nudge(-1);
	start--;
	if (start < 0) start = 0;
	newSlice(sortCol, sortReverse);
	show();
}

function tableUp() {
	return nudge(1);
	start++;
	if (start > sTable.matchesFilter.length - 1) start = sTable.matchesFilter.length - 1;
	newSlice(sortCol, sortReverse);
	show();
}



////////
var encs;
$(document).ready( function() {
return;
	wildbook.init(function() {
		encs = new wildbook.Collection.Encounters();
		encs.fetch({
/*
			// h/t http://stackoverflow.com/questions/9797970/backbone-js-progress-bar-while-fetching-collection
			xhr: function() {
				var xhr = $.ajaxSettings.xhr();
				xhr.onprogress = fetchProgress;
				return xhr;
			},
*/
			jdoql: jdoql,
			success: function() { flukeMatchingData = encs.models; doTable(); },
		});
	});
});



function _colIndividual(o) {
	//var i = '<b><a target="_new" href="individuals.jsp?number=' + o.individualID + '">' + o.individualID + '</a></b> ';
	var i = '<b>' + o.individualID + '</b> ';
	if (!extra[o.individualID]) return i;
	i += (extra[o.individualID].firstIdent || '') + ' <i>';
	i += (extra[o.individualID].genusSpecies || '') + '</i>';
	return i;
}


function _colNumberEncounters(o) {
	if (!extra[o.individualID]) return '';
	var n = extra[o.individualID].numberEncounters;
	if (n == undefined) return '';
	return n;
}

/*
function _colYearsBetween(o) {
	return o.get('maxYearsBetweenResightings');
}
*/

function _colNumberLocations(o) {
	if (!extra[o.individualID]) return '';
	var n = extra[o.individualID].locations;
	if (n == undefined) return '';
	return n;
}


function _colTaxonomy(o) {
	if (!o.get('genus') || !o.get('specificEpithet')) return 'n/a';
	return o.get('genus') + ' ' + o.get('specificEpithet');
}


function _colRowNum(o) {
	return o._rowNum;
}


function _colThumb(o) {
	if (!extra[o.individualID]) return '';
	var url = extra[o.individualID].thumbUrl;
	if (!url) return '';
	return '<div style="background-image: url(' + url + ');"><img src="' + url + '" /></div>';
}


function _colModified(o) {
	var m = o.get('modified');
	if (!m) return '';
	var d = wildbook.parseDate(m);
	if (!wildbook.isValidDate(d)) return '';
	return d.toLocaleDateString();
}


function _textExtraction(n) {
	var s = $(n).text();
	var skip = new RegExp('^(none|unassigned|)$', 'i');
	if (skip.test(s)) return 'zzzzz';
	return s;
}






var tableContents = document.createDocumentFragment();

function xdoTable() {
	resultsTable = new pageableTable({
		columns: testColumns,
		tableElement: $('#results-table'),
		sliderElement: $('#results-slider'),
		tablesorterOpts: {
			headers: { 0: {sorter: false} },
			textExtraction: _textExtraction,
		},
	});

	resultsTable.tableInit();

	encs = new wildbook.Collection.Encounters();
	var addedCount = 0;
	encs.on('add', function(o) {
		var row = resultsTable.tableCreateRow(o);
		//row.click(function() { var w = window.open('encounter.jsp?number=' + row.data('id'), '_blank'); w.focus(); });
		//row.click(function() { displayImage(row.data('id'), $('#image-compare')); });
		row.addClass('clickable');
		row.appendTo(tableContents);
		addedCount++;
/*
		var percentage = Math.floor(addedCount / flukeMatchingData.length * 100);
console.log(percentage);
$('#progress').html(percentage);
*/
		if (addedCount >= flukeMatchingData.length) {
			$('#results-table').append(tableContents);
		}
	});

	_.each(flukeMatchingData, function(o) {
//console.log(o);
		encs.add(new wildbook.Model.Encounter(o));
	});
	$('#progress').remove();
	resultsTable.tableShow();

/*
	encs.fetch({
		//fields: { individualID: 'newMatch' },
		success: function() {
			$('#progress').remove();
			resultsTable.tableShow();
		}
	});
*/

}


function _colIndLink(o) {
	var iid = o.get('individualID');
	if (!iid || (iid == 'Unknown') || (iid == 'Unassigned')) return 'Unassigned';
	//if (!iid || (iid == 'Unknown') || (iid == 'Unassigned')) return '<a onClick="return justA(event);" class="pt-vm-button" target="_blank" href="encounterVM.jsp?number=' + o.id + '">Visual Matcher</a><span class="unassigned">Unassigned</span>';
//
//
	return '<a target="_blank" onClick="return justA(event);" title="Individual ID: ' + iid + '" href="../individuals.jsp?number=' + iid + '">' + iid + '</a>';
}


//stops propagation of click to enclosing <TR> which wants click too
function justA(ev) {
	ev.stopPropagation();
	return true;
}


//new way

function _colEncDate(o) {
	return o.dateAsString();
}

function _colEncDateSort(o) {
	var d = o.date();
	if (!d) return 0;
	return d.getTime();
}

//old way
//function _colEncDate(o) {
//	var d = o.date();
//	if (!d) return '';
//	return d.toLocaleDateString();
//}

//function _colEncDateSort(o) {
//	var d = o.date();
//	if (!d) return '';
//	return d.getTime();
//}

function _colTaxonomy(o) {
	if (!o.get('genus') || !o.get('specificEpithet')) return 'n/a';
	return o.get('genus') + ' ' + o.get('specificEpithet');
}


function _colRowNum(o) {
	return o._rowNum;
}


function _colThumb(o) {
	var url = o.thumbUrl();
	if (!url) return '';
	return '<div style="background-image: url(' + url + ');"><img src="' + url + '" /></div>';
	return '<img src="' + url + '" />';
}


function _colModified(o) {
	var m = o.get('modified');
	if (!m) return '';
	var d = wildbook.parseDate(m);
	if (!wildbook.isValidDate(d)) return '';
	return d.toLocaleDateString();
}

function _colModifiedSort(o) {
	var m = o.get('modified');
	if (!m) return '';
	var d = wildbook.parseDate(m);
	if (!wildbook.isValidDate(d)) return '';
	return d.getTime();
}

function _colCreationDate(o) {
	var m = o.get('dwcDateAdded');
	if (!m) return '';
	var d = wildbook.parseDate(m);
	if (!wildbook.isValidDate(d)) return '';
	return d.toLocaleDateString();
}

function _colCreationDateSort(o) {
	var m = o.get('dwcDateAdded');
	if (!m) return '';
	var d = wildbook.parseDate(m);
	if (!wildbook.isValidDate(d)) return 0;
	return d.getTime();
}



function _textExtraction(n) {
	var s = $(n).text();
	var skip = new RegExp('^(none|unassigned|)$', 'i');
	if (skip.test(s)) return 'zzzzz';
	return s;
}

function _getValue(obj, key) {
//console.log('_getValue(%o,%o)', obj, key);
    if (colDefn[key].i == undefined) return '-?-';
    return obj[colDefn[key].i];
}


function _encValue(obj, key) {
    //var id = _getValue(obj, key);
    return '<div class="enc-button link-button" target="_new">encounter</div>';
}

function _indivValue(obj, key) {
    var id = _getValue(obj, key);
    return id + ' <div class="indiv-button link-button" target="_new">INDIV</div>';
}

function _cleanFloatValue(obj, key) {
    return _cleanFloat(_getValue(obj, key));
}

function _cleanFloat(f) {
    f = f.toString();
    if (f == '0') return f;
    var dot = f.indexOf('.');
    if (dot < 0) return f + '.000';
    return f.substring(0, dot + 4);
}

function applyFilter() {
	var t = $('#filter-text').val();
console.log(t);
	sTable.filter(t);
	start = 0;
	newSlice(0);
	show();
	computeCounts();
	displayCounts();
}



function drawChart(d1, d2) {
    var data = new google.visualization.DataTable();
    data.addColumn('number', 'x');
    data.addColumn('number', 'y');

    var isDorsal = (Math.abs(d2[0][0] - d2[2][0]) < 30);
    if (isDorsal) {
        console.warn('drawChart() thinks this is a dorsal fin');
        var d1inv = [];
        for (var i = 0 ; i < d1.length ; i++) {
            var y = -400 - d1[i][1];
            if (y < -100) continue;
            d1inv.push([ d1[i][0], y]);
        }
        data.addRows(d1inv);
    } else {
        data.addRows(d1);
    }

    var data2 = new google.visualization.DataTable();
    data2.addColumn('number', 'x');
    data2.addColumn('number', 'y');
    if (isDorsal) {
        var d2inv = [];
        for (var i = 0 ; i < d2.length ; i++) {
            var y = -400 - d2[i][1];
            if (y < -100) continue;
            d2inv.push([ d2[i][0], y]);
        }
        data2.addRows(d2inv);
    } else {
        data2.addRows(d2);
    }

    var joinedData = google.visualization.data.join(data, data2, 'full', [[0, 0]], [1], [1]);
    var options = {
        title: 'Render Fluke',
        width: 600,
        height: 400,
        pointSize: 5,
        backgroundColor: { fill: 'transparent' },
        color: 'yellow',
        series: {
            0: { color: 'blue' },
            1: { color: 'yellow' },
            2: { color: 'green' },
            3: { color: 'green' }
        }
    };
    var chart = new google.visualization.LineChart(document.getElementById('chart'));
    chart.draw(joinedData, options);
}
