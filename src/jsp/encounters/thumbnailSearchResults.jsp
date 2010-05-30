<!DOCTYPE html PUBLIC "-//W3C//DTD XHTML 1.0 Transitional//EN"
        "http://www.w3.org/TR/xhtml1/DTD/xhtml1-transitional.dtd">
<%@ page contentType="text/html; charset=iso-8859-1" language="java"
	import="java.util.StringTokenizer,org.ecocean.*, java.lang.Integer, java.lang.NumberFormatException, java.util.Vector, java.util.Iterator"%>

<html>
<head>


<%
int startNum=1;
int endNum=15;

try{ 

	if (request.getParameter("startNum")!=null) {
		startNum=(new Integer(request.getParameter("startNum"))).intValue();
	}
	if (request.getParameter("endNum")!=null) {
		endNum=(new Integer(request.getParameter("endNum"))).intValue();
	}

} catch(NumberFormatException nfe) {
	startNum=1;
	endNum=15;
}


Shepherd myShepherd=new Shepherd();

  			Iterator allEncounters;
			Vector rEncounters=new Vector();			

			myShepherd.beginDBTransaction();
			
			allEncounters=myShepherd.getAllEncountersNoQuery();
			while (allEncounters.hasNext()) {
				Encounter temp_enc=(Encounter)allEncounters.next();
				rEncounters.add(temp_enc);
			}

//filter for encounters of MarkedIndividuals that have been resighted------------------------------------------
			if((request.getParameter("resightOnly")!=null)&&(request.getParameter("numResights")!=null)) {
				int numResights=1;

				try{
					numResights=(new Integer(request.getParameter("numResights"))).intValue();
					}
				catch(NumberFormatException nfe) {}

				for(int q=0;q<rEncounters.size();q++) {
					Encounter rEnc=(Encounter)rEncounters.get(q);
					if(rEnc.isAssignedToMarkedIndividual().equals("Unassigned")){
						rEncounters.remove(q);
						q--;
						}
					else{
						MarkedIndividual s=myShepherd.getMarkedIndividual(rEnc.isAssignedToMarkedIndividual());
						if(s.totalEncounters()<numResights) {
							rEncounters.remove(q);
							q--;
						}
					}
				}
			}
//end if resightOnly--------------------------------------------------------------------------------------

//filter for only approved and unapproved encounters------------------------------------------
if((request.getParameter("enctype")!=null)&&(request.getParameter("enctype").equals("acceptedEncounters"))) {
				for(int q=0;q<rEncounters.size();q++) {
					Encounter rEnc=(Encounter)rEncounters.get(q);
					if(rEnc.wasRejected()){
						rEncounters.remove(q);
						q--;
						}
				}
}
//accepted and unapproved only filter--------------------------------------------------------------------------------------

//filter for sex------------------------------------------
if(request.getParameter("male")==null) {
				for(int q=0;q<rEncounters.size();q++) {
					Encounter rEnc=(Encounter)rEncounters.get(q);
					if(rEnc.getSex().equals("male")){
						rEncounters.remove(q);
						q--;
						}
				}
}
if(request.getParameter("female")==null) {
				for(int q=0;q<rEncounters.size();q++) {
					Encounter rEnc=(Encounter)rEncounters.get(q);
					if(rEnc.getSex().equals("female")){
						rEncounters.remove(q);
						q--;
						}
				}
}
if(request.getParameter("unknown")==null) {
				for(int q=0;q<rEncounters.size();q++) {
					Encounter rEnc=(Encounter)rEncounters.get(q);
					if(rEnc.getSex().equals("unsure")){
						rEncounters.remove(q);
						q--;
						}
				}
}
//filter by sex--------------------------------------------------------------------------------------

