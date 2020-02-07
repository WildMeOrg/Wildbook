<%@ page contentType="text/html; charset=utf-8" language="java" import="org.ecocean.servlet.ServletUtilities,java.util.ArrayList,java.util.List,java.util.Iterator,java.util.Properties,
org.ecocean.media.MediaAsset,
org.json.JSONObject, org.json.JSONArray,
javax.jdo.Query,
java.io.FileInputStream, java.io.File, java.io.FileNotFoundException, org.ecocean.*, org.apache.commons.lang3.StringEscapeUtils" %>
<%!

private static void addClause(List<String> props, String colName, String propVal) {
    if ((propVal == null) || propVal.equals("unknown")) return;
    props.add(
        "\"" + colName + "\" = '" + Util.sanitizeUserInput(propVal) + "' OR " +
        "\"" + colName + "\" = 'unknown' OR " +
        "\"" + colName + "\" IS NULL"
    );
}

private static JSONArray findSimilar(HttpServletRequest request, Shepherd myShepherd, Encounter enc, User user, JSONObject userData) {
    if ((enc == null) || (user == null) || (userData == null)) return null;
    Double lat = enc.getDecimalLatitudeAsDouble();
    Double lon = enc.getDecimalLongitudeAsDouble();
    if ((lat == null) || (lon == null)) {
        System.out.println("WARNING: findSimilar() has no lat/lon for " + enc);
        return null;
    }

    List<String> props = new ArrayList<String>();
    addClause(props, "PATTERNINGCODE", userData.optString("colorPattern", null));
    addClause(props, "EARTIP", userData.optString("earTip", null));
    addClause(props, "SEX", userData.optString("sex", null));
    addClause(props, "COLLAR", userData.optString("collar", null));
    addClause(props, "LIFESTAGE", userData.optString("lifeStage", null));
    if (props.size() < 1) {
        System.out.println("WARNING: findSimilar() has no props sql from userData " + userData.toString());
        return null;
    }
    
    //technically we dont need to exclude our enc, as we are not 'approved', but meh.
    String sql = "SELECT \"CATALOGNUMBER\" AS encId, ST_Distance(toMercatorGeometry(\"DECIMALLATITUDE\", \"DECIMALLONGITUDE\"),toMercatorGeometry(" + lat + ", " + lon + ")) AS dist, \"PATTERNINGCODE\", \"EARTIP\", \"SEX\", \"COLLAR\", \"LIFESTAGE\" FROM \"ENCOUNTER\" WHERE validLatLon(\"DECIMALLATITUDE\", \"DECIMALLONGITUDE\") AND \"CATALOGNUMBER\" != '" + enc.getCatalogNumber() + "' AND \"STATE\" = 'processing' AND ((" + String.join(") OR (", props) + ")) ORDER BY dist";
System.out.println("findSimilar() userData " + userData.toString() + " --> SQL: " + sql);

    JSONArray found = new JSONArray();
    Query q = myShepherd.getPM().newQuery("javax.jdo.query.SQL", sql);
    List results = (List)q.execute();
    Iterator it = results.iterator();
    String[] propMap = new String[]{"colorPattern", "earTip", "sex", "collar", "lifeStage"};
    while (it.hasNext()) {
        JSONObject el = new JSONObject();
        Object[] row = (Object[]) it.next();
        String encId = (String)row[0];
        Double dist = (Double)row[1];
        if (dist > 3000) continue;  //sanity perimeter
        Encounter menc = myShepherd.getEncounter(encId);
        if (menc == null) continue;
        JSONObject propMatches = new JSONObject();
        el.put("encounterId", encId);
        if (menc.getIndividual() != null) {
            el.put("individualId", menc.getIndividual().getId());
            //el.put("name", menc.getIndividual().getDisplayName());
            el.put("matchPhoto", getMatchPhoto(request, myShepherd, menc.getIndividual()));
        }
        el.put("encounterEventId", menc.getEventID());
        el.put("distance", dist);
System.out.println("findSimilar() -> " + el.toString());
        for (int i = 0 ; i < propMap.length ; i++) {
            String val = (String)row[i + 2];
            String ud = userData.optString(propMap[i], null);
            el.put(propMap[i], val);
            propMatches.put(propMap[i], (val != null) && (ud != null) && ud.equals(val));
        }
        el.put("matches", propMatches);
        JSONArray mas = new JSONArray();
        if (!Util.collectionIsEmptyOrNull(menc.getAnnotations())) for (Annotation ann : menc.getAnnotations()) {
            MediaAsset ma = ann.getMediaAsset();
            if (ma == null) continue;
            JSONObject mj = new JSONObject();
            mj.put("annotationId", ann.getId());
            if (!ann.isTrivial()) mj.put("bbox", ann.getBbox());
            mj.put("id", ma.getId());
            mj.put("url", ma.safeURL(myShepherd, request));
            mas.put(mj);
        }
        el.put("assets", mas);
        found.put(el);
    }
    return found;
}

