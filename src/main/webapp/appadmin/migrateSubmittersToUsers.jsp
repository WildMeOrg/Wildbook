<!DOCTYPE html PUBLIC "-//W3C//DTD XHTML 1.0 Transitional//EN"
        "http://www.w3.org/TR/xhtml1/DTD/xhtml1-transitional.dtd">
<%@ page contentType="text/html; charset=utf-8" language="java" import="org.joda.time.LocalDateTime,
org.joda.time.format.DateTimeFormatter,
org.joda.time.format.ISODateTimeFormat,java.net.*,
org.ecocean.grid.*,
java.io.*,java.util.*, java.io.FileInputStream, java.io.File, java.io.FileNotFoundException, org.ecocean.*,org.ecocean.servlet.*,org.ecocean.media.*,javax.jdo.*, java.lang.StringBuffer, java.util.Vector, java.util.Iterator, java.lang.NumberFormatException"%>

<%

String context="context0";
context=ServletUtilities.getContext(request);

Shepherd myShepherd=new Shepherd(context);



%>

<html>
<head>
<title>Migrate to Users</title>

</head>


<body>

<ul>
<%

myShepherd.beginDBTransaction();
List<String> allEncs=myShepherd.getAllEncounterNumbers();
myShepherd.rollbackDBTransaction();


	
	
	//Iterator encs=myShepherd.getAllEncounters();
	
	
	int numEncs=allEncs.size();
	
	//while(encs.hasNext()){
		
		for(int k=0;k<numEncs;k++){
			
			try{
		
			String encS=allEncs.get(k);
			%>
			<li>Encounter: <%=encS %>
			
			<%
			myShepherd.beginDBTransaction();
			Encounter enc=myShepherd.getEncounter(encS);
			//if(enc.getSubmitters()==null){
			
				System.out.println(k + "/" + numEncs + ") Setting submitters/photographers/informOthers for: "+enc.getCatalogNumber());
			
				boolean madeChange=false;
			
				//null the things we shouldn't be collecting anymore at all
				//no use case/GDPR
				//enc.setSubmitterAddress(null);
				//enc.setSubmitterPhone(null);
				//enc.setPhotographerAddress(null);
				//enc.setPhotographerPhone(null);
			
				//now let's do our conversion, creating our new arrays
				List<User> submitters=new ArrayList<User>();
				List<User> photographers=new ArrayList<User>();
				List<User> informOthers=new ArrayList<User>();	
				if(enc.getSubmitters()!=null){
					submitters=enc.getSubmitters();
				}
				if(enc.getPhotographers()!=null){
					photographers=enc.getPhotographers();
				}
				if(enc.getInformOthers()!=null){
					informOthers=enc.getInformOthers();
				}
			
			//convert enc.submitterEmail
			if(enc.getSubmitterEmail()!=null){
				StringTokenizer str=new StringTokenizer(enc.getSubmitterEmail(),",");
				if(str.countTokens()>0){
					int numTokens=str.countTokens();
					for(int i=0;i<numTokens;i++){
						String email=str.nextToken().trim();
						if(!email.equals("")&&(myShepherd.getUserByEmailAddress(email)==null)){
							User user=new User(email,Util.generateUUID());
							myShepherd.getPM().makePersistent(user);
							myShepherd.commitDBTransaction();
							myShepherd.beginDBTransaction();
							//can we also set the person's name?
							if((numTokens==1)&&(enc.getSubmitterName()!=null)){
								user.setFullName(enc.getSubmitterName());
							}
							if(enc.getSubmitterOrganization()!=null){user.setAffiliation(enc.getSubmitterOrganization());}
							if(enc.getSubmitterProject()!=null){user.setUserProject(enc.getSubmitterProject());}
							%>
							&nbsp;Created new: <%=user.getUUID() %>,<%=user.getEmailAddress() %>,<%=user.getUsername() %>,<%=user.getAffiliation() %>,<%=user.getUserProject() %>
							<%
							submitters.add(user);
							enc.setSubmitters(submitters);
							myShepherd.commitDBTransaction();
							myShepherd.beginDBTransaction();
						}
						else if(!email.equals("")){
							User user=myShepherd.getUserByEmailAddress(email);
							if((submitters!=null)&&(!submitters.contains(user))){
								submitters.add(user);
							
								enc.setSubmitters(submitters);
								%>
								&nbsp;Added existing: <%=user.getUUID() %>,<%=user.getEmailAddress() %>,<%=user.getUsername() %>,<%=user.getAffiliation() %>,<%=user.getUserProject() %>
								<%
								myShepherd.commitDBTransaction();
								myShepherd.beginDBTransaction();
							
							}
						}
					}
				}
				else{
					//if there's not an email address, let's just assume they want to be forgotten
				}
				
	
				
			} //end enc.submitter
			
			
			//enc.photographer
			if(enc.getPhotographerEmail()!=null){
				StringTokenizer str=new StringTokenizer(enc.getPhotographerEmail(),",");
				if(str.countTokens()>0){
					int numTokens=str.countTokens();
					for(int i=0;i<numTokens;i++){
						String email=str.nextToken().trim();
						if((!email.equals(""))&&(myShepherd.getUserByEmailAddress(email)==null)){
							User user=new User(email,Util.generateUUID());
							myShepherd.getPM().makePersistent(user);
							myShepherd.commitDBTransaction();
							myShepherd.beginDBTransaction();
							//can we also set the person's name?
							if((numTokens==1)&&(enc.getPhotographerName()!=null)){
								user.setFullName(enc.getPhotographerName());
							}
							//if(enc.getPhotographerOrganization()!=null){user.setAffiliation(enc.getSubmitterOrganization());}
							//if(enc.getPhotographerProject()!=null){user.setUserProject(enc.getSubmitterProject());}
							%>
							<li>Created new: <%=user.getUUID() %>,<%=user.getEmailAddress() %>,<%=user.getUsername() %>,<%=user.getAffiliation() %>,<%=user.getUserProject() %></li>
							<%
							photographers.add(user);
							enc.setPhotographers(photographers);
							myShepherd.commitDBTransaction();
							myShepherd.beginDBTransaction();
						}
						else if(!email.equals("")){
							User user=myShepherd.getUserByEmailAddress(email);
							if((photographers!=null)&&(!photographers.contains(user))){
								photographers.add(user);
								enc.setPhotographers(photographers);
								%>
								&nbsp;Added existing: <%=user.getUUID() %>,<%=user.getEmailAddress() %>,<%=user.getUsername() %>,<%=user.getAffiliation() %>,<%=user.getUserProject() %>
								<%
								myShepherd.commitDBTransaction();
								myShepherd.beginDBTransaction();
							}
							
						}
					}
				}
				else{
					//if there's not an email address, let's just assume they want to be forgotten
				}
				
	
				
			} //end enc.photographers
			
			
			
			//enc.informOthers
			if((enc.getOLDInformOthersFORLEGACYCONVERSION()!=null)&&(!enc.getOLDInformOthersFORLEGACYCONVERSION().trim().equals(""))){
				StringTokenizer str=new StringTokenizer(enc.getOLDInformOthersFORLEGACYCONVERSION(),",");
				if(str.countTokens()>0){
					int numTokens=str.countTokens();
					for(int i=0;i<numTokens;i++){
						String email=str.nextToken().trim();
						if(!email.equals("")&&(myShepherd.getUserByEmailAddress(email)==null)){
							User user=new User(email,Util.generateUUID());
							myShepherd.getPM().makePersistent(user);
							myShepherd.commitDBTransaction();
							myShepherd.beginDBTransaction();
							%>
							<li>Created new: <%=user.getUUID() %>,<%=user.getEmailAddress() %>,<%=user.getUsername() %>,<%=user.getAffiliation() %>,<%=user.getUserProject() %></li>
							<%
							informOthers.add(user);
							enc.setInformOthers(informOthers);
							myShepherd.commitDBTransaction();
							myShepherd.beginDBTransaction();
						}
						else if(!email.equals("")){
							User user=myShepherd.getUserByEmailAddress(email);
							if((informOthers!=null)&&(!informOthers.contains(user))){
								informOthers.add(user);
								enc.setInformOthers(informOthers);
								%>
								&nbsp;Added existing: <%=user.getUUID() %>,<%=user.getEmailAddress() %>,<%=user.getUsername() %>,<%=user.getAffiliation() %>,<%=user.getUserProject() %>
								<%
								myShepherd.commitDBTransaction();
								myShepherd.beginDBTransaction();
							}
							
						}
					}
				}
				else{
					//if there's not an email address, let's just assume they want to be forgotten
				}
				
	
				
			} //end enc.informOthers
			

	  //}
			
			myShepherd.rollbackDBTransaction();
			
			%>
			</li>
			
			<%

	}
			catch(Exception e){
				myShepherd.rollbackDBTransaction();
				%>
				<p>Reported error: <%=e.getMessage() %> <%=e.getStackTrace().toString() %></p>
				<%
				e.printStackTrace();
			}
	
}

//finally{
	myShepherd.rollbackDBTransaction();
	myShepherd.closeDBTransaction();

//}

%>

</ul>

</body>
</html>