//filter for length------------------------------------------
if((request.getParameter("selectLength")!=null)&&(request.getParameter("lengthField")!=null)&&(!request.getParameter("lengthField").equals("skip"))&&(!request.getParameter("selectLength").equals(""))) {

try {

double dbl_size=(new Double(request.getParameter("lengthField"))).doubleValue();

if(request.getParameter("selectLength").equals("gt")) {
				for(int q=0;q<rEncounters.size();q++) {
					Encounter rEnc=(Encounter)rEncounters.get(q);
					if(rEnc.getSize()<dbl_size){
						rEncounters.remove(q);
						q--;
						}
				}
}
if(request.getParameter("selectLength").equals("lt")) {
				for(int q=0;q<rEncounters.size();q++) {
					Encounter rEnc=(Encounter)rEncounters.get(q);
					if((rEnc.getSize()>dbl_size)||(rEnc.getSize()<0.1)){
						rEncounters.remove(q);
						q--;
						}
				}
}
if(request.getParameter("selectLength").equals("eq")) {
				for(int q=0;q<rEncounters.size();q++) {
					Encounter rEnc=(Encounter)rEncounters.get(q);
					if(rEnc.getSize()!=dbl_size){
						rEncounters.remove(q);
						q--;
						}
				}
}

} catch(NumberFormatException nfe) {
//do nothing, just skip on
}

}
//filter by length--------------------------------------------------------------------------------------

//filter for location------------------------------------------
if((request.getParameter("locationField")!=null)&&(!request.getParameter("locationField").equals(""))) {
				for(int q=0;q<rEncounters.size();q++) {
					Encounter rEnc=(Encounter)rEncounters.get(q);
					String locString=request.getParameter("locationField").toLowerCase();
					if(rEnc.getLocation().toLowerCase().indexOf(locString)==-1){
						rEncounters.remove(q);
						q--;
						}
				}
}
//location filter--------------------------------------------------------------------------------------

//submitter or photographer name filter------------------------------------------
if((request.getParameter("nameField")!=null)&&(!request.getParameter("nameField").equals(""))) {
				for(int q=0;q<rEncounters.size();q++) {
					Encounter rEnc=(Encounter)rEncounters.get(q);
					String locString=request.getParameter("nameField").replaceAll("%20"," ").toLowerCase();
					if((rEnc.getSubmitterName().toLowerCase().replaceAll("%20"," ").indexOf(locString)<0)&&(rEnc.getPhotographerName().toLowerCase().replaceAll("%20"," ").indexOf(locString)<0)){
						rEncounters.remove(q);
						q--;
						}
				}
}
//end name filter--------------------------------------------------------------------------------------

//filter for location code------------------------------------------
if((request.getParameter("locationCodeField")!=null)&&(!request.getParameter("locationCodeField").equals(""))) {
				for(int q=0;q<rEncounters.size();q++) {
					Encounter rEnc=(Encounter)rEncounters.get(q);
					String locString=request.getParameter("locationCodeField").toLowerCase();
					if(!rEnc.getLocationCode().toLowerCase().startsWith(locString)){
						rEncounters.remove(q);
						q--;
						}
				}
}
//location code filter--------------------------------------------------------------------------------------
			
//filter for date------------------------------------------
if(request.getParameter("dateLimit")!=null) {
	if((request.getParameter("day1")!=null)&&(request.getParameter("month1")!=null)&&(request.getParameter("year1")!=null)&&(request.getParameter("day2")!=null)&&(request.getParameter("month2")!=null)&&(request.getParameter("year2")!=null)) {
		try{
		
			//get our date values
			int day1=(new Integer(request.getParameter("day1"))).intValue();
			int day2=(new Integer(request.getParameter("day2"))).intValue();
			int month1=(new Integer(request.getParameter("month1"))).intValue();
			int month2=(new Integer(request.getParameter("month2"))).intValue();
			int year1=(new Integer(request.getParameter("year1"))).intValue();
			int year2=(new Integer(request.getParameter("year2"))).intValue();
			
			//order our values
			int minYear=year1;
			int minMonth=month1;
			int minDay=day1;
			int maxYear=year2;
			int maxMonth=month2;
			int maxDay=day2;
			if(year1>year2) {
				minDay=day2;
				minMonth=month2;
				minYear=year2;
				maxDay=day1;
				maxMonth=month1;
				maxYear=year1;
			}
			else if(year1==year2) {
				if(month1>month2) {
					minDay=day2;
					minMonth=month2;
					minYear=year2;
					maxDay=day1;
					maxMonth=month1;
					maxYear=year1;
				}
				else if(month1==month2) {
					if(day1>day2) {
						minDay=day2;
						minMonth=month2;
						minYear=year2;
						maxDay=day1;
						maxMonth=month1;
						maxYear=year1;
					}
				}
			}

			
			for(int q=0;q<rEncounters.size();q++) {
				Encounter rEnc=(Encounter)rEncounters.get(q);
				int m_day=rEnc.getDay();
				int m_month=rEnc.getMonth();
				int m_year=rEnc.getYear();
				if((m_year>maxYear)||(m_year<minYear)){
					rEncounters.remove(q);
					q--;
				}
				else if(((m_year==minYear)&&(m_month<minMonth))||((m_year==maxYear)&&(m_month>maxMonth))) {
					rEncounters.remove(q);
					q--;
				}
				else if(((m_year==minYear)&&(m_month==minMonth)&&(m_day<minDay))||((m_year==maxYear)&&(m_month==maxMonth)&&(m_day>maxDay))) {
					rEncounters.remove(q);
					q--;
				}
			} //end for
		} catch(NumberFormatException nfe) {
			//do nothing, just skip on
		}
	}
}
//date filter--------------------------------------------------------------------------------------







