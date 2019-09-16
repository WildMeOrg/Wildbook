<%@ page contentType="text/html; charset=utf-8" 
		language="java"
        import="org.ecocean.servlet.ServletUtilities,
java.io.IOException,
org.json.JSONObject,
org.json.JSONArray,
java.util.List,
java.util.ArrayList,
org.ecocean.servlet.ReCAPTCHA,
org.ecocean.*, java.util.Properties" %>

<%

String context = ServletUtilities.getContext(request);
Shepherd myShepherd = new Shepherd(context);
myShepherd.setAction("toggleMode.jsp");
myShepherd.beginDBTransaction();

boolean uwMode = Util.booleanNotFalse(SystemValue.getString(myShepherd, "uwMode"));

out.println("<p>uwMode <i>was</i> <b>" + uwMode + "</b> ... </p>");

uwMode = !uwMode;
SystemValue.set(myShepherd, "uwMode", Boolean.toString(uwMode));

out.println("<p>uwMode <i>is now</i> <b>" + uwMode + "</b> ... </p>");

myShepherd.commitDBTransaction();

%>
