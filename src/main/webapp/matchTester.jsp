<html><head>
<title>
kitizen science : matchTester
</title>

<style>
body {
    font-family: arial, sans;
}

#sql {
    background-color: #FFB;
    font-size: 0.8em;
    color: #666;
}
#code {
    font-family: monospace;
    padding: 10px;
    border: solid blue 3px;
}
#code-wrapper {
    width: 35%;
    position: absolute;
    top: 0;
    right: 10px;
    background-color: #EEE;
}
#code-wrapper:hover {
    z-index: 30;
}

#match-results {
    position: relative;
}

#attributes {
    width: 55%;
}

.prop {
    display: inline-block;
    padding: 5px;
}

.match-result {
    position: relative;
    height:50px;
}
.match-result:hover {
    background-color: #eee;
}
.match-photo {
    position: absolute;
    top: 0;
    right: 0;
    height: 45px;
}
.match-photo:hover {
    height: 1024px;
    z-index: 50;
}

.match-bit {
    font-size: 0.8em;
    display: inline-block;
    padding: 1 6px;
    margin: 3px;
    background-color: #DDD;
}

.match-bit-score {
    background-color: #AA8;
}

</style>
<script src="https://ajax.googleapis.com/ajax/libs/jquery/3.4.1/jquery.min.js"></script>

<script>
var props = {
colorPattern: [
	'black',
	'bw',
	'tabby_torbie',
	'tab_torb_white',
	'orange',
	'grey',
	'calico_torto',
	'beige_cream_wh'
],

earTip: [
	'yes_left',
	'yes_right',
	'no',
	'unknown'
],

lifeStage: [
	'kitten',
	'adult'
],

collar: [
	'yes',
	'no',
	'unknown'
],

sex: [
	'male',
	'female',
	'unknown'
]
};

var matchData = null;
var encId = 'b1e06c13-43d6-4299-a612-1a3f4ecc33e2';

$(document).ready(function() {
    $('#enc-info').html('<a target="_new" href="encounters/encounter.jsp?number=' + encId + '">' + encId + '</a>');
    $('#code').text(matchScore.toString());

    var h = '';
    for (var attr in props) {
        h += '<div class="prop"><b>' + attr + '</b><br />';
        h += '<select name="' + attr + '" id="' + attr + '">';
        for (var i = 0 ; i < props[attr].length ; i++) {
            h += '<option>' + props[attr][i] + '</option>';
        }
        h += '</select>';
        h += '</div>'
    }
    $('#pulldowns').html(h);
});

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

function findMatches() {
    $('#go-button').hide();
    var udata = {};
    $('#pulldowns select').each(function(i, el) {
        udata[el.id] = $('#' + el.id).val();
    });
    getMatches(udata);
}

function getMatches(userData) {
    var url = 'encounters/encounterDecide.jsp?id=' + encId + '&getSimilar=' + encodeURI(JSON.stringify(userData));
    $.ajax({
        url: url,
        complete: function(xhr) {
            $('#go-button').show();
            console.log(xhr);
            if (!xhr || !xhr.responseJSON || !xhr.responseJSON.success) {
                console.warn("responseJSON => %o", xhr.responseJSON);
                $('#match-results').html('ERROR searching: ' + ((xhr && xhr.responseJSON && xhr.responseJSON.error) || 'Unknown problem'));
            } else {
                if (!xhr.responseJSON.similar || !xhr.responseJSON.similar.length) {
                    $('#match-results').html('<b>No matches found</b>');
                } else {
                    var ordering = {};
                    matchData = xhr.responseJSON;
                    for (var i = 0 ; i < matchData.similar.length ; i++) {
                        var score = matchScore(matchData.similar[i], userData);
                        matchData.similar[i].score = score;
                        ordering[Math.floor(score * 100000) + '.00' + i] = i;
                    }
                    var okeys = Object.keys(ordering);
                    okeys.sort(function(a,b) { return (a - b); }).reverse();
                    $('#match-results').html('<p id="sql">SQL: <b>' + matchData.similar[0].query+ '</b></p>');
                    for (var i = 0 ; i < okeys.length ; i++) {
                        console.log('%o %o %o', i, okeys[i], ordering[okeys[i]]);
                        displayMatch(i, matchData.similar[ordering[okeys[i]]]);
                    }
                }
            }
        },
        dataType: 'json',
        type: 'GET'
    });
}

function displayMatch(ct, m) {
    var h = '<div class="match-result">';
    h += '<div class="match-bit match-bit-score">' + (ct+1) + ') <b>' + m.score + '</b></div> ';
    for (var bit in m) {
        if (['score', 'assets', 'matchPhoto', 'individualId','query'].includes(bit)) continue;
        var val = m[bit];
        if (bit == 'encounterId') val = '<a target="_new" href="encounters/encounter.jsp?number=' + val + '">enc</a>';
        if (bit == 'matches') val = JSON.stringify(val);
        h += '<div class="match-bit match-bit-' + bit + '">' + bit + ': <b>' + val + '</b></div>';
    }
    if (m.matchPhoto) h += '<img class="match-photo" src="' + m.matchPhoto.url + '" />';
    h += '</div>';
    $('#match-results').append(h);
}
</script>
</head>
<body>
<div id="attributes">
<p>spoofing as <b id="enc-info"></b> (will be skipped)</p>

    <div id="pulldowns"></div>
<input id="go-button" type="button" value="find matches" onclick="findMatches();" />
</div>

<div id="code-wrapper">
<b>current <i>matchScore()</i> function</b>
    <pre id="code"></pre>
</div>


<div id="match-results"></div>

</body>
</html>
