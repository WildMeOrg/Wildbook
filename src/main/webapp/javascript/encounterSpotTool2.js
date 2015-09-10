    var itool;
    var defaultCursor = 'crosshair';

    var spotStyle = {
        null: {
            strokeStyle: 'green',
            fillStyle: 'rgba(0,255,0,0.4)'
        },
        tipLeft: {
            strokeStyle: 'yellow',
            fillStyle: 'rgba(255,255,0,0.4)'
        },
        notch: {
            strokeStyle: '#888',
            fillStyle: 'rgba(128,128,128,0.4)'
        },
        tipRight: {
            strokeStyle: 'blue',
            fillStyle: 'rgba(0,0,255,0.4)'
        },
        _line: {
            xstrokeStyle: 'rgba(255,255,0,0.4)',
            strokeStyle: 'white',
            lineWidth: 25,
            lineCap: 'round'
        },
        _dim: {
            strokeStyle: 'none',
            fillStyle: 'rgba(255,255,255,0.6)'
        },
        _ghost: {
            strokeStyle: 'black',
            fillStyle: 'rgba(255,255,255,0.7)',
            lineWidth: 3,
            setLineDash: [4,4]
        },
        _hilite: {
            strokeStyle: '#FCA',
            fillStyle: 'rgba(255,200,0,0.4)'
        }
    };

