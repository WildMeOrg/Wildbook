<!DOCTYPE html PUBLIC "-//W3C//DTD XHTML 1.0 Transitional//EN"
        "http://www.w3.org/TR/xhtml1/DTD/xhtml1-transitional.dtd">
<%@ page contentType="text/html; charset=iso-8859-1" language="java"
	import="org.ecocean.*, javax.jdo.*, java.lang.StringBuffer, java.lang.Integer, java.lang.NumberFormatException, java.io.*, java.util.Vector, java.util.Iterator, java.util.StringTokenizer"%>
<%@ taglib uri="di" prefix="di"%>

<html>
<head>
<%
int startNum=1;
int endNum=10;


try{ 

	if (request.getParameter("startNum")!=null) {
		startNum=(new Integer(request.getParameter("startNum"))).intValue();
	}
	if (request.getParameter("endNum")!=null) {
		endNum=(new Integer(request.getParameter("endNum"))).intValue();
	}

} catch(NumberFormatException nfe) {
	startNum=1;
	endNum=10;
}
int listNum=endNum;

Shepherd myShepherd=new Shepherd();
int day1=1, day2=31, month1=1, month2=12, year1=0, year2=3000;
try{month1=(new Integer(request.getParameter("month1"))).intValue();} catch(NumberFormatException nfe) {}
try{month2=(new Integer(request.getParameter("month2"))).intValue();} catch(NumberFormatException nfe) {}
try{year1=(new Integer(request.getParameter("year1"))).intValue();} catch(NumberFormatException nfe) {}
try{year2=(new Integer(request.getParameter("year2"))).intValue();} catch(NumberFormatException nfe) {}


//qStringParams for links
String sexParam="";
if(request.getParameter("sex")!=null) {sexParam="&sex="+request.getParameter("sex");}
String keywordParam="";
if(request.getParameter("keyword")!=null) {keywordParam="&keyword="+request.getParameter("keyword");}
String numResightsParam="";
if(request.getParameter("numResights")!=null) {numResightsParam="&numResights="+request.getParameter("numResights");}
String lengthParams="";
if((request.getParameter("selectLength")!=null)&&(request.getParameter("lengthField")!=null)) {
	lengthParams="&selectLength="+request.getParameter("selectLength")+"&lengthField="+request.getParameter("lengthField");
}
String locCodeParam="";
if(request.getParameter("locationCodeField")!=null) {locCodeParam="&locationCodeField="+request.getParameter("locationCodeField");}
String dateParams="day1="+day1+"&day2="+day2+"&month1="+month1+"&month2="+month2+"&year1="+year1+"&year2="+year2;
String exportParam="";
if(request.getParameter("export")!=null) {exportParam="&export=true";}
String numberSpots="";
if(request.getParameter("numspots")!=null) {numberSpots="&numspots="+request.getParameter("numspots");}
String qString=dateParams+sexParam+numResightsParam+locCodeParam+lengthParams+exportParam+keywordParam+numberSpots;

int numResults=0;

Iterator allSharks;
Vector rSharks=new Vector();			
myShepherd.beginDBTransaction();
Extent sharkClass=myShepherd.getPM().getExtent(MarkedIndividual.class, true);
Query query=myShepherd.getPM().newQuery(sharkClass);
if(request.getParameter("sort")!=null) {
	if(request.getParameter("sort").equals("sex")){allSharks=myShepherd.getAllMarkedIndividuals(query, "sex ascending");}
	else if(request.getParameter("sort").equals("name")) {allSharks=myShepherd.getAllMarkedIndividuals(query, "name ascending");}
	else if(request.getParameter("sort").equals("numberEncounters")) {allSharks=myShepherd.getAllMarkedIndividuals(query, "numberEncounters descending");}
	else{allSharks=myShepherd.getAllMarkedIndividuals(query);}
}
else{
	allSharks=myShepherd.getAllMarkedIndividuals(query);
}
//process over to Vector
while (allSharks.hasNext()) {
	MarkedIndividual temp_shark=(MarkedIndividual)allSharks.next();
	rSharks.add(temp_shark);
}

			
//sharks in a particular location ID
if((request.getParameter("locationCodeField")!=null)&&(!request.getParameter("locationCodeField").equals(""))) {
				for(int q=0;q<rSharks.size();q++) {
					MarkedIndividual tShark=(MarkedIndividual)rSharks.get(q);
					
					StringTokenizer st=new StringTokenizer(request.getParameter("locationCodeField"),",");
					boolean exit=false;
					while((st.hasMoreTokens())&&(!exit)){
						if(!tShark.wasSightedInLocationCode(st.nextToken())) {
							rSharks.remove(q);
							q--;
							exit=true;
						}
					}
				} 		//end for
}//end if in locationCode

