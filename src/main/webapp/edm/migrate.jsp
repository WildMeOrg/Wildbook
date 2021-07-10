<%@ page contentType="text/plain; charset=utf-8" language="java"
     import="org.ecocean.*,
java.util.Map,
java.io.BufferedReader,
java.io.IOException,
java.io.InputStream,
java.io.InputStreamReader,
java.io.File,
java.util.Arrays,
org.json.JSONObject,
java.lang.reflect.*,

org.ecocean.customfield.*,

org.ecocean.media.*
              "
%>




<%

Shepherd myShepherd = new Shepherd("context0");

//individualCount
String fieldName = "groupBehavior";

/*
Class cls = Occurrence.class;
for (Field f : cls.getDeclaredFields()) {
    out.println(f);
}
*/

Field field = Occurrence.class.getDeclaredField(fieldName);

CustomFieldDefinition cfd = new CustomFieldDefinition(field);
out.println(cfd);

%>



