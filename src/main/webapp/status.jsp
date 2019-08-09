<%@ page contentType="text/html; charset=utf-8" language="java"
         import="org.ecocean.servlet.ServletUtilities,org.ecocean.*, java.util.Properties,
org.json.JSONObject,
org.joda.time.DateTime
" %>
<%

String context = ServletUtilities.getContext(request);
String urlLoc = "//" + CommonConfiguration.getURLLocation(request);
Shepherd myShepherd = new Shepherd(context);

  //handle some cache-related security
  response.setHeader("Cache-Control", "no-cache"); //Forces caches to obtain a new copy of the page from the origin server
  response.setHeader("Cache-Control", "no-store"); //Directs caches not to store the page under any circumstance
  response.setDateHeader("Expires", 0); //Causes the proxy cache to see the page as "stale"
  response.setHeader("Pragma", "no-cache"); //HTTP 1.0 backward compatibility

    String sid = Util.generateUUID().substring(0,8);

    if ("send".equals(request.getQueryString())) {
        JSONObject jobj = ServletUtilities.jsonFromHttpServletRequest(request);
        String sendSid = jobj.optString("sid", "UNKNOWN_" + sid);
        System.out.println("STATUS DUMP (sid=" + sendSid + "): " + jobj.toString());
        Util.writeToFile(jobj.toString(4), "/tmp/status-" + sendSid + ".json");
        out.println("{\"success\": true}");
        return;
    }

    User user = AccessControl.getUser(request, myShepherd);
    DateTime now = new DateTime();
    String ustamp = sid + ":" + ((user == null) ? "ANON" : user.getUsername() + ":" + user.getUUID());

%><html>
<head><title>Kitizen Science: status debugging</title>
      <script src="<%=urlLoc %>/tools/jquery/js/jquery.min.js?u=<%=ustamp%>"></script>

<%
if (user == null) {
    out.println("</head><body><h1>not logged in</h1></body></html>");
    return;
}
%>

<script>
var lsKey = 'org.wildbook.catnip.<%=user.getUsername()%>.false';
var stored;
$(document).ready(function() {
    var t = new Date();
    $('#local-time').html(t.toLocaleString());
    $('#local-agent').html(window.navigator.userAgent + ' | ' + window.navigator.appVersion + ' | ' + window.navigator.vendor);

    var storedRaw = localStorage.getItem(lsKey);
    if (storedRaw) {
        $('#stored-has-value').html('yes');
        $('#stored-length').html(storedRaw.length);
        stored = JSON.parse(storedRaw);
        if (stored) {
            $('#stored-parsed').html('yes');
            $('#stored-trial').html(stored.trial);
            var d = new Date(stored.timestamp);
            $('#stored-timestamp').html(d.toLocaleString());
            $('#stored-timestamp-raw').html(stored.timestamp);
            $('#stored-completed').html(stored.results.length);

            var send = {
                trial: stored.trial,
                results: stored.results,
                storedTimestamp: stored.timestamp,
                _timestamp: t.getTime(),
                _servertime: <%=System.currentTimeMillis()%>,
                _remoteHost: $('#remote-host').text(),
                uid: '<%=user.getUUID()%>',
                sid: '<%=sid%>'
            };

            $.ajax({
                url: 'status.jsp?send',
                type: 'POST',
                contentType: 'applicaton/json',
                data: JSON.stringify(send),
                dataType: 'json'
            });

        } else {
            $('#stored-parsed').html('NO');
        }
    } else {
        $('#stored-has-value').html('NO');
    }
});
</script>
<style>
body {
    font-family: arial, sans;
}
</style>
</head><body>


<p>
user: <b><%=user.getUsername()%></b> (<%=user.getUUID()%>) <br />
addr: <b><span id="remote-host"><%=ServletUtilities.getRemoteHost(request)%></span></b> | sid=<b><%=sid%></b><br />
date/time: <b><%=now%></b> (server) | <b id="local-time"></b> (local)<br />
browser: <b id="local-agent"></b> <br />
</p>

<p>
stored has value? <b id="stored-has-value">?</b><br />
stored length: <b id="stored-length">?</b><br />
stored parsed: <b id="stored-parsed">?</b><br />
trial: <b id="stored-trial">?</b><br />
completed: <b id="stored-completed">?</b> of 120<br />
timestamp: <b id="stored-timestamp">?</b> (<span id="stored-timestamp-raw">?</span>)<br />
</p>

</body></html>
