<%@ page contentType="text/html; charset=utf-8" language="java" import="org.ecocean.servlet.ServletUtilities,java.util.ArrayList,java.util.List,java.util.Iterator,java.util.Properties,
org.ecocean.media.MediaAsset,
org.json.JSONObject, org.json.JSONArray,
javax.jdo.Query,
java.io.FileInputStream, java.io.File, java.io.FileNotFoundException, org.ecocean.*, org.apache.commons.lang3.StringEscapeUtils" %>
<%!

private static JSONArray findSimilar(HttpServletRequest request, Shepherd myShepherd, Encounter enc, User user, JSONObject userData) {
    if ((enc == null) || (user == null) || (userData == null)) return null;
    Double lat = enc.getDecimalLatitudeAsDouble();
    Double lon = enc.getDecimalLongitudeAsDouble();
    if ((lat == null) || (lon == null)) {
        System.out.println("WARNING: findSimilar() has no lat/lon for " + enc);
        return null;
    }

    List<String> props = new ArrayList<String>();
    String colorPattern = userData.optString("colorPattern", null);
    if (colorPattern != null) props.add("\"PATTERNINGCODE\" = '" + Util.sanitizeUserInput(colorPattern) + "'");
    String earTip = userData.optString("earTip", null);
    if (earTip != null) props.add("\"EARTIP\" = '" + Util.sanitizeUserInput(earTip) + "'");
    String sex = userData.optString("sex", null);
    if (sex != null) props.add("\"SEX\" = '" + Util.sanitizeUserInput(sex) + "'");
    String collar = userData.optString("collar", null);
    if (collar != null) props.add("\"COLLAR\" = '" + Util.sanitizeUserInput(collar) + "'");
    String lifeStage = userData.optString("lifeStage", null);
    if (lifeStage != null) props.add("\"LIFESTAGE\" = '" + Util.sanitizeUserInput(lifeStage) + "'");
    if (props.size() < 1) {
        System.out.println("WARNING: findSimilar() has no props sql from userData " + userData.toString());
        return null;
    }
    
    //technically we dont need to exclude our enc, as we are not 'approved', but meh.
    String sql = "SELECT \"CATALOGNUMBER\" AS encId, ST_Distance(toMercatorGeometry(\"DECIMALLATITUDE\", \"DECIMALLONGITUDE\"),toMercatorGeometry(" + lat + ", " + lon + ")) AS dist, \"PATTERNINGCODE\", \"EARTIP\", \"SEX\", \"COLLAR\", \"LIFESTAGE\" FROM \"ENCOUNTER\" WHERE validLatLon(\"DECIMALLATITUDE\", \"DECIMALLONGITUDE\") AND \"STATE\" = 'approved' AND \"CATALOGNUMBER\" != '" + enc.getCatalogNumber() + "' AND ((" + String.join(") OR (", props) + ")) ORDER BY dist";
System.out.println("findSimilar() SQL: " + sql);

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
.attribute {
    text-align: center;
    border: solid 1px #444;
    background-color: #EFEFEF;
    padding: 7px;
    margin: 20px 0;
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
    margin: 2px;
    cursor: pointer;
    width: 100%;
}
.attribute-option:hover {
    background-color: #ACA;
}
.attribute-selected {
    background-color: #6B6 !important;
}
.attribute-muted {
    opacity: 0.4;
}
.attribute-muted:hover {
    opacity: 1.0;
}

#earTip .attribute-option, #colorPattern .attribute-option {
    width: 22%
}
#lifeStage .attribute-option {
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
    padding-left: 20%;
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
    background-color: #BBB;
}
#flag .input-wrapper input {
    vertical-align: top;
}

