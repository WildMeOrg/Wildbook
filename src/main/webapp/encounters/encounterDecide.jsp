<%@ page contentType="text/html; charset=utf-8" language="java" import="org.ecocean.servlet.ServletUtilities,java.util.ArrayList,java.util.List,java.util.Iterator,java.util.Properties,
org.ecocean.media.MediaAsset,
org.ecocean.media.Feature,
org.json.JSONObject, org.json.JSONArray,
java.util.Collection,
java.util.HashMap,
javax.jdo.Query,
java.io.FileInputStream, java.io.File, java.io.FileNotFoundException, org.ecocean.*, org.apache.commons.lang3.StringEscapeUtils" %>

<%!

private static String rotationInfo(MediaAsset ma) {
    if ((ma == null) || (ma.getMetadata() == null)) return null;
    HashMap<String,String> orient = ma.getMetadata().findRecurse(".*orient.*");
    if (orient == null) return null;
    for (String k : orient.keySet()) {
System.out.println("rotationInfo: " + k + "=" + orient.get(k) + " on " + ma);
        if (orient.get(k).matches(".*90.*")) return orient.get(k);
        if (orient.get(k).matches(".*270.*")) return orient.get(k);
    }
    return null;
}

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
    String sql = "SELECT \"CATALOGNUMBER\" AS encId, ST_Distance(toMercatorGeometry(\"DECIMALLATITUDE\", \"DECIMALLONGITUDE\"),toMercatorGeometry(" + lat + ", " + lon + ")) AS dist, \"PATTERNINGCODE\", \"EARTIP\", \"SEX\", \"COLLAR\", \"LIFESTAGE\" FROM \"ENCOUNTER\" WHERE validLatLon(\"DECIMALLATITUDE\", \"DECIMALLONGITUDE\") AND \"CATALOGNUMBER\" != '" + enc.getCatalogNumber() + "' AND ((" + String.join(") OR (", props) + ")) ORDER BY dist"; // removed AND (\"STATE\" = 'processing' OR \"STATE\" = 'mergereview' OR \"STATE\" = 'disputed')
