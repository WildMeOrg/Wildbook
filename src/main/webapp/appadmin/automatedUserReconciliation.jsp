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
    <div id="originalCounts"></div>
    <div id="updateCounts"></div>
    <div id="content-container">
      <!-- js-generated html goes here -->
    </div>
    <script>
    let txt = getText("myUsers.properties");
    displayProgressBar("Loading");
    let headerTxt = getText("header.properties");
    $(document).ready(function() {
      populateNewButtonAndDisplay(null, '<h4>Step 1. Deduplicate Less Complete Accounts</h4>', '<button onclick="ajaxDedupeLessCompleteWrapper()">Do It</button>');
      populateCounts("User counts", "originalCounts", false);
    });

    function populateCounts(label, divId, includeUpdateButton){
      let theHtml = '';
      theHtml += '<h2>' + label + '</h2>';
      let totalUserCount = '<%= myShepherd.getAllUsers().size()%>';
      theHtml += '<p>Total # Users: ' + totalUserCount + '</p>';
      <%
        List < User > usersWithNullEmail=new ArrayList < User > ();
        String filter = "SELECT FROM org.ecocean.User WHERE emailAddress == null";
        Query query = myShepherd.getPM().newQuery(filter);
        Collection c = (Collection)(query.execute());
        if (c != null) usersWithNullEmail = new ArrayList < User > (c);
        query.closeAll();
      %>
      let usersWithNullEmail = '<%= usersWithNullEmail.size() %>';
      <%
        ArrayList < User > usersWithNullUsername=new ArrayList < User > ();
        filter = "SELECT FROM org.ecocean.User WHERE username == null";
        query = myShepherd.getPM().newQuery(filter);
        c = (Collection)(query.execute());
        if (c != null) usersWithNullUsername = new ArrayList < User > (c);
        query.closeAll();
      %>
      let usersWithNullUsername = '<%= usersWithNullUsername.size() %>';
      theHtml += '<p>Total # Users with null email addresses: ' + usersWithNullEmail + '</p>';
      theHtml += '<p>Total # Users with null usernames: ' + usersWithNullUsername + '</p>';

      if(includeUpdateButton){
        theHtml += '<button onclick="updateCounts()">Update</button>';
      }
      $("#" + divId).empty();
      $("#" + divId).append(theHtml);
    }

    function updateCounts(){
      populateCounts("Current counts", 'updateCounts', true);
    }

    function populateNewButtonAndDisplay(data, headerText, buttonText){
      let theHtml = '';
        theHtml += headerText;
        theHtml += buttonText;
      $('#content-container').empty();
      $('#content-container').append(theHtml);
    }

    function populateErrorAndDisplay(errorMsg) {
        let theHtml = '';
        theHtml += '<p>' + errorMsg + '</p>';
        $('#content-container').empty();
        $('#content-container').append(theHtml);
      }

    function populateFinal(data){
      let theHtml = '';
      theHtml += '<p> You\'re done!</p>';
      $('#content-container').empty();
      $('#content-container').append(theHtml);
    }

    function ajaxDedupeLessCompleteWrapper(){
      let dedupeLessCompleteJson = {};
      dedupeLessCompleteJson['dedupeLessCompleteDesired'] = true;
      doAjaxCallForDedupeLessCompleteAccounts(dedupeLessCompleteJson);
    }

    function ajaxSuspendLowerCredentialedWrapper() {
      let suspendLessCredentialedJson = {};
      suspendLessCredentialedJson['suspendLessCredentialedDesired'] = true;
      doAjaxCallForSuspendingLowerCredentialedSimilarAccounts(suspendLessCredentialedJson);
    }

    function ajaxOrphanEncountersToPublicWrapper() {
      let assignOrphanedEncountersToPublicJson = {};
      assignOrphanedEncountersToPublicJson['assignOrphanedEncountersToPublicDesired'] = true;
      doAjaxCallForAssigningOrphanEncountersToPublic(assignOrphanedEncountersToPublicJson);
    }

    function ajaxRenameUsernamelessAnonymousWrapper() {
      let renameUsernamelessToAnonymousJson = {};
      renameUsernamelessToAnonymousJson['renameUsernamelessToAnonymousDesired'] = true;
      doAjaxCallForRenamingUsernamelessToAnonymous(renameUsernamelessToAnonymousJson);
    }

    function ajaxSuspendEmaillessOrInvalidWrapper() {
      let suspendEmaillessOrInvalidEmailJson = {};
      suspendEmaillessOrInvalidEmailJson['suspendEmaillessOrInvalidEmailDesired'] = true;
      doAjaxCallForSuspendingEmaillessOrInvalidEmails(suspendEmaillessOrInvalidEmailJson);
    }

    function ajaxSuspendEmaillessAndUsernamelessWrapper() {
      let suspendUsersMissingEmailAndUsernameJson = {};
      suspendUsersMissingEmailAndUsernameJson['suspendUsersMissingEmailAndUsernameDesired'] = true;
      doAjaxCallForSuspendingEmaillessAndUsernameless(suspendUsersMissingEmailAndUsernameJson);
    }

    function doAjaxCallForDedupeLessCompleteAccounts(jsonRequest){
      displayProgressBar("De-duplicating less complete users (this step can take a VERY long time)...");
      $.ajax({
      url: wildbookGlobals.baseUrl + '../UserConsolidate',
      type: 'POST',
      data: JSON.stringify(jsonRequest),
      dataType: 'json',
      contentType: 'application/json',
      success: function(data) {
        console.log("data from doAjaxCallForDedupeLessCompleteAccounts:");
        console.log(data);
          if(data && data.consolidateLessCompleteAccountsResultsJson && data.consolidateLessCompleteAccountsResultsJson.success){
            populateNewButtonAndDisplay(data, '<h4>Step 2. Suspend Lower-credentialed Accounts</h4>', '<button onclick="ajaxSuspendLowerCredentialedWrapper()">Do It</button>');
          }else{
            populateErrorAndDisplay("Whoops. That step was not successful. Please refer to the logs for more detail.");
          }
        },
          error: function(x,y,z) {
              console.warn('%o %o %o', x, y, z);
          }
      });
    }

    function doAjaxCallForSuspendingLowerCredentialedSimilarAccounts(jsonRequest) {
      displayProgressBar("Suspending lower credentialed similar accounts (this step also takes some time)...");
        $.ajax({
          url: wildbookGlobals.baseUrl + '../UserConsolidate',
          type: 'POST',
          data: JSON.stringify(jsonRequest),
          dataType: 'json',
          contentType: 'application/json',
          success: function (data) {
            console.log("data from doAjaxCallForSuspendingLowerCredentialedSimilarAccounts:");
            console.log(data);
            if (data && data.suspendLowerCredAccountsResultsJson && data.suspendLowerCredAccountsResultsJson.success) {
              populateNewButtonAndDisplay(null, '<h4>Step 3. Assign Orphan Encounters to a Public Account</h4>', '<button onclick="ajaxOrphanEncountersToPublicWrapper()">Do It</button>');
            } else {
              populateErrorAndDisplay("Whoops. That step was not successful. Please refer to the logs for more detail.");
            }
          },
            error: function(x, y, z) {
              console.warn('%o %o %o', x, y, z);
            }
          });
      }

      function doAjaxCallForAssigningOrphanEncountersToPublic(jsonRequest) {
        displayProgressBar("Assigning orphaned encounters to Public user...");
          $.ajax({
            url: wildbookGlobals.baseUrl + '../UserConsolidate',
            type: 'POST',
            data: JSON.stringify(jsonRequest),
            dataType: 'json',
            contentType: 'application/json',
            success: function (data) {
              console.log("data from doAjaxCallForAssigningOrphanEncountersToPublic:");
              console.log(data);
              if (data && data.makeEncountersMissingSubmittersPublicJsonResults && data.makeEncountersMissingSubmittersPublicJsonResults.success) {
                populateNewButtonAndDisplay(null, '<h4>Step 4. Rename Usernameless Accounts To Anonymous_uuid</h4>', '<button onclick="ajaxRenameUsernamelessAnonymousWrapper()">Do It</button>');
              } else {
                populateErrorAndDisplay("Whoops. That step was not successful. Please refer to the logs for more detail.");
              }
            },
            error: function (x, y, z) {
              console.warn('%o %o %o', x, y, z);
            }
          });
        }

        function doAjaxCallForRenamingUsernamelessToAnonymous(jsonRequest) {
          displayProgressBar("Renaming usernameless users to anonymous...");
            $.ajax({
              url: wildbookGlobals.baseUrl + '../UserConsolidate',
              type: 'POST',
              data: JSON.stringify(jsonRequest),
              dataType: 'json',
              contentType: 'application/json',
              success: function (data) {
                console.log("data from doAjaxCallForRenamingUsernamelessToAnonymous");
                console.log(data);
                if (data && data.assignUsernamelessToAnonymousJsonResults && data.assignUsernamelessToAnonymousJsonResults.success) {
                  populateNewButtonAndDisplay(null, '<h4>Step 5. "Suspend" emailless or invalid emails</h4>', '<button onclick="ajaxSuspendEmaillessOrInvalidWrapper()">Do It</button>');
                } else {
                  populateErrorAndDisplay("Whoops. That step was not successful. Please refer to the logs for more detail.");
                }
              },
              error: function (x, y, z) {
                console.warn('%o %o %o', x, y, z);
              }
            });
          }

          function doAjaxCallForSuspendingEmaillessOrInvalidEmails(jsonRequest) {
            displayProgressBar("Suspending emailless or invalid email accounts...");
              $.ajax({
                url: wildbookGlobals.baseUrl + '../UserConsolidate',
                type: 'POST',
                data: JSON.stringify(jsonRequest),
                dataType: 'json',
                contentType: 'application/json',
                success: function (data) {
                  console.log("data from doAjaxCallForSuspendingEmaillessOrInvalidEmails");
                  console.log(data);
                  if (data && data.assignEmaillessOrInvalidEmailAddressesJsonResults && data.assignEmaillessOrInvalidEmailAddressesJsonResults.success) {
                    populateNewButtonAndDisplay(null, '<h4>Step 6. "Suspend" emailless and usernameless</h4>', '<button onclick="ajaxSuspendEmaillessAndUsernamelessWrapper()">Do It</button>');
                  } else {
                    populateErrorAndDisplay("Whoops. That step was not successful. Please refer to the logs for more detail.");
                  }
                },
                error: function (x, y, z) {
                  console.warn('%o %o %o', x, y, z);
                }
              });
            }

            function doAjaxCallForSuspendingEmaillessAndUsernameless(jsonRequest) {
              displayProgressBar("Suspending emailless and usernameless accounts...");
                $.ajax({
                  url: wildbookGlobals.baseUrl + '../UserConsolidate',
                  type: 'POST',
                  data: JSON.stringify(jsonRequest),
                  dataType: 'json',
                  contentType: 'application/json',
                  success: function (data) {
                    console.log("data from doAjaxCallForSuspendingEmaillessAndUsernameless");
                    console.log(data);
                    if (data && data.assignEmaillessAndUsernamelessEmailAddressesAndUsernamesJsonResults && data.assignEmaillessAndUsernamelessEmailAddressesAndUsernamesJsonResults.success) {
                      populateFinal(data);
                    } else {
                      populateErrorAndDisplay("Whoops. That step was not successful. Please refer to the logs for more detail.");
                    }
                  },
                  error: function (x, y, z) {
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
