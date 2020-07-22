<!DOCTYPE html PUBLIC "-//W3C//DTD XHTML 1.0 Transitional//EN"
        "http://www.w3.org/TR/xhtml1/DTD/xhtml1-transitional.dtd">
<%@ page contentType="text/html; charset=utf-8" language="java" import="org.joda.time.LocalDateTime,
org.joda.time.format.DateTimeFormatter,
org.joda.time.format.ISODateTimeFormat,java.net.*,
org.ecocean.grid.*,
org.ecocean.servlet.ServletUtilities,
java.io.*,java.util.*, java.io.FileInputStream, java.io.File, java.io.FileNotFoundException, org.ecocean.*,org.ecocean.servlet.*,javax.jdo.*, java.lang.StringBuffer, java.util.Vector, java.util.Iterator, java.lang.NumberFormatException"%>

<%!
public ArrayList<User> getUsersByHashedEmailAddress(Shepherd myShepherd,String hashedEmail){
    ArrayList<User> users=new ArrayList<User>();
    String filter="SELECT FROM org.ecocean.User WHERE hashedEmailAddress == \""+hashedEmail+"\"";
    Query query=myShepherd.getPM().newQuery(filter);
    Collection c = (Collection) (query.execute());
    if(c!=null){users=new ArrayList<User>(c);}
	return users;
  }
%>

<%!
public ArrayList<User> getUsersByUsername(Shepherd myShepherd,String username){
    ArrayList<User> users=new ArrayList<User>();
    String filter="SELECT FROM org.ecocean.User WHERE username == \""+username+"\"";
    Query query=myShepherd.getPM().newQuery(filter);
    Collection c = (Collection) (query.execute());
    if(c!=null){
      users=new ArrayList<User>(c);
    }
	return users;
  }
%>


<%!
public List<Encounter> getSubmitterEncountersForUser(Shepherd myShepherd, User user){
	String filter="SELECT FROM org.ecocean.Encounter where (submitters.contains(user)) && user.uuid==\""+user.getUUID()+"\" VARIABLES org.ecocean.User user";

	ArrayList<Encounter> encs=new ArrayList<Encounter>();
    Query query=myShepherd.getPM().newQuery(filter);
    Collection c = (Collection) (query.execute());
    if(c!=null){encs=new ArrayList<Encounter>(c);}
    query.closeAll();
    return encs;
}
%>

<%!
public List<Encounter> getPhotographerEncountersForUser(Shepherd myShepherd, User user){
	String filter="SELECT FROM org.ecocean.Encounter where (photographers.contains(user)) && user.uuid==\""+user.getUUID()+"\" VARIABLES org.ecocean.User user";

	ArrayList<Encounter> encs=new ArrayList<Encounter>();
    Query query=myShepherd.getPM().newQuery(filter);
    Collection c = (Collection) (query.execute());
    if(c!=null){encs=new ArrayList<Encounter>(c);}
    query.closeAll();
    return encs;
}
%>

<%!
public int consolidate(Shepherd myShepherd,User useMe,List<User> dupes){
	// int numSwaps=0; //TODO never used???
	dupes.remove(useMe);
	int numDupes=dupes.size();
	for(int i=0;i<dupes.size();i++){
		User currentDupe=dupes.get(i);
		List<Encounter> encs=getPhotographerEncountersForUser(myShepherd,currentDupe);
		for(int j=0;j<encs.size();j++){
			Encounter currentEncounter=encs.get(j);
			consolidatePhotographers(myShepherd, currentEncounter, useMe, currentDupe);
		}
		List<Encounter> encs2=getSubmitterEncountersForUser(myShepherd,currentDupe);
		for(int j=0;j<encs.size();j++){
			Encounter currentEncounter=encs.get(j);
      consolidateSubmitters(myShepherd, currentEncounter, useMe, currentDupe);
		}
		dupes.remove(currentDupe);
		myShepherd.getPM().deletePersistent(currentDupe);
		myShepherd.commitDBTransaction();
		myShepherd.beginDBTransaction();
		i--;
	}
	return numDupes;
}

public void consolidatePhotographers(Shepherd myShepherd, Encounter enc, User useMe, User currentUser){
  List<User> photos=enc.getPhotographers();
  if(photos.contains(currentUser)){
    photos.remove(currentUser);
    photos.add(useMe);
  }
  enc.setPhotographers(photos);
  myShepherd.commitDBTransaction();
  myShepherd.beginDBTransaction();
}

public void consolidateSubmitters(Shepherd myShepherd, Encounter enc, User useMe, User currentUser){
  List<User> subs=enc.getSubmitters();
  if(subs.contains(currentUser)){
    subs.remove(currentUser);
    subs.add(useMe);
  }
  enc.setSubmitters(subs);
  myShepherd.commitDBTransaction();
  myShepherd.beginDBTransaction();
}
%>

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
	List<User> weKnowAbout=new ArrayList<User>();
	for(int i=0;i<users.size();i++){
		User user=users.get(i);
		if(!weKnowAbout.contains(user)){
			if(user.getHashedEmailAddress()!=null){
				List<User> dupes=getUsersByHashedEmailAddress(myShepherd,user.getHashedEmailAddress());
				if(dupes.size()>1){
					%>
					<li>
					<%
					ArrayList<Integer> namesPlace=new ArrayList<Integer>(); //TODO there has to be a better name for this than namesPlace! What on earth does that mean?
					for(int k=0;k<dupes.size();k++){
						String username="";
						if(dupes.get(k).getUsername()!=null){
							username="("+dupes.get(k).getUsername()+")";
							namesPlace.add(new Integer(k));
						}
					%>
						<%=dupes.get(k).getEmailAddress()+username+"(Encounters: "+getSubmitterEncountersForUser(myShepherd,dupes.get(k)).size()+"/ Photographer encounters: "+getPhotographerEncountersForUser(myShepherd,dupes.get(k)).size()+")" %>,
					<%
					}
					if(namesPlace.size()==0){
						User useMe=dupes.get(0);
						consolidate(myShepherd,useMe,dupes);
						%>
						are now resolved to:&nbsp;<%=useMe.getEmailAddress() %>
						<%
					}
					else if(namesPlace.size()==1){
						User useMe=dupes.get(namesPlace.get(0).intValue());
						consolidate(myShepherd,useMe,dupes);

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
      Send
      <span class="button-icon" aria-hidden="true" />
    </button>
  </form>
</body>
</html>
