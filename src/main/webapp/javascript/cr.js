
var CRtool = {
	maction: false,
	drewHandle: false,
	tolerance: 10, //how close to something to be close enough
	rotation: 0,
	rect: [],
	iconsOn: true,
	shiftDown: false,
	_lastTouch: [0,0],

	styles: {
		stroke: 'rgba(255,255,0,0.6)',
		strokeTopLine: 'rgba(100,255,200,0.6)',
		fill: 'rgba(255,255,0,0.6)',
		lineWidthBox: 3,
		lineWidthIn: 1,
	},

	iconPNGs: {
		rotate: 'iVBORw0KGgoAAAANSUhEUgAAAAwAAAAMCAYAAABWdVznAAAAAXNSR0IArs4c6QAAAAZiS0dEAP8A/wD/oL2nkwAAAAlwSFlzAAALEwAACxMBAJqcGAAAAAd0SU1FB94GHhISMe3uBQQAAAAZdEVYdENvbW1lbnQAQ3JlYXRlZCB3aXRoIEdJTVBXgQ4XAAAAR0lEQVQoz52QSQ4AIAgDp4b/fxlPHowgaI9kQhd4lIKbJ4wDUgEfBhbA1yfjBQawj44t+XKzYplDoxFj66funJGDkijfxQGYJKAMFyDZ++UAAAAASUVORK5CYII=',
		scaleNE: 'iVBORw0KGgoAAAANSUhEUgAAAAwAAAAMCAYAAABWdVznAAAAAXNSR0IArs4c6QAAAAZiS0dEAP8A/wD/oL2nkwAAAAlwSFlzAAALEwAACxMBAJqcGAAAAAd0SU1FB94GHhIUCOSxKooAAAAZdEVYdENvbW1lbnQAQ3JlYXRlZCB3aXRoIEdJTVBXgQ4XAAAAQklEQVQoz5VRyw4AIAgC//+f6doqQjm5yWMoMAQ/O734bJjuQtaADACornMSKPXUMcuUvhKiM005e70a/sYmRGEbC1q/DAs34sNRAAAAAElFTkSuQmCC',
		scaleNW: 'iVBORw0KGgoAAAANSUhEUgAAAAwAAAAMCAYAAABWdVznAAAAAXNSR0IArs4c6QAAAAZiS0dEAP8A/wD/oL2nkwAAAAlwSFlzAAALEwAACxMBAJqcGAAAAAd0SU1FB94GHhIUGPkGOu4AAAAZdEVYdENvbW1lbnQAQ3JlYXRlZCB3aXRoIEdJTVBXgQ4XAAAARUlEQVQoz2NgIBEwQun/eORQAAuxCmGACYvYf2I1MBKjiQmPc/6TEhj/kTCKAUwEQg/DJkYibEJRz0RkPOGNB7I8jRMAAE2VCxFyVbghAAAAAElFTkSuQmCC'
	},

	init: function(els) {
		var me = this;
		for (var k in els) {
			this[k] = els[k];
		}
		if (this.oCanvas) this.oCtx = this.oCanvas.getContext('2d');
		if (this.wCanvas) this.wCtx = this.wCanvas.getContext('2d');

		document.addEventListener('keydown', function(ev) { if (ev.keyCode == 16) { me.shiftDown = true;} });
		document.addEventListener('keyup', function(ev) { if (ev.keyCode == 16) { me.shiftDown = false;} });

		if (!this.imgEl) return;

		this.iCanvas = document.createElement('canvas');
		this.iCanvas.width = 12;
		this.iCanvas.height = 12;
		this.iCanvas.style.display = 'none';
		document.getElementsByTagName('body')[0].appendChild(this.iCanvas);
		this.iCtx = this.iCanvas.getContext('2d');
console.log('iCanvas init');

		this.imgEl.onload = function() { me.imageReady(); };
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
	},

	imageReady: function() {
console.log('imageReady called');
console.log('iCtx is %o', this.iCtx);
console.log('oCtx is %o', this.oCtx);
console.log('imgEl.width = ' + this.imgEl.width);
		this.info('img size: <b>' + this.imgEl.naturalWidth + 'x' + this.imgEl.naturalHeight);
		if (!this.oCanvas) return;
console.log('starting imageReady');

		var me = this;
		this.oCanvas.ontouchmove = function(ev) {
			me.eventPosFix(ev);
			me._lastTouch = [ev.offsetX, ev.offsetY];
			me.mmove(ev);
		};
		this.oCanvas.ontouchstart = function(ev) {
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

		this.scale = this.imgEl.naturalWidth / this.imgEl.width;
		this.oCanvas.width = this.imgEl.width;
		this.oCanvas.height = this.imgEl.height;
		this.oCanvas.style.display = 'block';
		this.setRectFrom2(0,
			0.25 * this.imgEl.width, 0.25 * this.imgEl.height,
			0.75 * this.imgEl.width, 0.75 * this.imgEl.height
		);
		this.oCtx.strokeStyle = this.styles.stroke;
		this.oCtx.fillStyle = this.styles.fill;
		this.drawRect();  //may be beat by waitForRect() but ... maybe not?
		this.toWork();
	},

	waitForIcons: function() {
		this._iconsLoading--;
console.log('count? ' + this._iconsLoading);
		if (this._iconsLoading > 0) return;
console.log('drawingRect');
		this.drawRect();
	},

	info: function(s) {
		if (!this.infoEl) return;
		this.infoEl.innerHTML = s;
	},


	oClear: function() {
		this.oCtx.clearRect(0, 0, this.imgEl.width, this.imgEl.height);
	},


	toWork: function(a,b) {
		var minX = this.min([this.rect[0], this.rect[2], this.rect[4], this.rect[6]]);
		var maxX = this.max([this.rect[0], this.rect[2], this.rect[4], this.rect[6]]);
		var minY = this.min([this.rect[1], this.rect[3], this.rect[5], this.rect[7]]);
		var maxY = this.max([this.rect[1], this.rect[3], this.rect[5], this.rect[7]]);
		//var idata = this.wCtx.getImageData(minX * this.scale, minY * this.scale, (maxX - minX) * this.scale, (maxY - minY) * this.scale);

		var w = (maxX - minX) * this.scale;
		var h = (maxY - minY) * this.scale;
		var rw = this.rectW() * this.scale;
		var rh = this.rectH() * this.scale;
		this.wCanvas.width = rw;
		this.wCanvas.height = rh;
//console.log('%d, %d', (rw-w)/2, (rh-h)/2);
		this.wCtx.rotate(-this.rotation);

		var A = Math.atan2(rh/2, rw/2) + this.rotation;
		var d = this.dist(0, 0, rw, rh)/2;
		var nx = d * Math.cos(A);
		var ny = d * Math.sin(A);
//console.log('(nx,ny) %f,%f : (m) %f,%f', nx, ny, w/2, h/2);
//console.log('%f,%f', w/2 - nx, h/2 - ny);

		this.wCtx.drawImage(this.imgEl, minX * this.scale, minY * this.scale, w, h, -(w/2-nx), -(h/2-ny), w, h);
		//this.wCtx.drawImage(this.imgEl, minX * this.scale, minY * this.scale, w, h, 0, 10, rw, rh);
		//this.wCtx.drawImage(this.imgEl, minX * this.scale - w/2, minY * this.scale - h/2, w * 1.5, h * 1.5, 0, 0, rw * 1.5, rh * 1.5);
		//this.wCtx.drawImage(this.imgEl, minX * this.scale - w/2, minY * this.scale - h/2, w * 1.0, h * 1.0, 0, 0, rw, rh);
		this.wCanvas.dispatchEvent(new Event('update'));
	},

	mmove: function(ev) {
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
			this.setRectFrom2(this._corner, ev.offsetX, ev.offsetY, this.rect[oppc*2], this.rect[oppc*2+1]);
			this.oClear();
			this.drawRect();
		}

		if (this.maction == 'rotate') {
			var a = this.angleFromCenter(ev.offsetX, ev.offsetY);
if (this.shiftDown) a = Math.floor(a / (Math.PI/4) + 0.5) * (Math.PI/4);
			var rot = a - this._prevAngle;
			if (Math.abs(rot) < 0.05) return;
			this._prevAngle = a;
			this.rotateRect(rot);
			this.oClear();
			this.drawRect();
		}

		if (this.maction == 'drag') {
			var dx = ev.offsetX - this.rect[0] - this._dx;
			var dy = ev.offsetY - this.rect[1] - this._dy;
			for (var i = 0 ; i < 4 ; i++) {
				this.rect[i*2] += dx;
				this.rect[i*2+1] += dy;
			}
			this.oClear();
			this.drawRect();
		}
	},

	mdown: function(ev) {
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
	},

	mup: function(ev) {
		ev.preventDefault();
		this.toWork();
		this.maction = false;
	},

	drawRect: function() {
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
	},

	dist: function(x1, y1, x2, y2) {
		return Math.sqrt((x1-x2)*(x1-x2) + (y1-y2)*(y1-y2));
	},

	rectW: function() {
		return this.dist(this.rect[0], this.rect[1], this.rect[2], this.rect[3]);
	},
	rectH: function() {
		return this.dist(this.rect[0], this.rect[1], this.rect[6], this.rect[7]);
	},

	nearCorner: function(x, y) {
		for (var i = 0 ; i < 4 ; i++) {
			var d = this.dist(this.rect[i*2], this.rect[i*2+1], x, y);
			if (d < this.tolerance) return i;
		}
		return -1;
	},


	// pts will be considered opposite corners
	setRectFrom2: function(corner, x1, y1, x2, y2) {
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
	},

	nearMidpoint: function(x, y) {
		for (var i = 0 ; i < 4 ; i++) {
			var ni = (i + 1) % 4;
			var m = this.midpoint(this.rect[i*2], this.rect[i*2+1], this.rect[ni*2], this.rect[ni*2+1]);
			var d = this.dist(m[0], m[1], x, y);
			if (d < this.tolerance) return i;
		}
		return -1;
	},

	midpoint: function(x1, y1, x2, y2) {
		return [(x1+x2)/2, (y1+y2)/2];
	},


	onSide: function(x1, y1, x2, y2, x3, y3) {
		return (x1 - x3) * (y2 - y3) - (x2 - x3) * (y1 - y3);
	},

	inTriangle: function(x, y, x1, y1, x2, y2, x3, y3) {
		var b1 = this.onSide(x, y, x1, y1, x2, y2) < 0;
		var b2 = this.onSide(x, y, x2, y2, x3, y3) < 0;
		var b3 = this.onSide(x, y, x3, y3, x1, y1) < 0;
		return ((b1 == b2) && (b2 == b3));
	},

	inRect: function(x, y) {
		return this.inTriangle(x, y, this.rect[0], this.rect[1], this.rect[2], this.rect[3], this.rect[6], this.rect[7]) || this.inTriangle(x, y, this.rect[2], this.rect[3], this.rect[4], this.rect[5], this.rect[6], this.rect[7]);
	},

	angleFromCenter: function(x, y) {
		var cp = this.midpoint(this.rect[0], this.rect[1], this.rect[4], this.rect[5]);
		var a = Math.atan2(cp[1] - y, cp[0] - x);
		if (a < 0) a += Math.PI*2;
		return a;
	},

	rotatePoint: function(x, y, cx, cy, A) {
		var B2 = Math.atan2(y - cy, x - cx) + A;
		var r = Math.sqrt((cx - x)*(cx - x) + (cy - y)*(cy - y));
		var x2 = cx + r * Math.cos(B2);
		var y2 = cy + r * Math.sin(B2);
		return [x2, y2];
	},

	rotateRect: function(A) {
		var cp = this.midpoint(this.rect[0], this.rect[1], this.rect[4], this.rect[5]);
		for (var i = 0 ; i < 4 ; i++) {
			var p = this.rotatePoint(this.rect[i*2], this.rect[i*2+1], cp[0], cp[1], A);
			this.rect[i*2] = p[0];
			this.rect[i*2+1] = p[1];
		}
		this.rotation += A;
	},

	min: function(arr) {
		var rtn = 1000000;
		for (var i = 0 ; i < arr.length ; i++) {
			if (arr[i] < rtn) rtn = arr[i];
		}
		return rtn;
	},
	max: function(arr) {
		var rtn = 0;
		for (var i = 0 ; i < arr.length ; i++) {
			if (arr[i] > rtn) rtn = arr[i];
		}
		return rtn;
	},


	drawIcon: function(icon, x, y) {
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
	},


	//make sure we have an offsetX,offsetY
	eventPosFix: function(ev) {
		if (ev.target && !ev.offsetX) {
			var pos = this.absPos(ev.target);
			ev.offsetX = ev.pageX - pos[0];
			ev.offsetY = ev.pageY - pos[1];
		}
	},

	absPos: function(el) {
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
	},


	//really more of a utility than necessary part of the tool
	scaleCanvas: function(scale, inCanvas, outCanvas) {
		outCanvas.width = inCanvas.width * scale;
		outCanvas.height = inCanvas.height * scale;
		var oc = outCanvas.getContext('2d');
		oc.drawImage(inCanvas, 0, 0, outCanvas.width, outCanvas.height);
		return outCanvas;
	},

}