private static JSONObject getMatchPhoto(HttpServletRequest request, Shepherd myShepherd, MarkedIndividual indiv) {
    if (indiv == null) return null;
    Annotation backup = null;
    Annotation found = null;
    foundOne: for (Encounter enc : indiv.getEncounters()) {
        if (Util.collectionIsEmptyOrNull(enc.getAnnotations())) continue;
        for (Annotation ann : enc.getAnnotations()) {
            MediaAsset ma = ann.getMediaAsset();
            if (ma == null) continue;
            if (backup == null) backup = ann;
            if (ma.hasKeyword("MatchPhoto")) {
                found = ann;
                break foundOne;
            }
        }
    }
System.out.println("getMatchPhoto(" + indiv + ") -> found = " + found);
System.out.println("getMatchPhoto(" + indiv + ") -> backup = " + backup);
    if ((backup == null) && (found == null)) return null;
    if (found == null) found = backup;
    MediaAsset ma = found.getMediaAsset();
    JSONObject rtn = new JSONObject();
    rtn.put("annotationId", found.getId());
    rtn.put("id", ma.getId());
    if (!found.isTrivial()) rtn.put("bbox", found.getBbox());
    rtn.put("url", ma.safeURL(myShepherd, request));
    return rtn;
}


%>
<%

//setup our Properties object to hold all properties
	Properties props=new Properties();
	String langCode=ServletUtilities.getLanguageCode(request);
	String context = ServletUtilities.getContext(request);
        request.setAttribute("pageTitle", "Kitizen Science &gt; Submission Input");
        Shepherd myShepherd = new Shepherd(context);
        myShepherd.setAction("encounterDecide.jsp");
        String encId = request.getParameter("id");
        Encounter enc = myShepherd.getEncounter(encId);
        if (enc == null) {
            response.setStatus(404);
            out.println("404 not found");
            return;
        }
/*
        if (!"new".equals(enc.getState())) {   //TODO other privilege checks here
            response.setStatus(401);
            out.println("401 access denied");
            return;
        }
*/
        JSONObject similarUserData = Util.stringToJSONObject(request.getParameter("getSimilar"));
        if (similarUserData != null) {
            User user = AccessControl.getUser(request, myShepherd);
            JSONObject rtn = new JSONObject();
            rtn.put("encounterId", enc.getCatalogNumber());
            rtn.put("userData", similarUserData);
            if (user != null) rtn.put("userId", user.getUUID());
            JSONArray found = findSimilar(request, myShepherd, enc, user, similarUserData);
            JSONArray sim = found;
            if (found == null) {
                rtn.put("success", false);
            } else {
                rtn.put("success", true);
                rtn.put("similar", sim);
            }
            response.setHeader("Content-type", "application/javascript");
            out.println(rtn.toString());
            return;
        }
%>

<jsp:include page="../header.jsp" flush="true" />
<script src="../tools/panzoom/jquery.panzoom.min.js"></script>

<style type="text/css">
h1 { background: none !important; }

.attribute-info {
    font-size: 0.9em;
    line-height: 1.3em;
}

.attribute {
    text-align: center;
    background-color: #EFEFEF;
    padding: 7px;
    margin: 0 0 20px 0;
}
.attribute h2 {
    padding: 0;
    margin: 0;
}


.column-images, .column-attributes, .column-match {
    display: inline-block;
    width: 47%;
    vertical-align: top;
}

.column-attributes, .column-match {
    float: right;
}

.column-match {
    display: none;
}

@media screen and (max-width: 800px) {
    .column-images, .column-attributes {
        width: 100%;
    }
    .column-attributes {
        float: none;
    }
}

