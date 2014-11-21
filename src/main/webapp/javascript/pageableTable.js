
/*

$(document).ready(function() {
	wildbook.init(function() {
		tableInit();
		fetchData();
	});
});

*/


var testColumns = {
	catalogNumber: { label: 'Number' },
	//verbatimLocality: { label: 'Location' },
	//dataTypes: { label: 'Data types', val: dataTypes },
	individualID: { label: 'Individual' },
	sex: { label: 'Sex', val: cleanValue },
};



function start() {
	wildbook.init(function() {
		TEST();
	});
}

var encs;
var foo;
function TEST() {
	foo = new pageableTable({
		columns: testColumns,
		tableElement: $('#results-table'),
		sliderElement: $('#results-slider'),
	});

	foo.tableInit();

	encs = new wildbook.Collection.Encounters();
	encs.on('add', function(o) {
		foo.tableAddRow(o);
foo.tableAddRow(o); foo.tableAddRow(o); foo.tableAddRow(o); foo.tableAddRow(o);
//tableAddRow(o); tableAddRow(o);tableAddRow(o); tableAddRow(o);
	});
	encs.fetch({
		//fields: { individualID: 'newMatch' },
		success: function() {
			foo.tableShow();
		}
	});

}


function pageableTable(opts) {
	var me = this;
	this.opts = opts;
	this.pstart = 0;
	if (!this.opts.perPage) this.opts.perPage = 20;

	if (!opts.tableElement) opts.tableElement = $('<table></table>');
	opts.tableElement.addClass('tablesorter').addClass('pageableTable').hide();

	this.tableBody = false;


	this.tableInit = function() {
		if (!this.opts.columns) return;
		this.tableBody = $('<tbody id="table-body"></tbody>');
		var hd = '<thead><tr><th class="ptcol-num">#</th>';
		_.each(this.opts.columns, function(cstruct, c) { hd += '<th class="ptcol-' + c + '">' + cstruct.label + '</th>'; });
		opts.tableElement.append(hd + '</tr></thead>').append(this.tableBody);
		opts.tableElement.on('mousewheel', function(ev) {  //firefox? DOMMouseScroll
			ev.preventDefault();
			var delta = Math.max(-1, Math.min(1, (event.wheelDelta || -event.detail)));
console.log(delta);
			if (delta != 0) me.pageNudge(-delta);
		});
	};


	this.lastSliderStart = -1;
	this.sliderInit = function() {
		if (!this.opts.sliderElement) return;
		this.opts.sliderElement.addClass('pageableTable-slider');
		if (this.rowCount - this.opts.perPage < 1) return;
		this.opts.sliderElement.slider({
			orientation: 'vertical',
			value: 100,
			slide: function(a, b) {
				var start = Math.floor((100 - b.value) / 100 * (me.rowCount - me.opts.perPage) + 0.5);
				if (start == me.lastSliderStart) return;
				me.lastSliderStart = start;
				console.log(start);
				me.pageTable(start);
			}
		});
	};


	this.rowCount = 0;
	this.tableAddRow = function(obj) {
		var i = this.rowCount++;
		var td = '';
		var search = '';
		_.each(this.opts.columns, function(cstruct, c) {
			var val = (cstruct.val ? cstruct.val(obj, c) : obj.get(c));
			search += ' ' + val;
			td += '<td>' + val + '</td>';
		});
		this.tableBody.append('<tr data-search="' + search + '" id="n' + i + '"><td>' + i + '</td>' + td + '</tr>');
	};


	this.tableShow = function() {
		this.opts.tableElement.tablesorter().show();
		this.sliderInit();
		this.opts.tableElement.bind('sortEnd', function() {
			me.pstart = 0;
			if (me.opts.sliderElement) me.opts.sliderElement.slider('option', 'value', 100);
			me.pageTable(0);
		});
		me.pageTable(0);
/*
		$(document).on('keydown', function(ev) {
		var m = { 38: -1, 40: 1 };
		if (!m[ev.which]) return;
		pageNudge(m[ev.which]);
		ev.preventDefault();
	});
*/

		if (this.opts.searchElement) {
			this.lastSearch = false;
			this.opts.searchElement.on('keyup', function() {
				var val = this.value;
				if (val == me.lastSearch) return;
				me.lastSearch = val;
				if (val == '') {
					$('#table-body tr.nof').removeClass('nof');
					me.pageTable(0);
					return;
				}
				me.tableSearch(val);
			});
		}


	};


	this.pageTable = function(start, howMany) {
		if (!howMany) howMany = this.opts.perPage || 20;
		this.pstart = start;
		this.tableBody.find('.pageableTable-visible').removeClass('pageableTable-visible');
		var ends = start + howMany;
		for (var i = start ; i < ends ; i++) {
			if (!this.tableBody.children()[i]) continue;
			if ($(this.tableBody.children()[i]).hasClass('pageableTable-nof')) {
				ends++;
				continue;
			}
			this.tableBody.children()[i].className = 'pageableTable-visible';
		}
	};


	this.pageNudge = function(n) {
		this.pstart += n;
		if (this.pstart < 0) this.pstart = 0;
		if (this.pstart > (this.rowCount - this.opts.perPage)) this.pstart = this.rowCount - this.opts.perPage;
		this.pageTable(this.pstart);

		if (this.opts.sliderElement) this.opts.sliderElement.slider('option', 'value', 100 - (this.pstart / (this.rowCount - this.opts.perPage)) * 100);
	};


	this.tableSearch = function(s) {
		var regex = new RegExp(s, 'i');
		this.tableBody.find('tr').removeClass('pageableTable-nof').each(function(i, el) {
			if (!regex.test(el.getAttribute('data-search'))) el.className = 'pageableTable-nof';
		});
		this.pageTable(0);
	};


}

