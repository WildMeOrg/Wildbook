<%@ page contentType="text/html; charset=utf-8" language="java"
     import="org.ecocean.*,
java.util.Map,
java.io.BufferedReader,
java.io.IOException,
java.io.InputStream,
java.io.InputStreamReader,
java.io.File,
org.ecocean.servlet.ServletUtilities,
org.json.JSONObject,

org.ecocean.media.*
              "
%>




<%

Shepherd myShepherd = new Shepherd("context0");

myShepherd.beginDBTransaction();


String salt = ServletUtilities.getSalt().toHex();
String hashedPassword = ServletUtilities.hashAndSaltPassword("test1234", salt);
User newUser = new User("admin", hashedPassword, salt);
newUser.setEmailAddress("admin@example.com");
myShepherd.getPM().makePersistent(newUser);

//Encounter enc = myShepherd.getEncounter(Util.generateUUID());

myShepherd.commitDBTransaction();

%>

<h1>initialized</h1>

<p><%=newUser%></p>