System.out.println("findSimilar() userData " + userData.toString() + " --> SQL: " + sql);

    JSONArray found = new JSONArray();
    Query q = myShepherd.getPM().newQuery("javax.jdo.query.SQL", sql);
    List results = (List)q.execute();
    Iterator it = results.iterator();
    String[] propMap = new String[]{"colorPattern", "earTip", "sex", "collar", "lifeStage"};
    boolean putQuery = false;
    while (it.hasNext()) {
        JSONObject el = new JSONObject();
        if (!putQuery) el.put("query", sql);
        putQuery = true;
        Object[] row = (Object[]) it.next();
        String encId = (String)row[0];
        Double dist = (Double)row[1];
        if (dist > 50) continue;  //sanity perimeter
        Encounter menc = myShepherd.getEncounter(encId);
        if (menc == null) continue;
        if(menc.getLocationID()!= null && enc.getLocationID() != null && !menc.getLocationID().equals(enc.getLocationID())){ //further filter by location ID: if both locationIDs exist but are different, don't include this match encounter in the match list
          System.out.println("the following encounter is too far away: " + menc.getCatalogNumber());
          continue;
        }
        JSONObject propMatches = new JSONObject();
        el.put("encounterId", encId);
        if (menc.getIndividual() != null) {
            el.put("individualId", menc.getIndividual().getId());
            el.put("name", menc.getIndividual().getDisplayName());
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
            mj.put("origWidth", ma.getWidth());
            mj.put("origHeight", ma.getHeight());
            mj.put("rotation", rotationInfo(ma));
            if (ann.getFeatures() != null) for (Feature f : ann.getFeatures()) { String foo = f.toString(); }
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
    Integer matchAssetId = SystemValue.getInteger(myShepherd, "MatchPhoto_" + indiv.getId());
    Integer match2AssetId = SystemValue.getInteger(myShepherd, "MatchPhoto2_" + indiv.getId());
if (match2AssetId != null) System.out.println(match2AssetId + " ???? " + indiv);
    Annotation backup = null;
    Annotation found = null;
    Annotation secondary = null;
    String encId = null;
    foundOne: for (Encounter enc : indiv.getEncounters()) {
        if (Util.collectionIsEmptyOrNull(enc.getAnnotations())) continue;
        for (Annotation ann : enc.getAnnotations()) {
            MediaAsset ma = ann.getMediaAsset();
            if (ma == null) continue;
            if (backup == null) {
                backup = ann;
                encId = enc.getCatalogNumber();
            }
            if ((match2AssetId != null) && (match2AssetId == ma.getId())) secondary = ann;
            if ( ((matchAssetId != null) && (matchAssetId == ma.getId())) ||
                 ((matchAssetId == null) && ma.hasKeyword("MatchPhoto")) ) {
                found = ann;
                encId = enc.getCatalogNumber();
                //break foundOne;  //dont break now cuz we need to catch secondary
            }
        }
    }
/*
System.out.println("getMatchPhoto(" + indiv + ") -> found = " + found);
System.out.println("getMatchPhoto(" + indiv + ") -> backup = " + backup);
System.out.println("getMatchPhoto(" + indiv + ") -> secondary = " + secondary);
*/
    if ((backup == null) && (found == null)) return null;
    if (found == null) found = backup;
    MediaAsset ma = found.getMediaAsset();
    JSONObject rtn = new JSONObject();
    rtn.put("annotationId", found.getId());
    rtn.put("encounterId", encId);
    rtn.put("id", ma.getId());
    rtn.put("origWidth", ma.getWidth());
    rtn.put("origHeight", ma.getHeight());
    rtn.put("rotation", rotationInfo(ma));
    if (found.getFeatures() != null) for (Feature f : found.getFeatures()) { String foo = f.toString(); }
    if (!found.isTrivial()) rtn.put("bbox", found.getBbox());
    rtn.put("url", ma.safeURL(myShepherd, request));
    if (secondary != null) {
        ma = secondary.getMediaAsset();
        JSONObject j2 = new JSONObject();
        j2.put("id", ma.getId());
        j2.put("annotationId", secondary.getId());
        if (secondary.getFeatures() != null) for (Feature f : secondary.getFeatures()) { String foo = f.toString(); }
        if (!secondary.isTrivial()) j2.put("bbox", secondary.getBbox());
        j2.put("url", ma.safeURL(myShepherd, request));
        j2.put("origWidth", ma.getWidth());
        j2.put("origHeight", ma.getHeight());
        j2.put("rotation", rotationInfo(ma));
        rtn.put("secondary", j2);
    }
    return rtn;
}


%>
<%

//setup our Properties object to hold all properties
	Properties props=new Properties();
	String langCode=ServletUtilities.getLanguageCode(request);
	String context = ServletUtilities.getContext(request);
  props = ShepherdProperties.getProperties("encounterDecide.properties", langCode, context);
  request.setAttribute("pageTitle", "Kitizen Science &gt; Submission Input");
  Shepherd myShepherd = new Shepherd(context);
  myShepherd.setAction("encounterDecide.jsp");
  myShepherd.beginDBTransaction();
  String encId = request.getParameter("id");
  Encounter enc = myShepherd.getEncounter(encId);
  if (enc == null) {
      response.setStatus(404);
      out.println("404 not found");
      return;
  }
  User user = AccessControl.getUser(request, myShepherd);
/*
        if (!"new".equals(enc.getState())) {   //TODO other privilege checks here
            response.setStatus(401);
            out.println("401 access denied");
            return;
        }
*/
        JSONObject similarUserData = Util.stringToJSONObject(request.getParameter("getSimilar"));
        if (similarUserData != null) {
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
            myShepherd.rollbackDBTransaction();
            myShepherd.closeDBTransaction();
            return;
        }

        //phase 2 over
        if ((user == null) || !request.isUserInRole("admin")) {
            System.out.println("encounterDecide id=" + encId + " disabled for non-admin user=" + user);
            myShepherd.rollbackDBTransaction();
            session.invalidate();
            response.sendRedirect("../secondTrialEnd.jsp");
            return;
        }

        String jdoql = "SELECT FROM org.ecocean.Decision WHERE encounter.catalogNumber=='" + enc.getCatalogNumber() + "' && user.uuid=='" + user.getUUID() + "'";
        Query query = myShepherd.getPM().newQuery(jdoql);
        Collection col = (Collection)query.execute();
        List<Decision> decs = new ArrayList<Decision>(col);
        query.closeAll();
        JSONArray jdecs = new JSONArray();
        for (Decision d : decs) {
            JSONObject jd = new JSONObject();
            jd.put("id", d.getId());
            jd.put("timestamp", d.getTimestamp());
            jd.put("property", d.getProperty());
            jd.put("value", d.getValue());
            jdecs.put(jd);
        }

        String urlLoc = "//" + CommonConfiguration.getURLLocation(request);
%>

<jsp:include page="../header.jsp" flush="true" />
<script>
var userDecisions = <%=jdecs.toString(4)%>;
</script>
<script src="<%=urlLoc %>/tools/simplePagination/jquery.simplePagination.js"></script>
<link type="text/css" rel="stylesheet" href="<%=urlLoc %>/tools/simplePagination/simplePagination.css"/>

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
.attribute h3 {
    padding: 0;
    margin: 0;
}

#secondary-instructions {
    xheight: 2em;
    font-size: 1.5em;
    line-height: 1.1em;
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

.column-scroll {
    height: 1400px;
    overflow-y: scroll;
    scrollbar-color: #bff223 blue;
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
#earTip .attribute-option {
    width: 22%;
}
#lifeStage .attribute-option {
    width: 47%;
}
#collar .attribute-option, #sex .attribute-option {
    width: 31%;
}