.option-checkbox {
    transform: scale(1.5);
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

.match-summary-detail {
    font-size: 0.8em;
    border-radius: 3px;
    background-color: #CCC;
    padding: 0 5px;
    margin: 0 4px;
}

.match-item {
    padding-top: 10px;
    border-top: 3px black solid;
    position: relative;
}
.match-item-info {
    background-color: rgba(255,255,200,0.7);
    padding: 0 8px;
    position: absolute;
    left: 0;
    top: 0;
}
.match-item:hover .match-item-info {
    display: none;
}
.match-asset-wrapper {
    position: relative;
    overflow: hidden;
    width: 300px;
    height: 300px;
}
.match-asset {
    position: absolute;
}
.match-choose {
    position: absolute;
    top: 0;
    right: 0;
    padding: 2px 8px;
    background-color: #AAA;
    border-radius: 5px;
}
.match-choose:hover {
    background-color: #DDA;
}
.match-choose label {
    font-weight: bold;
    cursor: pointer;
}
#match-chosen-button {
    display: none;
}
#match-controls {
    padding: 10px;
    background: #CCC;
    position: fixed;
    bottom: 10px;
    border-radius: 10px;
    width: 30%;
    height: 150px;
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
    $('.attribute-option').on('click', function(ev) { clickAttributeOption(ev); });
    $('.attribute-option').append('<input type="checkbox" class="option-checkbox" />');
    $('#flag input').on('change', function() { updateData(); });
    $('#width-info').html($(window).width());
    $('.enc-asset').panzoom().on('panzoomend', function(ev, panzoom, matrix, changed) {
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
    $('.enc-asset').on('dblclick', function(ev) {
        $(ev.currentTarget).panzoom('reset');
    });

});

function clickAttributeOption(ev) {
    if (dataLocked) return;
    console.log(ev);
    $('#' + ev.currentTarget.parentElement.id + ' .attribute-selected').removeClass('attribute-selected');
    $('#' + ev.currentTarget.parentElement.id + ' .attribute-option').addClass('attribute-muted');
    $('#' + ev.currentTarget.parentElement.id + ' .option-checkbox').prop('checked', false);
    ev.currentTarget.classList.add('attribute-selected');
    $('#' + ev.currentTarget.parentElement.id + ' .attribute-selected .option-checkbox').prop('checked', true);
    ev.currentTarget.classList.remove('attribute-muted');
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
    }
}

function doSave() {
    $('#save-div').hide();
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
                dataLocked = true;
                enableMatch();
            }
        },
        contentType: 'application/javascript',
        type: 'POST'
    });
}

