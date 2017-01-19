
function ImageTools(opts) {
	this.opts = opts;  //will get passed to .init() below

	//some defaults which may be overridden via opts through init()
	this.maction = false;
	this.drewHandle = false;
	this.tolerance = 10; //how close to something to be close enough
	this.spotTolerance = 5; //how close for spots
	this.rotation = 0;
	this.rect = [];
	this.iconsOn = true;
	this.spots = [];
	this.shiftDown = false;
	this._lastTouch = [0,0];

	this.activeSpotType = 'spot';

	this.toolsEnabled = {
		spotPicker: false,
		spotDisplay: false,
	};

	this.styles = {
		stroke: 'rgba(255,255,0,0.6)',
		strokeTopLine: 'rgba(100,255,200,0.6)',
		fill: 'rgba(255,255,0,0.6)',
		lineWidthBox: 3,
		lineWidthIn: 1,
		spotFill: 'rgba(200,255,0,0.6)',
		spotControlStroke: 'rgba(255,255,255,0.7)',
	};

	this.iconPNGs = {
		rotate: 'iVBORw0KGgoAAAANSUhEUgAAAAwAAAAMCAYAAABWdVznAAAAAXNSR0IArs4c6QAAAAZiS0dEAP8A/wD/oL2nkwAAAAlwSFlzAAALEwAACxMBAJqcGAAAAAd0SU1FB94GHhISMe3uBQQAAAAZdEVYdENvbW1lbnQAQ3JlYXRlZCB3aXRoIEdJTVBXgQ4XAAAAR0lEQVQoz52QSQ4AIAgDp4b/fxlPHowgaI9kQhd4lIKbJ4wDUgEfBhbA1yfjBQawj44t+XKzYplDoxFj66funJGDkijfxQGYJKAMFyDZ++UAAAAASUVORK5CYII=',
		scaleNE: 'iVBORw0KGgoAAAANSUhEUgAAAAwAAAAMCAYAAABWdVznAAAAAXNSR0IArs4c6QAAAAZiS0dEAP8A/wD/oL2nkwAAAAlwSFlzAAALEwAACxMBAJqcGAAAAAd0SU1FB94GHhIUCOSxKooAAAAZdEVYdENvbW1lbnQAQ3JlYXRlZCB3aXRoIEdJTVBXgQ4XAAAAQklEQVQoz5VRyw4AIAgC//+f6doqQjm5yWMoMAQ/O734bJjuQtaADACornMSKPXUMcuUvhKiM005e70a/sYmRGEbC1q/DAs34sNRAAAAAElFTkSuQmCC',
		scaleNW: 'iVBORw0KGgoAAAANSUhEUgAAAAwAAAAMCAYAAABWdVznAAAAAXNSR0IArs4c6QAAAAZiS0dEAP8A/wD/oL2nkwAAAAlwSFlzAAALEwAACxMBAJqcGAAAAAd0SU1FB94GHhIUGPkGOu4AAAAZdEVYdENvbW1lbnQAQ3JlYXRlZCB3aXRoIEdJTVBXgQ4XAAAARUlEQVQoz2NgIBEwQun/eORQAAuxCmGACYvYf2I1MBKjiQmPc/6TEhj/kTCKAUwEQg/DJkYibEJRz0RkPOGNB7I8jRMAAE2VCxFyVbghAAAAAElFTkSuQmCC'
	};

	this.init = function(els) {
console.log('init!!!!');
		var me = this;
		for (var k in els) {
			this[k] = els[k];
		}
		if (this.oCanvas) this.oCtx = this.oCanvas.getContext('2d');
		if (this.wCanvas) this.wCtx = this.wCanvas.getContext('2d');
		if (this.lCanvas) this.lCtx = this.lCanvas.getContext('2d');

		//this.spotMode = false;

		document.addEventListener('keydown', function(ev) {
			if (ev.keyCode == 16) me.shiftDown = true;
			if (ev.keyCode == 17) me.spotMode = true;
		});
		document.addEventListener('keyup', function(ev) {
			if (ev.keyCode == 16) me.shiftDown = false;
			if (ev.keyCode == 17) me.spotMode = false;
		});

		if (!this.imgEl) return;
                this.imgEl.setAttribute('crossOrigin', 'anonymous');

		//sourceEl is what/where actual copy comes from -- and thus can be and img, canvas, or (apparently!) video
		if (!this.sourceEl) this.sourceEl = this.imgEl;
		// ... and the offset is where to grab from, which can vary if you have a "padding" border around it
		if (!this.sourceElOffsetX) this.sourceElOffsetX = 0;
		if (!this.sourceElOffsetY) this.sourceElOffsetY = 0;

		this.iCanvas = document.createElement('canvas');
		this.iCanvas.width = 12;
		this.iCanvas.height = 12;
		this.iCanvas.style.display = 'none';
		document.getElementsByTagName('body')[0].appendChild(this.iCanvas);
		this.iCtx = this.iCanvas.getContext('2d');
console.log('iCanvas init');

		this.imgEl.addEventListener('load', function() { me.imageReady(); });
/*
		if (this.imgEl.complete) {
			this.imageReady();
		} else {
			this.imgEl.onload = this.imageReady;
		}
*/

		//set up little handle icons
		this.iconImgs = {};
		this._iconsLoading = 3;  //Object.keys(this.iconPNGs).length;  //$#*@ IE
		for (var i in this.iconPNGs) {
			this.iconImgs[i] = document.createElement('img');
			this.iconImgs[i].style.display = 'none';
			this.iconImgs[i].src = 'data:image/png;base64,' + this.iconPNGs[i];
			//this.iconImgs[i].onLoad = function() { me.waitForIcons(); };
			this.iconImgs[i].onLoad = this.waitForIcons();
			document.getElementsByTagName('body')[0].appendChild(this.iconImgs[i]);
		}

		this.imageReady();
	};

	this.imageReady = function() {
		if (this._imageReadyCalled) return;
		this._imageReadyCalled = true;

		var me = this;
console.log('imageReady called');
console.log('iCtx is %o', this.iCtx);
console.log('oCtx is %o', this.oCtx);
console.log('imgEl.width = ' + this.imgEl.width);
		this.info('img size: <b>' + this.imgEl.naturalWidth + 'x' + this.imgEl.naturalHeight);

		//this.wCanvas.addEventListener('click', function(ev) { me.spotClick(ev); }, false);

		this.scale = this.imgEl.naturalWidth / this.imgEl.width;

		//this allows crop/rotate within the work canvas
		if (this.allInOne) {
			this.setRectFrom2(0, 0, 0, this.imgEl.width, this.imgEl.height);
console.log(this.rect);
		}

		if (!this.oCanvas) return;

		this.setRectFrom2(0,
			0.25 * this.imgEl.width, 0.25 * this.imgEl.height,
			0.75 * this.imgEl.width, 0.75 * this.imgEl.height
		);

console.log('starting imageReady');

		this.oCanvas.ontouchmove = function(ev) {
console.log(ev);
console.log('tmove %o', ev.changedTouches);
			me.eventPosFix(ev);
			var pos = me.absPos(ev.target);
console.log('pos = %o', pos);
			me._lastTouch = [ev.offsetX, ev.offsetY];
console.log('%d, %d', ev.offsetX, ev.offsetY);
			me.mmove(ev);
		};
		this.oCanvas.ontouchstart = function(ev) {
console.log('tstart %o', ev.changedTouches);
			me.eventPosFix(ev);
			me.mdown(ev);
		};
		this.oCanvas.ontouchend = function(ev) {
			ev.offsetX = me._lastTouch[0];
			ev.offsetY = me._lastTouch[1];
			me.mup(ev);
		};

		this.oCanvas.addEventListener('mousedown', function(ev) { me.mdown(ev); }, false);
		this.oCanvas.addEventListener('mouseup', function(ev) { me.mup(ev); }, false);
		this.oCanvas.addEventListener('mousemove', function(ev) { me.mmove(ev); }, false);

		this.oCanvas.width = this.imgEl.width;
		this.oCanvas.height = this.imgEl.height;
		this.oCanvas.style.display = 'block';
		this.oCtx.strokeStyle = this.styles.stroke;
		this.oCtx.fillStyle = this.styles.fill;
		this.drawRect();  //may be beat by waitForRect() but ... maybe not?
		this.toWork();
	};

	this.waitForIcons = function() {
		this._iconsLoading--;
console.log('count? ' + this._iconsLoading);
		if (this._iconsLoading > 0) return;
console.log('drawingRect');
		this.drawRect();
	};

	this.info = function(s) {
return;
		if (!this.infoEl) return;
		this.infoEl.innerHTML = s;
	};


	this.oClear = function() {
		this.oCtx.clearRect(0, 0, this.imgEl.width, this.imgEl.height);
	};


	this.xyOrigToWork = function(xy) {
console.log("xyOrigToWork ................. xy (%d,%d) .........", xy[0], xy[1]);
		var cp = this.midpoint(this.rect[0], this.rect[1], this.rect[4], this.rect[5]);
		var rw = this.rectW() * this.scale;
		var rh = this.rectH() * this.scale;
		var wscale = rw / this.wCanvas.offsetWidth;
		var A = Math.atan2(xy[1] - cp[1] * this.scale, xy[0] - cp[0] * this.scale) - this.rotation;
		var d = this.dist(xy[0], xy[1], cp[0] * this.scale, cp[1] * this.scale);
console.log('d[%f] cos(A) %f   sin(A) %f', d, Math.cos(A), Math.sin(A));
console.log('rw/2 %d rh/2 %d (wscale %f)', rw/2, rh/2, wscale);
		var r = [(rw / 2 + d * Math.cos(A)) / wscale, (rh / 2 + d * Math.sin(A)) / wscale];
console.log("rtn .......... (%d,%d) .............", r[0], r[1]);
/*
		this.wCtx.setTransform(1, 0, 0, 1, 0, 0);
		this.wCtx.fillStyle = this.styles.fill;
		this.wCtx.beginPath();
		this.wCtx.arc(r[0] * wscale, r[1] * wscale, 3 * wscale, 2*Math.PI, false);
		this.wCtx.fill();
*/
		return r;
	};

	this.xyWorkToOrig = function(xy) {
console.log("xyWorkToOrig ----------------- xy (%d,%d) ---------", xy[0], xy[1]);
		var cp = this.midpoint(this.rect[0], this.rect[1], this.rect[4], this.rect[5]);
console.log(' [cp %f, %f]', cp[0], cp[1]);
		var rw = this.rectW() * this.scale;
		var rh = this.rectH() * this.scale;
		var wscale = rw / this.wCanvas.offsetWidth;
//console.log('w=%d, h=%d (rw=%d, rh=%d) this.scale=%f [wscale=%f]', w, h, rw, rh, this.scale, wscale);
		var A = Math.atan2(xy[1] * wscale - rh/2, xy[0] * wscale - rw/2) + this.rotation;
		var d = this.dist(xy[0] * wscale, xy[1] * wscale, rw/2, rh/2);
console.log('d[%f] cos(A) %f   sin(A) %f', d, Math.cos(A), Math.sin(A));
		var r = [cp[0] * this.scale + d * Math.cos(A), cp[1] * this.scale + d * Math.sin(A)];
console.log("rtn ========== (%d,%d) =============", r[0], r[1]);
/*
		this.oCtx.fillStyle = this.styles.fill;
		this.oCtx.beginPath();
		this.oCtx.arc(r[0]/this.scale, r[1]/this.scale, 3, 2*Math.PI, false);
		this.oCtx.fill();
*/
//var rev = this.xyOrigToWork(r);
		return r;
	};

	this.toWork = function() {
		var minX = this.min([this.rect[0], this.rect[2], this.rect[4], this.rect[6]]);
		var maxX = this.max([this.rect[0], this.rect[2], this.rect[4], this.rect[6]]);
		var minY = this.min([this.rect[1], this.rect[3], this.rect[5], this.rect[7]]);
		var maxY = this.max([this.rect[1], this.rect[3], this.rect[5], this.rect[7]]);
		//var idata = this.wCtx.getImageData(minX * this.scale, minY * this.scale, (maxX - minX) * this.scale, (maxY - minY) * this.scale);

		var w = (maxX - minX) * this.scale;
		if (w > this.imgEl.naturalWidth) w = this.imgEl.naturalWidth;
		var h = (maxY - minY) * this.scale;
		if (h > this.imgEl.naturalHeight) h = this.imgEl.naturalHeight;
		var rw = this.rectW() * this.scale;
		var rh = this.rectH() * this.scale;
		this.wCanvas.width = rw;
		this.wCanvas.height = rh;

		if (this.lCanvas) {
			this.lCanvas.width = rw;
			this.lCanvas.height = rh;
		}

//console.log('%d, %d', (rw-w)/2, (rh-h)/2);
		this.wCtx.rotate(-this.rotation);
console.log('rh = %o', rh);

		var A = Math.atan2(rh/2, rw/2) + this.rotation;
		var d = this.dist(0, 0, rw, rh)/2;
		var nx = d * Math.cos(A);
		var ny = d * Math.sin(A);
//console.log('(nx,ny) %f,%f : (m) %f,%f', nx, ny, w/2, h/2);
//console.log('%f,%f', w/2 - nx, h/2 - ny);

//console.log('minX %f minY %f', w, h);
//console.log('foo %f', -(w/2-nx));
		/////this.wCtx.drawImage(this.imgEl, minX * this.scale, minY * this.scale, w, h, -(w/2-nx), -(h/2-ny), w, h);
		this.wCtx.drawImage(this.sourceEl, this.sourceElOffsetX + minX * this.scale, this.sourceElOffsetY + minY * this.scale, w, h, -(w/2-nx), -(h/2-ny), w, h);

		this.drawSpots();

		this.triggerEvent('imageTools:workCanvas:update');
	};


	this.drawSpots = function() {
		if (!this.lCtx || !this.toolsEnabled || !(this.toolsEnabled.spotPicker || this.toolsEnabled.spotDisplay) || !this.spots || (this.spots.length < 1)) return;  //nothing to do
		var rw = this.rectW() * this.scale;
		var wscale = rw / this.lCanvas.offsetWidth;
console.log('wscale = %f', wscale);
/*
				this.lCtx.beginPath();
				this.lCtx.arc(50, 50, 20, 0, 2*Math.PI, false);
				this.lCtx.fillStyle = this.styles.spotFill;
				this.lCtx.fill();
		return;
*/
		//this.wCtx.setTransform(1, 0, 0, 1, 0, 0);
			for (var i = 0 ; i < this.spots.length ; i++) {
				var xy = this.spots[i].xy;
				if (this.wCanvas) xy = this.xyOrigToWork(this.spots[i].xy);

console.log('%d -> (%d,%d)', i, xy[0], xy[1]);
				this.lCtx.beginPath();
				var r = this.spotTolerance * wscale;
                                if (r < 3) r = 3;
				this.lCtx.arc(xy[0] * wscale, xy[1] * wscale, r, 0, 2*Math.PI, false);
				//this.lCtx.arc(xy[0], xy[1], this.spotTolerance/3, 0, 2*Math.PI, false);
				if (this.spots[i].type == 'spot') {
					this.lCtx.fillStyle = this.styles.spotFill;
					this.lCtx.fill();
				} else {
					this.lCtx.lineWidth = 3;
					this.lCtx.strokeStyle = this.styles.spotControlStroke;
					this.lCtx.stroke();
					this.lCtx.fillStyle = this.styles.spotFill;
					this.lCtx.font = 'bold ' + (wscale * 15) + 'px Arial';
					this.lCtx.fillText((this.spots[i].label || this.spots[i].type), (xy[0] + 8) * wscale, (xy[1] + 15) * wscale);
				}
			}
		};


	this.mmove = function(ev) {
		this.eventPosFix(ev);
		ev.preventDefault();

		this.info(this.shiftDown +")"+ ev.offsetX + ', ' + ev.offsetY + ': ' + Math.floor(this.angleFromCenter(ev.offsetX, ev.offsetY) * 180/Math.PI));
		if (!this.maction) {
			var c = this.nearCorner(ev.offsetX, ev.offsetY);
			var m = this.nearMidpoint(ev.offsetX, ev.offsetY);

			if ((c < 0) && (m < 0) && this.drewHandle) {
				this.drewHandle = false;
				this.oClear();
				this.drawRect();
				return;
			}

			if ((c > -1) && !this.drewHandle && !this.iconsOn) {
				this.drewHandle = true;
				this.oCtx.beginPath();
				this.oCtx.arc(this.rect[c*2], this.rect[c*2+1], this.tolerance, 0, 2*Math.PI, false);
				this.oCtx.fill();
				if (c % 2 == 0) {
					this.drawIcon('scaleNW', this.rect[c*2] - 6, this.rect[c*2+1] - 6);
				} else {
					this.drawIcon('scaleNE', this.rect[c*2] - 6, this.rect[c*2+1] - 6);
				}
			}

			if ((m > -1) && !this.drewHandle && !this.iconsOn) {
				var nc = (m + 1) % 4;
				var mp = this.midpoint(this.rect[m*2], this.rect[m*2+1], this.rect[nc*2], this.rect[nc*2+1]);
				this.drewHandle = true;
				this.oCtx.lineWidth = 2;
				this.oCtx.beginPath();
				this.oCtx.arc(mp[0], mp[1], this.tolerance, 0, 2*Math.PI, false);
				this.oCtx.fill();
				this.drawIcon('rotate', mp[0] - 6, mp[1] - 6);
			}
			return;
		}

		if (this.maction == 'resize') {
			var oppc = (this._corner + 2) % 4;
			var r = this.rectFrom2(this._corner, ev.offsetX, ev.offsetY, this.rect[oppc*2], this.rect[oppc*2+1]);
			if (!r) return;  //"should never happen"
			if (this.rectOutOfBounds(r)) return;
			//this.setRectFrom2(this._corner, ev.offsetX, ev.offsetY, this.rect[oppc*2], this.rect[oppc*2+1]);
			this.rect = r;
			this.oClear();
			this.drawRect();
		}

		if (this.maction == 'rotate') {
			var a = this.angleFromCenter(ev.offsetX, ev.offsetY);
if (this.shiftDown) a = Math.floor(a / (Math.PI/4) + 0.5) * (Math.PI/4);
			var rot = a - this._prevAngle;
			if (Math.abs(rot) < 0.05) return;

			var r = this.rotateArbitraryRect(rot, this.rect);
			if (this.rectOutOfBounds(r)) return;

			this._prevAngle = a;
			this.rect = r;
			this.rotation += rot;
			this.oClear();
			this.drawRect();
		}

		if (this.maction == 'drag') {
			var dx = ev.offsetX - this.rect[0] - this._dx;
			var dy = ev.offsetY - this.rect[1] - this._dy;
			var r = [];
			for (var i = 0 ; i < 4 ; i++) {
				r[i*2] = this.rect[i*2] + dx;
				r[i*2+1] = this.rect[i*2+1] + dy;
			}
			if (this.rectOutOfBounds(r)) {
				for (var i = 0 ; i < 4 ; i++) {
					r[i*2] = this.rect[i*2] + dx;
					r[i*2+1] = this.rect[i*2+1];
				}
			}
			if (this.rectOutOfBounds(r)) {
				for (var i = 0 ; i < 4 ; i++) {
					r[i*2] = this.rect[i*2];
					r[i*2+1] = this.rect[i*2+1] + dy;
				}
			}
			if (this.rectOutOfBounds(r)) return;
			this.rect = r;
			this.oClear();
			this.drawRect();
		}
	};

	this.mdown = function(ev) {
		this.eventPosFix(ev);
console.log('down %d,%d', ev.offsetX, ev.offsetY);
		ev.preventDefault();

		var c = this.nearCorner(ev.offsetX, ev.offsetY);
		if (c > -1) {
			this.maction = 'resize';
			this._corner = c;
			return;
		}

		var m = this.nearMidpoint(ev.offsetX, ev.offsetY);
		if (m > -1) {
			this.maction = 'rotate';
			this._prevAngle = this.angleFromCenter(ev.offsetX, ev.offsetY);
			return;
		}

		if (this.inRect(ev.offsetX, ev.offsetY)) {
			this.maction = 'drag';
			this._dx = ev.offsetX - this.rect[0];
			this._dy = ev.offsetY - this.rect[1];
			return;
		}
	};

	this.mup = function(ev) {
		ev.preventDefault();
		this.toWork();
		this.maction = false;
	};

	this.spotClick = function(ev) {
		var rtn = false;
console.log(ev);
		if (!this.toolsEnabled || !this.toolsEnabled.spotPicker) return;
		ev.preventDefault();
console.log('click ev %o', ev);
		this.eventPosFix(ev);
console.log('offsetX %d, offsetY %d', ev.offsetX, ev.offsetY);

		var xy = this.xyWorkToOrig([ev.offsetX, ev.offsetY]);
console.log('spot clicked is (%d,%d) type=%s', xy[0], xy[1], this.activeSpotType);

		var spot = this.isNearSpot(xy[0], xy[1]);
		if (spot < 0) {
			rtn = {xy: xy, type: this.activeSpotType, label: this.activeSpotLabel};
			this.spots.push(rtn);
			this.triggerEvent('imageTools:spot:added', rtn);
		} else { //remove
			rtn = this.spots[spot];
			rtn._removed = true;
			this.triggerEvent('imageTools:spot:removed', rtn);
			this.spots.splice(spot, 1);
		}
console.log(this.spots);
		this.toWork();
		return rtn;
	};

	this.isNearSpot = function(x, y) {
		var wscale = this.rectW() * this.scale / this.wCanvas.offsetWidth;
		var d = this.spotTolerance * wscale;
//console.log('>>>>> isNearSpot: wscale=%f, tol=%d', wscale, d);
		for (var i = 0 ; i < this.spots.length ; i++) {
//var q = this.dist(x, y, this.spots[i].xy[0], this.spots[i].xy[1]);
//console.log('. . . . . . spot %d: %f  (mod = %f)', i, q, q/wscale);
			if (this.dist(x, y, this.spots[i].xy[0], this.spots[i].xy[1]) <= d) return i;
		}
		return -1;
	};

	//only spots which are in selected/visible region
	this.spotsVisible = function() {
		var vis = [];
		for (var i = 0 ; i < this.spots.length ; i++) {
			var xy = this.xyOrigToWork(this.spots[i].xy);
			if ((xy[0] >= 0) && (xy[0] <= this.wCanvas.offsetWidth) && (xy[1] >= 0) && (xy[1] <= this.wCanvas.offsetHeight)) vis.push(this.spots[i]);
		}
		return vis;
	};

	this.drawRect = function() {
		if (!this.oCtx) return;
//console.log('(%d,%d) (%d,%d) (%d,%d) (%d,%d)', this.rect[0], this.rect[1], this.rect[2], this.rect[3], this.rect[4], this.rect[5], this.rect[6], this.rect[7]);
		this.oCtx.strokeStyle = this.styles.stroke;
		this.oCtx.lineWidth = this.styles.lineWidthBox;
		this.oCtx.beginPath();
		this.oCtx.moveTo(this.rect[2], this.rect[3]);
		this.oCtx.lineTo(this.rect[4], this.rect[5]);
		this.oCtx.lineTo(this.rect[6], this.rect[7]);
		this.oCtx.lineTo(this.rect[0], this.rect[1]);
		this.oCtx.stroke();

		this.oCtx.lineWidth = this.styles.lineWidthIn;
		var m1 = this.midpoint(this.rect[0], this.rect[1], this.rect[2], this.rect[3]);
		var m2 = this.midpoint(this.rect[4], this.rect[5], this.rect[6], this.rect[7]);
		this.oCtx.moveTo(m1[0], m1[1]);
		this.oCtx.lineTo(m2[0], m2[1]);
		this.oCtx.stroke();
		m1 = this.midpoint(this.rect[0], this.rect[1], this.rect[6], this.rect[7]);
		m2 = this.midpoint(this.rect[4], this.rect[5], this.rect[2], this.rect[3]);
		this.oCtx.moveTo(m1[0], m1[1]);
		this.oCtx.lineTo(m2[0], m2[1]);
		this.oCtx.stroke();
		this.oCtx.closePath();

		this.oCtx.lineWidth = this.styles.lineWidthBox;
		this.oCtx.strokeStyle = this.styles.strokeTopLine;
		this.oCtx.beginPath();
		this.oCtx.moveTo(this.rect[0], this.rect[1]);
		this.oCtx.lineTo(this.rect[2], this.rect[3]);
		this.oCtx.stroke();
		this.oCtx.closePath();

		if (this.iconsOn) {
			for (var c = 0 ; c < 4 ; c++) {
				this.oCtx.beginPath();
				this.oCtx.arc(this.rect[c*2], this.rect[c*2+1], this.tolerance, 0, 2*Math.PI, false);
				this.oCtx.fill();
				this.drawIcon(((c % 2 == 0) ? 'scaleNW' : 'scaleNE'), this.rect[c*2] - 6, this.rect[c*2+1] - 6);
				var m = this.midpoint(this.rect[c*2], this.rect[c*2+1], this.rect[((c+1)%4)*2], this.rect[((c+1)%4)*2+1]);
				this.oCtx.beginPath();
				this.oCtx.arc(m[0], m[1], this.tolerance, 0, 2*Math.PI, false);
				this.oCtx.fill();
				this.drawIcon('rotate', m[0] - 6, m[1] - 6);
			}
		}
	};

	this.dist = function(x1, y1, x2, y2) {
		return Math.sqrt((x1-x2)*(x1-x2) + (y1-y2)*(y1-y2));
	};

	this.rectW = function() {
console.log('rectW %o', this.rect);
		return this.dist(this.rect[0], this.rect[1], this.rect[2], this.rect[3]);
	};
	this.rectH = function() {
		return this.dist(this.rect[0], this.rect[1], this.rect[6], this.rect[7]);
	};

	this.nearCorner = function(x, y) {
		for (var i = 0 ; i < 4 ; i++) {
			var d = this.dist(this.rect[i*2], this.rect[i*2+1], x, y);
			if (d < this.tolerance) return i;
		}
		return -1;
	};


	// pts will be considered opposite corners
	this.setRectFrom2 = function(corner, x1, y1, x2, y2) {
		var r = this.rectFrom2(corner, x1, y1, x2, y2);
		if (r) this.rect = r;
	};

	//computes, but does not set
	this.rectFrom2 = function(corner, x1, y1, x2, y2) {
/*   this is done now via rectOutOfBounds, not here
		if ((x1 < 0) || (x1 > this.imgEl.width) || (y1 < 0) || (y1 > this.imgEl.height) ||
			(x2 < 0) || (x2 > this.imgEl.width) || (y2 < 0) || (y2 > this.imgEl.height)) return;
*/
		var rect = [];
		var oppc = (corner + 2) % 4;
		rect[corner*2] = x1;
		rect[corner*2+1] = y1;
		rect[oppc*2] = x2;
		rect[oppc*2+1] = y2;

		var d = this.dist(x1, y1, x2, y2);
		var x = x2 - x1;
		var y = y2 - y1;
		var A = Math.atan2(y, x);
		var B = A - this.rotation;
		var n = d * Math.cos(B);
		var m = d * Math.sin(B);
	
		var c1 = (corner + 1) % 4;
		var c2 = (corner + 3) % 4;
		if (corner % 2 == 1) {
			c1 = (corner + 3) % 4;
			c2 = (corner + 1) % 4;
		}
//console.log('corner=%d, oppc=%d, c1=%d, c2=%d', corner, oppc, c1, c2);
		rect[c1*2] = x1 + n * Math.cos(this.rotation);
		rect[c1*2+1] = y1 + n * Math.sin(this.rotation);
		rect[c2*2] = x1 - m * Math.sin(this.rotation);
		rect[c2*2+1] = y1 + m * Math.cos(this.rotation);
//console.log('(%d,%d) (%d,%d) (%d,%d) (%d,%d)', this.rect[0], this.rect[1], this.rect[2], this.rect[3], this.rect[4], this.rect[5], this.rect[6], this.rect[7]);
		return rect;
	};

	//can test arbitrary rect or will test this.rect if none passed
	this.rectOutOfBounds = function(rect) {
		if (this.noBounds) return false;
		if (!this.imgEl || (this.imgEl.width < 1)) return true;
		if (!rect) rect = this.rect;
		for (var i = 0 ; i < 8 ; i++) {
			if (rect[i] < 0) return true;
			if ((i % 2 == 0) && (rect[i] > this.imgEl.width)) return true;
			if ((i % 2 == 1) && (rect[i] > this.imgEl.height)) return true;
		}
		return false;
	};


	// pts will be considered opposite corners
	this.setRectFrom2____ORIG = function(corner, x1, y1, x2, y2) {
		if ((x1 < 0) || (x1 > this.imgEl.width) || (y1 < 0) || (y1 > this.imgEl.height) ||
			(x2 < 0) || (x2 > this.imgEl.width) || (y2 < 0) || (y2 > this.imgEl.height)) return;
		var oppc = (corner + 2) % 4;
		this.rect[corner*2] = x1;
		this.rect[corner*2+1] = y1;
		this.rect[oppc*2] = x2;
		this.rect[oppc*2+1] = y2;

		var d = this.dist(x1, y1, x2, y2);
		var x = x2 - x1;
		var y = y2 - y1;
		var A = Math.atan2(y, x);
		var B = A - this.rotation;
		var n = d * Math.cos(B);
		var m = d * Math.sin(B);
	
		var c1 = (corner + 1) % 4;
		var c2 = (corner + 3) % 4;
		if (corner % 2 == 1) {
			c1 = (corner + 3) % 4;
			c2 = (corner + 1) % 4;
		}
//console.log('corner=%d, oppc=%d, c1=%d, c2=%d', corner, oppc, c1, c2);
		this.rect[c1*2] = x1 + n * Math.cos(this.rotation);
		this.rect[c1*2+1] = y1 + n * Math.sin(this.rotation);
		this.rect[c2*2] = x1 - m * Math.sin(this.rotation);
		this.rect[c2*2+1] = y1 + m * Math.cos(this.rotation);
//console.log('(%d,%d) (%d,%d) (%d,%d) (%d,%d)', this.rect[0], this.rect[1], this.rect[2], this.rect[3], this.rect[4], this.rect[5], this.rect[6], this.rect[7]);
	};

	this.nearMidpoint = function(x, y) {
		for (var i = 0 ; i < 4 ; i++) {
			var ni = (i + 1) % 4;
			var m = this.midpoint(this.rect[i*2], this.rect[i*2+1], this.rect[ni*2], this.rect[ni*2+1]);
			var d = this.dist(m[0], m[1], x, y);
			if (d < this.tolerance) return i;
		}
		return -1;
	};

	this.midpoint = function(x1, y1, x2, y2) {
		return [(x1+x2)/2, (y1+y2)/2];
	};

	this.slidePoint = function(m, x1, y1, x2, y2) {
		return [(x2-x1) * m + x1, (y2-y1) * m + y1];
	};

	this.onSide = function(x1, y1, x2, y2, x3, y3) {
		return (x1 - x3) * (y2 - y3) - (x2 - x3) * (y1 - y3);
	};

	this.inTriangle = function(x, y, x1, y1, x2, y2, x3, y3) {
		var b1 = this.onSide(x, y, x1, y1, x2, y2) < 0;
		var b2 = this.onSide(x, y, x2, y2, x3, y3) < 0;
		var b3 = this.onSide(x, y, x3, y3, x1, y1) < 0;
		return ((b1 == b2) && (b2 == b3));
	};

	this.inRect = function(x, y) {
		return this.inTriangle(x, y, this.rect[0], this.rect[1], this.rect[2], this.rect[3], this.rect[6], this.rect[7]) || this.inTriangle(x, y, this.rect[2], this.rect[3], this.rect[4], this.rect[5], this.rect[6], this.rect[7]);
	};

	this.angleFromCenter = function(x, y) {
		var cp = this.midpoint(this.rect[0], this.rect[1], this.rect[4], this.rect[5]);
		var a = Math.atan2(cp[1] - y, cp[0] - x);
		if (a < 0) a += Math.PI*2;
		return a;
	};

	this.rotatePoint = function(x, y, cx, cy, A) {
		var B2 = Math.atan2(y - cy, x - cx) + A;
		var r = Math.sqrt((cx - x)*(cx - x) + (cy - y)*(cy - y));
		var x2 = cx + r * Math.cos(B2);
		var y2 = cy + r * Math.sin(B2);
		return [x2, y2];
	};

	this.rotateRect = function(A) {
		var r = this.rotateArbitraryRect(A, this.rect);
		if (!r) return;
		this.rect = r;
/*
		var cp = this.midpoint(this.rect[0], this.rect[1], this.rect[4], this.rect[5]);
		for (var i = 0 ; i < 4 ; i++) {
			var p = this.rotatePoint(this.rect[i*2], this.rect[i*2+1], cp[0], cp[1], A);
			this.rect[i*2] = p[0];
			this.rect[i*2+1] = p[1];
		}
*/
		this.rotation += A;
	};

	this.rotateArbitraryRect = function(A, rect) {
		if (!rect || (rect.length < 8)) return;
		var cp = this.midpoint(rect[0], rect[1], rect[4], rect[5]);
		var r = [];
		for (var i = 0 ; i < 4 ; i++) {
			var p = this.rotatePoint(rect[i*2], rect[i*2+1], cp[0], cp[1], A);
			r[i*2] = p[0];
			r[i*2+1] = p[1];
		}
		return r;
	};


	this.scaleRect = function(s, cx, cy) {
console.log('scaleRect(%f)', s);
		if (cx == undefined) {
			var cp = this.midpoint(this.rect[0], this.rect[1], this.rect[4], this.rect[5]);
			cx = cp[0];
			cy = cp[1];
		}
		var p0 = this.slidePoint(s, cx, cy, this.rect[0], this.rect[1]);
		var p1 = this.slidePoint(s, cx, cy, this.rect[4], this.rect[5]);
console.log('p0 %d,%d', p0[0], p0[1]);
console.log('p1 %d,%d', p1[0], p1[1]);
		var fulld = this.dist(0, 0, this.imgEl.width, this.imgEl.height);
		var d = this.dist(p0[0], p0[1], p1[0], p1[1]);
console.log('d=%d fulld=%d ', d, fulld);
		if ((d > fulld) || (p0[0] < 0) || (p0[1] < 0) || (p1[0] > this.imgEl.width) || (p1[1] > this.imgEl.height)) {
			p0 = [0, 0];
			p1 = [this.imgEl.width, this.imgEl.height];
		}
console.info('=================================================== %o %o', p0, p1);
console.info(this.rect);
		this.setRectFrom2(0, p0[0], p0[1], p1[0], p1[1]);
console.info(this.rect);
	};

	this.translateRect = function(dx, dy) {
		var r = [];
		for (var i = 0 ; i < 4 ; i++) {
			r[i*2] = this.rect[i*2] + dx;
			r[i*2+1] = this.rect[i*2+1] + dy;
			if ((r[i*2] < 0) || (r[i*2] > this.imgEl.width) || (r[i*2+1] < 0) || (r[i*2+1] > this.imgEl.height)) return;
		}
		this.rect = r;
	};

	this.min = function(arr) {
		var rtn = 1000000;
		for (var i = 0 ; i < arr.length ; i++) {
			if (arr[i] < rtn) rtn = arr[i];
		}
		return rtn;
	};
	this.max = function(arr) {
		var rtn = 0;
		for (var i = 0 ; i < arr.length ; i++) {
			if (arr[i] > rtn) rtn = arr[i];
		}
		return rtn;
	};


	this.drawIcon = function(icon, x, y) {
		//this.oCtx.drawImage(this.iconImgs[icon], x, y);  //icon does not rotate, oof.  so lets rotate....
		this.iCtx.setTransform(1, 0, 0, 1, 0, 0);
		this.iCtx.clearRect(0, 0, 12, 12);

		this.iCtx.rotate(this.rotation);
		var A = Math.atan2(6, 6) - this.rotation;
		var d = this.dist(0, 0, 12, 12)/2;
		var nx = d * Math.cos(A);
		var ny = d * Math.sin(A);
		this.iCtx.drawImage(this.iconImgs[icon], 0, 0, 12, 12, nx - 6, ny - 6, 12, 12);

		this.oCtx.drawImage(this.iCanvas, x, y);
	};


	//make sure we have an offsetX,offsetY
	this.eventPosFix = function(ev) {
		if (ev.changedTouches) {
			var pos = this.absPos(ev.target);
			ev.offsetX = ev.changedTouches[0].clientX - pos[0];
			ev.offsetY = ev.changedTouches[0].clientY - pos[1];
		} else if ((ev.offsetX == undefined) && (ev.layerX != undefined)) {
			ev.offsetX = ev.layerX;
			ev.offsetY = ev.layerY;
		} else if (ev.target && (ev.offsetX == undefined)) {
			var pos = this.absPos(ev.target);
			ev.offsetX = ev.screenX - pos[0];
			ev.offsetY = ev.screenY - pos[1];
		}
	};

	this.absPos = function(el) {
		if (!el) return false;
		var g = el.getClientRects();  //seems good enough for touch-friendly devices so lets use just this
		return [ g[0].left, g[0].top ];

		var p = [0,0];
		if (el.offsetTop) p[0] = el.offsetLeft;
		if (el.offsetLeft) p[1] = el.offsetLeft;
		var pp = this.absPos(p.parentElement);
		if (pp) {
			p[0] += pp[0];
			p[1] += pp[1];
		}
		return p;
	};


	//really more of a utility than necessary part of the tool
	this.scaleCanvas = function(scale, inCanvas, outCanvas) {
		outCanvas.width = inCanvas.width * scale;
		outCanvas.height = inCanvas.height * scale;
		var oc = outCanvas.getContext('2d');
		oc.drawImage(inCanvas, 0, 0, outCanvas.width, outCanvas.height);
		return outCanvas;
	};


	this.createLabelCanvas = function() {
		if (!this.imgEl) return;
		var cvs = document.createElement('canvas');
		cvs.width = this.imgEl.width;
		cvs.height = this.imgEl.height;
		var wrapper = document.createElement('div');
		wrapper.style.position = 'relative';
		var p = this.imgEl.parentNode;
		p.insertBefore(wrapper, this.imgEl);
		wrapper.appendChild(this.imgEl);
		cvs.style.position = 'absolute';
		cvs.style.top = 0;
		cvs.style.left = 0;
		wrapper.appendChild(cvs);
		this.lCanvas = cvs;
		this.lCtx = cvs.getContext('2d');
		return cvs;
	};



	this.triggerEvent = function(type, obj) {
		if (!this.eventTarget) return;
		var ev = new Event(type);
		ev._object = obj;
		ev._imageTools = this;
console.info('dispatched event %o', ev);
		return this.eventTarget.dispatchEvent(ev);
	};

	this.init(opts);



}