.attribute-option {
    display: inline-block;
    padding: 3px 8px;
    background-color: #DDD;
    margin: 7px 2px;
    cursor: pointer;
    width: 100%;
}
.attribute-option:hover {
    background-color: #bff223;
}
.attribute-selected {
    background-color: #bff223 !important;
}
.attribute-muted {
    opacity: 0.4;
}
.attribute-muted:hover {
    opacity: 1.0;
}

#colorPattern .attribute-option {
    width: 22%;
    margin-bottom: 13px;
}
#earTip .attribute-option, #lifeStage .attribute-option {
    width: 47%;
}
#collar .attribute-option, #sex .attribute-option {
    width: 31%;
}

.attribute-option .attribute-title {
    overflow: hidden;
    font-size: 0.8em;
    line-height: 1.2em;
    height: 2.5em;
    font-weight: bold;
}

.attribute-unknown {
    font-size: 4em;
    color: #444;
    text-align: center;
}

#flag .input-wrapper {
    display: block;
    text-align: left;
    padding-left: 15px;
    margin-bottom: 5px;
}
#flag label {
    cursor: pointer;
    margin: 0 0 0 8px;
    width: 90%;
    font-weight: bold;
    line-height: 1.1em;
}
#flag .input-wrapper:hover {
    background-color: #bff223;
}
#flag .input-wrapper input {
    vertical-align: top;
}

.option-checkbox {
    box-shadow: none;
    margin: 7px !important;
}

.flag-note {
    display: block;
    font-weight: normal;
    font-size: 0.8em;
}

#save-complete {
    display: none;
}

.button-disabled {
    cursor: not-allowed !important;
    background-color: #DDD !important;
}

.match-summary-detail {
    white-space: nowrap;
    font-size: 0.8em;
    border-radius: 3px;
    background-color: #CCC;
    padding: 0 5px;
    margin: 0 4px;
}

.match-item {
    padding: 50px 10px 10px 10px;
    border-top: 3px black solid;
    position: relative;
}
.match-item:hover {
    background-color: #bff223;
}

.match-name {
    position: absolute;
    top: 0;
    left: 0;
    padding: 0 8px;
    font-size: 1.3em;
}

.match-name a:hover {
    color: #000;
}

.match-item-info {
    display: none;
    background-color: rgba(255,255,200,0.7);
    padding: 0 8px;
    position: absolute;
    left: 0;
    top: 0;
}
.match-asset-wrapper {
    position: relative;
    xoverflow: hidden;
    xwidth: 500px;
    xheight: 500px;
    margin-bottom: 5px;
}

.match-asset-img-wrapper {
    width: 500px;
    height: 400px;
    background-color: #AAA;
    position: relative;
    overflow:hidden;
}

.match-asset-img {
    position: absolute;
    //transform-origin: 0 0 !important;
    max-width: none !important;
    display: none;
}
.zoom-hint {
    position: absolute;
    top: 10px;
    right: 10px;
    display: inline-block;
    z-index: 100;
    background-color: rgba(255,255,255,0.4);
    border-radius: 10px;
    padding: 10px;
    pointer-events: none;
}
.zoom-hint .el-zoom-in {
    pointer-events: none;
    margin-bottom: 15px;
    cursor: zoom-in;
}
.zoom-hint .el-zoom-out {
    pointer-events: visible;
    cursor: zoom-out;
}

.zoom-hint .el {
    display: block;
}

.enc-asset-wrapper {
    margin-bottom: 5px;
}

.match-asset {
    position: absolute;
}
.match-choose {
    position: absolute;
    top: 0;
    right: 0;
    padding: 2px 8px;
    background-color: #9dc327;
    border-radius: 5px;
}
.match-choose:hover {
    background-color: #8db317;
}
.match-choose label {
    font-weight: bold;
    cursor: pointer;
}
#match-controls {
    margin: -10px;
    border: solid 1px black;
    padding: 10px;
    background: #CCC;
    position: fixed;
    bottom: 10px;
    border-radius: 10px;
    width: 15%;
    right: 5%;
    z-index: 100;
}
</style>

