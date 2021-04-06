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
org.ecocean.media.*,
javax.jdo.*,
java.lang.StringBuffer,
java.util.Vector,
java.util.Iterator,
org.ecocean.servlet.importer.*,
org.json.JSONObject,
java.lang.NumberFormatException"%>

<%
String context="context0";
context=ServletUtilities.getContext(request);
Shepherd myShepherd=new Shepherd(context);
String urlLoc = "//" + CommonConfiguration.getURLLocation(request);
%>

<jsp:include page="../header.jsp" flush="true"/>
    <script src="<%=urlLoc %>/tools/simplePagination/jquery.simplePagination.js"></script>
    <link type="text/css" rel="stylesheet" href="<%=urlLoc %>/tools/simplePagination/simplePagination.css"/>
    <div id="match-results"><i>searching....</i></div>
    <div id="pagination-section"></div>

    </div>

    <%
    myShepherd.beginDBTransaction();
    try{
      %>

      <script type="text/javascript">
        $(document).ready(function() {
        });

        function exampleFn(attr){
        }
      </script>
      <%

      Encounter targetEncounter = myShepherd.getEncounter("68f7d66f-f6fa-4554-bfba-aa6c11bb942f");
      
      myShepherd.updateDBTransaction();
      myShepherd.commitDBTransaction();
      myShepherd.beginDBTransaction();

      }catch(Exception e){
        e.printStackTrace();
      }
      finally{
        myShepherd.rollbackDBTransaction();
      	myShepherd.closeDBTransaction();
      }

    %>
<jsp:include page="../footer.jsp" flush="true"/>
