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
    margin: 10px 0;
}
.attribute h2 {
    padding: 0;
    margin: 0;
}

.column-images, .column-attributes {
    display: inline-block;
    width: 47%;
    padding: 1%;
    vertical-align: top;
}

.attribute-option {
    display: inline-block;
    padding: 3px 8px;
    background-color: #DDD;
    margin: 2px;
    cursor: pointer;
}
.attribute-option:hover {
    background-color: #ACA;
}

#colorPattern .attribute-option {
    width: 22%
}

.attribute-option .attribute-title {
    font-size: 0.8em;
    line-height: 1.2em;
    height: 2.5em;
    font-weight: bold;
}

</style>
</head>
<body>

<div class="container maincontent">
<h1>Submission <%=enc.getCatalogNumber().substring(0,8)%></h1>

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
    </div>
</div>


</div>

<jsp:include page="../footer.jsp" flush="true" />

