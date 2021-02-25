/*
    ugly port to "general usage"... sorry it has hard-coded element ids and classes  :(
*/

//these must be overriden by page calling spotInit() first
var localLocationIds = [];
var subdirPrefix = null;
var rightSide = null;

var localOnly = [];  //pointers into jsonData
var usingLocal = true;
var xmlData = null;
var jsonData = [];
var currentPair = 0;
var pageOffset = 0;

function spotInit(xmlFile) {
console.log('====> %o', xmlFile);
    $.ajax({
        url: xmlFile,
        dataType: 'xml',
        type: 'GET',
        complete: function(xhr) {
            console.info(xhr);
            if (!xhr || (xhr.status != 200) || !xhr.responseXML) {
                var errorMsg = 'Unknown error';
                if (xhr) errorMsg = xhr.status + ' - ' + xhr.statusText
                $('#spot-display').html('<h1 class="error">Unable to fetch data: ' + errorMsg + '</h1>');
            } else {
                xmlData = $(xhr.responseXML);
                spotDisplayInit(xmlData);
            }
        }
    });
}

function overlaps(arr1, arr2) {
    // h/t https://medium.com/@alvaro.saburido/set-theory-for-arrays-in-es6-eb2f20a61848
    return arr1.filter(x => arr2.includes(x)).length;
}

function hashDir(dir) {
    if (dir.length != 36) return dir;
    return dir.substr(0,1) + '/' + dir.substr(1,1) + '/' + dir;
}

function spotDisplayInit(xml) {
    var joff = 0;
    xmlData.find('match').each(function(i, el) {
        var m = xmlAttributesToJson(el);
        m.encounters = [];
        for (var j = 0 ; j < el.children.length ; j++) {
            var e = xmlAttributesToJson(el.children[j]);
            e.imgUrl = subdirPrefix + '/' + hashDir(e.number) + '/extract' + (rightSide ? 'Right' : '') + e.number + '.jpg';
            e.spots = [];
            for (var i = 0 ; i < el.children[j].children.length ; i++) {
                e.spots.push(xmlAttributesToJson(el.children[j].children[i]));
            }
            if (j == 0) {
                m.isLocal = localLocationIds.includes(e.locationID);
                e.isLocal = m.isLocal;
                if (m.isLocal) localOnly.push(joff);
            }
            m.encounters.push(e);
        }
        jsonData.push(m);
        joff++;
    });
    if (localOnly.length) {
        toggleLocalMode(true);
    } else {
        toggleLocalMode(false);
        $('#mode-message').html('Showing all matches. (None match locally.)');
        $('#mode-button-local').hide();
    }
}

function xmlAttributesToJson(el) {
    var j = {};
    for (var i = 0 ; i < el.attributes.length ; i++) {
        j[el.attributes[i].name] = el.attributes[i].value;
    }
    return j;
}

function spotDisplayPair(mnum) {
    if (!jsonData[mnum] || !jsonData[mnum].encounters || (jsonData[mnum].encounters.length != 2)) return;
    var max = (usingLocal ? localOnly.length : jsonData.length);
    currentPair = mnum;
    setPageOffsetFromCurrentPair();
console.log('currentPair=%d, pageOffset=%d (usingLocal = %o)', currentPair, pageOffset, usingLocal);
    $('.table-row-highlight').removeClass('table-row-highlight');
    $('#table-row-' + mnum).addClass('table-row-highlight');
    for (var i = 0 ; i < 2 ; i++) {
        spotDisplaySide(1 - i, jsonData[mnum].encounters[i]);
    }
    if (pageOffset < 1) {
        $('#match-button-prev').hide();
    } else {
        $('#match-button-prev').show();
    }
    if (pageOffset >= max - 1) {
        $('#match-button-next').hide();
    } else {
        $('#match-button-next').show();
    }
    $('#match-info').html('Match score: <b>' + jsonData[mnum].finalscore + '</b> (Match ' + (pageOffset+1) + ' of ' + max + ')');
}

function setPageOffsetFromCurrentPair() {
    if (!usingLocal) {
        pageOffset = currentPair;
    } else {
        pageOffset = 0;
        for (var i = 0 ; i < localOnly.length ; i++) {
            if (localOnly[i] == currentPair) {
                pageOffset = i;
                return;
            }
        }
    }
}

var attrOrder = ['number', 'date', 'sex', 'assignedToShark', 'size', 'location', 'locationID'];
var attrLabel = {
    number: 'Enc ID',
    date: 'Date', 
    sex: 'Sex',
    assignedToShark: 'Assigned to',
    size: 'Size',
    location: 'Location',
    locationID: 'Location ID'
};
function spotDisplaySide(side, data) {
console.log('spotDisplaySide ==> %i %o', side, data);
    $('#match-side-' + side + ' img').prop('src', data.imgUrl);
    var h = '<div class="match-side-attributes">';
    $('#match-side-' + side + ' img').load(function() {
        fitRightImage();
    });
    for (var i = 0 ; i < attrOrder.length ; i++) {
        var label = attrLabel[attrOrder[i]] || attrOrder[i];
        var value = data[attrOrder[i]];
        if (i == 0) value = '<a target="_new" href="encounter.jsp?number=' + value + '">' + value + '</a>';
        h += '<div><div class="match-side-attribute-label">' + label + '</div>';
        h += '<div class="match-side-attribute-value">' + value + '&nbsp;</div></div>';
    }
    h += '</div>';
    $('#match-side-' + side + ' .match-side-info').html(h);
}

function spotDisplayButton(delta) {
    pageOffset += delta;
    var max = (usingLocal ? localOnly.length : jsonData.length);
    if (pageOffset < 0) pageOffset = max;
    if (pageOffset > (max - 1)) pageOffset = 0;
    if (usingLocal) {
        currentPair = localOnly[pageOffset];
    } else {
        currentPair = pageOffset;
    }
    spotDisplayPair(currentPair);
}

function matchImgDone(img, encI) {
console.log('done %o %o', img, encI);
    var ratio = img.clientWidth / img.naturalWidth;
    var wrapper = $(img).parent();
    wrapper.find('.match-side-spot').remove();
    for (var i = 0 ; i < jsonData[currentPair].encounters[encI].spots.length ; i++) {
        var sp = $('<div title="spot ' + (i+1) + '" id="spot-' + encI + '-' + i + '" class="match-side-spot match-side-spot-' + i + '" />');
        sp.css({
            left: (jsonData[currentPair].encounters[encI].spots[i].x * ratio - 3) + 'px',
            top: (jsonData[currentPair].encounters[encI].spots[i].y * ratio - 3) + 'px'
        });
        sp.on('mouseenter', function(ev) {
            ev.target.classList.forEach(function(cl) {
                if (cl.startsWith('match-side-spot-')) $('.' + cl).addClass('match-spot-highlight');
            });
        })
        .on('mouseout', function(ev) {
            $('.match-spot-highlight').removeClass('match-spot-highlight');
        });
        wrapper.append(sp);
    }
}

function toggleLocalMode(mode) {
    if (mode && !localOnly.length) return;
    usingLocal = mode;
    if (mode) {
        $('#mode-message').html('Showing only matches with locations in: <b>' + localLocationIds.join(', ') + '</b>');
        $('.tr-location-nonlocal').hide();
        $('#mode-button-local').hide();
        $('#mode-button-all').show();
        spotDisplayPair(localOnly[0]);
    } else {
        $('.tr-location-nonlocal').show();
        $('#mode-message').html('Showing all matches');
        $('#mode-button-local').show();
        $('#mode-button-all').hide();
        spotDisplayPair(0);
    }
}
