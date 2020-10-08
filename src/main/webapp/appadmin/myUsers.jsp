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
  <script>
    let txt = getText("myUsers.properties");
    $(document).ready(function() {
      populatePage();
    });

    function populatePage(){
      $('#title').html(txt.title);
    }
  </script>
  <head>
    <title id="title"></title>
  </head>
  <body>
    <jsp:include page="header.jsp" flush="true"/>
    <%
    myShepherd.beginDBTransaction();
    try{

    }
    catch(Exception e){
    	myShepherd.rollbackDBTransaction();
    }
    finally{
    	myShepherd.closeDBTransaction();
    }
    %>
    <jsp:include page="footer.jsp" flush="true"/>
  </body>
</html>
