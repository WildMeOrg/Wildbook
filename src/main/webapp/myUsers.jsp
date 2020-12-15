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
myShepherd.setAction("myUsers.jsp");
myShepherd.beginDBTransaction();
int numFixes=0;
String urlLoc = "//" + CommonConfiguration.getURLLocation(request);
//TODO which of the above not needed?
%>
<jsp:include page="/header.jsp" flush="true"/>
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
    let headerTxt = getText("header.properties");
    $(document).ready(function() {
      let userEmail = '<%= currentUser.getEmailAddress()%>';
      if(isValidEmail(userEmail)){
        let hasUserAlreadyMadeConsolidationChoicesJson = {};
        hasUserAlreadyMadeConsolidationChoicesJson['username'] = '<%= currentUser.getUsername()%>';
        hasUserAlreadyMadeConsolidationChoicesJson['action'] = 'getUserConsolidationChoiceStatus';
        doAjaxCallForUserPreferenceGet(hasUserAlreadyMadeConsolidationChoicesJson);
      }else{
        promptUserToUpdateTheirEmailAddress(userEmail);
      }
    });

    function isValidEmail(email) {
      if(email){
        var re = /^(([^<>()\[\]\\.,;:\s@"]+(\.[^<>()\[\]\\.,;:\s@"]+)*)|(".+"))@((\[[0-9]{1,3}\.[0-9]{1,3}\.[0-9]{1,3}\.[0-9]{1,3}\])|(([a-zA-Z\-0-9]+\.)+[a-zA-Z]{2,}))$/;
        return re.test(email.toLowerCase()) && email;
      } else {
        return false;
      }
    }

    function doAjaxCallForUserPreferenceGet(jsonRequest){
      displayProgressBar();
      $.ajax({
      url: wildbookGlobals.baseUrl + '../UserPreferences',
      type: 'POST',
      data: JSON.stringify(jsonRequest),
      dataType: 'json',
      contentType: 'application/json',
      success: function(data) {
          if(data.success){
            if(data.userConsolidationChoicesMade==="false"){
              let userDuplicateJsonRequest = {};
              userDuplicateJsonRequest['username'] = '<%= currentUser.getUsername()%>';
              if(userDuplicateJsonRequest){
                populatePage();
                doAjaxForGetDuplicateUsers(userDuplicateJsonRequest);
              }
            }else{
              displayAlreadyMadeChoices();
            }
          }else{
          }
          },
          error: function(x,y,z) {
              console.warn('%o %o %o', x, y, z);
          }
      });
    }

    function promptUserToUpdateTheirEmailAddress(currentEmailAddress){
      let updateYourEmailAddressHtml = '';
      updateYourEmailAddressHtml += '<h4>' + txt.updateYourEmailMessagePt1 + ': ' + '</h4>' + currentEmailAddress;
      updateYourEmailAddressHtml += '<br>';
      updateYourEmailAddressHtml += '<h4>' + txt.updateYourEmailMessagePt2 + ' <a href=' + '<%=urlLoc %>' + '/myAccount.jsp>' + headerTxt.myAccount + '</a></h4>';
      updateYourEmailAddressHtml += '<h4>' + txt.updateYourEmailMessagePt3 + '</h4>';
      $('#content-container').empty();
      $('#content-container').append(updateYourEmailAddressHtml);
    }

    function displayAlreadyMadeChoices(){
      let displayAlreadyMadeHtml = '';
      displayAlreadyMadeHtml += '<h3>' + txt.alreadyBeenHere + '</h3>';
      displayAlreadyMadeHtml += '<button onclick="changeUserConsolidationChoicesMadeToFalse()">' + txt.tryAgain + '</button>';
      $('#content-container').empty();
      $('#content-container').append(displayAlreadyMadeHtml);
    }

    function doAjaxForGetDuplicateUsers(userDuplicateJsonRequest){
      $.ajax({
      url: wildbookGlobals.baseUrl + '../UserConsolidate',
      type: 'POST',
      data: JSON.stringify(userDuplicateJsonRequest),
      dataType: 'json',
      contentType: 'application/json',
      success: function(data) {
          let users = data.users;
          if(users && users.length>0){
            for(let i=0; i<users.length; i++){
              populateCandidateUser(users[i].username, users[i].email,users[i].fullname, users[i].uuid);
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

    function populateCandidateUser(username, email, fullname, uuid){
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
      candidateHtml +=  '<input type="radio" id="merge-radio" name="radio__' + uuid+'__'+username + '__'+ email+ '__'+fullname +'" value="merge" data-id="radio__' + uuid + '__' + username + '__' + email + '__' + fullname +'" onclick="radioClicked()">';
      candidateHtml +=  '<label for="radio__' + uuid+'__'+username + '__'+ email+ '__'+fullname +'">' + txt.merge + '</label>';
      candidateHtml +=  '<br>';
      candidateHtml +=  '<input type="radio" id="noClaim-radio" name="radio__' + uuid + '__'+ username + '__' + email + '__' + fullname +'" value="noClaim" data-id="radio__' + uuid + '__' + username + '__' + email +'__'+fullname +'" onclick="radioClicked()">';
      candidateHtml +=  '<label for="radio__' + uuid + '__'+ username + '__' + email + '__' + fullname +'">' + txt.doNotClaim + '</label>';
      candidateHtml +=  '</div>';
      candidateHtml +=  '</div>';
      candidateHtml += '</div>';
      $('#candidate-users-container').append(candidateHtml);
    }

    function populateNoDuplicates(){
      let noDuplicatesHtml = '';
      noDuplicatesHtml += '<h3>' + txt.noDuplicates + '</h3>';
      $('#content-container').empty();
      $('#content-container').append(noDuplicatesHtml);
    }

    function populatePage(){
      $('#title').html(txt.title);
      let pageHtml = '';
      pageHtml += '<div id="candidate-users-container">';
      pageHtml += '</div>';
      pageHtml += '<div class="submission-section">';
      pageHtml += '<button class="disabled-btn proj-action-btn" id="disabled-apply-user-consolidation-button" onclick="alertUserThatTheyAreNotDone();">' + txt.applyChanges + '<span class="button-icon" aria-hidden="true"></span></button>';
      pageHtml += '<button type="button" id="apply-user-consolidation-button" onclick="applyButtonClicked();" class="sleeker-button" style="display: none;">';
      pageHtml += '<span>' + txt.applyChanges + '</span><span class="button-icon" aria-hidden="true"></span>';
      pageHtml += '</button>';
      pageHtml += '</div>';
      $('#content-container').empty();
      $('#content-container').append(pageHtml);
    }

    function applyButtonClicked(){
      if (confirm(txt.confirm)) {
        let radioElements = $('[data-id^=radio_]');
        if(radioElements){
          let ajaxJson = {};
          ajaxJson['mergeDesired'] = true; //should be true even if userInfoArr is empty
          ajaxJson['username'] = '<%= currentUser.getUsername()%>';
          ajaxJson['userInfoArr'] = [];
          for(let i=0; i<radioElements.length; i++){
            let currentRadioElement = radioElements[i];
            let isChecked = currentRadioElement.checked;
            if(isChecked){
              let currentVal = currentRadioElement.value;
              if(currentVal==="merge"){
                let currentUserDetails = $(currentRadioElement).data().id.split("__");
                // ajaxJson['mergeDesired'] = true;
                currentUserDetails.shift();
                ajaxJson['userInfoArr'].push({uuid:currentUserDetails[0], username: currentUserDetails[1], email: currentUserDetails[2], fullname: currentUserDetails[3]});
              }
            }
          }
          doAjaxCallForMergingUser(ajaxJson);
        }
      }
    }

    function alertUserThatTheyAreNotDone(){
      alert("You must make a choice for each account to submit.");
    }

    function doAjaxCallForMergingUser(jsonRequest){
      displayProgressBar();
      $.ajax({
      url: wildbookGlobals.baseUrl + '../UserConsolidate',
      type: 'POST',
      data: JSON.stringify(jsonRequest),
      dataType: 'json',
      contentType: 'application/json',
      success: function(data) {
          let responseArray =[];
          jsonRequest.userInfoArr.forEach(userInfoObj =>{
            let keyForDataInResponseChecking = "details_"+userInfoObj.uuid+"__"+userInfoObj.username+"__"+userInfoObj.email+"__"+userInfoObj.fullname;
            let valuesOfUserInfoObjPrettified = Object.values(userInfoObj).join(", ");
            if(data[keyForDataInResponseChecking]){
              if(data[keyForDataInResponseChecking] === "SingleMatchFoundForUserAndConsdolidated"){
                  responseArray.push(valuesOfUserInfoObjPrettified + ": " + txt.singleMatchFoundForUserAndConsdolidated);
              }
              if(data[keyForDataInResponseChecking] === "FoundMoreThanOneMatchOrNoMatchesForUser"){
                  responseArray.push(valuesOfUserInfoObjPrettified + ": " + txt.foundMoreThanOneMatchOrNoMatchesForUser);
              }
              if(data[keyForDataInResponseChecking] === "ErrorConsolidatingReportToStaff"){
                  responseArray.push(valuesOfUserInfoObjPrettified + ": " + txt.errorConsolidatingReportToStaff);
              }
            }
          });
          // if(data.success && shouldDisplayWhetherDoneBefore){
          let skipActions = true;
          markUserDedupeAsDoneForUser(skipActions);
          displayConfirmations(responseArray);
          // }
          },
          error: function(x,y,z) {
              console.warn('%o %o %o', x, y, z);
          }
      });
    }

    function displayProgressBar(){
      let progressHtml = '';
      progressHtml += '<div id="progress-wrapper">';
      progressHtml += '<h4>'+ txt.LoadingStatus + '</h4>';
      progressHtml += '<div class="progress">';
      progressHtml += '<div class="progress-bar progress-bar-striped active" role="progressbar" aria-valuenow="50" aria-valuemin="0" aria-valuemax="100" style="width: 50%">';
      progressHtml += '<span class="sr-only">'+txt.PercentComplete+'</span>';
      progressHtml += '</div>';
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

    function markUserDedupeAsDoneForUser(skipActions){
      let userPreferenceUpdateConsolidationChoiceJson = {};
      userPreferenceUpdateConsolidationChoiceJson['action']='setUserConsolidationChoicesTrue';
      // let shouldDisplayWhetherDoneBefore = true;
      doAjaxCallForUserPreferenceUpdate(userPreferenceUpdateConsolidationChoiceJson, skipActions);
    }

    function doAjaxCallForUserPreferenceUpdate(jsonRequest, skipActions){
      displayProgressBar();
      $.ajax({
      url: wildbookGlobals.baseUrl + '../UserPreferences',
      type: 'POST',
      data: JSON.stringify(jsonRequest),
      dataType: 'json',
      contentType: 'application/json',
      success: function(data) {
          if(data.success && !skipActions){
            window.location.reload();
          } else{
            if (!skipActions){
              displayAlreadyMadeChoices();
            }
          }
          },
          error: function(x,y,z) {
              console.warn('%o %o %o', x, y, z);
          }
      });
    }



    function changeUserConsolidationChoicesMadeToFalse(){
      let userPreferenceUpdateConsolidationChoiceJson = {};
      userPreferenceUpdateConsolidationChoiceJson['action']='setUserConsolidationChoicesFalse';
      // let shouldDisplayWhetherDoneBefore = false;
      let skipActions = false;
      doAjaxCallForUserPreferenceUpdate(userPreferenceUpdateConsolidationChoiceJson, false);
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
<jsp:include page="/footer.jsp" flush="true"/>
