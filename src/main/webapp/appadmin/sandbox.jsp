<!DOCTYPE html PUBLIC "-//W3C//DTD XHTML 1.0 Transitional//EN"
        "http://www.w3.org/TR/xhtml1/DTD/xhtml1-transitional.dtd">
<%@ page contentType="text/html; charset=utf-8" language="java" import="org.joda.time.LocalDateTime,
org.joda.time.format.DateTimeFormatter,
org.joda.time.format.ISODateTimeFormat,java.net.*,
org.ecocean.grid.*,
org.ecocean.servlet.ServletUtilities,
java.io.*,java.util.*,
java.io.FileInputStream,
java.io.File,
java.io.FileNotFoundException,
org.ecocean.*,
org.ecocean.servlet.*,
javax.jdo.*,
java.lang.StringBuffer,
java.util.Vector,
java.util.Iterator,
org.ecocean.servlet.importer.*,
java.lang.NumberFormatException"%>

<%
String context="context0";
context=ServletUtilities.getContext(request);
Shepherd myShepherd=new Shepherd(context);
int numFixes=0;
%>

<jsp:include page="../header.jsp" flush="true"/>
    <script>
      let txt = getText("myUsers.properties");
    </script>
    <%
    myShepherd.beginDBTransaction();
    try{

        Role newRole1=new Role("jenna","Namunyak");
        newRole1.setContext("context0");
        myShepherd.getPM().makePersistent(newRole1);

        newRole1=new Role("jenna","Loisaba");
        newRole1.setContext("context0");
        myShepherd.getPM().makePersistent(newRole1);

        newRole1=new Role("jenna","Lewa");
        newRole1.setContext("context0");
        myShepherd.getPM().makePersistent(newRole1);

        newRole1=new Role("jenna","Mugie");
        newRole1.setContext("context0");
        myShepherd.getPM().makePersistent(newRole1);

        newRole1=new Role("michael","Ol Pejeta");
        newRole1.setContext("context0");
        myShepherd.getPM().makePersistent(newRole1);

        newRole1=new Role("michael","Ol Pejeta");
        newRole1.setContext("context0");
        myShepherd.getPM().makePersistent(newRole1);

        newRole1=new Role("michael","Ol Pejeta");
        newRole1.setContext("context0");
        myShepherd.getPM().makePersistent(newRole1);

        myShepherd.commitDBTransaction();
      	myShepherd.beginDBTransaction();


        // User userToRetain = myShepherd.getUserByUUID("702df060-0151-49f3-834a-4c3cd383c961");//"702df060-0151-49f3-834a-4c3cd383c961");
        // User userToBeConsolidated = myShepherd.getUserByUUID("d9ff86dd-88b5-4de8-aeaf-ea161b9e41e2");//"0fa7dc0b-107e-43e0-942d-cc2326f09036");  //d9ff86dd-88b5-4de8-aeaf-ea161b9e41e2 = craig o'neil
        //
        // List<User> similarUsers = UserConsolidate.getSimilarUsers(userToBeConsolidated, myShepherd.getPM());
        // System.out.println("similarUsers are: " + similarUsers.toString());


      }finally{
        myShepherd.rollbackDBTransaction();
      	myShepherd.closeDBTransaction();
      }

    %>
<jsp:include page="../footer.jsp" flush="true"/>
