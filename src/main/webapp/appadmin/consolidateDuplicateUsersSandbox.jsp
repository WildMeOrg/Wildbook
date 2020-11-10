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
        User userToRetain = myShepherd.getUserByUUID("702df060-0151-49f3-834a-4c3cd383c961");//"702df060-0151-49f3-834a-4c3cd383c961");
        User userToBeConsolidated = myShepherd.getUserByUUID("0fa7dc0b-107e-43e0-942d-cc2326f09036");//"0fa7dc0b-107e-43e0-942d-cc2326f09036");

        //lifted directly from consolidateImportTaskCreator method in UserConsolidate.java
        System.out.println("consolidating import tasks created by user: " + userToBeConsolidated.toString() + " into user: " + userToRetain.toString());
        String filter="SELECT FROM org.ecocean.servlet.importer.ImportTask WHERE creator.uuid==\""+userToBeConsolidated.getUUID()+"\""; // && user.uuid==\""+userToBeConsolidated.getUUID()+"\" VARIABLES org.ecocean.User user"
        System.out.println("query is: " + filter);
      	List<ImportTask> impTasks=new ArrayList<ImportTask>();
        Query query=myShepherd.getPM().newQuery(filter);
        Collection c = (Collection) (query.execute());
        if(c!=null){
          impTasks=new ArrayList<ImportTask>(c);
        }
        query.closeAll();
        System.out.println("got here 1");
        if(impTasks!=null && impTasks.size()>0){
          System.out.println("got here 1.5 impTasks not null and size is: " + impTasks.size());
          for(int i=0; i<impTasks.size(); i++){
            System.out.println("got here 2 with index: " + i);
            ImportTask currentImportTask = impTasks.get(i);
            if(currentImportTask.getCreator()!=null & currentImportTask.getCreator().equals(userToBeConsolidated)){
              System.out.println("setting creator");
              currentImportTask.setCreator(userToRetain);
              // myShepherd.commitDBTransaction();
              // myShepherd.beginDBTransaction();
            }
          }
          // myShepherd.commitDBTransaction();
          // myShepherd.beginDBTransaction();
          System.out.println("got here 3");
        }else{
          System.out.println("got here instead 1.25");
        }
        myShepherd.commitDBTransaction();
        myShepherd.beginDBTransaction();
        System.out.println("got here 3.5 just committed transaction and started new one in consolidateImportTaskCreator. Ending consolidateImportTaskCreator....");
        }catch(Exception e){
        System.out.println("error consolidating user: ");
        e.printStackTrace();
      }finally{
        myShepherd.rollbackDBTransaction();
      	myShepherd.closeDBTransaction();
      }

    %>
<jsp:include page="../footer.jsp" flush="true"/>
