

function SortTable(opts) {
	this.opts = opts;
	var me = this;

	this.sorts = [];
	this.sortsInd = [];
	this.values = [];
	this.searchValues = [];

	this.matchesFilter = [];

	this._sortCache = [];
	this._sortCacheRev = [];

	this.initSort = function() {
		for (var c = 0 ; c < this.opts.columns.length ; c++) {
			var s = [];
			for (var i = 0 ; i < this.opts.data.length ; i++) {
				s.push(this.sortValueAt(this.opts.data[i], c) + '       ' + i);
			}
			s.sort(this.opts.columns[c].sortFunction);
			var si = [];
			for (var i = 0 ; i < s.length ; i++) {
				s[i] = s[i].slice(-7) - 0;
				si[s[i]] = i;
			}
			this.sorts[c] = s;
			this.sortsInd[c] = si;
		}
	};


	//TODO lazyloaded values
	this.initValues = function() {
		for (var i = 0 ; i < this.opts.data.length ; i++) {
			this.values[i] = [];
			this.searchValues[i] = '';
			this.matchesFilter.push(i);
			for (var c = 0 ; c < this.opts.columns.length ; c++) {
				var val = this.valueAt(this.opts.data[i], c);
				this.values[i].push(val);
				this.searchValues[i] += val + ' ';
			}
		}
	};

        this.refreshValue = function(i, col) {
            this.values[i][col] = this.valueAt(this.opts.data[i], col);
        };

	this.refreshSort = function(col) {
		var s = [];
		for (var i = 0 ; i < this.opts.data.length ; i++) {
			s.push(this.sortValueAt(this.opts.data[i], col) + '       ' + i);
		}
		s.sort(this.opts.columns[col].sortFunction);
		var si = [];
		for (var i = 0 ; i < s.length ; i++) {
			s[i] = s[i].slice(-7) - 0;
			si[s[i]] = i;
		}
		this.sorts[col] = s;
		this.sortsInd[col] = si;
                this._sortCache = [];
                this._sortCacheRev = [];
	};




//TODO cache the full slice until filter changes (per column)
//TODO when no filter, just return the sorts[col]
	this.slice = function(col, start, end, reverse) {
		if ((end == undefined) || (end > this.matchesFilter.length)) end = this.matchesFilter.length;
		if ((start == undefined) || (start > this.matchesFilter.length)) start = 0;
console.log('start %o end %o', start, end);

                if (col < 0) return this.matchesFilter.slice(start, end);  //means do not sort, so return as passed basically

		var at = -1;
		var s = [];
/*
		var keys = [];
		var map = {};
		for (var i = start ; i <= end ; i++) {
//console.log('%d %d %d', i, this.matchesFilter[i], this.sortsInd[col][this.matchesFilter[i]]);
			var k = this.sortsInd[col][this.matchesFilter[i]];
			keys.push(k);
			map[k] = this.matchesFilter[i];
		}
		keys.sort(function(a,b) { return a - b; });
		for (var i = 0 ; i < keys.length ; i++) {
			s.push(map[keys[i]]);
		}
*/

		if (!this._sortCache[col]) {
			if (this.matchesFilter.length == this.opts.data.length) {  //we have not been filtered, so dont do too much work
				this._sortCache[col] = this.sorts[col].slice();
				this._sortCacheRev[col] = this.sorts[col].slice();
				this._sortCacheRev[col].reverse();
			} else {
				this._sortCache[col] = [];
				this._sortCacheRev[col] = [];
				for (var i = 0 ; i < this.opts.data.length ; i++) {
					if (this.matchesFilter.indexOf(this.sorts[col][i]) < 0) continue;
					this._sortCache[col].push(this.sorts[col][i]);
					this._sortCacheRev[col].unshift(this.sorts[col][i]);
				}
			}
console.log(this._sortCache[col]);
		}

		if (reverse) return this._sortCacheRev[col].slice(start, end);
		return this._sortCache[col].slice(start, end);
/*
		for (var i = 0 ; i < this.opts.data.length ; i++) {
			var offset = i;
			if (reverse) offset = this.opts.data.length - i - 1;
			if (this.matchesFilter.indexOf(this.sorts[col][offset]) < 0) continue;
			at++;
			if ((at < start) || (at > end)) continue;
			s.push(this.sorts[col][offset]);
		}
*/

		return s;
	};


	this.lastSliderStart = -1;
	this.sliderInit = function() {
		if (!this.opts.sliderElement) return;
		this.opts.sliderElement.addClass('pageableTable-slider');
		if (this.opts.data.length - this.opts.perPage < 1) return;
		this.opts.sliderElement.slider({
			orientation: 'vertical',
			value: 100,
//TODO generalize this function!
			slide: function(a, b) {
				//var s = Math.floor((100 - b.value) / 100 * (me.opts.data.length - me.opts.perPage) + 0.5);
				var s = Math.floor((100 - b.value) / 100 * (me.matchesFilter.length - me.opts.perPage) + 0.5);
				if (s == me.lastSliderStart) return;
				me.lastSliderStart = s;
				console.log(s);
				start = s;
				newSlice(sortCol, sortReverse);
				show();
				//me.pageTable(start);
			}
		});
	};

	this.dump = function(ind) {
		var header = ['#', 'idx'];
		for (var c = 0 ; c < this.opts.columns.length ; c++) {
			header.push(this.opts.columns[c].label || this.opts.columns[c].key);
		}
		console.info(header.join(' | '));
		for (var i = 0 ; i < ind.length ; i++) {
			var row = [i, ind[i]];
			for (var c = 0 ; c < this.opts.columns.length ; c++) {
				row.push(this.values[ind[i]][c]);
			}
			console.log(row.join(' | '));
		}
	};

	this.sliderSet = function(percent) {
		if (!this.opts.sliderElement) return;
		if (this.matchesFilter.length - this.opts.perPage < 1) return;
		this.opts.sliderElement.slider('option', 'value', percent);
	};

	this.filter = function(s) {
		this._sortCache = [];
		this._sortCacheRev = [];
		if (s == undefined) {
			//TODO this is a kinda hacky trick to get an array of ints 0..LENGTH-1 ... but is it cross-browser enough?
			this.matchesFilter = Object.keys(this.values);
			return;
		}
		this.matchesFilter = [];
		var regex = new RegExp(s, 'i');
		for (var i = 0 ; i < this.opts.data.length ; i++) {
			if (regex.test(this.searchValues[i])) this.matchesFilter.push(i);
		}
	};


	this.valueAt = function(obj, colnum) {
		if (this.opts.columns[colnum].value) return this.opts.columns[colnum].value(obj, colnum);
		return keyValue(obj, this.opts.columns[colnum].key);
		//return obj[this.opts.columns[colnum].key];
	};


	this.sortValueAt = function(obj, colnum) {
		if (this.opts.columns[colnum].sortValue) return this.opts.columns[colnum].sortValue(obj, colnum);
		if (this.opts.columns[colnum].value) return this.opts.columns[colnum].value(obj, colnum);
		return keyValue(obj, this.opts.columns[colnum].key);
	};

}



//this allows us to use plain old hash/object or hacktacular backbone style
function keyValue(obj, key) {
	if (obj.attributes) return obj.get(key);
	return obj[key];
}