//sharks with a particular alternateID
if((request.getParameter("alternateIDField")!=null)&&(!request.getParameter("alternateIDField").equals(""))) {
				for(int q=0;q<rSharks.size();q++) {
					MarkedIndividual tShark=(MarkedIndividual)rSharks.get(q);
					if((tShark.getAlternateID()==null)||(!tShark.getAlternateID().startsWith(request.getParameter("alternateIDField")))) {
						rSharks.remove(q);
						q--;
					}
					
				} 		//end for
}//end if with alternateID


//sharks with a photo keyword assigned to one of their encounters
if(request.getParameterValues("keyword")!=null){
String[] keywords=request.getParameterValues("keyword");
int kwLength=keywords.length;
for(int kwIter=0;kwIter<kwLength;kwIter++) {
		String kwParam=keywords[kwIter];
		if(myShepherd.isKeyword(kwParam)) {
			Keyword word=myShepherd.getKeyword(kwParam);
			for(int q=0;q<rSharks.size();q++) {
				MarkedIndividual tShark=(MarkedIndividual)rSharks.get(q);
				if(!tShark.isDescribedByPhotoKeyword(word)) {
					rSharks.remove(q);
					q--;
				}
			} //end for
		} //end if isKeyword
}
}



//sharks of a particular sex
if(request.getParameter("sex")!=null) {
				for(int q=0;q<rSharks.size();q++) {
					MarkedIndividual tShark=(MarkedIndividual)rSharks.get(q);
					if((request.getParameter("sex").equals("male"))&&(!tShark.getSex().equals("male"))) {
						rSharks.remove(q);
						q--;
					}
					else if((request.getParameter("sex").equals("female"))&&(!tShark.getSex().equals("female"))) {
						rSharks.remove(q);
						q--;
					}
					else if((request.getParameter("sex").equals("unsure"))&&(!tShark.getSex().equals("unsure"))) {
						rSharks.remove(q);
						q--;
					}
					else if((request.getParameter("sex").equals("mf"))&&(tShark.getSex().equals("unsure"))) {
						rSharks.remove(q);
						q--;
					}
				} //end for
}//end if of sex




//sharks of a particular size
if((request.getParameter("selectLength")!=null)&&(request.getParameter("lengthField")!=null)) {
				try {
					double size;
					size=(new Double(request.getParameter("lengthField"))).doubleValue();
					for(int q=0;q<rSharks.size();q++) {
					MarkedIndividual tShark=(MarkedIndividual)rSharks.get(q);
					if(request.getParameter("selectLength").equals("greater")){
						if(tShark.avgLengthInPeriod(year1, month1, year2, month2)<size) {
							rSharks.remove(q);
							q--;
						}
					}
					else if(request.getParameter("selectLength").equals("less")) {
						if(tShark.avgLengthInPeriod(year1, month1, year2, month2)>size) {
							rSharks.remove(q);
							q--;
						}
					}

				} //end for
			} catch(NumberFormatException nfe) {}
}//end if is of size
			
//min number of resights			
if((request.getParameter("numResights")!=null)&&(request.getParameter("numResightsOperator")!=null)) {
				int numResights=1;
				String operator = "greater";
				try{
					numResights=(new Integer(request.getParameter("numResights"))).intValue();
					operator = request.getParameter("numResightsOperator");
				}
				catch(NumberFormatException nfe) {}
				for(int q=0;q<rSharks.size();q++) {
					MarkedIndividual tShark=(MarkedIndividual)rSharks.get(q);
					
					
					if(operator.equals("greater")){
						if(tShark.getMaxNumYearsBetweenSightings()<numResights) {
							rSharks.remove(q);
							q--;
						}
					}
					else if(operator.equals("less")){
						if(tShark.getMaxNumYearsBetweenSightings()>numResights) {
							rSharks.remove(q);
							q--;
						}
					}
					else if(operator.equals("equals")){
						if(tShark.getMaxNumYearsBetweenSightings() != numResights) {
							rSharks.remove(q);
							q--;
						}
					}
					
					
				} //end for
}//end if resightOnly

