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
        Encounter targetEncounter = myShepherd.getEncounter("967cd20d-6170-4334-a51e-6b409f30d130");
        // User mfisher1 = myShepherd.getUserByUUID("411704e5-045e-45c7-a14c-53d8ada46bc7");
        User mfisher1_only_collaborator = myShepherd.getUserByUUID("07271b48-2d8d-4c93-a195-678204eb33b5");
        if(targetEncounter!=null && mfisher1_only_collaborator!=null){
          System.out.println("targetEncounter is: " + targetEncounter.toString());
          System.out.println("mfisher1_only_collaborator is: " + mfisher1_only_collaborator.toString());
          targetEncounter.addSubmitter(mfisher1_only_collaborator);
          System.out.println("added submitter");
        }
        // Occurrence targetOccurrence = myShepherd.getOccurrence("a3fa9ea3-dfd4-4553-bcb9-6bf7fe018a88");
        // System.out.println("targetOccurrence is: " + targetOccurrence.toString());
        // User mfisher1_only_collaborator = myShepherd.getUserByUUID("07271b48-2d8d-4c93-a195-678204eb33b5");
        // List<User> newUsersToAdd = new ArrayList<User>();
        // if(targetOccurrence!=null && mfisher1_only_collaborator!=null){
        //   newUsersToAdd.add(mfisher1_only_collaborator);
        //   System.out.println("newUsersToAdd is: " + newUsersToAdd.toString());
        //   targetOccurrence.setSubmitters(newUsersToAdd);
        //   targetOccurrence.setInformOthers(newUsersToAdd);
        // }
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
