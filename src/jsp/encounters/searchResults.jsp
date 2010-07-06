<!DOCTYPE html PUBLIC "-//W3C//DTD XHTML 1.0 Transitional//EN" "http://www.w3.org/TR/xhtml1/DTD/xhtml1-transitional.dtd">
<%@ page contentType="text/html; charset=utf-8" language="java"
	import="org.ecocean.servlet.*,org.ecocean.*, javax.jdo.*, java.lang.StringBuffer, java.util.StringTokenizer,org.dom4j.Document, org.dom4j.DocumentHelper, org.dom4j.io.OutputFormat, org.dom4j.io.XMLWriter, java.lang.Integer, org.dom4j.Element, java.lang.NumberFormatException, java.io.*, java.util.Vector, java.util.Iterator, jxl.*, jxl.write.*, java.util.Calendar,java.util.Properties,java.util.StringTokenizer,java.util.ArrayList"%>


<html>
<head>
<%!
    public void finalize(WritableWorkbook workbook) {
        try {
			workbook.write(); 
        } 
		catch (Exception e) {
			System.out.println("Unknown error writing output Excel file...");
			e.printStackTrace();
		}
    }
%>

<%!
public String addEmails(Vector encs){

StringBuffer contributors=new StringBuffer();
int size=encs.size();
for(int f=0;f<size;f++){

	Encounter tempEnc=(Encounter)encs.get(f);

		//calculate the number of submitter contributors
	if((tempEnc.getSubmitterEmail()!=null)&&(!tempEnc.getSubmitterEmail().equals(""))) {
		//check for comma separated list
		if(tempEnc.getSubmitterEmail().indexOf(",")!=-1) {
			//break up the string
			StringTokenizer stzr=new StringTokenizer(tempEnc.getSubmitterEmail(),",");
			while(stzr.hasMoreTokens()) {
				String token=stzr.nextToken();
				if (contributors.indexOf(token)==-1) {
					contributors.append(token+"\n");
				}
			}
		}
		else if (contributors.indexOf(tempEnc.getSubmitterEmail())==-1) {
			contributors.append(tempEnc.getSubmitterEmail()+"\n");
		}
	}
	
		//calculate the number of photographer contributors
	if((tempEnc.getPhotographerEmail()!=null)&&(!tempEnc.getPhotographerEmail().equals(""))) {
		//check for comma separated list
		if(tempEnc.getPhotographerEmail().indexOf(",")!=-1) {
			//break up the string
			StringTokenizer stzr=new StringTokenizer(tempEnc.getPhotographerEmail(),",");
			while(stzr.hasMoreTokens()) {
				String token=stzr.nextToken();
				if (contributors.indexOf(token)==-1) {
					contributors.append(token+"\n");
				}
			}
		}
		else if (contributors.indexOf(tempEnc.getPhotographerEmail())==-1) {
			contributors.append(tempEnc.getPhotographerEmail()+"\n");
		}
	}


}

return contributors.toString();

} //end for
%>


<%
	Shepherd myShepherd=new Shepherd();

//setup our Properties object to hold locale properties
Properties props=new Properties();
try{
	props.load(getClass().getResourceAsStream("/bundles/en/locales.properties"));
}
catch(Exception e){System.out.println("     Could not load locales.properties in the encounter search results."); e.printStackTrace();}


int startNum=1;
int endNum=10;

//Let's setup our email export file options
String emailFilename="emailResults_"+request.getRemoteUser()+".txt";
File emailFile=new File(getServletContext().getRealPath(("/encounters/"+emailFilename)));


//let's set up our Excel spreasheeting operations
String filename="searchResults_"+request.getRemoteUser()+".xls";
String kmlFilename="KMLExport_"+request.getRemoteUser()+".kml";
//File file=new File((new File(".")).getCanonicalPath()+File.separator+"webapps"+File.separator+"ROOT"+File.separator+"encounters"+File.separator+filename);
File file=new File(getServletContext().getRealPath(("/encounters/"+filename)));