// mode, bits -> RST
function setTool() {
    $('body').on('keyup', function(ev) {
        var map = {83: 0, 68: 8, 77: 1, 90: 2, 82: 4, 88: 6};
        if (map[ev.which] == undefined) return;
        modeMenuSet(map[ev.which]);
    });

    var opts = {
        el: document.getElementById('target-img'),
        eventListeners: {
            contextmenu: function(ev) {
    console.log('menu!!!');
                itool._addingSpot = false;
                ev.preventDefault();
                ev.stopPropagation();
            },

            mousemove: function(ev) {
                ev.preventDefault();

                //t._insideSpot refers to if mouse went *down* on a spot
                if (itool._insideSpot > -1) {
                    clearLabelCanvas();
                    itool.imageElement.style.cursor = 'grabbing';
                    itool.imageElement.style.cursor = '-moz-grabbing';
                    itool.imageElement.style.cursor = '-webkit-grabbing';
console.log('dragging spot %d', itool._insideSpot);
                    itool._labelCanvasNeedsClearing = true;

                    drawSpot(itool.lctx, itool.spots[itool._insideSpot], '_ghost');
                    drawSpot(itool.lctx, [ev.offsetX, ev.offsetY], '_hilite');
                    return;
                }

                var s = itool.isNearSpot(ev.offsetX, ev.offsetY);
                if ((s > -1) && (itool._mode & 8)) {
                    itool.imageElement.style.cursor = 'not-allowed';
                } else if (s > -1) {
console.log(s);
                    //t.imageElement.style.cursor = 'context-menu';
                    itool.imageElement.style.cursor = 'grab';
                    itool.imageElement.style.cursor = '-moz-grab';
                    itool.imageElement.style.cursor = '-webkit-grab';
                } else {
                    itool.imageElement.style.cursor = defaultCursor;
                }

                clearLabelCanvas();
                if (!itool._mouseDown) return;
                itool.imageElement.style.cursor = 'move';
//console.log('(%d,%d)', ev.offsetX, ev.offsetY);
//console.log(ev);
                //note: never ever do translate with either rotate/scale. i.e. no odd numbers other than 1
                if (itool._mode & 4) {
                    var r = itool.angleFromCenter(ev.offsetX, ev.offsetY) - itool._startAngle;
                //if (r < 0) r += 360;
/////console.log('%.1f (%.3f)', r * (180/Math.PI), r);
                    itool.rotate(itool._originalRotation + r);
                    refreshEdgeCanvas();
                    updateSaveStatus();
                }
                if (itool._mode & 2) {
                    var c = itool.getCenter();
                    var d = itool.dist(c[0], c[1], ev.offsetX, ev.offsetY);
                    itool.scale(d / itool._startDist);// * itool._originalScale);
                    refreshEdgeCanvas();
                    updateSaveStatus();
                }
                if (itool._mode & 1) {
                    itool.translate(ev.offsetX - itool._mouseDown[0], ev.offsetY - itool._mouseDown[1]);
                    refreshEdgeCanvas();
                    updateSaveStatus();
                }
                if (!itool._moved) clearCanvas();  //only on first move
                itool._moved = true;
            },
/*
            mouseover: function(ev) {
                console.log('over!');
            },
*/
            mousedown: function(ev) {
                itool._addingSpot = false;
                itool._insideSpot = itool.isNearSpot(ev.offsetX, ev.offsetY);

                                if ((itool._insideSpot > -1) && (itool._mode & 8)) {
                                    removeSpot(itool._insideSpot);
                                    itool._insideSpot = -1;
                                    return;
                                }
if (itool._insideSpot > -1) {
    console.log('spot=%d', itool._insideSpot);
    return false;
}
                itool._addingSpot = true;
                if (!itool._mode) return;
                itool._mouseDown = [ev.offsetX, ev.offsetY];
                itool._startAngle = itool.angleFromCenter(ev.offsetX, ev.offsetY);
                itool._originalRotation = itool.getRotation();
                var c = itool.getCenter();
                itool._startDist = itool.dist(c[0], c[1], ev.offsetX, ev.offsetY);
                itool._originalScale = itool.getScale();
            },
            mouseup: function(ev) {
                itool.imageElement.style.cursor = defaultCursor;

                if (itool._insideSpot > -1) {
                    var s = -2;
                    //var s = itool.isNearSpot(ev.offsetX, ev.offsetY);
                    if (s != itool._insideSpot) {
                        itool.spots[itool._insideSpot][0] = ev.offsetX;
                        itool.spots[itool._insideSpot][1] = ev.offsetY;
                        spotsUpdate();
                    }
                    clearLabelCanvas();
                }
                if (itool._addingSpot && !itool._moved) {
                    addSpot(ev.offsetX, ev.offsetY);
                }
                refreshCanvas();
                itool._addingSpot = false;
                itool._insideSpot = -1;
                itool._mouseDown = false;
                itool._moved = false;
            },
/*
            mouseout: function(ev) {
                itool._mouseDown = false;
                //t.rotate(itool._originalRotation);
            },
*/
                
        }
    };



    itool = new ImageTools(opts);
    itool.spots = [];
    setMode(0);

    $('#edge-params').slider({
        range: true,
        stop: function() { if (edgeCanvas) doEdge(); },
        min: 0,
        max: 200,
        values: [edgeA, edgeB],
        slide: function(ev, ui) {
            if (!edgeCanvas) return;
            edgeA = ui.values[0];
            edgeB = ui.values[1];
console.warn('edgeA = %o ; edgeB = %o', edgeA, edgeB);
            var ctx = edgeCanvas.getContext('2d');
            var ctx2 = edgeDetect(ctx);
            var imageData = ctx2.getImageData(0, 0, ctx2.canvas.width, ctx2.canvas.height);
            ctx.putImageData(imageData, 0, 0);
        }
    });

    $('#edge-transparency').slider({
        min: 0,
        max: 100,
        value: 85,
        slide: function(ev, ui) {
            if (!edgeCanvas) return;
            edgeCanvas.style.opacity = ui.value / 100;
        }
    });

//t.imageElement.addEventListener('mousemove', function() { console.log('mm'); }, false);
    userMessage('ready to pick <b>3 spots</b> at <b>tips</b> and <b>center</b>');
    //CTX = doEdge();
}

function setMode(m) {
    if (m & 2) {
        defaultCursor =  '-webkit-zoom-in';
    } else if ((m == 0) || (m == 8)) {
        defaultCursor = 'crosshair';
    } else if (m) {
        defaultCursor = 'move';
    }
    itool._mode = m;
}


function modeMenuChange(el) {
    setMode(el.value);
}

//also sets the menu accordingly
function modeMenuSet(i) {
    $('#edit-mode-menu').val(i);
    setMode(i);
}


function refreshEdgeCanvas() {
    if (!edgeCanvas) return;
    edgeCanvas.style.transform = itool.imageElement.style.transform;
}

var alreadySaved = false;
function updateSaveStatus(noChangesMade) {
    if (!noChangesMade) alreadySaved = false;
    if (!alreadySaved && ((itool.spots.length > 0) || (itool.transformToCss(itool.transform) != 'matrix(1,0,0, 0,1,0, 0,0,1)'))) {
        $('#save-button').prop('disabled', null);
    } else {
        $('#save-button').prop('disabled', 'disabled');
    }
}


