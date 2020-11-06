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
java.lang.NumberFormatException"%>

<%
String context="context0";
context=ServletUtilities.getContext(request);
Shepherd myShepherd=new Shepherd(context);
int numFixes=0;
%>

<html>
  <head>
    <title>Consolidate Duplicate Users</title>
  </head>
  <body>
    <ol>
    <%
    myShepherd.beginDBTransaction();
    try{
      List<User> users=myShepherd.getAllUsers();
      if(users!=null && users.size()>0){
        for(int i=0;i<users.size();i++){
          User currentUser=users.get(i);
          if(currentUser!=null){

            List<User> similarUsers = UserConsolidate.getSimilarUsers(currentUser, myShepherd.getPM());
            if(similarUsers!=null){
              similarUsers.remove(currentUser);
            }
            if(similarUsers!=null && similarUsers.size()>0){
              %>
              <p><%= currentUser.getUsername() + "; " + currentUser.getEmailAddress() +"; " + currentUser.getFullName()%>,
              <%
              for(int j=0; j<similarUsers.size();j++){
                User currentSimilarUser = similarUsers.get(j);
                if(currentSimilarUser!=null){
                  %>
                  <%= currentSimilarUser.getUsername() + "; " + currentSimilarUser.getEmailAddress() + "; " + currentSimilarUser.getFullName()%>,
                  <%
                  // try{
                  //   UserConsolidate.consolidateUser(myShepherd, currentUser, currentSimilarUser);
                  // } catch(Exception e){
                      // System.out.println("error consolidating user: " + currentSimilarUser.toString() + " into user: " + currentUser.toString());
                  //   e.printStackTrace();
                  // }
                }
              }
            }
            %>
            </p>
            <%
          }
        }
      }
    }
    catch(Exception e){
    	myShepherd.rollbackDBTransaction();
    }
    finally{
    	myShepherd.closeDBTransaction();
    }
    %>
  </body>
</html>
