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

<jsp:include page="../header.jsp" flush="true"/>
    <script>
      let txt = getText("myUsers.properties");
    </script>
    <ol>
    <%
    myShepherd.beginDBTransaction();
    try{
      List<User> users=myShepherd.getAllUsers();
      if(users!=null && users.size()>0){
        System.out.println(users.size() + " total users in the database");
          List<User> nonNullUsers = UserConsolidate.removeNulls(users);
          List<User> userMasterListToRemoveFrom = nonNullUsers;
          System.out.println(nonNullUsers.size() + " non null users in the database");
          for(int i=0;i<nonNullUsers.size();i++){
          User currentUser= UserConsolidate.chooseARandomUserFromList(userMasterListToRemoveFrom);
          userMasterListToRemoveFrom.remove(currentUser);
          if(currentUser!=null){

            List<User> similarUsers = UserConsolidate.getSimilarUsers(currentUser, myShepherd.getPM());
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
                  try{
                    UserConsolidate.consolidateUser(myShepherd, currentUser, currentSimilarUser);
                    %>
                    <!-- <script>
                    $(document).ready(function() {
                      <%
                        if(currentSimilarUser.getUsername()!=null){
                      %>
                      let ajaxJson = {};
                      ajaxJson['mergeDesired'] = true; //should be true even if userInfoArr is empty
                      ajaxJson['username'] = '<%= currentUser.getUsername()%>';
                      ajaxJson['userInfoArr'] = [];
                      ajaxJson['userInfoArr'].push({username: '<%= currentSimilarUser.getUsername()%>', email: '<%= currentSimilarUser.getEmailAddress()%>', fullname: '<%= currentSimilarUser.getFullName()%>'});
                      console.log("ajaxJson is: ");
                      console.log(ajaxJson);
                      doAjaxCallForMergingUser(ajaxJson);
                      <%
                        }
                      %>
                    });

                    function doAjaxCallForMergingUser(jsonRequest){
                      $.ajax({
                      url: wildbookGlobals.baseUrl + '../UserConsolidate',
                      type: 'POST',
                      data: JSON.stringify(jsonRequest),
                      dataType: 'json',
                      contentType: 'application/json',
                      success: function(data) {
                          console.log("data are");
                          console.log(data);
                          let responseArray =[];
                          jsonRequest.userInfoArr.forEach(userInfoObj =>{
                            let keyForDataInResponseChecking = "details_"+userInfoObj.username+"__"+userInfoObj.email+"__"+userInfoObj.fullname;
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
                          // if(data.success && shouldDisplayWhetherDoneBefore){}
                          displayConfirmations(responseArray);
                        },
                          error: function(x,y,z) {
                              console.warn('%o %o %o', x, y, z);
                          }
                      });
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
                    </script> -->
                    <%
                  } catch(Exception e){
                      System.out.println("error consolidating user: " + currentSimilarUser.toString() + " into user: " + currentUser.toString());
                    e.printStackTrace();
                  } //end catch
                } // end if currentSimilarUser!=null
              } // end for loop of similarUsers
            } // end if users!=null && users.size()>0
            %>
            </p>
            <div class="content-container"></div>
            <%
          }
        }
      }
    }
    catch(Exception e){
      System.out.println("error in consolidateDuplicateUsersChecker.jsp in the whole try catch loop");
      e.printStackTrace();

    }
    finally{
      myShepherd.commitDBTransaction();
    	myShepherd.closeDBTransaction();
    }
    %>
<jsp:include page="../footer.jsp" flush="true"/>