%>
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
<table width="720" border="0" cellspacing="0" cellpadding="0">
	<tr>
		<td>
		<p>
		<h1 class="intro">Thumbnail Image\Video Search Results</h1>
		</p>
		<p>Below are thumbnails <%=startNum%> - <%=endNum%> that matched
		your search.</p>
		</td>
	</tr>
</table>

<table id="results" border="0" width="100%">
	<%		

			int countMe=0;
			Vector thumbLocs=new Vector();
			
			try {
				thumbLocs=myShepherd.getThumbnails(rEncounters.iterator(), startNum, endNum);

			
			
	for(int rows=0;rows<5;rows++) {		%>

	<tr>

		<%
				for(int columns=0;columns<3;columns++){
					if(countMe<thumbLocs.size()) {
						String combined=(String)thumbLocs.get(countMe);
						StringTokenizer stzr=new StringTokenizer(combined,"BREAK");
						String thumbLink=stzr.nextToken();
						String encNum=stzr.nextToken();
						String fileName=stzr.nextToken();
						boolean video=true;
						if(!thumbLink.endsWith("video.jpg")){
							thumbLink="http://"+CommonConfiguration.getURLLocation()+"/encounters/"+thumbLink;
							video=false;
						}
						String link="http://"+CommonConfiguration.getURLLocation()+"/encounters/encounter.jsp?number="+encNum;
						
					%>

		<td><a href="<%=link%>"><img src="<%=thumbLink%>"
			alt="shark photo" border="1" /></a></td>
		<%
					
					countMe++;
					}
				} //endFor
				%>
	</tr>
	<%} //endFor
	
				} catch(Exception e) {
				%>
	<tr>
		<td>
		<p>Tried to get the thumbnails from shepherd, but I hit an error.</p>
		</td>
	</tr>
	<%}
%>

</table>

<%




	startNum=startNum+15;	
	endNum=endNum+15;


String numberResights="";
if(request.getParameter("numResights")!=null){
	numberResights="&numResights="+request.getParameter("numResights");
}
String qString=request.getQueryString();
int startNumIndex=qString.indexOf("&startNum");
if(startNumIndex>-1) {
	qString=qString.substring(0,startNumIndex);
}

%>
<p><a
	href="thumbnailSearchResults.jsp?<%=qString%><%=numberResights%>&startNum=<%=startNum%>&endNum=<%=endNum%>">See
next results (<%=startNum%> - <%=endNum%></a>)</p>
<%
if((startNum-15)>1) {%>
<p><a
	href="thumbnailSearchResults.jsp?<%=qString%><%=numberResights%>&startNum=<%=(startNum-30)%>&endNum=<%=(startNum-16)%>">See
previous results (<%=(startNum-30)%> - <%=(startNum-16)%>)</a></p>

<%}
if((startNum-15)==1) {
%>
<p>
<table width="720" border="0" cellspacing="0" cellpadding="0">
	<tr>
		<td align="right">
		<p><strong>Matching images\videos</strong>: <%=myShepherd.getNumThumbnails(rEncounters.iterator())%></p>
	</tr>
</table>
</p>
<%}

myShepherd.rollbackDBTransaction();
myShepherd.closeDBTransaction();
%> <br> <jsp:include page="../footer.jsp" flush="true" />
</div>
</div>
<!-- end page --></div>
<!--end wrapper -->

</body>
</html>