function updateScanTool() {
    if (!itool.paths || (itool.paths.length < 2)) return;
    $('#scan-tool').show();
}


function drawFinalPaths() {
    if (!itool.paths || (!itool.paths[0] && !itool.paths[1])) return;
    var imageData = itool.ctx.getImageData(0, 0, itool.ctx.canvas.width, itool.ctx.canvas.height);
    var rgb = [ [255,255,100], [0,100,255] ];
    var paths = [];
    for (var i = 0 ; i < 2 ; i++) {
        if (!itool.paths[i]) continue;
        paths[i] = [];
        for (var j = 0 ; j < itool.paths[i].length ; j++) {
            var cp = itool.toCanvasPoint(itool.paths[i][j]);
//console.info('(%f,%f) -> (%f,%f)', itool.paths[i][j][0], itool.paths[i][j][1], cp[0], cp[1]);
            //cp[0] *= scale;
            //cp[1] *= scale;
            paths[i].push(cp);
        }
        drawPath(imageData, paths[i], rgb[i]);
    }
    itool.ctx.putImageData(imageData, 0, 0);
}

function save() {
    var scale = itool.imageElement.naturalWidth / itool.imageElement.width;
    alreadySaved = true;
    updateSaveStatus(true);
    imageMessage();
    userMessage('saving...');
    var data = {
        id: imageID,
        name: imageID + '-' + new Date().getTime() + '.jpg',
        clientWidth: itool.imageElement.width,
        transform: itool.matrixToTransform(itool.transform),
        points: [],
        paths: [null, null]
    };
    for (var i = 0 ; i < 2 ; i++) {
        if (!itool.paths || !itool.paths[i]) continue;
        data.paths[i] = [];
        for (var j = 0 ; j < itool.paths[i].length ; j++) {
            var cp = itool.toCanvasPoint(itool.paths[i][j]);
            cp[0] *= scale;
            cp[1] *= scale;
            data.paths[i].push(cp);
        }
    }
    for (var i = 0 ; i < itool.spots.length ; i++) {
        var cp = itool.toCanvasPoint(itool.spots[i]);
        cp[0] *= scale;
        cp[1] *= scale;
        data.points.push(cp);
    }
console.warn('sending data: %o', data); //return;
    $.ajax({
        url: '../SubmitSpotsAndTransformImage',
        type: 'POST',
        data: JSON.stringify(data),
        complete: function(d) {
            var j = JSON.parse(d.responseText);
            if (j && j.success) {
                //console.info('looks like it worked');
                imageMessage('saved!');
                var i = itool.imageElement.src.lastIndexOf('/');
                var url = itool.imageElement.src.substring(0, i+1) + j.name;
                userMessage('<b>saved successfully.</b>  <a target="_new" title="view image in new window" href="' + url + '">[view image]</a>');
                updateScanTool();
            } else if (j && j.error) {
                imageMessage('ERROR saving');
                userMessage('ERROR saving: <b>' + j.error + '</b>');
                updateSaveStatus();
                alert('ERROR saving: ' + j.error);
            } else {
                updateSaveStatus();
                imageMessage('ERROR saving');
                userMessage('ERROR ' + d.status + ' saving: <b>' + d.statusText + '</b>');
                alert('ERROR ' + d.status + ' saving: ' + d.statusText);
            }
console.log('save returned: %o', d);
        },
        dataType: 'json',
        contentType: 'application/json'
    });
}

var currentSpotType = null;
function addSpot(x,y,type) {
    if (!type) type = currentSpotType;
    if (($('[name="edge-mode"]:checked').val() == 'auto') && (itool.spots.length >= 3)) {  //cant add more when in auto mode
        return;
    }
    itool.spots.push([x, y, type]);
/*
    var s = itool.matrixMultiply(itool.transformInverse(), [x, y, 1]);
console.log('(%d,%d) -> (%d,%d)', x, y, s[0], s[1]);
    itool.spots.push([s[0], s[1], type]);
*/
console.info('addSpots(%d,%d) -> %o', x, y, itool.spots);
    spotsUpdate();
    updateSaveStatus();
}

function removeSpot(i) {
    itool.spots.splice(i, 1);
    spotsUpdate();
    updateSaveStatus();
    if (itool.spots.length < 1) modeMenuSet(0);
}

