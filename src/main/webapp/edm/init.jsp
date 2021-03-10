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

   System.out.println("[init.jsp.]: Making new admin user.");

   // Make user
   String passwd = request.getParameter("adminPassword");
   if (passwd == null) {
      throw new ServletException("failed to set adminPassword");
   }
   String hashedPassword = ServletUtilities.hashAndSaltPassword(passwd, salt);
   adminUser = new User("admin", hashedPassword, salt);
   adminUser.setEmailAddress("admin@example.com");
   myShepherd.getPM().makePersistent(adminUser);
} else {
   System.out.println("[init.jsp.]: There was already an admin user in the system.");
}


System.out.println("[init.jsp.]: Trying to set roles on first-run admin user.");

// All meaningful roles, just give admin godmode for now
Role newRole1=new Role("admin","admin");
newRole1.setContext("context0");
myShepherd.getPM().makePersistent(newRole1);

Role newRole2=new Role("admin","orgAdmin");
newRole2.setContext("context0");
myShepherd.getPM().makePersistent(newRole2);

Role newRole3=new Role("admin","researcher");
newRole3.setContext("context0");
myShepherd.getPM().makePersistent(newRole3);

Role newRole4=new Role("admin", "machinelearning");
newRole4.setContext("context0");
myShepherd.getPM().makePersistent(newRole4);

Role newRole5=new Role("admin","rest");
newRole5.setContext("context0");
myShepherd.getPM().makePersistent(newRole5);

myShepherd.commitDBTransaction();
myShepherd.closeDBTransaction();

System.out.println("[init.jsp.]: Roles set.");

%>


<p>
admin: <%= adminUser %>
<p>

