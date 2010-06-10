<!DOCTYPE html PUBLIC "-//W3C//DTD XHTML 1.0 Transitional//EN"
        "http://www.w3.org/TR/xhtml1/DTD/xhtml1-transitional.dtd">
<%@ page contentType="text/html; charset=utf-8" language="java"
	import="org.joda.time.format.ISODateTimeFormat,org.joda.time.DateTime,org.joda.time.format.DateTimeFormatter,java.util.Properties, java.io.FileInputStream, java.io.File, java.io.FileNotFoundException, org.ecocean.*,org.ecocean.servlet.*,javax.jdo.*, java.lang.StringBuffer, java.util.Vector, java.util.Iterator, java.lang.NumberFormatException"%>


<%

//set up dateTime
DateTime dt=new DateTime();
DateTimeFormatter fmt = ISODateTimeFormat.date();
String strOutputDateTime = fmt.print(dt);

//setup our Properties object to hold all properties
	Properties props=new Properties();
	String langCode="en";
	
	Shepherd myShepherd=new Shepherd();
	String currentSort="nosort";
	String displaySort="";
	if (request.getParameter("sort")!=null) {currentSort=request.getParameter("sort");
		if(request.getParameter("sort").startsWith("name")){displaySort=" sorted by Name";}
	}
	currentSort="&amp;sort="+currentSort;
	int lowCount=1, highCount=10;
	if ((request.getParameter("start")!=null)&&(request.getParameter("end")!=null)) {
		lowCount=(new Integer(request.getParameter("start"))).intValue();
		highCount=(new Integer(request.getParameter("end"))).intValue();
		if(highCount>(lowCount+9)) {highCount=lowCount+9;}
	}	


%>

<html>
<head>
<title><%=CommonConfiguration.getHTMLTitle() %></title>
<meta http-equiv="Content-Type" content="text/html; charset=utf-8" />
<meta name="Description"
	content="<%=CommonConfiguration.getHTMLDescription() %>" />
<meta name="Keywords"
	content="<%=CommonConfiguration.getHTMLKeywords() %>" />
<meta name="Author" content="<%=CommonConfiguration.getHTMLAuthor() %>" />
<link href="<%=CommonConfiguration.getCSSURLLocation() %>"
	rel="stylesheet" type="text/css" />
<link rel="shortcut icon"
	href="<%=CommonConfiguration.getHTMLShortcutIcon() %>" />

</head>


<body>
<div id="wrapper">
<div id="page"><jsp:include page="../header.jsp" flush="true">
	<jsp:param name="isResearcher"
		value="<%=request.isUserInRole("researcher")%>" />
	<jsp:param name="isManager"
		value="<%=request.isUserInRole("manager")%>" />
	<jsp:param name="isReviewer"
		value="<%=request.isUserInRole("reviewer")%>" />
	<jsp:param name="isAdmin" value="<%=request.isUserInRole("admin")%>" />
</jsp:include>

<div id="main">

<div id="maincol-wide" style="width: 810px;">
<div id="maintext">
<%

myShepherd.beginDBTransaction();
//build a query
Extent encClass=myShepherd.getPM().getExtent(Adoption.class, true);
Query query=myShepherd.getPM().newQuery(encClass);
String order="adoptionStartDate descending";
query.setOrdering(order);

int totalCount=0;

totalCount= myShepherd.getNumAdoptions();

%>
<table id="results" border="0">
	<tr>
		<td colspan="4">
		<h1>View All Adoptions</h1>
		</td>
	</tr>
	<tr>
		<th class="caption" colspan="4">Below are some of the <strong><%=totalCount%></strong>
		adoptions currently stored in the database. Click <strong>Next</strong>
		to view the next set of encounters. Click <strong>Previous</strong> to
		see the previous set of encounters.
		</td>
	</tr>
</table>






<table id="results" border="0" width="100%">
	<tr class="paging">
		<td align="left">
		<%

String rejectsLink="";
String unapprovedLink="";
String userLink="";

