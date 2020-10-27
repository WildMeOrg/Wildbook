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
User currentUser = null;
myShepherd.setAction("myUsers.jsp");
myShepherd.beginDBTransaction();
int numFixes=0;
String urlLoc = "//" + CommonConfiguration.getURLLocation(request);
//TODO which of the above not needed?
%>
<jsp:include page="../header.jsp" flush="true"/>
<link rel="stylesheet" href="<%=urlLoc %>/cust/mantamatcher/css/manta.css"/>
<style type="text/css">
  .sleeker-button{
    width: auto;
    height: 3em;
    margin-left: 0.2em;
    padding: 0 5px;
    vertical-align: middle;
    display: inline-block;
    background-color: @whaleSharkblue;
  }
  .flex-container{
    display: flex;
  }
  .candidate-block {
    background-color: #DADADA;
    display: block;
    width: fit-content;
    padding: 1.5em;
    margin-bottom: 0.5em;
  }
  .radio-container{
    vertical-align: middle;
    width: fit-content;
    padding-left: 0.5em;
    padding-top: 1.5em;
    padding-bottom: 1.5em;
    display: flex;
    justify-content: center;
    align-items: center;
  }
  .disabled-btn { /* moving this to *.less AND moving that beneath buttons custom import in manta.less did not work. */
    background:#62676d30;
    border:0;
    color:#fff;
    line-height:2em;
    padding:7px 13px;
    font-weight:300;
    vertical-align:middle;
    margin-right:10px;
    margin-top:15px
  }

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
    $(document).ready(function() {
      let hasUserAlreadyMadeConsolidationChoicesJson = {};
      hasUserAlreadyMadeConsolidationChoicesJson['username'] = '<%= currentUser.getUsername()%>';
      hasUserAlreadyMadeConsolidationChoicesJson['action'] = 'getUserConsolidationChoiceStatus';
      doAjaxCallForUserPreferenceGet(hasUserAlreadyMadeConsolidationChoicesJson);
    });

    function doAjaxForGetDuplicateUsers(userDuplicateJsonRequest){
      $.ajax({
      url: wildbookGlobals.baseUrl + '../UserConsolidate',
      type: 'POST',
      data: JSON.stringify(userDuplicateJsonRequest),
      dataType: 'json',
      contentType: 'application/json',
      success: function(data) {
          console.log("data coming back is:");
          console.log(data);
          let users = data.users;
          if(users && users.length>0){
            for(let i=0; i<users.length; i++){
              populateCandidateUser(users[i].username, users[i].email,users[i].fullname);
            }
          }else{
            populateNoDuplicates();
          }
          },
          error: function(x,y,z) {
              console.warn('%o %o %o', x, y, z);
          }
      });
    }

    function populateCandidateUser(username, email, fullname){
      let candidateHtml = '';
      candidateHtml += '<div class="flex-container">';
      candidateHtml += '<div class="candidate-block">';
      candidateHtml += '<p><strong>';
      candidateHtml += txt.username;
      candidateHtml += '</strong> ';
      if(username){
        candidateHtml += username;
      }else{
        candidateHtml += '-';
      }
      candidateHtml += '</p>';
      candidateHtml += '<p><strong>';
      candidateHtml += txt.email;
      candidateHtml += '</strong> ';
      if(email){
        candidateHtml += email;
      }else{
        candidateHtml += '-';
      }
      candidateHtml += '</p>';
      candidateHtml += '<p><strong>';
      candidateHtml += txt.name;
      candidateHtml += '</strong> ';
      if(fullname){
        candidateHtml += fullname;
      }else{
        candidateHtml += '-';
      }
      candidateHtml += '</p>';
      candidateHtml += '</div>';
      candidateHtml +=  '<div class="radio-container">';
      candidateHtml +=  '<div class="radio-button-pair-container">';
      candidateHtml +=  '<input type="radio" id="merge-radio" name="radio__' + username+ '__'+fullname +'" value="merge" data-id="radio__' + username+ '__' +email+'__'+fullname +'" onclick="radioClicked()">';
      candidateHtml +=  '<label for="merge-radio">' + txt.merge + '</label>';
      candidateHtml +=  '<br>';
      candidateHtml +=  '<input type="radio" id="noClaim-radio" name="radio__' + username+ '__'+fullname +'" value="noClaim" data-id="radio__' + username+ '__' +email+'__'+fullname +'" onclick="radioClicked()">';
      candidateHtml +=  '<label for="noClaim-radio">' + txt.doNotClaim + '</label>';
      candidateHtml +=  '</div>';
      candidateHtml +=  '</div>';
      candidateHtml += '</div>';
      $('#candidate-users-container').append(candidateHtml);
    }

    function populateNoDuplicates(){
      let noDuplicatesHtml = '';
      noDuplicatesHtml += '<h3>' + txt.noDuplicates + '</h3>';
      // noDuplicatesHtml += '';
      // noDuplicatesHtml += '';
      $('#content-container').empty();
      $('#content-container').append(noDuplicatesHtml);
    }

    function populatePage(){
      // console.log("got here 2");
      $('#title').html(txt.title);
      let pageHtml = '';
      pageHtml += '<div id="candidate-users-container">';
      pageHtml += '</div>';
      pageHtml += '<div class="submission-section">';
      pageHtml += '<button class="disabled-btn proj-action-btn" id="disabled-apply-user-consolidation-button">' + txt.applyChanges + '<span class="button-icon" aria-hidden="true"></span></button>';
      pageHtml += '<button type="button" id="apply-user-consolidation-button" onclick="applyButtonClicked();" class="sleeker-button" style="display: none;">';
      pageHtml += '<span>' + txt.applyChanges + '</span><span class="button-icon" aria-hidden="true"></span>';
      pageHtml += '</button>';
      pageHtml += '</div>';
      $('#content-container').empty();
      $('#content-container').append(pageHtml);
    }

    function applyButtonClicked(){
      //TODO confirm button
      console.log("applyButtonClicked clicked!");
      let radioElements = $('[data-id^=radio_]');
      if(radioElements){
        let ajaxJson = {};
        ajaxJson['mergeDesired'] = false;
        ajaxJson['username'] = '<%= currentUser.getUsername()%>';
        ajaxJson['userInfoArr'] = [];
        for(let i=0; i<radioElements.length; i++){
          let currentRadioElement = radioElements[i];
          let isChecked = currentRadioElement.checked;
          if(isChecked){
            let currentVal = currentRadioElement.value;
            console.log("currentVal is: " + currentVal);
            console.log("currentRadioElement desired is:");
            if(currentVal==="merge"){
              let currentUserDetails = $(currentRadioElement).data().id.split("__");
              // console.log(currentUserDetails);
              ajaxJson['mergeDesired'] = true;
              currentUserDetails.shift();
              console.log(currentUserDetails);
              //TODO track these users submitted and compare them to the results in the callback
              ajaxJson['userInfoArr'].push({username: currentUserDetails[0], email: currentUserDetails[1], fullname: currentUserDetails[2]});
            }
          }
        }
        doAjaxCallForMergingUser(ajaxJson);
      }
    }

    function doAjaxCallForMergingUser(jsonRequest){
      displayProgressBar();
      console.log("jsonRequest in doAjaxCallForMergingUser is: ");
      console.log(jsonRequest);
      $.ajax({
      url: wildbookGlobals.baseUrl + '../UserConsolidate',
      type: 'POST',
      data: JSON.stringify(jsonRequest),
      dataType: 'json',
      contentType: 'application/json',
      success: function(data) {
          console.log("data coming back are:");
          console.log(data);
          let responseArray =[];
          jsonRequest.userInfoArr.forEach(userInfoObj =>{
            let keyForDataInResponseChecking = "details_"+userInfoObj.username+"__"+userInfoObj.email+"__"+userInfoObj.fullname;
            console.log("keyForDataInResponseChecking is: " + keyForDataInResponseChecking);
            let valuesOfUserInfoObjPrettified = Object.values(userInfoObj).join(", ");
            console.log("valuesOfUserInfoObjPrettified is: " + valuesOfUserInfoObjPrettified);
            if(data[keyForDataInResponseChecking]){
              console.log(valuesOfUserInfoObjPrettified + ": " + data[keyForDataInResponseChecking]);
              responseArray.push(valuesOfUserInfoObjPrettified + ": " + data[keyForDataInResponseChecking]);
            }
          });
          displayConfirmations(responseArray);
          if(data.success){
            console.log("marking as done for user");
            markUserDedupeAsDoneForUser();
          }
          },
          error: function(x,y,z) {
              console.warn('%o %o %o', x, y, z);
          }
      });
    }

    function displayProgressBar(){
      let progressHtml = '';
      progressHtml += '<div class="progress">';
      progressHtml += '<div class="progress-bar progress-bar-striped active" role="progressbar" aria-valuenow="50" aria-valuemin="0" aria-valuemax="100" style="width: 50%">';
      progressHtml += '<span class="sr-only">'+txt.PercentComplete+'</span>';
      progressHtml += '</div>';
      progressHtml += '</div>';
      $('#content-container').empty();
      $('#content-container').append(progressHtml);
    }

    function displayConfirmations(arrayOfResponses){
      let confirmationHtml = '';
      confirmationHtml += '<h3>'+txt.completed+'</h3>'
      arrayOfResponses.forEach(response =>{
        confirmationHtml += '<p>' + response + '</p>';
      });
      $('#content-container').empty();
      $('#content-container').append(confirmationHtml);
    }

    function markUserDedupeAsDoneForUser(){
      console.log("markUserDedupeAsDoneForUser entered");
      let userPreferenceUpdateConsolidationChoiceJson = {};
      userPreferenceUpdateConsolidationChoiceJson['action']='setUserConsolidationChoicesTrue';
      doAjaxCallForUserPreferenceUpdate(userPreferenceUpdateConsolidationChoiceJson);
    }

    function doAjaxCallForUserPreferenceUpdate(jsonRequest){
      console.log("doAjaxCallForUserPreferenceUpdate entered");
      displayProgressBar();
      console.log("jsonRequest in doAjaxCallForUserPreferenceUpdate is: ");
      console.log(jsonRequest);
      $.ajax({
      url: wildbookGlobals.baseUrl + '../UserPreferences',
      type: 'POST',
      data: JSON.stringify(jsonRequest),
      dataType: 'json',
      contentType: 'application/json',
      success: function(data) {
          console.log("data coming back from doAjaxCallForUserPreferenceUpdate are:");
          console.log(data);
          if(data.success){
            console.log("would be reloading page...");
            // TODO reload page?
          }
          },
          error: function(x,y,z) {
              console.warn('%o %o %o', x, y, z);
          }
      });
    }

    function doAjaxCallForUserPreferenceGet(jsonRequest){
      console.log("doAjaxCallForUserPreferenceGet entered");
      displayProgressBar();
      console.log("jsonRequest in doAjaxCallForUserPreferenceGet is: ");
      console.log(jsonRequest);
      $.ajax({
      url: wildbookGlobals.baseUrl + '../UserPreferences',
      type: 'POST',
      data: JSON.stringify(jsonRequest),
      dataType: 'json',
      contentType: 'application/json',
      success: function(data) {
          console.log("data coming back from doAjaxCallForUserPreferenceGet are:");
          console.log(data);
          if(data.success){
            console.log("success!")
            if(data.userConsolidationChoicesMade==="false"){
              console.log("userConsolidationChoicesMade is: " + data.userConsolidationChoicesMade + ' and is expected to be false');
              let userDuplicateJsonRequest = {};
              userDuplicateJsonRequest['username'] = '<%= currentUser.getUsername()%>';
              console.log("userDuplicateJsonRequest is: ")
              console.log(userDuplicateJsonRequest);
              if(userDuplicateJsonRequest){
                populatePage();
                doAjaxForGetDuplicateUsers(userDuplicateJsonRequest);
              }
            }else{
              console.log("userConsolidationChoicesMade is: " + data.userConsolidationChoicesMade + ' and is expected to be true');
              displayAlreadyMadeChoices();
            }
          }
          },
          error: function(x,y,z) {
              console.warn('%o %o %o', x, y, z);
          }
      });
    }

    function displayAlreadyMadeChoices(){
      console.log("displayAlreadyMadeChoices entered");
      let displayAlreadyMadeHtml = '';
      displayAlreadyMadeHtml += '<h3>TODO you already did this</h3>';
      displayAlreadyMadeHtml += '<button onclick="changeUserConsolidationChoicesMadeToFalse()">I forgot to deduplicate something. I would like to try again</button>';
      $('#content-container').empty();
      $('#content-container').append(displayAlreadyMadeHtml);
    }

    function changeUserConsolidationChoicesMadeToFalse(){
      let userPreferenceUpdateConsolidationChoiceJson = {};
      userPreferenceUpdateConsolidationChoiceJson['action']='setUserConsolidationChoicesFalse';
      doAjaxCallForUserPreferenceUpdate(userPreferenceUpdateConsolidationChoiceJson);
    }


    function radioClicked(){
      let radioElements = $('[data-id^=radio_]');
      let uniqueValues = [];
      let numberRadioButtonsClicked = 0;
      if(radioElements){
        for(let i=0; i<radioElements.length; i++){
          let currentRadioElement = radioElements[i];
          let isChecked = currentRadioElement.checked;
          if(isChecked){numberRadioButtonsClicked++;}
          let currentVal = currentRadioElement.value;
          if(!uniqueValues.includes(currentVal)){uniqueValues.push(currentVal);}
        }
        let numberOfRadioButtonSelectionsThatShouldHaveBeenMade = radioElements.length/uniqueValues.length;
        if(numberOfRadioButtonSelectionsThatShouldHaveBeenMade == numberRadioButtonsClicked){
          enableApplyChanges();
        }else{
          disableApplyChanges();
        }
      }
    }

    function enableApplyChanges(){
      // console.log("enableApplyChanges entered");
      $('#disabled-apply-user-consolidation-button').hide();
      $('#apply-user-consolidation-button').show();
    }

    function disableApplyChanges(){
      // console.log("disableApplyChanges entered");
      $('#apply-user-consolidation-button').hide();
      $('#disabled-apply-user-consolidation-button').show();
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
<jsp:include page="../footer.jsp" flush="true"/>
