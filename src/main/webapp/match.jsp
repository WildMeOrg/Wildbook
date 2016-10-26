<%@ page contentType="text/html; charset=utf-8" language="java"
         import="org.ecocean.*, org.ecocean.servlet.ServletUtilities, java.awt.Dimension, java.io.File, java.util.*, java.util.concurrent.ThreadPoolExecutor,
org.apache.commons.lang3.StringUtils,
javax.servlet.http.HttpSession" %>
<%@ taglib uri="http://www.sunwesttek.com/di" prefix="di" %>
<%!

public boolean validateSources(String[] sources) {
	return false;
}

%>

<jsp:include page="header.jsp" flush="true"/>
<style>
textarea {
	width: 50%;
	height: 200px;
}
.error {
	color: #D11;
	font-weight: bold;
}
img.ident-img {
	max-height: 150px;
	max-width: 500px;
}
.ident-img-wrapper {
	background: #EEE url(images/throbber.gif) 50% 50% no-repeat;
	display: inline-block;
	margin: 10px;
	float: left;
	min-height: 200px;
	min-width: 200px;
}

#ident-workarea {
}

</style>
<script type="text/javascript">
var imageData = [];
function beginIdentify() {
	$('#ident-controls input').hide();
	var validUrls = parseUrls($('#ident-sources').val());
	if (!validUrls) {
		$('#ident-message').html('<p class="error">could not parse any valid urls</p>');
		return;
	}
	$('#ident-message').html('<p>found ' + validUrls.length + ' URL' + ((validUrls.length == 1) ? '' : 's') + '</p>');
	$('#ident-form').hide();
	for (var i = 0 ; i < validUrls.length ; i++) {
		testSource(validUrls[i]);
	}
	return true;
}

function testSource(srcUrl) {
	imageData.push({ url: srcUrl, complete: false, success: false });
	var id = imageData.length - 1;
	imageData[id].id = id;
	var img = $('<img class="ident-img" id="ident-img-' + id + '" src="' + srcUrl + '" />');
	img.on('load', function(d) {
		console.warn('loaded: %o', d);
	});
	img.on('error', function(d) {
		console.warn('failed: %o', d);
		$(d.target).hide().parent().css('background-color', '#FAA').css('background-image', 'none');
	});
	var el = $('<div class="ident-img-wrapper" id="ident-img-wrapper-' + id + '"></div>');
	el.append(img);
	$('#ident-workarea').append(el);
	//$('#ident-img-' + id).prop('src', srcUrl);
}

function parseUrls(txt) {
	var urls = [];
	var regex = new RegExp(/^https*:\/\/\S+/, "img");
	var matches;
	while ((matches = regex.exec(txt)) !== null) {
//console.info('%d %o', count, matches);
		urls.push(matches[0]);
	}
	if (urls.length < 1) return false;
	return urls;
}

$(document).ready(function() {
	$('#ident-sources').val(
		'http://XXurshot.nationalgeographic.com/u/ss/fQYSUbVfts-T7pS2VP2wnKyN8wxywmXtY0-FwsgxoQrNvelepBpZYlfdosa_7a4TKiob2NUQHNjOVO4ezl7B/\n' +
		'http://yourshot.nationalgeographic.com/u/ss/fQYSUbVfts-T7pS2VP2wnKyN8wxywmXtY0-FwsgxoQrIeSJ5qh_enoDUnJ71Up6NAT7B4wk3-delLR_irIB8/'
	);
});
</script>

<div class="container maincontent">

<%
String context=ServletUtilities.getContext(request);
Shepherd myShepherd = new Shepherd(context);
myShepherd.setAction("match.jsp");

// TODO maybe have some config to disable this!

String[] sources = request.getParameterValues("sources");
boolean valid = validateSources(sources);
if ((sources != null) && valid) {
  
} else {  //show a form

%>

<div id="ident-message">
	<%= ((sources == null) ? "" : "<p class=\"error\">there was an error parsing sources</p>") %>
</div>
<div id="ident-workarea"></div>
<form id="ident-form">
	<textarea id="ident-sources"><%= ((sources == null) ? "" : StringUtils.join(sources, "\n")) %></textarea>

	<div id="ident-controls">
		<input type="button" value="identify!" onClick="return beginIdentify();" />
	</div>
</form>

<%
}
%>



</div>

<jsp:include page="footer.jsp" flush="true"/>