<script type="text/javascript">
var dataLocked = false;
var encounterId = '<%=enc.getCatalogNumber()%>';
var userData = {
    colorPattern: false,
    earTip: false,
    lifeStage: false,
    collar: false,
    sex: false
};
$(document).ready(function() {
    utickState.encounterDecide = { initTime: new Date().getTime(), clicks: [] };
    $('.attribute-option').on('click', function(ev) { clickAttributeOption(ev); });
    $('.attribute-option').append('<input type="radio" class="option-checkbox" />');
    $('#flag input').on('change', function() { updateData(); });
    $('.enc-asset').panzoom({maxScale:9}).on('panzoomend', function(ev, panzoom, matrix, changed) {
        if (!changed) return $(ev.currentTarget).panzoom('zoom');
    });
/*
    $('.enc-asset').panzoom().on('panzoomchange', function(ev, panzoom, matrix, changed) {
        $('body').css('overflow', 'auto');
        //$('html').css('overflow', 'auto');
    });
    $('.enc-asset').panzoom().on('panzoomstart', function(ev, panzoom, matrix, changed) {
        $('body').css('overflow', 'hidden');
        //$('html').css('overflow', 'hidden');
    });
*/

/*
    $('.enc-asset').on('dblclick', function(ev) {
        $(ev.currentTarget).panzoom('reset');
    });
*/

});

function zoomOut(el, imgWrapperClass) {
    event.stopPropagation();
    var iEl = $(el).closest(imgWrapperClass).find('img');
    iEl.attr('style', null).show().css('width', '100%');
    iEl.panzoom('reset');
    //$(el).closest(imgWrapperClass).find('img').css('transform-origin', '50% 50% !important').panzoom('reset');
}

function clickAttributeOption(ev) {
    if (dataLocked) return;
    console.log(ev);
    $('#' + ev.currentTarget.parentElement.id + ' .attribute-selected').removeClass('attribute-selected');
    $('#' + ev.currentTarget.parentElement.id + ' .attribute-option').addClass('attribute-muted');
    $('#' + ev.currentTarget.parentElement.id + ' .option-checkbox').prop('checked', false);
    ev.currentTarget.classList.add('attribute-selected');
    $('#' + ev.currentTarget.parentElement.id + ' .attribute-selected .option-checkbox').prop('checked', true);
    ev.currentTarget.classList.remove('attribute-muted');
/*
    utickState.encounterDecide.clicks.push({
        t: new Date().getTime(),
        prop: ev.currentTarget.parentElement.id,
        val: ev.currentTarget.id
    });
*/
    userData[ev.currentTarget.parentElement.id] = ev.currentTarget.id;
    checkSaveStatus();
}

function updateData() {
    delete(userData.flag);
    $('#flag input:checked').each(function(i, el) {
        if (!userData.flag) userData.flag = [];
        userData.flag.push(el.id);
    });
}

function checkSaveStatus() {
    console.log(userData);
    var complete = true;
    for (var attr in userData) {
        complete = complete && userData[attr];
    }
    if (complete) {
        $('#save-incomplete').hide();
        $('#save-complete').show();
        $('#save-button').removeClass('button-disabled').attr('title', 'Save your answers').removeAttr('disabled');
    }
}

function doSave() {
    $('#save-div').hide();
    utickState.encounterDecide.attrSaveTime = new Date().getTime();
    var mdata = {};
    for (var k in userData) {
        mdata[k] = { value: userData[k] };
    }
    $.ajax({
        url: '../DecisionStore',
        data: JSON.stringify({ encounterId: encounterId, multiple: mdata }),
        dataType: 'json',
        complete: function(xhr) {
            console.log(xhr);
            if (!xhr || !xhr.responseJSON || !xhr.responseJSON.success) {
                console.warn("responseJSON => %o", xhr.responseJSON);
                alert('ERROR saving: ' + ((xhr && xhr.responseJSON && xhr.responseJSON.error) || 'Unknown problem'));
            } else {
                utickState.encounterDecide.attrSavedTime = new Date().getTime();
                dataLocked = true;
                enableMatch();
            }
        },
        contentType: 'application/javascript',
        type: 'POST'
    });
}