//min number of spots		
if(request.getParameter("numspots")!=null) {
				int numspots=1;
				try{
					numspots=(new Integer(request.getParameter("numspots"))).intValue();
					}
				catch(NumberFormatException nfe) {}
				for(int q=0;q<rSharks.size();q++) {
					MarkedIndividual tShark=(MarkedIndividual)rSharks.get(q);
					int total=tShark.totalEncounters();
					boolean removeShark=true;
					for(int k=0;k<total;k++) {
						Encounter enc=tShark.getEncounter(k);
						if(enc.getNumSpots()>=numspots) {removeShark=false;}

					} //end for encounters
					if(removeShark) {
							rSharks.remove(q);
							q--;
					} //end if

				} //end for sharks
}//end if numspots


//now filter for date-----------------------------
for(int q=0;q<rSharks.size();q++) {
					MarkedIndividual tShark=(MarkedIndividual)rSharks.get(q);
					if(!tShark.wasSightedInPeriod(year1, month1, year2, month2)) {
						rSharks.remove(q);
						q--;
					}
} //end for
//--------------------------------------------------



if(rSharks.size()<listNum) {listNum=rSharks.size();}
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
<div id="page"><jsp:include page="header.jsp" flush="true">
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
		<h1 class="intro"><span class="para"><img
			src="images/markedIndividualIcon.gif" width="26" height="51" align="absmiddle" />
		Shark Search Results</h1>
		<p>Below are sharks <%=startNum%> - <%=listNum%> that matched your
		search. Click any column heading to sort by that field.</p>
		</td>
	</tr>
</table>



<table width="720" border="1">
	<tr>
		<td bgcolor="#99CCFF"></td>
		<td align="left" valign="top" bgcolor="#99CCFF"><strong><a
			href="individualSearchResults.jsp?<%=qString%>&sort=name&startNum=<%=startNum%>&endNum=<%=endNum%>">Shark</a></strong></td>
		<td align="left" valign="top" bgcolor="#99CCFF"><strong><a
			href="individualSearchResults.jsp?<%=qString%>&sort=numberEncounters&startNum=<%=startNum%>&endNum=<%=endNum%>">Encounters</a></strong></td>
		<td align="left" valign="top" bgcolor="#99CCFF"><strong><a
			href="individualSearchResults.jsp?<%=qString%>&sort=sex&startNum=<%=startNum%>&endNum=<%=endNum%>">Sex</a></strong></td>
		<td align="left" valign="top" bgcolor="#99CCFF"><strong>Max
		Years Between Resights</strong></td>

	</tr>

	<%	

//set up the statistics counters	
int count=0;
int numNewlyMarked = 0;