function spotsUpdate() {
    if (itool.spots.length < 3) {
        userMessage('required spots needed: <b>' + (3 - itool.spots.length) + '</b>  (you can <b>drag</b> spots to move them.)');
    } else if (itool.spots.length == 3) {
        doEdge();
    }
}


function resetAll() {
    itool.transformReset();
    itool.doTransform();
    itool.spots = [];
    delete(itool.paths);
    spotsUpdate();
    updateSaveStatus();
    clearCanvas();
    $(edgeCanvas).remove();
    edgeCanvas = null;
}


function backToEncounter() {
    //TODO check for changes and confirm
    window.location = 'encounter.jsp?number=' + encounterNumber;
}

function refreshCanvas() {
    clearCanvas();
    drawSpots();
    drawFinalPaths();
}

function clearCanvas() {
    itool.ctx.clearRect(0, 0, itool.canvasElement.width, itool.canvasElement.height);
}

function clearLabelCanvas() {
    if (!itool._labelCanvasNeedsClearing) return;
    itool.lctx.clearRect(0, 0, itool.labelCanvasElement.width, itool.labelCanvasElement.height);
    itool._labelCanvasNeedsClearing = false;
}

function drawSpots() {
    for (var i = 0 ; i < itool.spots.length ; i++) {
/*
//var m = itool.computeTransformMatrix(2 * Math.PI - itool.getRotation(), 1, 0, 0);
//var m = itool.computeTransformMatrix(itool.getRotation(), 1, 0, 0);
//var m = itool.transformInverse();
var m = itool.transform;
        var p = itool.matrixMultiply(m, [t.spots[i][0] - 150, itool.spots[i][1] - 150, 1]);
console.log('%d,%d -> %o', itool.spots[i][0], itool.spots[i][1], p);
*/
        drawSpot(itool.ctx, itool.spots[i], itool.spots[i][2]);
/*
        contextSetStyles(itool.ctx, 
        itool.ctx.beginPath();
        var p = itool.toCanvasPoint(itool.spots[i]);
        itool.ctx.arc(p[0], p[1], 20, 0, 2 * Math.PI);
        itool.ctx.fill();
        itool.ctx.stroke();
*/
    }
}


function contextSetStyles(ctx, style) {
    if (!style) return;
    for (var s in style) {
        if (s == 'setLineDash') {
            ctx.setLineDash(style[s]);
        } else {
            ctx[s] = style[s];
        }
    }
}


function drawSpot(ctx, p, styleKey, r) {
    if (!r) r = 8;
    contextSetStyles(ctx, spotStyle[styleKey]);
    ctx.beginPath();
    var cp = itool.toCanvasPoint(p);
    ctx.arc(cp[0], cp[1], r, 0, 2 * Math.PI);
    if (spotStyle[styleKey].fillStyle != 'none') ctx.fill();
    if (spotStyle[styleKey].strokeStyle != 'none') ctx.stroke();
}



function userMessage(m) {
    $('#user-message').html(m);
}

function imageMessage(m) {
    if (m) {
        $('#image-message').html(m).show();
    } else {
        $('#image-message').html('').hide();
    }
}


var fctx;
var PATH;
var edgeCanvas;

var edgeA = 20;
var edgeB = 50;
var edgeScrubbing = false;