var matchData = null;
var attributeReadable = {
    colorPattern: 'color/pattern',
    earTip: 'ear tip',
    lifeStage: 'life stage'
};
function enableMatch() {
    $('.column-attributes').hide();
    $('.column-match').show();
    $('#subtitle').text('Step 2');
    window.scrollTo(0,0);
    var h = '';
    for (var i in userData) {
        h += '<span class="match-summary-detail">' + (attributeReadable[i] || i) + ': <b>' + userData[i] + '</b></span>';
    }
    $('#match-summary').html(h);
    var url = 'encounterDecide.jsp?id=' + encounterId + '&getSimilar=' + encodeURI(JSON.stringify(userData));
console.log(url);
    $.ajax({
        url: url,
        complete: function(xhr) {
            console.log(xhr);
            if (!xhr || !xhr.responseJSON || !xhr.responseJSON.success) {
                console.warn("responseJSON => %o", xhr.responseJSON);
                alert('ERROR searching: ' + ((xhr && xhr.responseJSON && xhr.responseJSON.error) || 'Unknown problem'));
            } else {
                if (!xhr.responseJSON.similar || !xhr.responseJSON.similar.length) {
                    $('#match-results').html('<b>No matches found</b>');
                } else {
                    matchData = xhr.responseJSON;
                    matchData.assetData = {};
                    matchData.userPresented = {};
                    var sort = {};
                    var seen = {};
                    for (var i = 0 ; i < xhr.responseJSON.similar.length ; i++) {
                        if (!xhr.responseJSON.similar[i].individualId) continue;
                        if (seen[xhr.responseJSON.similar[i].individualId]) continue;
                        var score = matchScore(xhr.responseJSON.similar[i], userData);
                        matchData.userPresented[xhr.responseJSON.similar[i].encounterId] = score;
                        if (score < 0) continue;
                        seen[xhr.responseJSON.similar[i].individualId] = true;
                        var h = '<div class="match-item">';
                        h += '<div class="match-name"><a title="More images of this cat" target="_new" href="../individualGallery.jsp?id=' + xhr.responseJSON.similar[i].individualId + '&subject=' + encounterId + '" title="Enc ' + xhr.responseJSON.similar[i].encounterId + '">More photos ' + xhr.responseJSON.similar[i].individualId.substr(0,8) + '</a></div>';
                        //h += '<div class="match-name">' + (xhr.responseJSON.similar[i].name || xhr.responseJSON.similar[i].encounterId.substr(0,8)) + '</div>';
                        h += '<div class="match-choose"><input id="mc-' + i + '" class="match-chosen-cat" type="radio" value="' + xhr.responseJSON.similar[i].encounterId + '" /> <label for="mc-' + i + '">matches this cat</label></div>';
/*
                        var numImages = xhr.responseJSON.similar[i].assets.length;
                        if (numImages > 2) numImages = 2;
                        for (var j = 0 ; j < numImages ; j++) {
                            h += '<div class="match-asset-wrapper">';
                            h += '<div class="zoom-hint" xstyle="transform: scale(0.75);"><span class="el el-lg el-zoom-in"></span><span onClick="return zoomOut(this, \'.match-asset-wrapper\')" class="el el-lg el-zoom-out"></span></div>';
                            h += '<div class="match-asset-img-wrapper"><img onLoad="matchAssetLoaded(this);" class="match-asset-img" id="match-asset-' + xhr.responseJSON.similar[i].assets[j].id + '" src="' + xhr.responseJSON.similar[i].assets[j].url + '" /></div></div>';
                            matchData.assetData[xhr.responseJSON.similar[i].assets[j].id] = xhr.responseJSON.similar[i].assets[j];
                        }
*/
                        h += '<div class="match-asset-wrapper">';
                        h += '<div class="zoom-hint" xstyle="transform: scale(0.75);"><span class="el el-lg el-zoom-in"></span><span onClick="return zoomOut(this, \'.match-asset-wrapper\')" class="el el-lg el-zoom-out"></span></div>';
                        h += '<div class="match-asset-img-wrapper"><img onLoad="matchAssetLoaded(this);" class="match-asset-img" id="match-asset-' + xhr.responseJSON.similar[i].matchPhoto.id + '" src="' + xhr.responseJSON.similar[i].matchPhoto.url + '" /></div></div>';
                        matchData.assetData[xhr.responseJSON.similar[i].matchPhoto.id] = xhr.responseJSON.similar[i].matchPhoto;

                        h += '<div class="match-item-info">';
                        h += '<div>' + xhr.responseJSON.similar[i].encounterId.substr(0,8) + '</div>';
                        h += '<div><b>' + (Math.round(xhr.responseJSON.similar[i].distance / 100) * 100) + 'm</b></div>';
                        h += '<div>score: <b>' + score + '</b></div>';
                        if (xhr.responseJSON.similar[i].sex) h += '<div>sex: <b>' + xhr.responseJSON.similar[i].sex + '</b></div>';
                        if (xhr.responseJSON.similar[i].colorPattern) h += '<div>color: <b>' + xhr.responseJSON.similar[i].colorPattern + '</b></div>';
                        h += '</div></div>';
                        if (!sort[score]) sort[score] = '';
                        sort[score] += h;
                    }
                    var keys = Object.keys(sort).sort(function(a,b) {return a-b;}).reverse();
                    $('#match-results').html('');
                    for (var i = 0 ; i < keys.length ; i++) {
                        $('#match-results').append(sort[keys[i]]);
                    }
                    $('#match-results').append('<div id="match-controls"><div><input type="checkbox" class="match-chosen-cat" value="no-match" id="mc-none" /> <label for="mc-none">None of these cats match</label></div><input type="button" id="match-chosen-button" value="Save match choice" disabled class="button-disabled" onClick="saveMatchChoice();" /></div>');
                    $('.match-chosen-cat').on('click', function(ev) {
                        var id = ev.target.id;
console.log(id);
                        $('.match-chosen-cat').prop('checked', false);
                        $('#' + id).prop('checked', true);
                        $('#match-chosen-button').removeClass('button-disabled').removeAttr('disabled');
                    });
                }
            }
        },
        dataType: 'json',
        type: 'GET'
    });
}