.attribute-option .attribute-title {
    overflow: hidden;
    font-size: 0.75em;
    line-height: 1.2em;
    height: 2.5em;
    font-weight: bold;
}

#colorPattern .attribute-title {
    font-size: 0.7em;
}

.attribute-option .attribute-title .xxxtinier {
    font-size: 0.9em;
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

#match-summary {
    display: none;
}
.match-summary-detail {
    display: none;
    white-space: nowrap;
    font-size: 0.8em;
    border-radius: 3px;
    background-color: #CCC;
    padding: 0 5px;
    margin: 0 4px;
}

.match-item {
    padding: 0 0 25px 0;
    xborder-top: 3px black solid;
    position: relative;
}
.match-item:hover {
    background-color: #bff223;
}

.match-name {
    position: absolute;
    bottom: 40px;
    font-size: 1em !important;
    right: 30px;
    z-index: 10;
    font-size: 1.3em;
    color: white;
    text-shadow:
        -1px -1px 0 #000,
        1px -1px 0 #000,
        -1px 1px 0 #000,
        1px 1px 0 #000;
/*
    background-color: rgba(255,255,255,0.3);
    border-radius: 3px;
    padding: 0 8px;
*/
}

.match-name a {
    color: #FFF;
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
    width: 520px;
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
.match-asset-wrapper .zoom-hint {
    right: 15px !important;
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

/*
.enc-asset-wrapper {
    margin-bottom: 5px;
}
*/

.match-asset {
    position: absolute;
}
.match-choose {
    position: absolute;
    top: 10px;
    z-index: 10;
    left: 10px;
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

#match-controls-after {
    padding-top: 15px;
}



/* aka enc-asset-wrapper? */
.enc-asset-wrapper,
.img-wrapper {
    width: 100%;
    height: 400px;
    display: inline-block;
    margin: 0 10px 4px 10px;
    position: relative;
    overflow: hidden;
    background-color: #DDD;
}
/* aka enc-asset? */
.enc-asset,
.gallery-img {
    position: absolute;
    max-width: none;
    display: none;
}
.gallery-box {
    pointer-events: none;
    position: absolute;
    outline: solid 2px #bff223;
}
.gallery-box-wrapper {
    pointer-events: none;
    position: absolute;
}
.canvas-box {
    pointer-events: none;
    position: absolute;
}
.img-info {
    position: absolute;
    right: 10px;
    top: 10px;
    display: inline-block;
    background-color: rgba(255,255,0,0.7);
    border-radius: 4px;
    padding: 2px 10px;
}

.part-2-instructions {
    margin-top: 20px;
    margin-bottom: 20px;
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
    var switchToMatch = false;
    for (var i = 0 ; i < userDecisions.length ; i++) {
        switchToMatch = true;  //will get overridden below if we already matched too
        if (userDecisions[i].property == 'match') {
            $('.maincontent').html('');
            alert('You have already processed this submission.\n\nMatched ' + new Date(userDecisions[i].timestamp).toLocaleString() + '; ID#' + userDecisions[i].id);
            window.location.href = '../queue.jsp';
            return;
        }
        userData[userDecisions[i].property] = userDecisions[i].value.value;
    }

    var docWidth = $(document).width();
    if (docWidth < 900) {
        //alert('Processing submissions on small screens or mobile devices can cause problems with image display.\n\nA desktop browser is recommended.');
        window.location.href = '../register.jsp?instructions#requirements';
        return;
    }

    utickState.encounterDecide = { initTime: new Date().getTime(), clicks: [] };

    if (switchToMatch) {
        enableMatch();
        return;
    }

    $('.attribute-option').on('click', function(ev) { clickAttributeOption(ev); });
    $('.attribute-option').append('<input type="radio" class="option-checkbox" />');
    $('#flag input').on('change', function() { updateData(); });
/*
    $('.enc-asset').panzoom({maxScale:9}).on('panzoomend', function(ev, panzoom, matrix, changed) {
        if (!changed) return $(ev.currentTarget).panzoom('zoom');
    });
*/



});

function zoomOut(el, imgWrapperClass) {
    event.stopPropagation();
    var iEl = $(el).closest(imgWrapperClass).find('img');
    iEl.attr('style', null).show().css('width', '100%');
    iEl.panzoom('reset');
    //$(el).closest(imgWrapperClass).find('img').css('transform-origin', '50% 50% !important').panzoom('reset');
    adjustBox(iEl.attr('id').substr(4));
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
    $('#secondary-instructions').html('Does the cat in this submission (left) match a cat already in our database (right)? Scroll through our list and select "Matches this cat" or "None of these cats match".');
    $('#secondary-instructions').addClass('part-2-instructions');
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
            if (!xhr || !xhr.responseJSON || !xhr.responseJSON.success) {
                console.warn("responseJSON => %o", xhr.responseJSON);
                alert('ERROR searching: ' + ((xhr && xhr.responseJSON && xhr.responseJSON.error) || 'Unknown problem'));
            } else {
                if (!xhr.responseJSON.similar || !xhr.responseJSON.similar.length) {
                    $('#match-results').html('<b>No matches found</b>');
                } else { //TODO Rutvik & Hardik: this whole section might need its logic modified slightly once default match photos are being assigned to encounters
                    matchData = xhr.responseJSON;
                    matchData.assetData = {};
                    matchData.userPresented = {};
                    var sort = {};
                    var seen = {};
                    let similarShortCircuitTracker = 0; //track the number of times things short circuit. If it ends up being the same as similar.length, we need to report no matches found
                    var shouldPopulatePaginator = true;
                    for (var i = 0 ; i < xhr.responseJSON.similar.length ; i++) {
                        if (!xhr.responseJSON.similar[i].individualId){ //TODO Rutvik & Hardik: comment this out once default match photos are being assigned to encounters
                          similarShortCircuitTracker ++; //TODO Rutvik & Hardik: comment this out once default match photos are being assigned to encounters
                          continue; //TODO Rutvik & Hardik: comment this out once default match photos are being assigned to encounters
                        } //TODO Rutvik & Hardik: comment this out once default match photos are being assigned to encounters
                        if (seen[xhr.responseJSON.similar[i].individualId]){
                          similarShortCircuitTracker ++;
                          continue;
                        }
                        var score = matchScore(xhr.responseJSON.similar[i], userData);
                        matchData.userPresented[xhr.responseJSON.similar[i].encounterId] = score;
                        if (score < 0){
                          similarShortCircuitTracker ++;
                          continue;
                        }
                        seen[xhr.responseJSON.similar[i].individualId] = true;
                        var h = '<div class="match-item">';
                        h += '<div class="match-name"><a title="More images of this cat" target="_new" href="../individualGallery.jsp?id=' + xhr.responseJSON.similar[i].individualId + '&subject=' + encounterId + '" title="Enc ' + xhr.responseJSON.similar[i].encounterId + '">See more photos of ' + xhr.responseJSON.similar[i].name + '</a></div>';
                        //h += '<div class="match-name">' + (xhr.responseJSON.similar[i].name || xhr.responseJSON.similar[i].encounterId.substr(0,8)) + '</div>';
                        h += '<div class="match-choose"><input id="mc-' + i + '" class="match-chosen-cat" type="radio" value="' + xhr.responseJSON.similar[i].encounterId + '" /> <label for="mc-' + i + '">Matches this cat</label></div>';
/*
                        var numImages = xhr.responseJSON.similar[i].assets.length;
                        if (numImages > 2) numImages = 2;
                        for (var j = 0 ; j < numImages ; j++) {
                            h += '<div class="match-asset-wrapper">';
                            h += '<div class="zoom-hint" xstyle="transform: scale(0.75);"><span class="el el-lg el-zoom-in"></span><span onClick="return zoomOut(this, \'.match-asset-wrapper\')" class="el el-lg el-zoom-out"></span></div>';
                            h += '<div class="match-asset-img-wrapper"><img onLoad="matchAssetLoaded(this, ' + passj + ');" class="match-asset-img" id="match-asset-' + xhr.responseJSON.similar[i].assets[j].id + '" src="' + xhr.responseJSON.similar[i].assets[j].url + '" /></div></div>';
                            matchData.assetData[xhr.responseJSON.similar[i].assets[j].id] = xhr.responseJSON.similar[i].assets[j];
                        }
*/
                        h += '<div class="match-asset-wrapper">';
                        h += '<div class="zoom-hint" xstyle="transform: scale(0.75);"><span class="el el-lg el-zoom-in"></span><span onClick="return zoomOut(this, \'.match-asset-wrapper\')" class="el el-lg el-zoom-out"></span></div>';

                        let queryAssetEls = document.getElementsByClassName("query-annotation");
                        let allQueryAssetIds = [];
                        for (var j=0;j<queryAssetEls.length;j++) {
                            allQueryAssetIds.push(queryAssetEls[j].id.replace("wrapper-",""));
                        }
                        //TODO Rutvik & Hardik: currently, xhr.responseJSON.similar[i] returns all match candidates (both Encounters that belong to MarkedIndividuals and Encounters that don't). Only encounters that are assigned to marked individuals can have matchPhotos, so any of the logic below that looks for *.matchPhoto will fail on Encounters not associated with MarkedIndividuals. When Sabrina asks you to assign default matchPhotos to encounters, it may be worth checking the logic below to make sure that it is working correctly.
                        if (xhr.responseJSON.similar[i].matchPhoto && (xhr.responseJSON.similar[i].matchPhoto.encounterId == encounterId) && xhr.responseJSON.similar[i].matchPhoto.secondary) {
                            if (allQueryAssetIds.includes(xhr.responseJSON.similar[i].matchPhoto.secondary.id.toString())) {
                                h = "";
                                similarShortCircuitTracker ++;
                                continue;
                            }
                            console.log("getting near matchAssetLoaded call");
                            var passj = JSON.stringify(xhr.responseJSON.similar[i].matchPhoto.secondary).replace(/"/g, "'");
                            console.info('i=%d (%s) blocking MatchPhoto in favor of secondary for %o', i, xhr.responseJSON.similar[i].individualId, xhr.responseJSON.similar[i].matchPhoto);
                            h += '<div class="match-asset-img-wrapper" id="wrapper-' + xhr.responseJSON.similar[i].matchPhoto.secondary.id + '"><img onLoad="matchAssetLoaded(this, ' + passj + ');" class="match-asset-img" id="img-' + xhr.responseJSON.similar[i].matchPhoto.secondary.id + '" src="' + xhr.responseJSON.similar[i].matchPhoto.secondary.url + '" /></div></div>';
                            matchData.assetData[xhr.responseJSON.similar[i].matchPhoto.secondary.id] = xhr.responseJSON.similar[i].matchPhoto.secondary;
                            } else {
                              if (xhr.responseJSON.similar[i].matchPhoto && allQueryAssetIds.includes(xhr.responseJSON.similar[i].matchPhoto.id.toString())) {
                                  h = "";
                                  similarShortCircuitTracker ++;
                                  continue;
                              }
                            console.log("getting near matchAssetLoaded call");
                            if(xhr.responseJSON.similar[i].matchPhoto){
                                var passj = JSON.stringify(xhr.responseJSON.similar[i].matchPhoto).replace(/"/g, "'");
                                h += '<div class="match-asset-img-wrapper" id="wrapper-' + xhr.responseJSON.similar[i].matchPhoto.id + '"><img onLoad="matchAssetLoaded(this, ' + passj + ');" class="match-asset-img" id="img-' + xhr.responseJSON.similar[i].matchPhoto.id + '" src="' + xhr.responseJSON.similar[i].matchPhoto.url + '" /></div></div>';
                                matchData.assetData[xhr.responseJSON.similar[i].matchPhoto.id] = xhr.responseJSON.similar[i].matchPhoto;
                            }
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
                    } //end for xhr.responseJSON.similar
                    var keys = Object.keys(sort).sort(function(a,b) {return a-b;}).reverse();
                    $('#match-results').html('');
                    for (var i = 0 ; i < keys.length ; i++) {
                        $('#match-results').append(sort[keys[i]]);
                    }
                    if(similarShortCircuitTracker == xhr.responseJSON.similar.length){
                      $('#match-results').html('<b>No matches found</b>');
                      shouldPopulatePaginator = false;
                    }

                    //$('#match-results').append('<div id="match-controls"><div><input type="checkbox" class="match-chosen-cat" value="no-match" id="mc-none" /> <label for="mc-none">None of these cats match</label></div><input type="button" id="match-chosen-button" value="Save match choice" disabled class="button-disabled" onClick="saveMatchChoice();" /></div>');
                }
                $('#match-controls-after').html('<input type="radio" class="match-chosen-cat" value="no-match" id="mc-none" /> <label for="mc-none" style="font-size: 1.5em;"><b>None of these cats match</b></label></div><br /><input type="button" id="match-chosen-button" value="Save match choice" disabled class="button-disabled" onClick="saveMatchChoice();" />');
                $('.match-chosen-cat').on('click', function(ev) {
                  var id = ev.target.id;
                  console.log(id);
                  $('.match-chosen-cat').prop('checked', false);
                  $('#' + id).prop('checked', true);
                  $('#match-chosen-button').removeClass('button-disabled').removeAttr('disabled');
                });
                if(shouldPopulatePaginator){
                  populatePaginator(keys);
                }
            }
        },
        dataType: 'json',
        type: 'GET'
    });
}

function populatePaginator(keyArray){
  if(keyArray){
    let items = $('.match-item');
    let numItems = keyArray.length;
    let perPage = 5;
    items.slice(perPage).hide();

    $('#pagination-section').pagination({
      items: numItems,
      itemsOnPage: perPage,
      cssStyle: "light-theme",
      onPageClick: function(pageNumber) {
        var showFrom = perPage * (pageNumber - 1);
        var showTo = showFrom + perPage;
        items.hide().slice(showFrom, showTo).show();
      }
    });
  }
}

function saveMatchChoice() {
    var ch = $('.match-chosen-cat:checked').val();
    if (!ch) return;
    console.log('saving %s', ch);
    $('#match-chosen-button').hide();
    utickState.encounterDecide.matchSaveTime = new Date().getTime();
    $.ajax({
        url: '../DecisionStore',
        data: JSON.stringify({ encounterId: encounterId, property: 'match', value: { id: ch, presented: (matchData && matchData.userPresented)? matchData.userPresented: {}, initTime: utickState.encounterDecide.initTime, attrSaveTime: utickState.encounterDecide.attrSaveTime, matchSaveTime: new Date().getTime() } }),
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
    if (mdata.matches.earTip) score += 1.0;
    if (mdata.matches.collar) score += 1.0;
    if (mdata.matches.lifeStage) score += 1.0;
    if (mdata.matches.sex) score += 1.0;
    if (mdata.distance) score += Math.pow(500 / mdata.distance, 2);

    //points for color matches, or nearly so
    if (mdata.matches.colorPattern) score += 4.0;  //exact match
    var ckey = udata.colorPattern + '_' + mdata.colorPattern;

    var colorNearMatch = {

        black_bw: 3,
        bw_black: 3,
        tabby_torbie_tab_torb_white: 3,
        tab_torb_white_tabby_torbie: 3,
        orange_calico_tortie: 3,
        grey_black: 3,
        calico_torti_tabby_torbie: 3,
        beige_cream_wh_orange: 3,

        black_calico_tortie: 2,
        bw_calico_tortie: 2,
        tabby_torbie_calico_tortie: 2,
        tab_torb_white_calico_tortie: 2,
        orange_beige_cream_wh: 2,
        grey_bw: 2,
        calico_torti_tab_torb_white: 2,
        beige_cream_wh_tabby_torbie: 2,

        black_grey: 1,
        bw_beige_cream_wh: 1,
        tabby_torbie_orange: 1,
        tab_torb_white_bw: 1,
        orange_tabby_torbie: 1,
        grey_calico_tortie: 1,
        calico_torti_bw: 1,
        beige_cream_wh_tab_torb_white: 1

    };

    if (colorNearMatch[ckey]) {
        score += colorNearMatch[ckey];
        console.log('colorNearMatch: %s => %s %f', mdata.encounterId, ckey, colorNearMatch[ckey]);
    }

    return Math.round(score * 100) / 100;
}

function matchAssetLoaded(el, imgInfo) {
    assetLoaded(el, imgInfo);
    return;
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
    wrapper.css('background-color', '#FFF');
    var iw = imgEl[0].naturalWidth;
    var ih = imgEl[0].naturalHeight;
    var ww = wrapper.width();
    var wh = wrapper.height();
    var ratio = ww / (matchData.assetData[id].bbox[2] + padding);
    if ((wh / (matchData.assetData[id].bbox[3] + padding)) < ratio) ratio = wh / (matchData.assetData[id].bbox[3] + padding);
//console.log('img=%dx%d / wrapper=%dx%d / box=%dx%d', iw, ih, ww, wh, matchData.assetData[id].bbox[2], matchData.assetData[id].bbox[3]);
//console.log('%.f', ratio);
	var dx = (ww / 2) - ((matchData.assetData[id].bbox[2] + padding) * ratio / 2);
	var dy = (wh / 2) - ((matchData.assetData[id].bbox[3] + padding) * ratio / 2);
//console.log('dx, dy %f, %f', dx, dy);
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



var zscale = 1;
function assetLoaded(el, imgInfo) {
    var imgEl = $(el);
    if (!imgEl.length) return;
    if (!imgInfo || !imgInfo.bbox) {
        imgEl.css({
            width: '100%',
            top: 0,
            left: 0
        });
        imgEl.show();
        imgEl.panzoom({maxScale:9}).on('panzoomend', function(ev, panzoom, matrix, changed) {
            if (!changed) return $(ev.currentTarget).panzoom('zoom');
        });
        return;
    }
console.log('imgInfo ==> %o', imgInfo);
    var wrapper = imgEl.parent();
    var ow = imgInfo.origWidth;
    var oh = imgInfo.origHeight;
    if (imgInfo.rotation) {
        ow = imgInfo.origHeight;
        oh = imgInfo.origWidth;
    }
    var iw = imgEl[0].naturalWidth;
    var ih = imgEl[0].naturalHeight;
    var ww = wrapper.width();
    var wh = wrapper.height();
    for (var i = 0 ; i < imgInfo.bbox.length ; i++) {
        imgInfo.bbox[i] *= iw / ow;
    }
    //var padding = ww * 0.05;
    var padding = imgInfo.bbox[2] * 0.3;
    var ratio = ww / (imgInfo.bbox[2] + padding);
    if ((wh / (imgInfo.bbox[3] + padding)) < ratio) ratio = wh / (imgInfo.bbox[3] + padding);
console.log('img=%dx%d / wrapper=%dx%d / box=%dx%d / padding=%d', iw, ih, ww, wh, imgInfo.bbox[2], imgInfo.bbox[3], padding);
console.log('%.f', ratio);
/*
	var dx = (ww / 2) - ((imgInfo.bbox[2] + padding) * ratio / 2);
	var dy = (wh / 2) - ((imgInfo.bbox[3] + padding) * ratio / 2);
console.log('dx, dy %f, %f', dx, dy);
*/

        imgEl.css({
            width: '100%',
            top: 0,
            left: 0
        });

imgEl.panzoom({maxScale:20})
    .on('zoomstart panzoomstart panstart', function(ev) {
//console.log('start----- %o', ev);
        $('#wrapper-' + ev.target.id.substring(4) + ' .canvas-box').hide();
    })
    .on('zoomend panzoomend', function(ev, panzoom, matrix, changed) {
        adjustBox(ev.target.id.substring(4));
        if (!changed) {
            var rtn = $(ev.currentTarget).panzoom('zoom');
            adjustBox(ev.target.id.substring(4));
            return rtn;
        }
    });

zscale = ww / ow;
var yscale = wh / oh;
var px = -(imgInfo.bbox[0] * zscale) + (ww / 2) - (imgInfo.bbox[2] * zscale / 2);
var py = -(imgInfo.bbox[1] * yscale) + (wh / 2) - (imgInfo.bbox[3] * yscale / 2);

var zz = 3 * ww / imgInfo.bbox[2];
if (zz < 1) zz = 1;
console.info('[ zz = %f ]  px, py = %f,%f (zscale %f, yscale %f)', zz, px, py, zscale, yscale);
imgEl.panzoom('pan', zz * px, zz * py);
imgEl.panzoom('zoom', zz);

	imgEl.show();

        var box = $('<canvas width="' + ow + '" height="' + oh + '" class="canvas-box"></canvas>');
        box.css({
            transformOrigin: '50% 50%',
            xopacity: 0.5,
            xleft: imgInfo.bbox[0] * zscale + 'px',
            xtop: imgInfo.bbox[1] * zscale + 'px',
            left: 0, top: 0,
            width: '100%',
            xheight: wh + 'px'
        });
        var ctx = box[0].getContext('2d');
        ctx.strokeStyle = '#bff223';
        ctx.lineWidth = 5;
        ctx.setLineDash([10, 4]);
        ctx.beginPath();
        //ctx.rect(imgInfo.bbox[0] * zscale, imgInfo.bbox[1] * zscale, imgInfo.bbox[2] * zscale, imgInfo.bbox[3] * zscale);
        ctx.rect(imgInfo.bbox[0] - (padding/2), imgInfo.bbox[1] - (padding/2), imgInfo.bbox[2] + padding, imgInfo.bbox[3] + padding);
        ctx.stroke();
        box.hide();
        wrapper.append(box);
        adjustBox(el.id.substr(4));
}


function adjustBox(id) {
    window.setTimeout(function() {
        var matrix = $('#img-' + id).css('transform');
        $('#wrapper-' + id + ' .canvas-box').css('transform', matrix).show();
    }, 300);
}


</script>

</head>
<body>

<div class="container maincontent">
<h2>Submission <%=((enc.getEventID() == null) ? enc.getCatalogNumber().substring(0,8) : enc.getEventID())%>: <span id="subtitle">Step 1</span></h2>

<%= NoteField.buildHtmlDiv("59b4eb8f-b77f-4259-b939-5b7c38d4504c", request, myShepherd) %>
<div class="org-ecocean-notefield-default" id="default-59b4eb8f-b77f-4259-b939-5b7c38d4504c">
<p>
There are two steps to processing each submission: selecting cat attributes, and then looking to see if the cat has a match in the database.
</p>
<h3 id="secondary-instructions"></h3>
</div>

<b id="width-info"></b>
<div>
    <div class="column-images">
<%
    ArrayList<Annotation> anns = enc.getAnnotations();
    if (!Util.collectionIsEmptyOrNull(anns)) for (Annotation ann : anns) {
        MediaAsset ma = ann.getMediaAsset();
        if (ma == null) continue;
        JSONObject j = new JSONObject();
        j.put("annotationId", ann.getId());
        j.put("origWidth", ma.getWidth());
        j.put("origHeight", ma.getHeight());
        j.put("rotation", rotationInfo(ma));
        if (ann.getFeatures() != null) for (Feature f : ann.getFeatures()) { String foo = f.toString(); }
        if (!ann.isTrivial()) j.put("bbox", ann.getBbox());

%>
        <div id="wrapper-<%=ma.getId()%>" class="enc-asset-wrapper query-annotation"><div class="zoom-hint"><span class="el el-lg el-zoom-in"></span><span onClick="return zoomOut(this, '.enc-asset-wrapper')" class="el el-lg el-zoom-out"></span></div><img id="img-<%=ma.getId()%>" onload="assetLoaded(this, <%=j.toString().replaceAll("\"", "'")%>);" class="enc-asset" src="<%=ma.safeURL(request)%>" /></div>
<%
    }
            myShepherd.rollbackDBTransaction();
            myShepherd.closeDBTransaction();
%>
    </div>

    <div class="column-attributes">

        <div class="attribute" style="display: none;">
            <h3>Flag Problems</h3>
            <div id="flag">
                <div class="input-wrapper"><input type="checkbox" name="flag" id="flag-detection" /><label for="flag-detection">Some photos missing (this) cat<span class="flag-note">A submission may include some photos that do not have a cat in them, or multi-cat submissions that are split into multiple submissions to process may some have photos without the focal cat.  Do any photos not contain the focal cat?</span></label></div>
                <div class="input-wrapper"><input type="checkbox" name="flag" id="flag-sensitive" /><label for="flag-sensitive">Sensitive or private information<span class="flag-note">To respect the privacy of those in our communities, our system should detect and automatically blur human faces, street signs, house numbers, license plates, and company logos. Do the photos in this submission contain any unblurred private information?</span></label></div>
                <div class="input-wrapper"><input type="checkbox" name="flag" id="flag-quality" /><label for="flag-quality">Very poor quality<span class="flag-note">Low image quality, blurring, resolution, or other technical problems prevent photos from being useful.</span></label></div>
            </div>
        </div>

        <div class="attribute">
            <h3>Primary Color / Coat Pattern</h3>
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
                    <div class="attribute-title"><span class="tinier">Tabby/Torbie</span> Grey or Brown</div>
                </div>
                <div id="tab_torb_white" class="attribute-option">
                    <img class="attribute-image" src="../images/instructions_tabwhite.jpg" />
                    <div class="attribute-title"><span class="tinier">Tabby/Torbie</span> &amp; White</div>
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
                    <div class="attribute-title">Calico / <span class="tinier">Tortoiseshell</span></div>
                </div>
                <div id="beige_cream_wh" class="attribute-option">
                    <img class="attribute-image" src="../images/instructions_light.jpg" />
                    <div class="attribute-title">Beige / White / Siamese</div>
                </div>
            </div>
        </div>

        <div class="attribute">
            <h3><%=props.getProperty("earTip")%></h3>
            <p class="attribute-info">
              <%=props.getProperty("zoomEarTipPt1")%>
              <br/>
              <%=props.getProperty("zoomEarTipPt2")%>
            </p>
            <div id="earTip" class="attribute-select">

                <div id="yes_left" class="attribute-option">
                    <img class="attribute-image" src="../images/instructions_tipleft.jpg" />
                    <div class="attribute-title"><%=props.getProperty("catLeft")%></div>
                </div>
                <div id="yes_right" class="attribute-option">
                    <img class="attribute-image" src="../images/instructions_tipright.jpg" />
                    <div class="attribute-title"><%=props.getProperty("catRight")%></div>
                </div>
                <div id="no" class="attribute-option">
                    <img class="attribute-image" src="../images/instructions_untipped.jpg" />
                    <div class="attribute-title"><%=props.getProperty("no")%></div>
                </div>
                <div id="unknown" class="attribute-option">
                    <img class="attribute-image" src="../images/unknown_eartip.png" />
                    <div class="attribute-title"><%=props.getProperty("unknown")%></div>
                </div>

            </div>
        </div>

        <div class="attribute">
            <h3>Life Stage</h3>
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
            <h3>Collar</h3>
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
            <h3>Sex</h3>
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
            <h3>Save / Complete</h3>
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
        <div class="column-scroll">
            <p id="match-summary"></p>
            <div id="match-results"><i>searching....</i></div>
            <div id="pagination-section"></div>
        </div>
        <div id="match-controls-after">
        </div>
    </div>

</div>


</div>

<jsp:include page="../footer.jsp" flush="true" />
