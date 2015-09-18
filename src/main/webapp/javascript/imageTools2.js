
function ImageTools(opts) {
    this.containerElement = false;
    this.imageElement = false;
    this.canvasElement = false;
    this.ctx = false;  //just for convenience
    this.labelCanvasElement = false;
    this.lctx = false;
    this._img;
    this.transform = [
        [1, 0, 0],
        [0, 1, 0],
        [0, 0, 1]
    ];
    this.opts = {};

    this.createContainerElement = function(el) {
        this.containerElement = document.createElement('div');
        this.containerElement.className = 'imageTools-containerElement';
        this.containerElement.style.position = 'absolute';
        this.containerElement.style.overflow = 'hidden';
        this.containerElement.style.top = el.offsetTop + 'px';
        this.containerElement.style.left = el.offsetLeft + 'px';
        this.containerElement.style.width = el.offsetWidth + 'px';
        this.containerElement.style.height = el.offsetHeight + 'px';
        var p = el.parentNode;  //TODO no parent?  is this possible?
        p.appendChild(this.containerElement);
        return this.containerElement;
    };

    this.createImageElement = function(el) {
        if (!this.containerElement) return;
        this.imageElement = new Image();
        this.imageElement.src = el.src;
        this.imageElement.className = 'imageTools-imageElement';  //should we do this no matter what?
        this.imageElement.style.position = 'absolute';
        this.imageElement.style.width = '100%';
        this.imageElement.style.height = '100%';
        this.imageElement.style.transformOrigin = '50% 50%';
        this.containerElement.appendChild(this.imageElement);

        //this is for use as untouched, unscaled
        this._img = new Image();
        this._img.src = el.src;

        return this.imageElement;
    };

    this.createCanvasElement = function() {
        if (!this.containerElement) return;
        this.canvasElement = document.createElement('canvas');
        this.canvasElement.className = 'imageTools-canvasElement';
        this.canvasElement.style.position = 'absolute';
        this.canvasElement.style.pointerEvents = 'none';
        this.canvasElement.style.width = '100%';
        this.canvasElement.style.height = '100%';
        this.canvasElement.width = parseFloat(this.containerElement.style.width);
        this.canvasElement.height = parseFloat(this.containerElement.style.height);
        this.containerElement.appendChild(this.canvasElement);
        this.ctx = this.canvasElement.getContext('2d');
        return this.canvasElement;
    };

    this.createLabelCanvasElement = function() {
        if (!this.containerElement) return;
        this.labelCanvasElement = document.createElement('canvas');
        this.labelCanvasElement.className = 'imageTools-labelCanvasElement';
        this.labelCanvasElement.style.position = 'absolute';
        this.labelCanvasElement.style.pointerEvents = 'none';
        this.labelCanvasElement.style.width = '100%';
        this.labelCanvasElement.style.height = '100%';
        this.labelCanvasElement.width = parseFloat(this.containerElement.style.width);
        this.labelCanvasElement.height = parseFloat(this.containerElement.style.height);
        this.containerElement.appendChild(this.labelCanvasElement);
        this.lctx = this.labelCanvasElement.getContext('2d');
        return this.labelCanvasElement;
    };


//////// general geometry type stuff ////

    this.dist = function(x1, y1, x2, y2) {
        return Math.sqrt(this.dist2(x1, y1, x2, y2));
    };

    //for speed, we can often work with dist^2 and only sqrt at the end, thus:
    this.dist2 = function(x1, y1, x2, y2) {
        if ((x2 == undefined) && (y2 == undefined)) {  //allows passing as (p1, p2)
            x2 = y1[0];  //oh boy, does order matter with these!
            y2 = y1[1];
            y1 = x1[1];
            x1 = x1[0];
        }
//console.warn('dist2 (%f,%f) -- (%f,%f)', x1, y1, x2, y2);
        return (x1-x2)*(x1-x2) + (y1-y2)*(y1-y2);
    };

    //since this is just for comparison purposes, we never both with sqrt (return dist^2)
    //    h/t  https://stackoverflow.com/a/1501725/1525311
    this.distToLineSegment2 = function(p, a, b) {  //line segment is from a to b
        var l2 = this.dist2(a, b);
        var t = ((p[0] - a[0]) * (b[0] - a[0]) + (p[1] - a[1]) * (b[1] - a[1])) / l2;
//console.log('l2=%d, t=%f', l2, t);
        if (t < 0) return this.dist2(p, a);
        if (t > 1) return this.dist2(p, b);
        return this.dist2(p[0], p[1], a[0] + t * (b[0] - a[0]), a[1] + t * (b[1] - a[1]));
    };

    //relative to the ui/dom world
    this.getCenter = function() {
        if (!this.imageElement) return;
        return [this.imageElement.offsetWidth / 2, this.imageElement.offsetHeight / 2];
    };

    this.angleFromCenter = function(x, y) {
        var cp = this.getCenter();
        var a = Math.atan2(cp[1] - y, cp[0] - x);
        if (a < 0) a += Math.PI*2;
        return a;
    };

    this.midpoint = function(x1, y1, x2, y2) {
        return [(x1+x2)/2, (y1+y2)/2];
    };

    this.toCanvasPoint = function(p) {
        var w = this.canvasElement.width / 2;
        var h = this.canvasElement.height / 2;
	var cp = this.matrixMultiply(this.transform, [p[0] - w, p[1] - h, 1]);
        cp = [cp[0] + w, cp[1] + h];
        for (var i = 2 ; i < p.length ; i++) {  //carry over any additional elements beyond x,y (e.g. spot type)
            cp[i] = p[i];
        }
        return cp;
    };

    this.fromCanvasPoint = function(cp) {
        var w = this.canvasElement.width / 2;
        var h = this.canvasElement.height / 2;
        var p = this.matrixMultiply(this.transformInverse(), [cp[0] - w, cp[1] - h, 1]);
        p = [p[0] + w, p[1] + h];
        for (var i = 2 ; i < cp.length ; i++) {  //carry over any additional elements beyond x,y (e.g. spot type)
            p[i] = cp[i];
        }
        return p;
    };

    this.isNearSpot = function(x, y) {
        var d = 4;
        for (var i = 0 ; i < this.spots.length ; i++) {
            if (this.dist(x, y, this.spots[i][0], this.spots[i][1]) <= d) return i;
        }
        return -1;
    };

    //note, this returns an array: [pathNumber, indexInPath]; as such, false means no match
    this.isNearPathPoint = function(x, y) {
        if (!this.paths) return false;
        var d = 3;
        for (var pn = 0 ; pn < this.paths.length ; pn++) {
            if (!this.paths[pn]) continue;
            for (var i = 0 ; i < this.paths[pn].length ; i++) {
                if (this.dist(x, y, this.paths[pn][i][0], this.paths[pn][i][1]) < d) return [pn, i];
            }
        }
        return false;
    };



//////////////////// crazy transform madness ////

    this.transformReset = function() {
        this.transform = [
            [1, 0, 0],
            [0, 1, 0],
            [0, 0, 1]
        ];
    };

    //this rotates this much *more*... see also rotateTo
    this.rotate = function(r) {
        return this.rotateTo(r + this.getRotation());
    };
    this.rotateTo = function(r) {
        this.setTransform(r, this.getScale(), this.getTranslateX(), this.getTranslateY());
        return this.doTransform();
    };
    this.scale = function(s) {
        return this.scaleTo(this.getScale() * s);
    };
    this.scaleTo = function(s) {
        this.setTransform(this.getRotation(), s, this.getTranslateX(), this.getTranslateY());
        return this.doTransform();
    };
    this.translate = function(x,y) {
        return this.translateTo(this.getTranslateX() + x, this.getTranslateY() + y);
    };
    this.translateTo = function(x,y) {
        this.setTransform(this.getRotation(), this.getScale(), x, y);
        return this.doTransform();
    };

    this.rotationToMatrix = function(r) {
        var c = Math.cos(r);
        var s = Math.sin(r);
        return [ [c, -s, 0], [s, c, 0], [0, 0, 1] ];
    };
    this.scaleToMatrix = function(s) {
        return [ [s, 0, 0], [0, s, 0], [0, 0, 1] ];
    };
    this.setTransform = function(rotation, scale, x, y) {
        this.transform = this.computeTransformMatrix(rotation, scale, x, y);
    };
    this.computeTransformMatrix = function(rotation, scale, x, y) {
        var rs = this.matrixMultiply(this.rotationToMatrix(rotation), this.scaleToMatrix(scale));
//console.log('computeTransformMatrix(%o,%o,%o,%o) -> rs = %o', rotation, scale, x, y, rs);
        rs[0][2] = x;
        rs[1][2] = y;
        return rs;
    };

    this.doTransform = function() {
        if (!this.imageElement) return;
        var m = this.matrixToCss(this.transform);
//console.log('doTransform() -> %o', m);
        this.imageElement.style.transform = m;
        return this.transform;
    };

    this.getScale = function() {
        return Math.sqrt(this.transform[0][0] * this.transform[0][0] + this.transform[1][0] * this.transform[1][0]);
    };
    this.getRotation = function() {
        return Math.atan2(this.transform[1][0], this.transform[0][0]);
    };
    this.getTranslateX = function() {
        return this.transform[0][2];
    };
    this.getTranslateY = function() {
        return this.transform[1][2];
    };


    //handles the case where it is two matrices *and* where m2 is a row vector (e.g. point)
    this.matrixMultiply = function(m2, m1) {
        if (Array.isArray(m1[0])) {
            var result = [];
            for(var j = 0; j < m2.length; j++) {
                result[j] = [];
                for(var k = 0; k < m1[0].length; k++) {
                    var sum = 0;
                    for(var i = 0; i < m1.length; i++) {
                        sum += m1[i][k] * m2[j][i];
                    }
                    result[j].push(sum);
                }
            }
            return result;

        } else {
            return [
                m2[0][0] * m1[0] + m2[0][1] * m1[1] + m2[0][2] * m1[2],
                m2[1][0] * m1[0] + m2[1][1] * m1[1] + m2[1][2] * m1[2],
                m2[2][0] * m1[0] + m2[2][1] * m1[1] + m2[2][2] * m1[2]
            ];
        }
    };

    this.transformInverse = function() {
        return this.matrixInverse(this.transform);
    };

    // h/t http://snipplr.com/view/101612/3x3-matrix-algebra/
    this.matrixInverse = function(m) {
        var A = m[1][1] * m[2][2] - m[1][2] * m[2][1];
        var B = m[1][2] * m[2][0] - m[1][0] * m[2][2];
        var C = m[1][0] * m[2][1] - m[1][1] * m[2][0];
        var determinant = m[0][0] * A + m[0][1] * B + m[0][2] * C;
        if (determinant == 0) {
            console.warn('%o is a singular matrix; no determinant!', m);
            return false;
        }
        var invd = 1 / determinant;
        return [
            [
                A * invd,
                (m[0][2] * m[2][1] - m[0][1] * m[2][2]) * invd,
                (m[0][1] * m[1][2] - m[0][2] * m[1][1]) * invd
            ], [
                B * invd,
                (m[0][0] * m[2][2] - m[0][2] * m[2][0]) * invd,
                (m[0][2] * m[1][0] - m[0][0] * m[1][2]) * invd
            ], [
                C * invd,
                (m[0][1] * m[2][0] - m[0][0] * m[2][1]) * invd,
                (m[0][0] * m[1][1] - m[0][1] * m[1][0]) * invd
            ]
        ];
    };

    this.matrixToCss = function(m) {
        return this.transformToCss(this.matrixToTransform(m));
    };
    this.transformToCss = function(t) {
        return 'matrix(' + t.join(', ') + ')';
    };

    this.transformToMatrix = function(t) {
        return [ [t[0], t[2], t[4]], [t[1], t[3], t[5]], [0, 0, 1] ];
    };
    this.matrixToTransform = function(m) {
        return [ m[0][0], m[1][0], m[0][1], m[1][1], m[0][2], m[1][2] ];
    };

    //uses the 6-element 1-dim array format of css
    this.transformMultiply = function(t1, t2) {
        var m = this.matrixMultiply(this.transformToMatrix(t1), this.transformToMatrix(t2));
        return this.matrixToTransform(m);
    };



/////////////////////////// related to mouse events etc ////////////////////

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

/////////// munging with the pixels /////

    //takes visible view and copies from original source
    this.clipImage = function(canvas) {
        if (!this.imageElement) return;
        if (!canvas) {
            canvas = document.createElement('canvas');
        }
        canvas.width = this.imageElement.width;
        canvas.height = this.imageElement.height;
        var ctx = canvas.getContext('2d');

/*
        var maxDim = 1000;
        var wscale = 1;
        var ww = this.imageElement.naturalWidth;
        var wh = this.imageElement.naturalHeight;
        if (ww > maxDim) {
            wscale = maxDim / ww;
        }
        if (wh * wscale > maxDim) {
            wscale = maxDim / wh;
        }
        ww *= wscale;
        wh *= wscale;
console.info('%f -> %dx%d', wscale, ww, wh);

        var wCanvas = document.createElement('canvas');
        wCanvas.width = ww;
        wCanvas.height = wh;
        var wctx = wCanvas.getContext('2d');
*/

        var tx = this.getTranslateX();
        var ty = this.getTranslateY();
        var scale = this.getScale();

        var sw = this.imageElement.naturalWidth / scale;
        var sh = this.imageElement.naturalHeight / scale;
        var clipR = this.dist(0,0, sw/2, sh/2);
        var sx = (this.imageElement.naturalWidth - clipR * 2) / 2;
        var sy = (this.imageElement.naturalHeight - clipR * 2) / 2;

        var squareSide = clipR * 2;
        if (squareSide > 1200) {
            squareSide = 1200;
        }

var R = this.imageElement.naturalWidth / this.imageElement.width / scale;
//attributes of the rectangle we will clip from square
var A = Math.atan2(sh, sw);
var aw = squareSide * Math.cos(A);
var ah = squareSide * Math.sin(A);
var dx = (squareSide - aw) / 2;
var dy = (squareSide - ah) / 2;
console.warn("A = %f, side=%f, aw=%f ah=%f (%f,%f) R=%f", A, squareSide, aw, ah, dx, dy, R);

        var wCanvas = document.createElement('canvas');
        wCanvas.width = squareSide;
        wCanvas.height = squareSide;
        var wctx = wCanvas.getContext('2d');

        wctx.translate(wCanvas.width / 2, wCanvas.height / 2);
        wctx.rotate(this.getRotation());
        wctx.translate(-wCanvas.width / 2, -wCanvas.height / 2);
        //wctx.translate(-sx, -sy);
console.info('(%f,%f clipR*2=%f squareSide=%f [%fx%f] t=[%f,%f])', sx, sy, clipR * 2, squareSide, sw, sh, tx, ty);
console.warn('sx=%d tx=%d / sy=%d ty=%d / R=%f', sx, tx, sy, ty, R);
        //wctx.drawImage(this._img, sx - tx * R, sy - ty * R, clipR * 2, clipR * 2, 0, 0, squareSide, squareSide);
        wctx.drawImage(this._img, sx , sy, clipR * 2, clipR * 2, 0, 0, squareSide, squareSide);

//return wCanvas;
        //workCanvas should now have an image we grab from to get final product
        var cr = aw / canvas.width;
        ctx.drawImage(wCanvas, dx - cr * tx, dy - cr * ty, aw, ah, 0, 0, canvas.width, canvas.height);
        //var scale = squareSide / (clipR * 2);
//console.info('scale=%f drawImage(%d,%d  %dx%d)', scale, (squareSide - sw * scale) / 2, (squareSide - sh * scale), sw * scale, sh * scale);
        //ctx.drawImage(wCanvas, (squareSide - sw) / 2, (squareSide - sh), sw * scale, sh * scale, 0, 0, canvas.width, canvas.height);
//return wCanvas;
        return canvas;
    };

/////////////////


    this.init = function(opts) {
        var me = this;
        this.opts = opts;
        this.createContainerElement(opts.el);
        var imgEl = this.createImageElement(opts.el, opts.useExistingImage);
        this.createCanvasElement();
        this.createLabelCanvasElement();
        if (opts.eventListeners) {
            for (var evType in opts.eventListeners) {
console.info('adding event listener type %s', evType);
                imgEl.addEventListener(evType, function(ev) {
//console.log('>>>>>%s<<<: [%s] %o', ev.type, evType, ev);
                    me.eventPosFix(ev);
                    //console.warn('%o', opts.eventListeners[evType]);
                    opts.eventListeners[ev.type](ev);
                }, false);
            }
        }
    };

    this.init(opts);
}