WritableWorkbook workbook = Workbook.createWorkbook(file); 
WritableSheet sheet = workbook.createSheet("Search Results", 0);
Label label0 = new Label(0, 0, "Date Last Modified"); 
sheet.addCell(label0);
Label label1 = new Label(1, 0, "Institution Code"); 
sheet.addCell(label1);
Label label2 = new Label(2, 0, "Collection Code"); 
sheet.addCell(label2);
Label label2a = new Label(3, 0, "Catalog Number"); 
sheet.addCell(label2a);
Label label3 = new Label(4, 0, "Record URL"); 
sheet.addCell(label3);
Label label5 = new Label(5, 0, "Scientific Name"); 
sheet.addCell(label5);
Label label6 = new Label(6, 0, "Basis of record"); 
sheet.addCell(label6);
Label label7 = new Label(7, 0, "Citation"); 
sheet.addCell(label7);
Label label8 = new Label(8, 0, "Kingdom"); 
sheet.addCell(label8);
Label label9 = new Label(9, 0, "Phylum"); 
sheet.addCell(label9);
Label label10 = new Label(10, 0, "Class"); 
sheet.addCell(label10);
Label label11 = new Label(11, 0, "Order"); 
sheet.addCell(label11);
Label label12 = new Label(12, 0, "Family"); 
sheet.addCell(label12);
Label label13 = new Label(13, 0, "Genus"); 
sheet.addCell(label13);
Label label14 = new Label(14, 0, "species"); 
sheet.addCell(label14);
Label label15 = new Label(15, 0, "Year Identified"); 
sheet.addCell(label15);
Label label16 = new Label(16, 0, "Month Identified"); 
sheet.addCell(label16);
Label label17 = new Label(17, 0, "Day Identified"); 
sheet.addCell(label17);

Label label18 = new Label(18, 0, "Year Collected"); 
sheet.addCell(label18);
Label label19 = new Label(19, 0, "Month Collected"); 
sheet.addCell(label19);
Label label20 = new Label(20, 0, "Day Collected"); 
sheet.addCell(label20);
Label label21 = new Label(21, 0, "Time of Day"); 
sheet.addCell(label21);
Label label22 = new Label(22, 0, "Locality"); 
sheet.addCell(label22);

Label label23 = new Label(23, 0, "Longitude"); 
sheet.addCell(label23);
Label label24 = new Label(24, 0, "Latitude"); 
sheet.addCell(label24);
Label label25 = new Label(25, 0, "Sex"); 
sheet.addCell(label25);
Label label26 = new Label(26, 0, "Notes"); 
sheet.addCell(label26);
Label label27 = new Label(27, 0, "Length (m)"); 
sheet.addCell(label27);
Label label28 = new Label(28, 0, "Marked Individual"); 
sheet.addCell(label28);
Label label29 = new Label(29, 0, "Location code"); 
sheet.addCell(label29);

WritableCellFormat floatFormat = new WritableCellFormat (NumberFormats.FLOAT); 
WritableCellFormat integerFormat = new WritableCellFormat (NumberFormats.INTEGER); 

//setup the KML output
Document document = DocumentHelper.createDocument();
Element root = document.addElement( "kml" );
root.addAttribute("xmlns","http://www.opengis.net/kml/2.2");
root.addAttribute("xmlns:gx","http://www.google.com/kml/ext/2.2");
Element docElement = root.addElement( "Document" );

boolean addTimeStamp = false;
boolean generateKML = false;
if(request.getParameter("generateKML")!=null){
	generateKML = true;
}
if(request.getParameter("addTimeStamp")!=null){
	addTimeStamp = true;
}

//add styles first if necessary
//Element styleElement1 = docElement.addElement( "Style" );

//should we generate emails
boolean generateEmails=false;
if(request.getParameter("generateEmails")!=null){generateEmails=true;}


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

StringBuffer exportString=new StringBuffer();
exportString.append("Number\tSex\tSize(m)\tDepth(m)\tLocation\tLocationCode\tLatitude\tLongitude\tDate\tMarkedIndividual\n");


