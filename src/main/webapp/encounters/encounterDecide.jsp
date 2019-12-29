<%@ page contentType="text/html; charset=utf-8" language="java" import="org.ecocean.servlet.ServletUtilities,java.util.ArrayList,java.util.List,java.util.ListIterator,java.util.Properties,
org.ecocean.media.MediaAsset,
java.io.FileInputStream, java.io.File, java.io.FileNotFoundException, org.ecocean.*, org.apache.commons.lang3.StringEscapeUtils" %>
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

%>

<jsp:include page="../header.jsp" flush="true" />


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

/*
.column-images, .column-attributes {
    display: inline-block;
    width: 47%;
    padding: 1%;
    vertical-align: top;
}
*/

.column-images, .column-attributes {
background-color: red;
    display: inline-block;
    width: 47%;
    xpadding: 1%;
    vertical-align: top;
}

.column-attributes {
    float: right;
}

@media screen and (max-width: 800px) {
    .column-images, .column-attributes {
        width: 100%;
        background-color: blue;
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

#colorPattern .attribute-option {
    width: 22%
}
#lifeStage .attribute-option {
    width: 47%;
}
#earTip .attribute-option, #collar .attribute-option, #sex .attribute-option {
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

#save-complete {
    display: none;
}

#match-div {
    display: none;
}
</style>

<script type="text/javascript">
var userData = {
    colorPattern: false,
    earTip: false,
    lifeStage: false,
    collar: false,
    sex: false
};
$(document).ready(function() {
    $('.attribute-option').on('click', function(ev) { clickAttributeOption(ev); });
    $('#flag input').on('change', function() { updateData(); });
    $('#width-info').html($(window).width());
});

function clickAttributeOption(ev) {
    console.log(ev);
    $('#' + ev.currentTarget.parentElement.id + ' .attribute-selected').removeClass('attribute-selected');
    $('#' + ev.currentTarget.parentElement.id + ' .attribute-option').addClass('attribute-muted');
    ev.currentTarget.classList.add('attribute-selected');
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
    enableMatch();
}

function enableMatch() {
    $('#match-div').show();
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
        <img class="enc-asset" src="<%=ma.safeURL(request)%>" />
<%
    }
%>
    </div>

    <div class="column-attributes">

        <div class="attribute">
            <h2>Observed Behavior</h2>
            <div class="note"><%=enc.getBehavior()%></div>
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

        <div class="attribute">
            <h2>Submitter</h2>
            (name and other metadata? goes here)
        </div>

        <div class="attribute">
            <h2>Flag Problems</h2>
            <div id="flag">
                <div class="input-wrapper"><input type="checkbox" name="flag" id="flag-missed" /><label for="flag-missed">Cat not selected</label></div>
                <div class="input-wrapper"><input type="checkbox" name="flag" id="flag-sensitive" /><label for="flag-sensitive">Sensitive or private information</label></div>
                <div class="input-wrapper"><input type="checkbox" name="flag" id="flag-quality" /><label for="flag-quality">Poor quality</label></div>
                <div class="input-wrapper"><input type="checkbox" name="flag" id="flag-other" /><label for="flag-other">Other problem</label></div>
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

        <div id="match-div" class="attribute">
            <h2>Look for matching cat</h2>
            <p>Instructions?  options?</p>
            <input type="button" value="see 23 similar cats nearby" />
        </div>

    </div>
</div>


</div>

<jsp:include page="../footer.jsp" flush="true" />

