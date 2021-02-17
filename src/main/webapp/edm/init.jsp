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

User adminUser = myShepherd.getUser("admin");

if (adminUser == null) {
   // Make user
   String passwd = request.getParameter("adminPassword");
   if (passwd == null) {
      throw new ServletException("failed to set adminPassword");
   }
   String hashedPassword = ServletUtilities.hashAndSaltPassword(passwd, salt);
   adminUser = new User("admin", hashedPassword, salt);
   adminUser.setEmailAddress("admin@example.com");
   myShepherd.getPM().makePersistent(adminUser);
}

myShepherd.commitDBTransaction();
%>


<p>
admin: <%= adminUser %>
<p>