function saveMatchChoice() {
    var ch = $('.match-chosen-cat:checked').val();
    if (!ch) return;
    console.log('saving %s', ch);
    $('#match-chosen-button').hide();
    $.ajax({
        url: '../DecisionStore',
        data: JSON.stringify({ encounterId: encounterId, property: 'match', value: { id: ch, presented: matchData.userPresented, initTime: utickState.encounterDecide.initTime, attrSaveTime: utickState.encounterDecide.attrSaveTime, matchSaveTime: new Date().getTime() } }),
        dataType: 'json',
        complete: function(xhr) {
            console.log(xhr);
            if (!xhr || !xhr.responseJSON || !xhr.responseJSON.success) {
                console.warn("responseJSON => %o", xhr.responseJSON);
                alert('ERROR saving: ' + ((xhr && xhr.responseJSON && xhr.responseJSON.error) || 'Unknown problem'));
            } else {
                window.location.href = '../queue.jsp';
            }
        },
        contentType: 'application/javascript',
        type: 'POST'
    });
}

//negative score will NOT be shown to user at all
function matchScore(mdata, udata) {
    //if (udata.colorPattern != mdata.colorPattern) return -1;  //dealbreaker
    var score = 1;
    if (mdata.matches.earTip) score += 0.5;
    if (mdata.matches.collar) score += 1.1;
    if (mdata.matches.lifeStage) score += 0.7;
    if (mdata.matches.colorPattern) score += 1.5;
    if (mdata.matches.sex) score += 1.2;
    if (mdata.distance) score += (10 / mdata.distance);
    return Math.round(score * 100) / 100;
}

function matchAssetLoaded(el) {
    $(el).panzoom({maxScale:9}).on('panzoomend', function(ev, panzoom, matrix, changed) {
        if (!changed) return $(ev.currentTarget).panzoom('zoom');
    });
    var id = el.id.substr(12);
    toggleZoom(id);
}

function toggleZoom(id) {
console.log('asset id=%o', id);
    var padding = 400;
    var imgEl = $('#match-asset-' + id);
    if (!imgEl.length) return;
    if (!matchData || !matchData.assetData || !matchData.assetData[id] || !matchData.assetData[id].bbox) {
        imgEl.css('width', '100%');
        imgEl.show();
        return;
    }
    var wrapper = imgEl.parent();
    var iw = imgEl[0].naturalWidth;
    var ih = imgEl[0].naturalHeight;
    var ww = wrapper.width();
    var wh = wrapper.height();
    var ratio = ww / (matchData.assetData[id].bbox[2] + padding);
    if ((wh / (matchData.assetData[id].bbox[3] + padding)) < ratio) ratio = wh / (matchData.assetData[id].bbox[3] + padding);
console.log('img=%dx%d / wrapper=%dx%d / box=%dx%d', iw, ih, ww, wh, matchData.assetData[id].bbox[2], matchData.assetData[id].bbox[3]);
console.log('%.f', ratio);
	var dx = (ww / 2) - ((matchData.assetData[id].bbox[2] + padding) * ratio / 2);
	var dy = (wh / 2) - ((matchData.assetData[id].bbox[3] + padding) * ratio / 2);
console.log('dx, dy %f, %f', dx, dy);
	var css = {
                transformOrigin: '0 0',
		transform: 'scale(' + ratio + ')',
		left: (dx - ratio * matchData.assetData[id].bbox[0] + padding/2*ratio) + 'px',
		top: (dy - ratio * matchData.assetData[id].bbox[1] + padding/2*ratio) + 'px'
	};
console.log('css = %o', css);
	imgEl.css(css);
/*
        imgEl.on('click', function(ev) {
console.log('CLICK IMG %o', ev);
            ev.target.style.transformOrigin = '50% 50%';
            ev.target.style.width = '100%';
        });
*/
	imgEl.show();
}