function doEdge() {
    if (!edgeCanvas) {
        edgeCanvas = document.createElement('canvas');
        var w = itool.imageElement.width;
        var h = itool.imageElement.height;
/*
        var w = itool.imageElement.naturalWidth;
        var h = itool.imageElement.naturalHeight;
        if (w > 1200) {
            h = (1200 / w) * h;
            w = 1200;
        }
        if (h > 900) {
            w = (900 / h) * w;
            h = 900;
        }
*/
        edgeCanvas.width = w;
        edgeCanvas.height = h;
        edgeCanvas.style.position = 'absolute';
        edgeCanvas.style.width = '100%';
        edgeCanvas.style.height = '100%';
        edgeCanvas.style.transformOrigin = '50% 50%';
        edgeCanvas.style.opacity = 0.85;
        edgeCanvas.style.pointerEvents = 'none';
        edgeCanvas.style.transform = itool.imageElement.style.transform;
        //document.getElementsByTagName('body')[0].appendChild(edgeCanvas);
        itool.containerElement.insertBefore(edgeCanvas, itool.canvasElement);

/*
        $('#edge-scrub').on('mousedown', function() { edgeScrubbing = true; });
        $('#edge-scrub').on('mouseup', function(ev) {
            ev.preventDefault();
            edgeScrubbing = false;
            doEdge();
        });
        $('#edge-scrub').on('mousemove', function(ev) {
            ev.preventDefault();
            if (!edgeScrubbing) return;
            edgeA = 0 + 120 * (ev.offsetX / ev.target.offsetWidth);
            edgeB = 0 + 200 * (ev.offsetY / ev.target.offsetHeight);
            var ctx = edgeCanvas.getContext('2d');
            var ctx2 = edgeDetect(ctx);
            var imageData = ctx2.getImageData(0, 0, ctx2.canvas.width, ctx2.canvas.height);
            ctx.putImageData(imageData, 0, 0);
        });
*/
        
    }
    var ctx = edgeCanvas.getContext('2d');
fctx = ctx;

    var ctx2 = edgeDetect(ctx);
    //var scale = itool.imageElement.naturalWidth / itool.imageElement.width;
    var scale = ctx2.canvas.width / itool.imageElement.width;
console.log('scale = %f', scale);
    if (itool.spots.length < 3) {
        console.warn('not enough spots to look for edge');
        return;
    }

    itool.spots = sortSpots(itool.spots);

    //adjust for scaling
    var spots = [];
    for (var i = 0 ; i < 3 ; i++) {
        spots[i] = [Math.floor(itool.spots[i][0] * scale), Math.floor(itool.spots[i][1] * scale), itool.spots[i][2]];
    }
console.log('spots: %o', spots);
    var imageData = ctx2.getImageData(0, 0, ctx2.canvas.width, ctx2.canvas.height);
    itool.paths = [];
    itool.paths[0] = bestPath(imageData, spots[0], spots[1]);
//PATH = pathLeft;
console.log('left path -> %o', itool.paths[0]);
    drawPath(imageData, itool.paths[0], [255,255,100]);
    itool.paths[1] = bestPath(imageData, spots[2], spots[1]);
console.log('right path -> %o', itool.paths[1]);
    drawPath(imageData, itool.paths[1], [100,200,255]);
    ctx.putImageData(imageData, 0, 0);

    if (itool.paths[0] && itool.paths[1]) {
        userMessage('<b>left and right fluke edges found!</b> you can save now if results are correct.');
        imageMessage('edges found! :)');
/*
        var id = itool.ctx.getImageData(0, 0, itool.canvasElement.width, itool.canvasElement.height);
        drawPath(id, itool.paths[0], [255,255,100]);
        drawPath(id, itool.paths[1], [100,200,255]);
        itool.ctx.putImageData(id, 0, 0);
*/
    } else {
        userMessage('fluke edges were <b>not found</b>.  adjust points and/or alter edge tolerance settings. you may <b>manually select points</b> also.');
        imageMessage('no edges. :(');
    }

/*
    var s = findSpotsOnLine(spots[0], spots[1], imageData);
    var s2 = findSpotsOnLine(spots[1], spots[2], imageData);
    var foundSpots = s.concat(s2);

console.log(imageData);
    var m = (spots[1][1] - spots[0][1]) / (spots[1][0] - spots[0][0]);
    var b = spots[0][1] - m * spots[0][0];
    var step = 4;
console.log('slope = %f, b = %f, step = %d', m, b, step);
    for (var x = spots[0][0] + step ; x < spots[1][0] ; x += step) {
        y = m * x + b;
console.warn('===(%d,%d)===================', x,y);
        var p = checkAround(imageData, x, y, m, 1);
        if (p) foundSpots.push(p);
    }
*/

/*
    for (var i = 0 ; i < spots.length ; i++) {
        drawSpot(ctx2, spots[i], '_hilite', 9);
    }
*/



/*
    for (var i = 0 ; i < foundSpots.length ; i++) {
        drawSpot(ctx2, foundSpots[i], '_dim', 3);
    }
*/

    return ctx2;
}


