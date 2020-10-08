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
      EncounterConsolidate.makeEncountersMissingSubmittersPublic(myShepherd);
    	List<User> users=myShepherd.getAllUsers();
    	List<User> weKnowAbout=new ArrayList<User>();
    	for(int i=0;i<users.size();i++){
    		User user=users.get(i);
        UserConsolidate.getUsersWithMissingUsernamesWhoMatchEmail(myShepherd.getPM(), user.getEmailAddress());
    		if(!weKnowAbout.contains(user)){
    			if(user.getHashedEmailAddress()!=null){
    				List<User> dupes = UserConsolidate.getUsersByHashedEmailAddress(myShepherd.getPM(),user.getHashedEmailAddress());
    				if(dupes.size()>1){
    					%>
    					<li>
    					<%
    					ArrayList<Integer> indicesOfDupesInWhichAnyUserNameExists=new ArrayList<Integer>();
    					for(int k=0;k<dupes.size();k++){
    						String username="";
    						if(dupes.get(k).getUsername()!=null){
    							username="("+dupes.get(k).getUsername()+")";
    							indicesOfDupesInWhichAnyUserNameExists.add(new Integer(k));
    						}
    					%>
    					<%=dupes.get(k).getEmailAddress()+username+"(Encounters: "+UserConsolidate.getSubmitterEncountersForUser(myShepherd.getPM(),dupes.get(k)).size()+"/ Photographer encounters: "+UserConsolidate.getPhotographerEncountersForUser(myShepherd.getPM(),dupes.get(k)).size()+")" %>,
    					<%
    					}
    					if(indicesOfDupesInWhichAnyUserNameExists.size()==0){
    						User useMe=dupes.get(0);
    						UserConsolidate.consolidate(myShepherd,useMe,dupes);
    						%>
    						are now resolved to:&nbsp;<%=useMe.getEmailAddress() %>
    						<%
    					}
    					else if(indicesOfDupesInWhichAnyUserNameExists.size()==1){
    						User useMe=dupes.get(indicesOfDupesInWhichAnyUserNameExists.get(0).intValue());
    						UserConsolidate.consolidate(myShepherd,useMe,dupes);
    						%>
    						are now resolved to:&nbsp;&nbsp;<%=useMe.getEmailAddress() %>(<%=useMe.getUsername() %>)
    						<%
    					}
    					else{
    						%>
    						are now resolved to:&nbsp;&nbsp;multiple usernames...no reconciliation.
    						<%
    					}
    					%>
    					</li>
    					<%
    					weKnowAbout.addAll(dupes);
    				}
    			}
    	   }
    	}
      List<String> peopleToEmail = UserConsolidate.getEmailAddressesOfUsersWithMoreThanOneAccountAssociatedWithEmailAddress(users, myShepherd.getPM());
      List<String> peopleToEmailCapsPreserved = UserConsolidate.getEmailAddressesOfUsersWithMoreThanOneAccountAssociatedWithEmailAddressPreserveCaps(users, peopleToEmail, myShepherd.getPM());
      System.out.println("peopleToEmailCapsPreserved has " + peopleToEmailCapsPreserved.size() + " entries");
      %>
      <ol>You should email:
      <%
      for(int i=0; i<peopleToEmail.size(); i++){
        %>
        <li>
        <%=peopleToEmail.get(i)%>:
        <br>
        <%
        User userWithEmailAddressOnNaughtyList = UserConsolidate.getFirstUserWithEmailAddress(myShepherd.getPM(), peopleToEmailCapsPreserved.get(i));
        if(userWithEmailAddressOnNaughtyList !=null){
          List<User> similarUsers = UserConsolidate.getSimilarUsers(userWithEmailAddressOnNaughtyList, myShepherd.getPM());
          for(int j=0; j<similarUsers.size(); j++){
            if(similarUsers.size()>0){
              %>
              <%=similarUsers.get(j).getUUID()%>,
              <%=similarUsers.get(j).getUsername()%>,
              <%=similarUsers.get(j).getFullName()%>,
              <%=similarUsers.get(j).getEmailAddress()%>
              <br>
              </li>
              </li>
              </ol>
              <%
            }
          }
        }
      }
      %>
      <p>Done with people to email list</p>
      <%
    }
    catch(Exception e){
    	myShepherd.rollbackDBTransaction();
    }
    finally{
    	myShepherd.closeDBTransaction();
    }
    %>

    </ol>
    <form action="../UserConsolidate?context=context0"
    method="post"
    id="manual-consolidate-form"
    name="manual-consolidate-form"
    lang="en"
    class="form-horizontal"
    accept-charset="UTF-8">
    <h2>Manually Consolidate By Username</h2>
        <div class="form-inline col-xs-12 col-sm-12 col-md-6 col-lg-6">
          <label class="control-label text-danger" for="username-input">Enter Username You Want to Keep</label>
          <input class="form-control" type="text" style="position: relative; z-index: 101;" name="username-input" id="username-input"/>
        </div>
      <button id="submitManualButton" class="large" type="submit">
        Consolidate
        <span class="button-icon" aria-hidden="true" />
      </button>
    </form>
  </body>
</html>