int numResults=0;

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
		catch(NumberFormatException nfe) {nfe.printStackTrace();}

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
if(request.getParameter("unidentifiable")==null) {
		for(int q=0;q<rEncounters.size();q++) {
			Encounter rEnc=(Encounter)rEncounters.get(q);
			if(rEnc.wasRejected()){
				rEncounters.remove(q);
				q--;
				}
		}
}
if(request.getParameter("unapproved")==null) {
		for(int q=0;q<rEncounters.size();q++) {
			Encounter rEnc=(Encounter)rEncounters.get(q);
			if((!rEnc.isApproved())&&(!rEnc.wasRejected())){
				rEncounters.remove(q);
				q--;
				}
		}
}
if(request.getParameter("approved")==null) {
		for(int q=0;q<rEncounters.size();q++) {
			Encounter rEnc=(Encounter)rEncounters.get(q);
			if(rEnc.isApproved()){
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

//filter by alive/dead status------------------------------------------
if(request.getParameter("alive")==null) {
		for(int q=0;q<rEncounters.size();q++) {
			Encounter rEnc=(Encounter)rEncounters.get(q);
			if(rEnc.getLivingStatus().equals("alive")){
				rEncounters.remove(q);
				q--;
				}
		}
}
if(request.getParameter("dead")==null) {
		for(int q=0;q<rEncounters.size();q++) {
			Encounter rEnc=(Encounter)rEncounters.get(q);
			if(rEnc.getLivingStatus().equals("dead")){
				rEncounters.remove(q);
				q--;
				}
		}
}
//filter by alive/dead status--------------------------------------------------------------------------------------



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
	nfe.printStackTrace();
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

//filter for alternate ID------------------------------------------
if((request.getParameter("alternateIDField")!=null)&&(!request.getParameter("alternateIDField").equals(""))) {
		for(int q=0;q<rEncounters.size();q++) {
			Encounter rEnc=(Encounter)rEncounters.get(q);
			String altID=request.getParameter("alternateIDField").toLowerCase();
			if(!rEnc.getAlternateID().toLowerCase().startsWith(altID)){
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
	nfe.printStackTrace();
		}
	}
}
//date filter--------------------------------------------------------------------------------------


//--let's estimate the number of results that might be unique

int numUniqueEncounters=0;
int numUnidentifiedEncounters=0;
int numDuplicateEncounters=0;
ArrayList uniqueEncounters=new ArrayList();
for(int q=0;q<rEncounters.size();q++) {
	Encounter rEnc=(Encounter)rEncounters.get(q);
	if(!rEnc.isAssignedToMarkedIndividual().equals("Unassigned")){
		String assemblage=rEnc.getIndividualID()+":"+rEnc.getYear()+":"+rEnc.getMonth()+":"+rEnc.getDay();
		if(!uniqueEncounters.contains(assemblage)){
			numUniqueEncounters++;
			uniqueEncounters.add(assemblage);
		}
		else{
			numDuplicateEncounters++;
		}
	}
	else{
		numUnidentifiedEncounters++;
	}
	
}

//--end unique counting------------------------------------------


//let's print out the contributors file
if(generateEmails){
	try{
	String contribs=addEmails(rEncounters);
	FileOutputStream fos=new FileOutputStream(emailFile);
	OutputStreamWriter outp=new OutputStreamWriter(fos);
	outp.write(contribs);
	outp.close();
	}
	catch(Exception e){
		e.printStackTrace();
%>
<p>Failed to write out the contributors file!</p>
<%
			}

		}
		%>
<title><%=CommonConfiguration.getHTMLTitle()%></title>
<meta http-equiv="Content-Type" content="text/html; charset=utf-8" />
<meta name="Description"
	content="<%=CommonConfiguration.getHTMLDescription()%>" />
<meta name="Keywords"
	content="<%=CommonConfiguration.getHTMLKeywords()%>" />
<meta name="Author" content="<%=CommonConfiguration.getHTMLAuthor()%>" />
<link href="<%=CommonConfiguration.getCSSURLLocation()%>"
	rel="stylesheet" type="text/css" />
<link rel="shortcut icon"
	href="<%=CommonConfiguration.getHTMLShortcutIcon()%>" />
</head>

<body onload="initialize()" onunload="GUnload()">
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
		<h1 class="intro">Encounter Search Results</h1>
		</p>
		<p>Below are encounters <%=startNum%> - <%=endNum%> that matched
		your search. Click any column heading to sort by that field.</p>
		</td>
	</tr>
</table>

<%
	if (request.getParameter("export")!=null) {
%>
<p>Exported Excel spreadsheet (.xls) file: <a
	href="http://<%=CommonConfiguration.getURLLocation()%>/encounters/<%=filename%>"><%=filename%></a><br>
<em>Right-click the link and save to your local hard drive.</em>
</p>
<%
	}
%> <%
	if (request.getParameter("generateKML")!=null) {
%>
<p>Exported Google Earth KML file: <a
	href="http://<%=CommonConfiguration.getURLLocation()%>/encounters/<%=kmlFilename%>"><%=kmlFilename%></a><br>
<em>Right-click the link and save to your local hard drive.</em>
</p>
<%
	}
%> <%
	if (generateEmails) {
%>
<p>Exported contributors file: <a
	href="http://<%=CommonConfiguration.getURLLocation()%>/encounters/<%=emailFilename%>"><%=emailFilename%></a><br>
<em>Right-click the link and save to your local hard drive.</em>
</p>
<%
	}
%>

<table width="720" border="1">
	<tr>
		<td bgcolor="#99CCFF"></td>
		<td align="left" valign="top" bgcolor="#99CCFF"><strong>Number</strong></td>
		<td align="left" valign="top" bgcolor="#99CCFF"><strong>Date</strong></td>
		<td align="left" valign="top" bgcolor="#99CCFF"><strong>Location</strong></td>
		<td align="left" valign="top" bgcolor="#99CCFF"><strong>Location
		ID</strong></td>
		<td align="left" valign="top" bgcolor="#99CCFF"><strong>Size</strong></td>
		<td align="left" valign="top" bgcolor="#99CCFF"><strong>Sex</strong></td>
		<td align="left" valign="top" bgcolor="#99CCFF"><strong>Assigned to</strong></td>
	</tr>

	<%
  					Vector haveGPSData=new Vector();
  					int count=0;

  						for(int f=0;f<rEncounters.size();f++) {
  						
  					//Encounter enc=(Encounter)allEncounters.next(); 
  					Encounter enc=(Encounter)rEncounters.get(f);
  					count++;
  					numResults++;
  					if((enc.getDWCDecimalLatitude()!=null)&&(enc.getDWCDecimalLongitude()!=null)) {
  						   haveGPSData.add(enc);
  					}

  				//populate KML file ====================================================
  				if(generateKML){
  					if((enc.getDWCDecimalLongitude()!=null)&&(enc.getDWCDecimalLatitude()!=null)){
  						Element placeMark = docElement.addElement( "Placemark" );
  						Element name = placeMark.addElement( "name" );
  						String nameText = "";
  						
  						//add the name
  						if(enc.isAssignedToMarkedIndividual().equals("Unassigned")){
  							nameText = "Encounter "+enc.getEncounterNumber();
  						}
  						else{
  							nameText = enc.isAssignedToMarkedIndividual()+": Encounter "+enc.getEncounterNumber();
  						}
  						name.setText(nameText);
  						
  						//add the visibility element
  						Element viz = placeMark.addElement( "visibility" );
  						viz.setText("1");
  						
  						/**
  						Element style = placeMark.addElement( "Style" );
  						Element iconStyle = style.addElement( "IconStyle" );
  						Element icon = iconStyle.addElement( "Icon" );
  						
  						
  						Element href = icon.addElement( "href" );
  						
  						String iconURL = "http://"+CommonConfiguration.getURLLocation()+"/images/geShark";
  						
  						if(enc.getSex().equals("male")){
  							iconURL +="_male";
  						}
  						else if(enc.getSex().equals("female")){
  							iconURL +="_female";
  						}
  						
  						//filter by size
  						if(enc.getSize()>0){
  							int intsize = (new Double(enc.getSize())).intValue();
  							iconURL +=("_"+intsize);
  						}
  						
  						iconURL +=".gif";

  						href.setText(iconURL);
  						*/
  						
  						//add the descriptive HTML
  						Element description = placeMark.addElement( "description" );
  						
  						String descHTML = "<p><a href=\"http://"+CommonConfiguration.getURLLocation()+"/encounters/encounter.jsp?noscript=true&number="+enc.getEncounterNumber()+"\">Direct Link</a></p>";
  						descHTML+= "<p> <strong>Date:</strong> "+enc.getDate()+"</p>";
  						descHTML+= "<p> <strong>Location:</strong><br>"+enc.getLocation()+"</p>";
  						if(enc.getSize()>0){
  							descHTML+= "<p> <strong>Size:</strong> "+enc.getSize()+" meters</p>";
  						}
  						descHTML+= "<p> <strong>Sex:</strong> "+enc.getSex()+"</p>";
  						if(!enc.getComments().equals("")){
  							descHTML+= "<p> <strong>Comments:</strong> "+enc.getComments()+"</p>";
  						}
  						
  						descHTML+="<strong>Images</strong><br>";
  						Vector imgs = enc.getAdditionalImageNames();
  						int imgsNum = enc.getAdditionalImageNames().size();
  						for(int imgNum = 0;imgNum<imgsNum;imgNum++){
  							descHTML+= ("<br>"+"<a href=\"http://"+CommonConfiguration.getURLLocation()+"/encounters/encounter.jsp?noscript=true&number="+enc.getEncounterNumber()+"\"><img src=\"http://"+CommonConfiguration.getURLLocation()+"/encounters/"+enc.getEncounterNumber()+"/"+(imgNum+1)+".jpg\"></a>");
  						}
  						
  						description.addCDATA(descHTML); 
  						
  						if(addTimeStamp){
  							//add the timestamp
  							String stampString = "";
  							if(enc.getYear()!=-1){
  								stampString+=enc.getYear();
  								if(enc.getMonth()!=-1){
  									String tsMonth = Integer.toString(enc.getMonth());
  									if(tsMonth.length()==1){tsMonth="0"+tsMonth;}
  									stampString+=("-"+tsMonth);
  									if(enc.getDay()!=-1){
  										String tsDay = Integer.toString(enc.getDay());
  										if(tsDay.length()==1){tsDay="0"+tsDay;}
  										stampString+=("-"+tsDay);
  									}
  								}
  							}
  						
  							if(!stampString.equals("")){
  								Element timeStamp = placeMark.addElement( "TimeStamp" );
  								timeStamp.addNamespace("gx","http://www.google.com/kml/ext/2.2");
  								Element when = timeStamp.addElement( "when" );
  								when.setText(stampString);
  							}
  						}
  						
  						//add the actual lat-long points
  						Element point = placeMark.addElement( "Point" );
  						Element coords = point.addElement( "coordinates" );
  						String coordsString = enc.getDWCDecimalLongitude()+","+enc.getDWCDecimalLatitude();
  						if(enc.getMaximumElevationInMeters()!=0.0){
  							coordsString+=","+enc.getMaximumElevationInMeters();
  						}
  						else{
  							coordsString+=",0";
  						}
  						coords.setText(coordsString);
  						
  						
  						
  					}
  				}
  				//end KML ==============================================================

  				if((numResults>=startNum)&&(numResults<=endNum)) {
  				%>
	<tr>
		<td width="102" bgcolor="#000000"><img
			src="<%=(enc.getEncounterNumber()+"/thumb.jpg")%>"></td>
		<td><a
			href="http://<%=CommonConfiguration.getURLLocation()%>/encounters/encounter.jsp?number=<%=enc.getEncounterNumber()%>"><%=enc.getEncounterNumber()%></a>

		<%
					  	if((enc.getAlternateID()!=null)&&(!enc.getAlternateID().equals("None"))){
					  %> <br><font size="-1"><%=enc.getAlternateID()%></font> <%
		  	}
		  %>
		
		</td>
		<td><%=enc.getDate()%></td>
		<td><%=enc.getLocation()%></td>
		<td><%=enc.getLocationCode()%></td>
		<td><%=enc.getSize()%> <%=enc.getMeasureUnits()%></td>
		<td><%=enc.getSex()%></td>
		<%
	if (enc.isAssignedToMarkedIndividual().trim().toLowerCase().equals("unassigned")) {
%>
		<td>Unassigned</td>
		<%
	} else {
%>
		<td><a
			href="../individuals.jsp?number=<%=enc.isAssignedToMarkedIndividual()%>"><%=enc.isAssignedToMarkedIndividual()%></a></td>
		<%
	}
%>


	</tr>
	<%
  	} //end if to control number displayed

  // Excel export =========================================================

   if ((request.getParameter("export")!=null)&&(ServletUtilities.isUserAuthorizedForEncounter(enc,request))) {
  	try{
  		Label lNumber = new Label(0, count, enc.getDWCDateLastModified());
  		sheet.addCell(lNumber);
  		
  		Label lNumberx1 = new Label(1, count, CommonConfiguration.getProperty("institutionCode"));
  		sheet.addCell(lNumberx1);
  		
  		Label lNumberx2 = new Label(2, count, CommonConfiguration.getProperty("catalogCode"));
  		sheet.addCell(lNumberx2);
  		
  		Label lNumberx3 = new Label(3, count, enc.getEncounterNumber());
  		sheet.addCell(lNumberx3);
  		
  		Label lNumberx4 = new Label(4, count, ("http://"+CommonConfiguration.getURLLocation()+"/encounters/encounter.jsp?number="+enc.getEncounterNumber()));
  		sheet.addCell(lNumberx4);
  		
  		Label lNumberx5 = new Label(5, count, (CommonConfiguration.getProperty("genus")+" "+CommonConfiguration.getProperty("species")));
  		sheet.addCell(lNumberx5);
  		
  		Label lNumberx6 = new Label(6, count, "P");
  		sheet.addCell(lNumberx6);
  		
  		Calendar toDay = Calendar.getInstance();
  		int year = toDay.get(Calendar.YEAR);
  		Label lNumberx7 = new Label(7, count, CommonConfiguration.getProperty("citation"));
  		sheet.addCell(lNumberx7);
  		
  		Label lNumberx8 = new Label(8, count, CommonConfiguration.getProperty("kingdom"));
  		sheet.addCell(lNumberx8);
  		
  		Label lNumberx9 = new Label(9, count, CommonConfiguration.getProperty("phylum"));
  		sheet.addCell(lNumberx9);
  		
  		Label lNumberx10 = new Label(10, count, CommonConfiguration.getProperty("class"));
  		sheet.addCell(lNumberx10);
  		
  		Label lNumberx11 = new Label(11, count, CommonConfiguration.getProperty("order"));
  		sheet.addCell(lNumberx11);

  		
  		Label lNumberx13 = new Label(12, count, CommonConfiguration.getProperty("family"));
  		sheet.addCell(lNumberx13);
  		
  		Label lNumberx14 = new Label(13, count, CommonConfiguration.getProperty("genus"));
  		sheet.addCell(lNumberx14);
  		
  		Label lNumberx15 = new Label(14, count, CommonConfiguration.getProperty("species"));
  		sheet.addCell(lNumberx15);
  		
  		if(enc.getYear()>0){
  	Label lNumberx16 = new Label(15, count, Integer.toString(enc.getYear()));
  	sheet.addCell(lNumberx16);
  	Label lNumberx19 = new Label(18, count, Integer.toString(enc.getYear()));
  	sheet.addCell(lNumberx19);
  		}
  		if(enc.getMonth()>0){
  	Label lNumberx17 = new Label(16, count, Integer.toString(enc.getMonth()));
  	sheet.addCell(lNumberx17);
  	Label lNumberx20 = new Label(19, count, Integer.toString(enc.getMonth()));
  	sheet.addCell(lNumberx20);
  		}
  		if(enc.getDay()>0){
  	Label lNumberx18 = new Label(17, count, Integer.toString(enc.getDay()));
  	sheet.addCell(lNumberx18);
  	Label lNumberx21 = new Label(20, count, Integer.toString(enc.getDay()));
  	sheet.addCell(lNumberx21);
  		}
  		
  		Label lNumberx22 = new Label(21, count, (enc.getDay()+":"+enc.getMinutes()));
  		sheet.addCell(lNumberx22);
  		
  		Label lNumberx23 = new Label(22, count, enc.getLocation());
  		sheet.addCell(lNumberx23);
  		
  		if((enc.getDWCDecimalLatitude()!=null)&&(enc.getDWCDecimalLongitude()!=null)){
  	Label lNumberx24 = new Label(23, count, enc.getDWCDecimalLongitude());
  	sheet.addCell(lNumberx24);
  	Label lNumberx25 = new Label(24, count, enc.getDWCDecimalLatitude());
  	sheet.addCell(lNumberx25);
  		}
  		//check for available locale oordinates
  		//this functionality is primarily used for data export to iobis.org
  		else if((enc.getLocationCode()!=null)&&(!enc.getLocationCode().equals(""))){
  	try{
  		String lc = enc.getLocationCode();
  		if(props.getProperty(lc)!=null){
  		
  			String gps=props.getProperty(lc);
  			StringTokenizer st=new StringTokenizer(gps,",");
  			Label lNumberx25 = new Label(24, count, st.nextToken());
  			sheet.addCell(lNumberx25);
  			Label lNumberx24 = new Label(23, count, st.nextToken());
  			sheet.addCell(lNumberx24);

  		}
  	}
  	catch(Exception e){e.printStackTrace();System.out.println("     I hit an error getting locales in searchResults.jsp.");}
  		}
  		
  		
  		if(!enc.getSex().equals("unsure")) {
  	Label lSex = new Label(25, count, enc.getSex());
  	sheet.addCell(lSex);
  		}
  		Label lNumberx26 = new Label(26, count, enc.getComments().replaceAll("<br>",". ").replaceAll("\n","").replaceAll("\r",""));
  		sheet.addCell(lNumberx26);
  		
  		if(enc.getSize()>0){
  	Label lNumberx27 = new Label(27, count, Double.toString(enc.getSize()));
  	sheet.addCell(lNumberx27);
  		}
  		if(!enc.isAssignedToMarkedIndividual().equals("Unassigned")){
  	Label lNumberx28 = new Label(28, count, enc.isAssignedToMarkedIndividual());
  	sheet.addCell(lNumberx28);
  		}
  		if(enc.getLocationCode()!=null){
  	Label lNumberx29 = new Label(29, count, enc.getLocationCode());
  	sheet.addCell(lNumberx29);
  		}
  		


    		
  	} catch(Exception we) {System.out.println("jExcel error processing search results...");we.printStackTrace();}
    	}
    

    } //end while
    
  // end Excel export =========================================================
  %>
</table>



<%
 	if ((request.getParameter("export")!=null)&&(request.getParameter("startNum")==null)) {
 		finalize(workbook);
 }
 workbook.close();

 myShepherd.rollbackDBTransaction();

 	startNum=startNum+10;	
 	endNum=endNum+10;

 	if(endNum>numResults) {
 		endNum=numResults;
 	}
 String numberResights="";
 if(request.getParameter("numResights")!=null){
 	numberResights="&numResights="+request.getParameter("numResights");
 }
 String qString=request.getQueryString();
 int startNumIndex=qString.indexOf("&startNum");
 if(startNumIndex>-1) {
 	qString=qString.substring(0,startNumIndex);
 }

 if(startNum<numResults) {
 %>
<p><a
	href="searchResults.jsp?<%=qString%><%=numberResights%>&startNum=<%=startNum%>&endNum=<%=endNum%>">See
next results <%=startNum%> - <%=endNum%></a></p>
<%
	}
if((startNum-10)>1) {
%>
<p><a
	href="searchResults.jsp?<%=qString%><%=numberResights%>&startNum=<%=(startNum-20)%>&endNum=<%=(startNum-11)%>">See
previous results <%=(startNum-20)%> - <%=(startNum-11)%></a></p>

<%
	}
%>
<p>
<table width="720" border="0" cellspacing="0" cellpadding="0">
	<tr>
		<td align="right">
		<p><strong>Matching encounters</strong>: <%=numResults%>
		<%
		if(request.isUserInRole("admin")){
		%>
			<br />
			<%=numUniqueEncounters%> identified and unique<br />
			<%=numUnidentifiedEncounters%> unidentified<br />
			<%=(numDuplicateEncounters)%> duplicates
			<%
		}
			%>
		</p>
		<%
			myShepherd.beginDBTransaction();
		%>
		<p><strong>Total encounters in the database</strong>: <%=(myShepherd.getNumEncounters()+(myShepherd.getNumUnidentifiableEncounters()))%></p>
		</td>
		<%
	  	myShepherd.rollbackDBTransaction();
	  %>
	</tr>
</table>
</p>
<br> <%
	//let's print out the KML file
if(generateKML){
	
	//File kmlFile=new File((new File(".")).getCanonicalPath()+File.separator+"webapps"+File.separator+"ROOT"+File.separator+"encounters"+File.separator+kmlFilename);
	File kmlFile=new File(getServletContext().getRealPath(("/encounters/"+kmlFilename)));

	FileWriter kmlWriter=new FileWriter(kmlFile);
	org.dom4j.io.OutputFormat format = org.dom4j.io.OutputFormat.createPrettyPrint();
	format.setLineSeparator(System.getProperty("line.separator"));
	org.dom4j.io.XMLWriter writer = new org.dom4j.io.XMLWriter(kmlWriter, format); 
	writer.write(document);
	writer.close(); 
}
%>


<p><strong><img src="../images/2globe_128.gif" width="64"
	height="64" align="absmiddle" /> Mapped Results</strong></p>
<%
	  	if(haveGPSData.size()>0) {
	  	  myShepherd.beginDBTransaction();
	  	  try{
	  %>

<p><i>Note</i>: If you zoom in too quickly, Google Maps may claim
that it does not have the needed maps. Zoom back out, wait a few seconds
to allow maps to load in the background, and then zoom in again.</p>
<script
	src="http://maps.google.com/maps?file=api&amp;v=2&amp;key=<%=CommonConfiguration.getGoogleMapsKey() %>"
	type="text/javascript"></script> <script type="text/javascript">
    function initialize() {
      if (GBrowserIsCompatible()) {
        var map = new GMap2(document.getElementById("map_canvas"));
        
		
		<%double centroidX=0;
			int countPoints=0;
			double centroidY=0;
			for(int c=0;c<haveGPSData.size();c++) {
				Encounter mapEnc=(Encounter)haveGPSData.get(c);
				countPoints++;
				centroidX=centroidX+Double.parseDouble(mapEnc.getDWCDecimalLatitude());
				centroidY=centroidY+Double.parseDouble(mapEnc.getDWCDecimalLongitude());
			}
			centroidX=centroidX/countPoints;
			centroidY=centroidY/countPoints;%>
			map.setCenter(new GLatLng(<%=centroidX%>, <%=centroidY%>), 1);
			map.addControl(new GSmallMapControl());
        	map.addControl(new GMapTypeControl());
			map.setMapType(G_HYBRID_MAP);
			<%for(int t=0;t<haveGPSData.size();t++) {

				Encounter mapEnc=(Encounter)haveGPSData.get(t);
				double myLat=(new Double(mapEnc.getDWCDecimalLatitude())).doubleValue();
				double myLong=(new Double(mapEnc.getDWCDecimalLongitude())).doubleValue();%>
				          var point<%=t%> = new GLatLng(<%=myLat%>,<%=myLong%>, false);
						  var marker<%=t%> = new GMarker(point<%=t%>);
						  GEvent.addListener(marker<%=t%>, "click", function(){
						  	window.location="http://<%=CommonConfiguration.getURLLocation()%>/encounters/encounter.jsp?number=<%=mapEnc.getEncounterNumber()%>";
						  });
						  GEvent.addListener(marker<%=t%>, "mouseover", function(){
						  	marker<%=t%>.openInfoWindowHtml("Shark: <strong><a target=\"_blank\" href=\"http://<%=CommonConfiguration.getURLLocation()%>/individuals.jsp?number=<%=mapEnc.isAssignedToMarkedIndividual()%>\"><%=mapEnc.isAssignedToMarkedIndividual()%></a></strong><br><table><tr><td><img align=\"top\" border=\"1\" src=\"http://<%=CommonConfiguration.getURLLocation()%>/encounters/<%=mapEnc.getEncounterNumber()%>/thumb.jpg\"></td><td>Date: <%=mapEnc.getDate()%><br>Sex: <%=mapEnc.getSex()%><br>Size: <%=mapEnc.getSize()%> m<br><br><a target=\"_blank\" href=\"http://<%=CommonConfiguration.getURLLocation()%>/encounters/encounter.jsp?number=<%=mapEnc.getEncounterNumber()%>\" >Go to encounter</a></td></tr></table>");
						  });

						  
						  map.addOverlay(marker<%=t%>);
			
		<%	
			}
		%>
		
		
      }
    }
    </script>
<div id="map_canvas" style="width: 510px; height: 350px"></div>
<%
	  	
	}
	catch(Exception e){e.printStackTrace();}	
		
		
	myShepherd.rollbackDBTransaction();
	myShepherd.closeDBTransaction();
	allEncounters=null;
	rEncounters=null;
	haveGPSData=null;
	  
	  } else {%>
<p>No GPS data is available for mapping.</p>
<br> <%}%> <jsp:include page="../footer.jsp" flush="true" />
</div>
</div>
<!-- end page --></div>
<!--end wrapper -->

</body>
</html>




