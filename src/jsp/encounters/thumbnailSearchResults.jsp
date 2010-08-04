<!DOCTYPE html PUBLIC "-//W3C//DTD XHTML 1.0 Transitional//EN"
        "http://www.w3.org/TR/xhtml1/DTD/xhtml1-transitional.dtd">
<%@ page contentType="text/html; charset=utf-8" language="java" import="java.util.StringTokenizer,org.ecocean.*, java.lang.Integer, java.lang.NumberFormatException, java.util.Vector, java.util.Iterator, java.util.GregorianCalendar, java.util.Properties, javax.jdo.*"%>

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

//let's load thumbnailSearch.properties
String langCode="en";
if(session.getAttribute("langCode")!=null){langCode=(String)session.getAttribute("langCode");}

Properties encprops=new Properties();
encprops.load(getClass().getResourceAsStream("/bundles/"+langCode+"/thumbnailSearchResults.properties"));


Shepherd myShepherd=new Shepherd();

  			Iterator allEncounters;
  			Vector rEncounters=new Vector();			

  			myShepherd.beginDBTransaction();
  			
  			rEncounters = EncounterQueryProcessor.processQuery(myShepherd, request);
			






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
		<h1 class="intro"><%=encprops.getProperty("title")%></h1>
		</p>
		<p><%=encprops.getProperty("belowMatches")%> <%=startNum%> - <%=endNum%> <%=encprops.getProperty("thatMatched")%></p>
		</td>
	</tr>
</table>

<table id="results" border="0" width="100%">
	<%		

			String[] keywords=request.getParameterValues("keyword");
			int countMe=0;
			Vector thumbLocs=new Vector();
			
			try {
				thumbLocs=myShepherd.getThumbnails(rEncounters.iterator(), startNum, endNum, keywords);

			
			
					for(int rows=0;rows<5;rows++) {		%>

						<tr>

							<%
							for(int columns=0;columns<3;columns++){
								if(countMe<thumbLocs.size()) {
									String combined=(String)thumbLocs.get(countMe);
									StringTokenizer stzr=new StringTokenizer(combined,"BREAK");
									String thumbLink=stzr.nextToken();
									String encNum=stzr.nextToken();
									int fileNamePos=combined.lastIndexOf("BREAK")+5;
									String fileName=combined.substring(fileNamePos);
									boolean video=true;
									if(!thumbLink.endsWith("video.jpg")){
										thumbLink="http://"+CommonConfiguration.getURLLocation()+"/encounters/"+thumbLink;
										video=false;
									}
									String link="http://"+CommonConfiguration.getURLLocation()+"/encounters/encounter.jsp?number="+encNum;
						
							%>

									<td>
										<table>
										<tr>
											<td>
												<a href="<%=link%>"><img src="<%=thumbLink%>" alt="photo" border="1" /></a>
											</td>
										</tr>
										<%
										if((keywords!=null)&&(keywords.length>0)&&(!keywords[0].equals("None"))){	
										int kwLength=keywords.length;
										%>
										<tr>
										<td>
											<strong><%=encprops.getProperty("matchingKeywords") %></strong>
											<%
									          for(int kwIter=0;kwIter<kwLength;kwIter++) {
									              String kwParam=keywords[kwIter];
									              if(myShepherd.isKeyword(kwParam)) {
									                Keyword word=myShepherd.getKeyword(kwParam);
									                if(word.isMemberOf(encNum+"/"+fileName)) {
									                	%>
														<br /><%= word.getReadableName()%>
														
														<%
														
									                  
									                }
									              } //end if isKeyword
									            }
											%>
										</td>
										</tr>
											<%
											
											}
											%>


										
										</table>
									</td>
							<%
					
								countMe++;
								} //end if
							} //endFor
							%>
					</tr>
				<%} //endFor
	
				} catch(Exception e) {
					e.printStackTrace();
				%>
	<tr>
		<td>
		<p><%=encprops.getProperty("error")%></p>.</p>
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
	href="thumbnailSearchResults.jsp?<%=qString%><%=numberResights%>&startNum=<%=startNum%>&endNum=<%=endNum%>"><%=encprops.getProperty("seeNextResults")%> (<%=startNum%> - <%=endNum%></a>)</p>
<%
if((startNum-15)>1) {%>
<p><a
	href="thumbnailSearchResults.jsp?<%=qString%><%=numberResights%>&startNum=<%=(startNum-30)%>&endNum=<%=(startNum-16)%>"><%=encprops.getProperty("seePreviousResults")%> (<%=(startNum-30)%> - <%=(startNum-16)%>)</a></p>

<%}
if((startNum-15)==1) {
%>
<p>
<table width="720" border="0" cellspacing="0" cellpadding="0">
	<tr>
		<td align="right">
		<p><strong><%=encprops.getProperty("totalMatches")%></strong>: <%=thumbLocs.size()%></p>
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


