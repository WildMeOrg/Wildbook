<!DOCTYPE html PUBLIC "-//W3C//DTD XHTML 1.0 Transitional//EN"
        "https://www.w3.org/TR/xhtml1/DTD/xhtml1-transitional.dtd">
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
User currentUser = null;
myShepherd.setAction("automatedUserReconciliation.jsp");
myShepherd.beginDBTransaction();
int numFixes=0;
String urlLoc = "//" + CommonConfiguration.getURLLocation(request);
%>
<jsp:include page="/header.jsp" flush="true"/>
<link rel="stylesheet" href="<%=urlLoc %>/cust/mantamatcher/css/manta.css"/>
<style type="text/css">

</style>
<%
try{
  currentUser = AccessControl.getUser(request, myShepherd);
  %>
  <div class="container maincontent">
    <div id="content-container">
      <!-- js-generated html goes here -->
    </div>
    <script>
    let txt = getText("myUsers.properties");
    displayProgressBar("Loading");
    let headerTxt = getText("header.properties");
    let dedupeLessCompleteJson = {};
    dedupeLessCompleteJson['dedupeLessCompleteDesired'] = true;
    let suspendLessCredentialedJson = {};
    suspendLessCredentialedJson['suspendLessCredentialedDesired'] = true;
    $(document).ready(function() {
        doAjaxCallForDedupeLessCompleteAccounts(dedupeLessCompleteJson);
        doAjaxCallForSuspendingLowerCredentialedSimilarAccounts(suspendLessCredentialedJson);
    });

    function doAjaxCallForDedupeLessCompleteAccounts(jsonRequest){
      $.ajax({
      url: wildbookGlobals.baseUrl + '../UserConsolidate',
      type: 'POST',
      data: JSON.stringify(jsonRequest),
      dataType: 'json',
      contentType: 'application/json',
      success: function(data) {
        console.log("deleteMe data coming back from doAjaxCallForDedupeLessCompleteAccounts is: ");
        console.log(data);
          if(data.success){
            console.log("deleteMe got here a1");
            //TODO display how many there are now vs how many there were
          }
        },
          error: function(x,y,z) {
              console.warn('%o %o %o', x, y, z);
          }
      });
    }

    function doAjaxCallForSuspendingLowerCredentialedSimilarAccounts(jsonRequest) {
        $.ajax({
          url: wildbookGlobals.baseUrl + '../UserConsolidate',
          type: 'POST',
          data: JSON.stringify(jsonRequest),
          dataType: 'json',
          contentType: 'application/json',
          success: function (data) {
            console.log("deleteMe data coming back from doAjaxCallForSuspendingLowerCredentialedSimilarAccounts is: ");
            console.log(data);
            if (data.success) {
              console.log("deleteMe got here a2");
              //TODO display how many there are now vs how many there were
            }
          },
            error: function(x, y, z) {
              console.warn('%o %o %o', x, y, z);
            }
          });
      }


    function displayProgressBar(loadingStatus){
      let progressHtml = '';
      progressHtml += '<div id="progress-wrapper">';
      progressHtml += '<h4>'+ loadingStatus + '</h4>';
      progressHtml += '<div class="progress">';
      progressHtml += '<div class="progress-bar progress-bar-striped active" role="progressbar" aria-valuenow="50" aria-valuemin="0" aria-valuemax="100" style="width: 50%">';
      progressHtml += '<span class="sr-only">'+txt.PercentComplete+'</span>';
      progressHtml += '</div>';
      progressHtml += '</div>';
      progressHtml += '</div>';
      $('#content-container').empty();
      $('#content-container').append(progressHtml);
    }

    </script>

  </div>
  <%
}
catch(Exception e){
  myShepherd.rollbackDBTransaction();
}
finally{
  myShepherd.closeDBTransaction();
}
%>
<jsp:include page="/footer.jsp" flush="true"/>