Vector histories=new Vector();
for(int f=0;f<rSharks.size();f++) {
	MarkedIndividual shark=(MarkedIndividual)rSharks.get(f);
	count++;
	
	//check if this individual was newly marked in this period
	Encounter[] dateSortedEncs=shark.getDateSortedEncounters(true);
	int sortedLength=dateSortedEncs.length-1;
	Encounter temp=dateSortedEncs[sortedLength];

	
		if((temp.getYear()==year1)&&(temp.getYear()<year2)&&(temp.getMonth()>=month1)){
			numNewlyMarked++;
		}
		else if((temp.getYear()>year1)&&(temp.getYear()==year2)&&(temp.getMonth()<=month2)){
			numNewlyMarked++;
		}
		else if((temp.getYear()>=year1)&&(temp.getYear()<=year2)&&(temp.getMonth()>=month1)&&(temp.getMonth()<=month2)){
			numNewlyMarked++;
		}
	
	
	if((count>=startNum)&&(count<=endNum)) {			
		Encounter tempEnc=shark.getEncounter(0);		
%>
	<tr>
		<td width="102" bgcolor="#000000"><img
			src="<%=("encounters/"+tempEnc.getEncounterNumber()+"/thumb.jpg")%>"></td>
		<td><a
			href="http://<%=CommonConfiguration.getURLLocation()%>/individuals.jsp?number=<%=shark.getName()%>"><%=shark.getName()%></a>
		<%
		  if((shark.getAlternateID()!=null)&&(!shark.getAlternateID().equals("None"))){
		  %> <br><font size="-1">AlternateID: <%=shark.getAlternateID()%></font> <%
		  }
		  %>
		  <br><font size="-1">First identified: <%=temp.getMonth() %>/<%=temp.getYear() %></font>
		
		</td>
		<td><%=shark.totalEncounters()%></td>
		<td><%=shark.getSex()%></td>
		<td><%=shark.getMaxNumYearsBetweenSightings()%></td>
	</tr>
	<%
} //end if to control number displayed
if (((request.getParameter("export")!=null)||(request.getParameter("capture")!=null))&&(request.getParameter("startNum")==null)) {
	//let's generate a programMarkEntry for this shark or check for an existing one
  	//first generate a history
	int startYear=3000;
	int endYear=3000;
	int startMonth=3000;
	int endMonth=3000;
	String history="";
	if(year1>year2){startYear=year2;endYear=year1;startMonth=month2;endMonth=year1;}
  	else{startYear=year1; endYear=year2;startMonth=month1; endMonth=month2;}
	int NumHistoryYears=(endYear-startYear)+1;

	//there will be yearDiffs histories
	while(startYear<=endYear){
		if(request.getParameter("subsampleMonths")!=null){
			int monthIter=startMonth;
			while(monthIter<=endMonth) {
				if(shark.wasSightedInMonth(startYear, monthIter)){history=history+"1";}
				else{history=history+"0";}
				monthIter++;
			} //end while
		}
		else {
			if(shark.wasSightedInYear(startYear)){history=history+"1";}
			else{history=history+"0";}
		}
		startYear++;
	}
	
	boolean foundIdenticalHistory=false;
	for(int h=0;h<histories.size();h++){

	}
	if(!foundIdenticalHistory){
		
		if(history.indexOf("1")!=-1) {

		}
	}	
	

  } //end if export
  
 } //end for
boolean includeZeroYears=true;

boolean subsampleMonths=false;
if(request.getParameter("subsampleMonths")!=null){
	subsampleMonths=true;
}
numResults=count;
  %>
</table>



<%
	myShepherd.rollbackDBTransaction();
	startNum+=10;	
	endNum+=10;
	if(endNum>numResults) {
		endNum=numResults;
	}





if(startNum<numResults) {%>
<p><a
	href="individualSearchResults.jsp?<%=qString%>&startNum=<%=startNum%>&endNum=<%=endNum%>&sort=<%=request.getParameter("sort")%>">See
next results <%=startNum%> - <%=endNum%></a></p>
<%}
if((startNum-10)>1) {%>
<p><a
	href="individualSearchResults.jsp?<%=qString%>&startNum=<%=(startNum-20)%>&endNum=<%=(startNum-11)%>&sort=<%=request.getParameter("sort")%>">See
previous results <%=(startNum-20)%> - <%=(startNum-11)%></a></p>

<%}%>
<p>
<table width="720" border="0" cellspacing="0" cellpadding="0">
	<tr>
		<td align="right">
		<p><strong>Matching sharks</strong>: <%=count%><br />
		Number first sighted in the specified period: <%=numNewlyMarked %>
		</p>
		<%myShepherd.beginDBTransaction();%>
		<p><strong>Total sharks in the database</strong>: <%=(myShepherd.getNumMarkedIndividuals())%></p>
		</td>
		<%
	  myShepherd.rollbackDBTransaction();
	  myShepherd.closeDBTransaction();
	  
	  %>
	</tr>
</table>
</p>
<br>
<p></p>
<jsp:include page="footer.jsp" flush="true" />
</div>
</div>
<!-- end page --></div>
<!--end wrapper -->
</body>
</html>


