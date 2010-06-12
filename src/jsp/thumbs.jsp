<!DOCTYPE html PUBLIC "-//W3C//DTD XHTML 1.0 Transitional//EN"
        "http://www.w3.org/TR/xhtml1/DTD/xhtml1-transitional.dtd">
<%@ page contentType="text/html; charset=utf-8" language="java"
	import="java.util.StringTokenizer,java.util.Properties, java.io.FileInputStream, java.io.File, java.io.FileNotFoundException, org.ecocean.*, java.lang.StringBuffer, java.util.Vector, java.util.Iterator, java.lang.NumberFormatException"%>
<%

//setup our Properties object to hold all properties
	Properties props=new Properties();
	String langCode="en";
	
	//check what language is requested
	if(request.getParameter("langCode")!=null){
		if(request.getParameter("langCode").equals("fr")) {langCode="fr";}
		if(request.getParameter("langCode").equals("de")) {langCode="de";}
		if(request.getParameter("langCode").equals("es")) {langCode="es";}
	}
	
	//set up the file input stream
	//FileInputStream propsInputStream=new FileInputStream(new File((new File(".")).getCanonicalPath()+"/webapps/ROOT/WEB-INF/classes/bundles/"+langCode+"/encounters.properties"));
	props.load(getClass().getResourceAsStream("/bundles/"+langCode+"/encounters.properties"));
	
	
	//load our variables for the submit page
	String title=props.getProperty("title");
	String submit_maintext=props.getProperty("maintext");
	String submit_reportit=props.getProperty("reportit");
	String submit_language=props.getProperty("language");
	String what_do=props.getProperty("what_do");
	String read_overview=props.getProperty("read_overview");
	String see_all_encounters=props.getProperty("see_all_encounters");
	String see_all_sharks=props.getProperty("see_all_sharks");
	String report_encounter=props.getProperty("report_encounter");
	String log_in=props.getProperty("log_in");
	String contact_us=props.getProperty("contact_us");
	String search=props.getProperty("search");
	String encounter=props.getProperty("encounter");
	String shark=props.getProperty("shark");
	String join_the_dots=props.getProperty("join_the_dots");
	String menu=props.getProperty("menu");
	String last_sightings=props.getProperty("last_sightings");
	String more=props.getProperty("more");
	String ws_info=props.getProperty("ws_info");
	String contributors=props.getProperty("contributors");
	String about=props.getProperty("about");
	String forum=props.getProperty("forum");
	String blog=props.getProperty("blog");
	String area=props.getProperty("area");
	String match=props.getProperty("match");
	

	Shepherd myShepherd=new Shepherd();
	String currentSort="nosort";
	String displaySort="";
	if (request.getParameter("sort")!=null) {currentSort=request.getParameter("sort");
		if(request.getParameter("sort").startsWith("name")){displaySort=" sorted by Name";}
		else if(request.getParameter("sort").startsWith("series")){displaySort=" sorted by Series Code";}
		else if(request.getParameter("sort").startsWith("sex")){displaySort=" sorted by Sex";}
		else if(request.getParameter("sort").startsWith("encounters")){displaySort=" sorted by # Encounters";}
		}
	currentSort="&amp;sort="+currentSort;
	int lowCount=1, highCount=15;
	if ((request.getParameter("start")!=null)&&(request.getParameter("end")!=null)) {
		lowCount=(new Integer(request.getParameter("start"))).intValue();
		highCount=(new Integer(request.getParameter("end"))).intValue();
		if((highCount>(lowCount+14))&&(session.getAttribute("logged")==null)) {highCount=lowCount+14;}
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
<%

myShepherd.beginDBTransaction();

try{
//trying
int totalCount=99999;


%>
<table border="0">
	<tr>
		<td>
		<h1>View Encounter Image Thumbnails</h1>
		</td>
	</tr>
	<tr>
		<th class="caption">Below are thumbnail images from the encounters currently stored in the visual database. Click <strong>Next</strong>
		to view the next set of thumbnails. Click <strong>Previous</strong> to
		see the previous set of thumbnails.
		</td>
	</tr>
</table>


<table id="results" border="0" width="100%">
	<tr class="paging">
		<td align="left">
		<%


if (highCount<totalCount) {%> <a
			href="http://<%=CommonConfiguration.getURLLocation()%>/thumbs.jsp?langCode=<%=langCode%>&amp;start=<%=(lowCount+15)%>&amp;end=<%=(highCount+15)%>">Next</a>
		<%} 
  if ((lowCount-15)>=1) {%> | <a
			href="http://<%=CommonConfiguration.getURLLocation()%>/thumbs.jsp?langCode=<%=langCode%>&amp;start=<%=(lowCount-15)%>&amp;end=<%=(highCount-15)%>">Previous</a>
		<%}%>
		</td>
		<td align="right" colspan="2">
		<%
 String startNum="1";
 String endNum="15";
  try{
 
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
 } catch(NumberFormatException nfe) {}
 
 %> Viewing: <%=lowCount%> - <%=highCount%></td>
	</tr>


	<%		

			int countMe=0;
			Vector thumbLocs=new Vector();
			thumbLocs=myShepherd.getThumbnails(lowCount, highCount);
			
			
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
			alt="whale shark photo" border="1" /></a></td>
		<%
					
					countMe++;
					}
				} //endFor
				%>
	</tr>
	<%} //endFor
%>
	<tr class="paging">
		<td align="left">
		<%
if (highCount<totalCount) {%> <a
			href="http://<%=CommonConfiguration.getURLLocation()%>/thumbs.jsp?langCode=<%=langCode%>&amp;start=<%=(lowCount+15)%>&amp;end=<%=(highCount+15)%>">Next</a>
		<%} 
  if ((lowCount-15)>=0) {%> | <a
			href="http://<%=CommonConfiguration.getURLLocation()%>/thumbs.jsp?langCode=<%=langCode%>&amp;start=<%=(lowCount-15)%>&amp;end=<%=(highCount-15)%>">Previous</a>
		<%}%>
		</td>
		<td align="right" colspan="2">Viewing: <%=lowCount%> - <%=highCount%>

		</td>
	</tr>
</table>



<%
 //System.out.println("allEncounters.jsp page is attempting to commit a transaction...");

myShepherd.rollbackDBTransaction();
myShepherd.closeDBTransaction();
myShepherd=null;
%>
<p></p>

<%
//end try
} catch(Exception ex) {

	System.out.println("!!!An error occurred on page thumbs.jsp. The error was:");
	ex.printStackTrace();
	System.out.println("thumbs.jsp page is attempting to rollback a transaction because of an exception...");

	myShepherd.rollbackDBTransaction();
	myShepherd.closeDBTransaction();
	myShepherd=null;
	%>

<p><strong>An error was encountered trying to load the
page. Please click re-load in your browser. If the error message
reappears, please email webmaster@whaleshark.org to inform us of the
error. Thank you!</strong></p>
<p></p>


<%
}
%>
</div>
<!-- end main --> <jsp:include page="footer.jsp" flush="true" /></div>
<!-- end page --></div>
<!--end wrapper -->
</body>
</html>