//desired outcome: (tipLeft, notch, tipRight[, ... others... ])
function sortSpots(s) {
    var spots = [];
    var len = s.length;
    for (var i = 0 ; i < len ; i++) {
        if (s[i][2] == 'tipLeft') {
            spots[0] = s[i];
            s.splice(i, 1);
            len--;
        } else if (s[i][2] == 'notch') {
            spots[1] = s[i];
            s.splice(i, 1);
            len--;
        } else if (s[i][2] == 'tipRight') {
            spots[2] = s[i];
            s.splice(i, 1);
            len--;
        }

    }
    //by now s will only contain un-special points, so we order by x coordinate
    s.sort(function(a,b) { return a[0] - b[0]; });
    //now fill out what we dont have
    if (!spots[0]) {
        spots[0] = s.shift();
        spots[0][2] = 'tipLeft';
    }
    if (!spots[1]) {
        spots[1] = s.shift();
        spots[1][2] = 'notch';
    }
    if (!spots[2]) {
        spots[2] = s.shift();
        spots[2][2] = 'tipRight';
    }
    return spots;
}

function bestPath(imageData, p1, p2, skipped) {
    if (!skipped) skipped = 0;
    if ((p1[0] < 0) || (p1[0] >= imageData.width) || (p1[1] < 0) || (p1[1] >= imageData.height)) return false;  //out of bounds
    var offset = (p1[1] * imageData.width + p1[0]) * 4;
//console.log('(%d,%d) %d [%d]', p1[0], p1[1], imageData.data[offset], skipped);
    if ((imageData.data[offset+1] > 0) && (imageData.data[offset+1] < 255)) return false;  //already visited
    imageData.data[offset+1] = 128;  //mark visited
    imageData.data[offset+2] = 128;

if (itool.dist(p1[0],p1[1],p2[0],p2[1]) < 8) {
console.warn('found a near-end! %d,%d', p1[0], p1[1]);
return [p1];
}

    var thisPt = false;
    if (imageData.data[offset]) {
//console.log('(%d,%d) -> %d [%d]', p1[0], p1[1], imageData.data[offset], skipped);
        thisPt = p1;
    } else if (skipped > 8) {  //too big a gap
        return false;
    } else {
        skipped++; //we are skipping a point, but will keep trying
    }
    var bestScore = false;
    var bPath = false;
    for (var y = -1 ; y < 2 ; y++) {
        for (var x = -1 ; x < 2 ; x++) {
//console.warn('%d,%d', x, y);
            if ((x == 0) && (y == 0)) continue;
            var s = skipped;
            var path = bestPath(imageData, [p1[0] + x, p1[1] + y], p2, s);
            if (!path || !path.length) continue;
            //if (!bestScore || (path.length > best.length)) best = path;
            var d = itool.dist(path[path.length-1][0], path[path.length-1][1], p2[0], p2[1]);
            if (!bestScore || (d < bestScore)) {
                bestScore = d;
                bPath = path;
            }
/*
console.log('(%d,%d) -> (%d,%d) = %.4f', x, y,
path[path.length-1][0], path[path.length-1][1], d);
*/

        }
/*
if (pts.length > 800) {
console.info('bailing');
    console.log(pts);
    return pts;
}
*/
    }

    if (bPath) {
        if (thisPt) bPath.unshift(thisPt);
        return bPath;
    } else {
        return false;
    }
    //return pts;
}


function drawPath(imageData, path, rgb) {
    for (var i = 0 ; i < path.length ; i++) {
        var x = Math.floor(path[i][0]);
        var y = Math.floor(path[i][1]);
        if ((x < 0) || (y < 0) || (x >= imageData.width) || (y >= imageData.height)) continue;
        var offset = (y * imageData.width + x) * 4;
        for (var c = 0 ; c < 3 ; c++) {
            imageData.data[offset + c] = rgb[c];
        }
        imageData.data[offset + 3] = 255;  //dont forget the alpha!
    }
}

function MEHtrace(imageData, p1, p2) {
    var pts = crawl(imageData, p1, p2, 3);
    fctx.putImageData(imageData, 0, 0);
console.log(pts);
}

