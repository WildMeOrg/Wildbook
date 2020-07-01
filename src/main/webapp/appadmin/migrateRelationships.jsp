<!DOCTYPE html PUBLIC "-//W3C//DTD XHTML 1.0 Transitional//EN"
        "http://www.w3.org/TR/xhtml1/DTD/xhtml1-transitional.dtd">
<%@ page contentType="text/html; charset=utf-8" language="java" import="org.joda.time.LocalDateTime,
org.joda.time.format.DateTimeFormatter,
org.joda.time.format.ISODateTimeFormat,java.net.*,
org.ecocean.grid.*,org.ecocean.social.*,
java.io.*,java.util.*, java.io.FileInputStream, java.io.File, java.io.FileNotFoundException, org.ecocean.*,org.ecocean.servlet.*,javax.jdo.*, java.lang.StringBuffer, java.util.Vector, java.util.Iterator, java.lang.NumberFormatException"%>

<%

String context="context0";
context=ServletUtilities.getContext(request);

Shepherd myShepherd=new Shepherd(context);



%>

<html>
<head>
<title>Migrate Relationships</title>

</head>


<body>
<ol>
<%
int numFixed=0;
myShepherd.beginDBTransaction();

try {

	Extent encClass = myShepherd.getPM().getExtent(Relationship.class, true);
	Query acceptedEncounters = myShepherd.getPM().newQuery(encClass);
	Collection c = (Collection) (acceptedEncounters.execute());
	ArrayList<Relationship> rels=new ArrayList<Relationship>(c);
	acceptedEncounters.closeAll();
	for(Relationship rel:rels){
		if(rel.getMarkedIndividualName1()!=null && Util.isUUID(rel.getMarkedIndividualName1()) && Util.isUUID(rel.getMarkedIndividualName2())){
			
			MarkedIndividual individual1=myShepherd.getMarkedIndividual(rel.getMarkedIndividualName1());
			MarkedIndividual individual2=myShepherd.getMarkedIndividual(rel.getMarkedIndividualName2());
			if(individual1!=null && individual2!=null){
				
				if(rel.getMarkedIndividual1()==null || rel.getMarkedIndividual2()==null){
					rel.setIndividual1(individual1);
					rel.setIndividual2(individual2);

					myShepherd.updateDBTransaction();
					%>
					
					
					<li>Migrated!</li>
					
					<%
				}

				
				//OK, let's migrate social memberships to social units with the new code
				
				//"type":"CommunityMembership","relatedSocialUnitName":"C",
				if(rel.getType()!=null&&rel.getRelatedSocialUnitName()!=null&&rel.getType().equals("CommunityMembership")){
					
					String socName=rel.getRelatedSocialUnitName();
					SocialUnit su=myShepherd.getSocialUnit(socName);
					if(su==null){
						
						%>
						<li>Warning: SocialUnit <%=socName %> is referenced but does not exist.</li>
						<%
						
					}
					if(su!=null){
						
						if(!su.hasMarkedIndividualAsMember(individual1)){
							Membership member=new Membership(individual1);
							myShepherd.getPM().makePersistent(member);
							su.addMember(member);
							myShepherd.updateDBTransaction();
							%>
							<li>Added <%=individual1.getDisplayName() %> to social unit name: <%=su.getSocialUnitName() %></li>
							<%
						}
						if(!su.hasMarkedIndividualAsMember(individual2)){
							Membership member=new Membership(individual2);
							myShepherd.getPM().makePersistent(member);
							su.addMember(member);
							myShepherd.updateDBTransaction();
							%>
							<li>Added <%=individual2.getDisplayName() %> to social unit name: <%=su.getSocialUnitName() %></li>
							<%
						}
							
					}
					
					
					
				}
				
				
				
			}
			else{
					%>
					<li>not found</li>
					<%
			}
		}
		}
		
		

	
}
catch(Exception e){
	e.printStackTrace();
}
finally{
	myShepherd.rollbackDBTransaction();
	myShepherd.closeDBTransaction();

}

%>
</ol>


</body>
</html>
