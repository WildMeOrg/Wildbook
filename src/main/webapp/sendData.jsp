<%@ page contentType="text/html; charset=utf-8" language="java"
         import="org.ecocean.servlet.ServletUtilities,org.ecocean.*, java.util.Properties,org.slf4j.Logger,org.slf4j.LoggerFactory" %>
<%

String context=ServletUtilities.getContext(request);

%>
<jsp:include page="header.jsp" flush="true" />
<style>
input {
    width: 15em;
}
</style>
<script>
function fillIn() {
    $('[name="lat"]').val(20 - Math.random() * 40);
    $('[name="lon"]').val(20 - Math.random() * 40);
    $('[name="datetime"]').val(new Date().toISOString());
}
$(document).ready(function() {
    fillIn();
});
</script>

<div class="container maincontent">

<form id="encounterForm"
	  action="SimpleEncounterForm"
	  method="post"
	  enctype="multipart/form-data"
      lang="en"
      accept-charset="UTF-8"
>

<p>
<b>Date/Time:</b> <input name="datetime" />
</p>
<p>
<b>Latitude:</b> <input name="lat" />
<b>Latitude:</b> <input name="lon" />
</p>

<p>
<b>Location:</b><br />
<select name="locationID">
<option>Seattle</option>
<option>Portland</option>
<option>New York</option>
</select>
</p>

<p>
<b>Images:</b><br />
<input name="theFiles" type="file" xwebkitdirectory xdirectory multiple accept="audio/*,video/*,image/*" />
</p>


<p>
<input type="submit" value="Send" />
</p>

</form>

      </div>
            <jsp:include page="footer.jsp" flush="true"/>
<%
  session.invalidate();
%>