function crawl(imageData, p1, p2, lvl) {
    var offset = (p1[1] * imageData.width + p1[0]) * 4;
    if ((imageData.data[offset+2] > 0) && (imageData.data[offset+2] < 255)) return false;
    var score = 0;
    if (imageData.data[offset+2] == 255) score = 1;

    if (score) {
        imageData.data[offset] = 255;
        imageData.data[offset+2] = 28;
        //imageData.data[offset+3] = 255;
    } else {
        imageData.data[offset+1] = 255;
        imageData.data[offset+2] = 200;
    }
//console.log('%d: (%d,%d) = %d', lvl, p1[0], p1[1], score);
    if (lvl < 1) return [[score, p1]];

    var outPts = [];
    var r = 10;
    for (var y = p1[1] - 1 ; y < p1[1] + 2 ; y++) {
        for (var x = p1[0] - 1 ; x < p1[0] + 2 ; x++) {
            if (x == y == 2) continue;
            var pts = crawl(imageData, [x, y], p2, lvl - 1);
//console.info('%d: (%d,%d) got %o [%o]', lvl - 1, x, y, pts, score);
            if (!pts) continue;
            for (var i = 0 ; i < pts.length ; i++) {
//console.log(' --- %o', pts);
                pts[i][0] += score;
                pts[i].push(p1);
                outPts = outPts.concat(pts);
            }
        }
    }
//console.warn('outPts -> %o', outPts);
    return outPts;
}


function quickLine(ctx, p1, p2, color, size) {
    size = size | 3;
    ctx.strokeStyle = color;
    ctx.lineWidth = size;
        ctx.beginPath();
        ctx.moveTo(p1[0], p1[1]);
        ctx.lineTo(p2[0], p2[1]);
        ctx.stroke();
}


function findSpotsOnLine(p1, p2, imageData) {
    var found = [];
    var m = (p2[1] - p1[1]) / (p2[0] - p1[0]);
    var b = p1[1] - m * p1[0];
    var step = 7;
console.log('slope = %f, b = %f, step = %d', m, b, step);
//quickLine(fctx, p1, p2, 'blue');
    for (var x = p1[0] + step ; x < p2[0] ; x += step) {
        y = m * x + b;
console.warn('===(%d,%d)===================', x,y);
        var p = checkAround(imageData, x, y, m, 3);
        if (p) found.push(p);
    }
    return found;
}


function checkAround(imageData, x, y, m, lvl) {
//var dot = fctx.createImageData(1,1);
    if (lvl > 0) {
        var ldepth = 4;
        if (lvl > 1) ldepth = 8;
console.log('%d: (%d,%d) -------------', lvl, x, y);
        var m2 = -1 / m;  //TODO handle 1/0
        var b = y - m2 * x;
var x2 = x + ldepth;
var y2 = m2 * x2 + b;
var x3 = x - ldepth;
var y3 = m2 * x3 + b;
//quickLine(fctx, [x2,y2], [x3,y3], 'rgba(0,255,0,0.2)', 1);

        for (var j = 0 ; j < ldepth ; j++) {
            var x2 = x + j;
            var y2 = Math.floor(m2 * x2 + b);
            var p = checkAround(imageData, x2, y2, m2, lvl - 1);
            if (p) return p;
            x2 = x - j;
            y2 = Math.floor(m2 * x2 + b);
            p = checkAround(imageData, x2, y2, m2, lvl - 1);
            if (p) return p;
        }

    } else {
/*
dot.data[0] = 255;
dot.data[1] = 255;
dot.data[2] = 0;
dot.data[3] = 200;
*/

        var offset = (y * imageData.width + x) * 4;
console.log('(%f,%f) [%d] -> %o', x, y, offset, imageData.data[offset]);
//imageData.data[offset+1] = 255;  imageData.data[offset+2] = 255;
//fctx.putImageData(dot, x, y);
        if (imageData.data[offset]) return [x,y];
    }
    return false;
}