var matchData = null;
function enableMatch() {
    $('.column-attributes').hide();
    $('.column-match').show();
    var h = '';
    for (var i in userData) {
        h += '<span class="match-summary-detail">' + i + ': <b>' + userData[i] + '</b></span>';
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
                    var sort = {};
                    for (var i = 0 ; i < xhr.responseJSON.similar.length ; i++) {
                        var score = matchScore(xhr.responseJSON.similar[i]);
                        var h = '<div class="match-item">';
                        h += '<div class="match-choose"><input id="mc-' + i + '" class="match-chosen-cat" type="checkbox" value="' + xhr.responseJSON.similar[i].encounterId + '" /> <label for="mc-' + i + '">matches this cat</label></div>';
                        for (var j = 0 ; j < xhr.responseJSON.similar[i].assets.length ; j++) {
                            h += '<div class="match-asset-wrapper"><img onLoad="matchAssetLoaded(this);" id="match-asset-' + xhr.responseJSON.similar[i].assets[j].id + '" src="' + xhr.responseJSON.similar[i].assets[j].url + '" /></div>';
                            matchData.assetData[xhr.responseJSON.similar[i].assets[j].id] = xhr.responseJSON.similar[i].assets[j];
                        }
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
                    $('#match-results').append('<div id="match-controls"><div><input type="checkbox" class="match-chosen-cat" value="no-match" id="mc-none" /> <label for="mc-none">No matches</label></div><input type="button" id="match-chosen-button" value="Save match choice" onClick="saveMatchChoice();" /></div>');
                    $('.match-chosen-cat').on('click', function(ev) {
                        var id = ev.target.id;
console.log(id);
                        $('.match-chosen-cat').prop('checked', false);
                        $('#' + id).prop('checked', true);
                        $('#match-chosen-button').show();
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
        data: JSON.stringify({ encounterId: encounterId, property: 'match', value: { id: ch } }),
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

function matchScore(mdata) {
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
    $(el).panzoom().on('panzoomend', function(ev, panzoom, matrix, changed) {
        if (!changed) return $(ev.currentTarget).panzoom('zoom');
    });
return;
    var id = el.id.substr(12);
    toggleZoom(id);
}

function toggleZoom(id) {
console.log('asset id=%o', id);
    if (!matchData || !matchData.assetData || !matchData.assetData[id] || !matchData.assetData[id].bbox) return;
    var imgEl = document.getElementById('match-asset-' + id);
    if (!imgEl) return;
    if (!imgEl.style) imgEl.style = {};
    var elWidth = imgEl.width;
    var imgWidth = imgEl.naturalWidth;
    console.log('elWidth=%o imgWidth=%o; ft.params => %o', elWidth, imgWidth, matchData.assetData[id].bbox);
    if (imgWidth < 1) return;
    var ratio = elWidth / imgWidth;
    var scale = imgWidth / matchData.assetData[id].bbox[2];
    console.log('ratio => %o; scale => %o', ratio, scale);
    //imgEl.style.transformOrigin = '0 0';
    imgEl.style.left = -(matchData.assetData[id].bbox[0] * scale * ratio) + 'px';
    imgEl.style.top = -(matchData.assetData[id].bbox[1] * scale * ratio) + 'px';
    //imgEl.style.left = -((matchData.assetData[id].bbox[0] + matchData.assetData[id].bbox[2]/2) * scale * ratio) + 'px';
    //imgEl.style.top = -((matchData.assetData[id].bbox[1] + matchData.assetData[id].bbox[3]/2) * scale * ratio) + 'px';
    imgEl.style.transform = 'scale(' + scale + ')';
console.info(imgEl.style);
}


</script>

</head>
<body>

<div class="container maincontent">
<h1>Submission <%=enc.getCatalogNumber().substring(0,8)%></h1>

<%= NoteField.buildHtmlDiv("59b4eb8f-b77f-4259-b939-5b7c38d4504c", request, myShepherd) %>
<div class="org-ecocean-notefield-default" id="default-59b4eb8f-b77f-4259-b939-5b7c38d4504c">
<p>Some instructions can go here.  This is editable.</p>
</div>

<b id="width-info"></b>
<div>
    <div class="column-images">
<%
    ArrayList<MediaAsset> assets = enc.getMedia();
    if (!Util.collectionIsEmptyOrNull(assets)) for (MediaAsset ma : assets) {
%>
        <div class="enc-asset-wrapper"><img class="enc-asset" src="<%=ma.safeURL(request)%>" /></div>
<%
    }
%>
    </div>

    <div class="column-attributes">

        <div class="attribute">
            <h2>Flag Problems</h2>
            <div id="flag">
                <div class="input-wrapper"><input type="checkbox" name="flag" id="flag-missed" /><label for="flag-missed">Cat(s) not detected<span class="flag-note">Our system should detect and draw a box around all cats in this photo submission.  Submissions with multiple cats should have each cat detected, and the focal cat highlighted with a ticker box line.  Are there any undetected (unboxed) cats in these photos?</span></label></div>
                <div class="input-wrapper"><input type="checkbox" name="flag" id="flag-sensitive" /><label for="flag-sensitive">Sensitive or private information<span class="flag-note">To respect the privacy of those in our communities, our system should detect and automatically blur human faces, street signs, house numbers, license plates, and company logos. Do the photos in this submission contain any unblurred private information?</span></label></div>
                <div class="input-wrapper"><input type="checkbox" name="flag" id="flag-quality" /><label for="flag-quality">Poor quality<span class="flag-note">Low image quality, blurring, resolution, or other technical problems prevent some photos from being useful.</span></label></div>
            </div>
        </div>

        <div class="attribute">
            <h2>Primary Color / Coat Pattern</h2>
            <div id="colorPattern" class="attribute-select">
                <div id="black" class="attribute-option">
                    <div class="attribute-title">Black</div>
                    <img class="attribute-image" src="../images/instructions_black.jpg" />
                </div>
                <div id="black-white" class="attribute-option">
                    <div class="attribute-title">Black &amp; White</div>
                    <img class="attribute-image" src="../images/instructions_bw.jpg" />
                </div>
                <div id="tabby-gb" class="attribute-option">
                    <div class="attribute-title">Grey or Brown Tabby/Torbie</div>
                    <img class="attribute-image" src="../images/instructions_tabby.jpg" />
                </div>
                <div id="tabby-w" class="attribute-option">
                    <div class="attribute-title">Tabby/Torbie &amp; White</div>
                    <img class="attribute-image" src="../images/instructions_tabwhite.jpg" />
                </div>
                <div id="orange" class="attribute-option">
                    <div class="attribute-title">Orange</div>
                    <img class="attribute-image" src="../images/instructions_orange.jpg" />
                </div>
                <div id="grey" class="attribute-option">
                    <div class="attribute-title">Dark Grey</div>
                    <img class="attribute-image" src="../images/instructions_grey.jpg" />
                </div>
                <div id="calico" class="attribute-option">
                    <div class="attribute-title">Calico / Tortoiseshell</div>
                    <img class="attribute-image" src="../images/instructions_tortical.jpg" />
                </div>
                <div id="white" class="attribute-option">
                    <div class="attribute-title">Beige / Cream / White</div>
                    <img class="attribute-image" src="../images/instructions_light.jpg" />
                </div>
            </div>
        </div>

        <div class="attribute">
            <h2>Ear Tip</h2>
            <div id="earTip" class="attribute-select">
                <div id="yes-left" class="attribute-option">
                    <div class="attribute-title">Yes - Cat's Left</div>
                    <img class="attribute-image" src="../images/instructions_tipleft.jpg" />
                </div>
                <div id="yes-right" class="attribute-option">
                    <div class="attribute-title">Yes - Cat's Right</div>
                    <img class="attribute-image" src="../images/instructions_tipright.jpg" />
                </div>
                <div id="no" class="attribute-option">
                    <div class="attribute-title">No</div>
                    <img class="attribute-image" src="../images/instructions_untipped.jpg" />
                </div>
                <div id="unknown" class="attribute-option">
                    <div class="attribute-title">Unknown</div>
                    <img class="attribute-image" src="../images/unknown.png" />
                </div>
            </div>
        </div>

        <div class="attribute">
            <h2>Life Stage</h2>
            <div id="lifeStage" class="attribute-select">
                <div id="kitten" class="attribute-option">
                    <div class="attribute-title">Kitten</div>
                    <img class="attribute-image" src="../images/instructions_kitten.jpg" />
                </div>
                <div id="adult" class="attribute-option">
                    <div class="attribute-title">Adult</div>
                    <img class="attribute-image" src="../images/instructions_adult.jpg" />
                </div>
            </div>
        </div>

        <div class="attribute">
            <h2>Collar</h2>
            <div id="collar" class="attribute-select">
                <div id="yes" class="attribute-option">
                    <div class="attribute-title">Collar</div>
                    <img class="attribute-image" src="../images/instructions_collar.jpg" />
                </div>
                <div id="no" class="attribute-option">
                    <div class="attribute-title">No Collar</div>
                    <img class="attribute-image" src="../images/instructions_nocollar.jpg" />
                </div>
                <div id="unknown" class="attribute-option">
                    <div class="attribute-title">Unknown</div>
                    <img class="attribute-image" src="../images/unknown.png" />
                </div>
            </div>
        </div>

        <div class="attribute">
            <h2>Sex</h2>
            <div id="sex" class="attribute-select">
                <div id="male" class="attribute-option">
                    <div class="attribute-title">Male</div>
                    <img class="attribute-image" src="../images/instructions_male.jpg" />
                </div>
                <div id="female" class="attribute-option">
                    <div class="attribute-title">Female</div>
                    <img class="attribute-image" src="../images/instructions_female.jpg" />
                </div>
                <div id="unknown" class="attribute-option">
                    <div class="attribute-title">Unknown</div>
                    <img class="attribute-image" src="../images/unknown.png" />
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
<input type="button" value="Save" onClick="return doSave()" />
            </p>
        </div>
    </div>

    <div class="column-match">
        <h2>Look for a matching cat</h2>
        <p id="match-summary"></p>
        <div id="match-results"><i>searching....</i></div>
    </div>

</div>


</div>

<jsp:include page="../footer.jsp" flush="true" />