if (highCount<totalCount) {%> <a
			href="http://<%=CommonConfiguration.getURLLocation()%>/adoptions/allAdoptions.jsp?langCode=<%=langCode%>&amp;start=<%=(lowCount+10)%>&amp;end=<%=(highCount+10)%><%=rejectsLink%><%=unapprovedLink%><%=userLink%><%=currentSort%>">Next</a>
		<%} 
  if ((lowCount-10)>=1) {%> | <a
			href="http://<%=CommonConfiguration.getURLLocation()%>/adoptions/allAdoptions.jsp?langCode=<%=langCode%>&amp;start=<%=(lowCount-10)%>&amp;end=<%=(highCount-10)%><%=rejectsLink%><%=unapprovedLink%><%=userLink%><%=currentSort%>">Previous</a>
		<%}%>
		</td>
		<td colspan="6" align="right">
		<%
 String startNum="1";
 String endNum="10";


 
 if(request.getParameter("start")!=null){
 	startNum=request.getParameter("start");
 }
 if((request.getParameter("end")!=null)&&(!request.getParameter("end").equals("99999"))){
 	endNum=request.getParameter("end");
 }
 else if((request.getParameter("end")!=null)&&(request.getParameter("end").equals("99999"))){
 		endNum=(new Integer(totalCount)).toString();
 	}

 if(((new Integer(endNum)).intValue())>totalCount) {
 	endNum=(new Integer(totalCount)).toString();
 }

 
 %> Viewing: <%=lowCount%> - <%=highCount%><%=displaySort%></td>
	</tr>

	<tr class="lineitem">
		<td bgcolor="#99CCFF" class="lineitem">&nbsp;</td>
		<td align="left" valign="top" bgcolor="#99CCFF" class="lineitem"><strong>Number</strong></td>

		<td align="left" valign="top" bgcolor="#99CCFF" class="lineitem"><strong>Name</strong></td>
		<td align="left" valign="top" bgcolor="#99CCFF" class="lineitem"><strong>Type</strong></td>
		<td align="left" valign="top" bgcolor="#99CCFF" class="lineitem"><strong>Adopted</strong></td>
		<td width="60" align="left" valign="top" bgcolor="#99CCFF"
			class="lineitem"><strong>Start date</strong></td>
		<td width="60" align="left" valign="top" bgcolor="#99CCFF"
			class="lineitem"><strong>End date</strong></td>

	</tr>
	<%		

  			Iterator allAdoptions;

			int total=totalCount;
			int iterTotal=totalCount;
			query=ServletUtilities.setRange(query,iterTotal,highCount,lowCount);
			
			allAdoptions=myShepherd.getAllAdoptionsWithQuery(query);

			int countMe=0;
			while (allAdoptions.hasNext()) {
				countMe++;
				Adoption enc=(Adoption)allAdoptions.next(); 


					%>
	<tr class="lineitems">
		<td width="102" height="60" class="lineitems"><a
			href="http://<%=CommonConfiguration.getURLLocation()%>/<%=CommonConfiguration.getAdoptionDirectory() %>/adoption.jsp?individual=<%=enc.getID()%>"><img
			src="http://<%=CommonConfiguration.getURLLocation() %>/adoptions/<%=(enc.getID()+"/thumb.jpg")%>"
			width="100" height="75" alt="adopter photo" border="0" /></a></td>

		<td class="lineitems"><a
			href="http://<%=CommonConfiguration.getURLLocation() %>/<%=CommonConfiguration.getAdoptionDirectory() %>/adoption.jsp?number=<%=enc.getID()%>"><%=enc.getID()%></a></td>
		<td class="lineitems"><%=enc.getAdopterName()%></td>
		<td class="lineitems"><%=enc.getAdoptionType()%></td>
		<td class="lineitems"><%=enc.getMarkedIndividual()%></td>
		<td class="lineitems"><%=enc.getAdoptionStartDate()%></td>
		<td class="lineitems"><%=enc.getAdoptionEndDate()%></td>


	</tr>
	<%
}
%>


	<tr class="paging">
		<td align="left">
		<%
if (highCount<totalCount) {%> <a
			href="http://<%=CommonConfiguration.getURLLocation()%>/adoptions/allAdoptions.jsp?langCode=<%=langCode%>&amp;start=<%=(lowCount+10)%>&amp;end=<%=(highCount+10)%><%=rejectsLink%><%=unapprovedLink%><%=userLink%><%=currentSort%>">Next</a>
		<%} 
  if ((lowCount-10)>=0) {%> | <a
			href="http://<%=CommonConfiguration.getURLLocation()%>/adoptions/allAdoptions.jsp?langCode=<%=langCode%>&amp;start=<%=(lowCount-10)%>&amp;end=<%=(highCount-10)%><%=rejectsLink%><%=unapprovedLink%><%=userLink%><%=currentSort%>">Previous</a>
		<%}%>
		</td>
		<td colspan="6" align="right">Viewing: <%=lowCount%> - <%=highCount%><%=displaySort%>

		</td>
	</tr>
</table>

<%
query.closeAll();

myShepherd.rollbackDBTransaction();
myShepherd.closeDBTransaction();
encClass=null;
query=null;
myShepherd=null;
%>
<p></p>


</div>
<!-- end maintext --></div>
<!-- end main-wide --></div>
<!-- end main --> <jsp:include page="../footer.jsp" flush="true" /></div>
<!-- end page --></div>
<!--end wrapper -->
</body>
</html>