/*
function fooooo() {
///console.log(offset);
//console.log(imageData.data[
//console.log('(%f,%f) [%d] -> (%o,%o,%o)', x, y, offset, imageData.data[offset], imageData.data[offset+1], imageData.data[offset+2]);
console.log('(%f,%f)', x, y);

        var m2 = -1/m;
        var b2 = y - m2 * x;
        FINDNEARBY: for (var i = 0 ; i < 15 ; i++) {
            var x2 = x + i;
            var y2 = Math.floor(m2 * x2 + b2);
            //var offset = (y2 * imageData.width + x2) * 4;
//console.log('%d: (%d,%d) [%d] -> %d', i, x2, y2, offset, imageData.data[offset]);
            var b3 = y2 - m * x2;
            for (var j = -4 ; j < 9 ; j++) {
                var x3 = x2 + i;
                var y3 = Math.floor(m * x3 + b3);
                var offset = (y3 * imageData.width + x3) * 4;
console.log('%d: (%d,%d) [%d] -> %d', i, x3, y3, offset, imageData.data[offset]);
                if (imageData.data[offset]) {
                    console.log('----- found!');
                    foundSpots.push([x2, y2]);
                    break FINDNEARBY;
                }
imageData.data[offset+1] = 255;  imageData.data[offset+2] = 255;
            }
//
            if (imageData.data[offset]) {
                console.log('----- found!');
                foundSpots.push([x2, y2]);
                break;
            }
//
            x2 = x - i;
            y2 = Math.floor(m2 * x2 + b2);
            //var offset = (y2 * imageData.width + x2) * 4;
//console.log('-%d: (%d,%d) [%d] -> %d', i, x2, y2, offset, imageData.data[offset]);
            for (var j = 0 ; j < 8 ; j++) {
                var x3 = x2 + i;
                var y3 = Math.floor(m * x3 + b3);
                var offset = (y3 * imageData.width + x3) * 4;
console.log('%d: (%d,%d) [%d] -> %d', i, x3, y3, offset, imageData.data[offset]);
                if (imageData.data[offset]) {
                    console.log('----- found!');
                    foundSpots.push([x2, y2]);
                    break FINDNEARBY;
                }
            }
        }
    }

    for (var i = 0 ; i < spots.length ; i++) {
        drawSpot(ctx2, spots[i], '_hilite');
    }
    for (var i = 0 ; i < foundSpots.length ; i++) {
        drawSpot(ctx2, foundSpots[i], '_dim', 3);
    }

//
    contextSetStyles(ctx2, '_line');
        ctx2.beginPath();
        ctx2.moveTo(spots[0][0], spots[0][1]);
        ctx2.lineTo(spots[1][0], spots[1][1]);
        ctx2.lineTo(spots[2][0], spots[2][1]);
        ctx2.stroke();
//

return ctx2;
}
*/

function edgeDetect(ctx) {
    var w = ctx.canvas.width;
    var h = ctx.canvas.height;
    ctx.drawImage(itool.imageElement, 0, 0, w, h);
    var img_u8 = new jsfeat.matrix_t(w, h, jsfeat.U8C1_t);
    var imageData = ctx.getImageData(0, 0, w, h);

    jsfeat.imgproc.grayscale(imageData.data, w, h, img_u8);

    var blur_radius = 1;
    var kernel_size = (blur_radius + 1) << 1;
    jsfeat.imgproc.gaussian_blur(img_u8, img_u8, kernel_size, 0);

    jsfeat.imgproc.canny(img_u8, img_u8, edgeA, edgeB);
    //kernel_size = (2) << 1;
    //jsfeat.imgproc.gaussian_blur(img_u8, img_u8, kernel_size, 0);

/* 2,20,50
                    var r = options.blur_radius|0;
                    var kernel_size = (r+1) << 1;

                    stat.start("gauss blur");
                    jsfeat.imgproc.gaussian_blur(img_u8, img_u8, kernel_size, 0);

    var data_buffer = idata;
    var matrix = new jsfeat.matrix_t(w, h, jsfeat.U8_t|jsfeat.C1_t, data_buffer);
//////////////
                    var imageData = ctx.getImageData(0, 0, 640, 480);

                    stat.start("grayscale");
                    stat.stop("grayscale");

                    var r = options.blur_radius|0;
                    var kernel_size = (r+1) << 1;

                    stat.start("gauss blur");
                    jsfeat.imgproc.gaussian_blur(img_u8, img_u8, kernel_size, 0);
                    stat.stop("gauss blur");

                    stat.start("canny edge");
                    jsfeat.imgproc.canny(img_u8, img_u8, options.low_threshold|0, options.high_threshold|0);
                    stat.stop("canny edge");

                    
///////////////
*/
                    // render result back to canvas
                    var data_u32 = new Uint32Array(imageData.data.buffer);
                    var alpha = (0xff << 24);
                    var i = img_u8.cols*img_u8.rows, pix = 0;
                    while(--i >= 0) {
                        pix = img_u8.data[i];
                        data_u32[i] = alpha | (pix << 16) | (pix << 8) | pix;
                    }
                    ctx.putImageData(imageData, 0, 0);
                    return ctx;
}