/*
var xcolumns = {
	catalogNumber: { label: 'Number' },
	verbatimLocality: { label: 'Location' },
	dataTypes: { label: 'Data types', val: dataTypes },
	sex: { label: 'Sex', val: cleanValue },
};

var columns = {
	catalogNumber: { label: 'Number' },
	//verbatimLocality: { label: 'Location' },
	//dataTypes: { label: 'Data types', val: dataTypes },
	individualID: { label: 'Individual' },
	sex: { label: 'Sex', val: cleanValue },
};

var encs = false;

var pstart = 0;
var perPage = 20;


var countTotal = -1;
var countProgress = -1;


var lastSliderStart = -1;
var hasSlider = false;
function initSlider() {
	if (countTotal - perPage < 1) return;
	hasSlider = true;
	$('#slider').slider({
		orientation: 'vertical',
		value: 100,
		slide: function(a, b) {
			var start = Math.floor((100 - b.value) / 100 * (countTotal - perPage) + 0.5);
			if (start == lastSliderStart) return;
			lastSliderStart = start;
			console.log(start);
			pageTable(start, perPage);
		}
	});
}
*/

/*
var subPending = 0;
var totalLoad = 0;
function fetchSubData() {
	subPending = encs.models.length * 2;
	totalLoad = subPending;
	_.each(encs.models, function(enc, i) {
		enc.fetchSub('measurements', { success: function(){checkSubData();} });
		enc.fetchSub('images', { success: function(){checkSubData();} });
	});
}

*/

/*
var lastPercent = -1;
function updateProgress() {
	var percent = 100 - Math.floor(countProgress / countTotal * 100);
	if (lastPercent == percent) return;
	lastPercent = percent;
console.log('pending: %s %%', percent);
	$('#progress').html(percent + '%');
	$('#progress-bar').css('width', percent + '%');
}

/*
function checkSubData() {
	subPending--;
	var percent = 100 - Math.floor(subPending / totalLoad * 100);
console.log('subPending: %d (%s %%)', subPending, percent);
	$('#progress').html(percent + '%').css('background-size', percent + '%');

	if (subPending < 1) {
		$('#progress').hide();
		showTable();
	}
}
*/


var tableBody = false;

var lastSearch = false;
function tableShow() {
	$('#progress').hide();
	$('#test-table').tablesorter().show();
	$('#test-table').bind('sortEnd', function() {
		pstart = 0;
		if (hasSlider) $('#slider').slider('option', 'value', 100);
		pageTable(0);
	});
	pageTable(0, perPage);
	$(document).on('keydown', function(ev) {
		var m = { 38: -1, 40: 1 };
		if (!m[ev.which]) return;
		pageNudge(m[ev.which]);
		ev.preventDefault();
	});

	$('#search').on('keyup', function() {
		var val = this.value;
		if (val == lastSearch) return;
		lastSearch = val;
		if (val == '') {
			$('#table-body tr.nof').removeClass('nof');
			pageTable(0, perPage);
			return;
		}
		tableSearch(val);
	});
}


var rowCount = 0;
function tableAddRow(obj) {
	var i = rowCount++;
	var td = '';
	var search = '';
	_.each(columns, function(cstruct, c) {
		var val = (cstruct.val ? cstruct.val(obj, c) : obj.get(c));
		search += ' ' + val;
		td += '<td>' + val + '</td>';
	});
	tableBody.append('<tr data-search="' + search + '" id="n' + i + '"><td>' + i + '</td>' + td + '</tr>');
}

//http://dev.wildme.org/mm/api/jdoql?select%20m%20from%20org.ecocean.Encounter%20where%20catalogNumber==%27073b9487-67ec-4705-a0fe-3fabc6525659%27%20&&%20measurements.contains(m)


function pageTable(start, howMany) {
	pstart = start;
	$('.visible').removeClass('visible');
	var ends = start + howMany;
	for (var i = start ; i < ends ; i++) {
		if (!$('#table-body').children()[i]) continue;
		if ($($('#table-body').children()[i]).hasClass('nof')) {
			ends++;
			continue;
		}
		$('#table-body').children()[i].className = 'visible';
	}
}


function pageNudge(n) {
	pstart += n;
	if (pstart < 0) pstart = 0;
	if (pstart > (countTotal - perPage)) pstart = countTotal - perPage;
	pageTable(pstart, perPage);

	if (hasSlider) $('#slider').slider('option', 'value', 100 - (pstart / (countTotal - perPage)) * 100);
}


function tableSearch(s) {
	var regex = new RegExp(s, 'i');
	$('#table-body tr').removeClass('nof').each(function(i, el) {
		if (!regex.test(el.getAttribute('data-search'))) el.className = 'nof';
	});
	pageTable(0, perPage);
}


function cleanValue(obj, fieldName) {
	var v = obj.get(fieldName);
	var empty = /^(null|unknown|none)$/i;
	if (empty.test(v)) v = '';
//console.log('%o[%s] -> %s', obj, fieldName, v);
	return v;
}


function dataTypes(obj, fieldName) {
	var dt = [];
	_.each(['measurements', 'images'], function(w) {
		if (obj[w] && obj[w].models && (obj[w].models.length > 0)) dt.push(w.substring(0,1));
	});
	return dt.join(', ');
}

/*
</script>

</head>
<body>

<div id="progress">loading<div id="#progress-bar"></div></div>

<div style="position: relative;">
<table id="test-table" class="tablesorter">
</table>
<div id="slider"></div>
</div>


<input id="search" placeholder="filter / search" />



<!--
<input type="button" value="^^^" onClick="pageDown();" style="float:left;" />
<input type="button" value="vvv" onClick="pageUp();" style="float:right;" />
-->


</body>
</html>
*/

