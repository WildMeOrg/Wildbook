

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
		var hd = '<thead><tr>';
		_.each(this.opts.columns, function(cstruct, c) { hd += '<th class="ptcol-' + c + '">' + cstruct.label + '</th>'; });
		opts.tableElement.append(hd + '</tr></thead>').append(this.tableBody);
		opts.tableElement.on('mousewheel', function(ev) {  //firefox? DOMMouseScroll
			if (!me.opts.sliderElement) return;
			ev.preventDefault();
			var delta = Math.max(-1, Math.min(1, (event.wheelDelta || -event.detail)));
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

	this.tableCreateRow = function(obj) {
		var i = this.rowCount++;
		var td = '';
		var search = '';
		_.each(this.opts.columns, function(cstruct, c) {
			obj._rowNum = i;
			var val = (cstruct.val ? cstruct.val(obj, c) : obj.get(c));
			var sval = val;
			if (sval && sval.indexOf && sval.indexOf('<') > -1) sval = $(sval).text();
			search += ' ' + sval;
			td += '<td class="ptcol-' + c + '">' + val + '</td>';
		});
		var tr = $('<tr title="' + obj.classNameShort() + ' ID: ' + obj.id + '" data-id="' + obj.id + '" data-search="' + search + '" id="n' + i + '">' + td + '</tr>');
		return tr;
	};

	this.tableAddRow = function(obj) {
		var tr = this.tableCreateRow(obj);
		this.tableBody.append(tr);
		return tr;
	};


	this.tableShow = function() {
		this.opts.tableElement.tablesorter(this.opts.tablesorterOpts).show();
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
			$(this.tableBody.children()[i]).addClass('pageableTable-visible');
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
			if (!regex.test(el.getAttribute('data-search'))) $(el).addClass('pageableTable-nof');
		});
		this.pageTable(0);
	};


}

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




function cleanValue(obj, fieldName) {
	var v = obj.get(fieldName);
	var empty = /^(null|unknown|none|undefined)$/i;
	if (empty.test(v)) v = '';
	return v;
}


function dataTypes(obj, fieldName) {
	var dt = [];
	_.each(['measurements', 'images'], function(w) {
		if (obj[w] && obj[w].models && (obj[w].models.length > 0)) dt.push(w.substring(0,1));
	});
	return dt.join(', ');
}