</script>

</head>
<body>

<div class="container maincontent">
<h1>Submission <%=((enc.getEventID() == null) ? enc.getCatalogNumber().substring(0,8) : enc.getEventID())%>: <span id="subtitle">Step 1</span></h1>

<%= NoteField.buildHtmlDiv("59b4eb8f-b77f-4259-b939-5b7c38d4504c", request, myShepherd) %>
<div class="org-ecocean-notefield-default" id="default-59b4eb8f-b77f-4259-b939-5b7c38d4504c">
<p>
There are two steps to processing each submission: selecting cat attributes, and then looking to see if the cat has a match in the database.
</p>
</div>

<b id="width-info"></b>
<div>
    <div class="column-images">
<%
    ArrayList<MediaAsset> assets = enc.getMedia();
    if (!Util.collectionIsEmptyOrNull(assets)) for (MediaAsset ma : assets) {
%>
        <div class="enc-asset-wrapper"><div class="zoom-hint"><span class="el el-lg el-zoom-in"></span><span onClick="return zoomOut(this, '.enc-asset-wrapper')" class="el el-lg el-zoom-out"></span></div><img class="enc-asset" src="<%=ma.safeURL(request)%>" /></div>
<%
    }
%>
    </div>

    <div class="column-attributes">

        <div class="attribute" style="display: none;">
            <h2>Flag Problems</h2>
            <div id="flag">
                <div class="input-wrapper"><input type="checkbox" name="flag" id="flag-detection" /><label for="flag-detection">Some photos missing (this) cat<span class="flag-note">A submission may include some photos that do not have a cat in them, or multi-cat submissions that are split into multiple submissions to process may some have photos without the focal cat.  Do any photos not contain the focal cat?</span></label></div>
                <div class="input-wrapper"><input type="checkbox" name="flag" id="flag-sensitive" /><label for="flag-sensitive">Sensitive or private information<span class="flag-note">To respect the privacy of those in our communities, our system should detect and automatically blur human faces, street signs, house numbers, license plates, and company logos. Do the photos in this submission contain any unblurred private information?</span></label></div>
                <div class="input-wrapper"><input type="checkbox" name="flag" id="flag-quality" /><label for="flag-quality">Very poor quality<span class="flag-note">Low image quality, blurring, resolution, or other technical problems prevent photos from being useful.</span></label></div>
            </div>
        </div>

        <div class="attribute">
            <h2>Primary Color / Coat Pattern</h2>
            <div id="colorPattern" class="attribute-select">
                <div id="black" class="attribute-option">
                    <img class="attribute-image" src="../images/instructions_black.jpg" />
                    <div class="attribute-title">Black</div>
                </div>
                <div id="bw" class="attribute-option">
                    <img class="attribute-image" src="../images/instructions_bw.jpg" />
                    <div class="attribute-title">Black &amp; White</div>
                </div>
                <div id="tabby_torbie" class="attribute-option">
                    <img class="attribute-image" src="../images/instructions_tabby.jpg" />
                    <div class="attribute-title">Grey or Brown Tabby/Torbie</div>
                </div>
                <div id="tab_torb_white" class="attribute-option">
                    <img class="attribute-image" src="../images/instructions_tabwhite.jpg" />
                    <div class="attribute-title">Tabby/Torbie &amp; White</div>
                </div>
                <div id="orange" class="attribute-option">
                    <img class="attribute-image" src="../images/instructions_orange.jpg" />
                    <div class="attribute-title">Orange</div>
                </div>
                <div id="grey" class="attribute-option">
                    <img class="attribute-image" src="../images/instructions_grey.jpg" />
                    <div class="attribute-title">Dark Grey</div>
                </div>
                <div id="calico_torto" class="attribute-option">
                    <img class="attribute-image" src="../images/instructions_tortical.jpg" />
                    <div class="attribute-title">Calico / Tortoiseshell</div>
                </div>
                <div id="beige_cream_wh" class="attribute-option">
                    <img class="attribute-image" src="../images/instructions_light.jpg" />
                    <div class="attribute-title">Beige / Cream / White</div>
                </div>
            </div>
        </div>

        <div class="attribute">
            <h2>Ear Tip</h2>
            <p class="attribute-info">Zoom all the way in to check.  Ear tipping can be difficult to see, and it depends on the angle of the photo and the amount of ear tip removed.<br />When in doubt, select unknown.</p>
            <div id="earTip" class="attribute-select">
                <div id="yes_left" class="attribute-option">
                    <img class="attribute-image" src="../images/instructions_tipleft.jpg" />
                    <div class="attribute-title">Yes - Cat's Left</div>
                </div>
                <div id="yes_right" class="attribute-option">
                    <img class="attribute-image" src="../images/instructions_tipright.jpg" />
                    <div class="attribute-title">Yes - Cat's Right</div>
                </div>
                <div id="no" class="attribute-option">
                    <img class="attribute-image" src="../images/instructions_untipped.jpg" />
                    <div class="attribute-title">No</div>
                </div>
                <div id="unknown" class="attribute-option">
                    <img class="attribute-image" src="../images/unknown.png" />
                    <div class="attribute-title">Unknown</div>
                </div>
            </div>
        </div>

        <div class="attribute">
            <h2>Life Stage</h2>
            <div id="lifeStage" class="attribute-select">
                <div id="kitten" class="attribute-option">
                    <img class="attribute-image" src="../images/instructions_kitten.jpg" />
                    <div class="attribute-title">Kitten (Under 6 Months)</div>
                </div>
                <div id="adult" class="attribute-option">
                    <img class="attribute-image" src="../images/instructions_adult.jpg" />
                    <div class="attribute-title">Adult (Over 6 Months)</div>
                </div>
            </div>
        </div>

        <div class="attribute">
            <h2>Collar</h2>
            <div id="collar" class="attribute-select">
                <div id="yes" class="attribute-option">
                    <img class="attribute-image" src="../images/instructions_collar.jpg" />
                    <div class="attribute-title">Collar</div>
                </div>
                <div id="no" class="attribute-option">
                    <img class="attribute-image" src="../images/instructions_nocollar.jpg" />
                    <div class="attribute-title">No Collar</div>
                </div>
                <div id="unknown" class="attribute-option">
                    <img class="attribute-image" src="../images/unknown.png" />
                    <div class="attribute-title">Unknown</div>
                </div>
            </div>
        </div>

        <div class="attribute">
            <h2>Sex</h2>
            <div id="sex" class="attribute-select">
                <div id="male" class="attribute-option">
                    <img class="attribute-image" src="../images/instructions_male.jpg" />
                    <div class="attribute-title">Male</div>
                </div>
                <div id="female" class="attribute-option">
                    <img class="attribute-image" src="../images/instructions_female.jpg" />
                    <div class="attribute-title">Female</div>
                </div>
                <div id="unknown" class="attribute-option">
                    <img class="attribute-image" src="../images/unknown.png" />
                    <div class="attribute-title">Unknown</div>
                </div>
            </div>
        </div>

        <div id="save-div" class="attribute">
            <h2>Save / Complete</h2>
            <p id="save-incomplete">
Make selections for all the options above, and then save here.
            </p>
            <p id="save-complete">
All required selections are made.  You may now save your answers. <br />
            </p>
<input disabled title="You must complete all selections above before saving" class="button-disabled" id="save-button" type="button" value="Save" onClick="return doSave()" />
        </div>
    </div>

    <div class="column-match">
        <h2 style="font-size: 1.8em; margin-top: 0;"><span onClick="$('.match-item-info').show();">Step 2:</span> Does the cat on the left match<br />another cat in the list below?</h2>
        <p id="match-summary"></p>
        <div id="match-results"><i>searching....</i></div>
    </div>

</div>


</div>

<jsp:include page="../footer.jsp" flush="true" />

