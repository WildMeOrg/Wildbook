<!DOCTYPE html PUBLIC "-//W3C//DTD XHTML 1.0 Transitional//EN" "http://www.w3.org/TR/xhtml1/DTD/xhtml1-transitional.dtd">
<%@ page contentType="text/html; charset=iso-8859-1" language="java" import="org.ecocean.servlet.ServletUtilities,org.joda.time.DateTime,org.joda.time.format.DateTimeFormatter,org.joda.time.format.ISODateTimeFormat,java.util.StringTokenizer,java.lang.StringBuffer,java.util.GregorianCalendar,org.ecocean.*, javax.jdo.*, java.lang.StringBuffer, java.util.Vector, java.util.Iterator" %>
<%

String context="context0";
context=ServletUtilities.getContext(request);


Shepherd myShepherd=new Shepherd(context);

//handle some cache-related security
response.setHeader("Cache-Control","no-cache"); //Forces caches to obtain a new copy of the page from the origin server
response.setHeader("Cache-Control","no-store"); //Directs caches not to store the page under any circumstance
response.setDateHeader("Expires", 0); //Causes the proxy cache to see the page as "stale"
response.setHeader("Pragma","no-cache"); //HTTP 1.0 backward compatibility 
%>

<%!
public void assignLength(double[] lengths, double val) {
	boolean haveAdded=false;
	for(int t=0;t<lengths.length;t++) {
		if((lengths[t]==0)&&(!haveAdded)) {lengths[t]=val;haveAdded=true;}
	}
}
%>

<%!
public double getAverage(double[] lengths) {
	double totalSize=0;
	double runningTotal=0;
	for(int z=0;z<lengths.length;z++) {
		if(lengths[z]!=0) {
			totalSize++;
			runningTotal=runningTotal+lengths[z];
		}
	}
	return (runningTotal/totalSize);
}
%>

<%!
public int getNumLengths(double[] lengths) {
	int totalSize=0;

	for(int z=0;z<lengths.length;z++) {
		if(lengths[z]!=0) {
			totalSize++;
		}
	}
	return totalSize;
}
%>

<html>
<head>
  <title><%=CommonConfiguration.getHTMLTitle(context) %>
  </title>
  <meta http-equiv="Content-Type" content="text/html; charset=utf-8"/>
  <meta name="Description"
        content="<%=CommonConfiguration.getHTMLDescription(context) %>"/>
  <meta name="Keywords"
        content="<%=CommonConfiguration.getHTMLKeywords(context) %>"/>
  <meta name="Author" content="<%=CommonConfiguration.getHTMLAuthor(context) %>"/>
  <link href="<%=CommonConfiguration.getCSSURLLocation(request,context) %>"
        rel="stylesheet" type="text/css"/>
  <link rel="shortcut icon"
        href="<%=CommonConfiguration.getHTMLShortcutIcon(context) %>"/>

  <style type="text/css">
    <!--
    .style1 {
      color: #FF0000
    }

    -->
  </style>
</head>

<body>
<div id="wrapper">
<div id="page">
<jsp:include page="../header.jsp" flush="true">
<jsp:param name="isResearcher" value="<%=request.isUserInRole(\"researcher\")%>"/>
<jsp:param name="isManager" value="<%=request.isUserInRole(\"manager\")%>"/>
<jsp:param name="isReviewer" value="<%=request.isUserInRole(\"reviewer\")%>"/>
<jsp:param name="isAdmin" value="<%=request.isUserInRole(\"admin\")%>"/>
</jsp:include>
<div id="main">
<p><h1 class="intro"><%=CommonConfiguration.getHTMLTitle(context) %> Statistics</h1></p>

<%
myShepherd.beginDBTransaction();

try{

GregorianCalendar cal=new GregorianCalendar();
int nowYear=cal.get(1);
//int nowYear=2007;
int yearDiff=nowYear-2003+1;

%>

<p><strong>General statistics</strong></p>
<p>Number of marked individual in this wildbook: <%=myShepherd.getNumMarkedIndividuals()%></p>
<p>Number of approved and unapproved encounters in this wildbook: <%=myShepherd.getNumEncounters()%></p>
<p>Number of unidentifiable encounters in this wildbook: <%=myShepherd.getNumUnidentifiableEncounters()%></p>
<%
int numPhotos=0;
int numNingalooPhotos=0;
int numLeftPatterns=0;
int numRightPatterns=0;
int numContributors=0;
int numContributors2008to2010=0;
int numNingalooContributors=0;
StringBuffer contributors=new StringBuffer();

int numNingalooVisualMatches=0;
int numNingalooPatternMatches=0;

int[] encNumArray=new int[yearDiff];
int[] encNumUtilizedArray=new int[yearDiff];
int[] encNumUtilizedSpotArray=new int[yearDiff];
int[] sharkNumArray=new int[yearDiff];

for(int y=0;y<yearDiff;y++){
	encNumArray[y]=0;
	encNumUtilizedArray[y]=0;
	encNumUtilizedSpotArray[y]=0;
	sharkNumArray[y]=0;
}


//ningaloo stats
double[] avgLength=new double[(nowYear-1995+1)];
int[] numLengthEncounters=new int[avgLength.length];
for(int sz=0;sz<numLengthEncounters.length;sz++) {
	numLengthEncounters[sz]=0;
}
//end ningaloo stats

Iterator it2=myShepherd.getAllMarkedIndividuals();
while(it2.hasNext()) {
	MarkedIndividual tempShark=(MarkedIndividual)it2.next();
	
	String year = tempShark.getDateTimeCreated();
	
	for(int y=0;y<yearDiff;y++){
		
		int checkYear = 2003+y;
		String sCheckYear = Integer.toString(checkYear);
		if(year.startsWith(sCheckYear)){
			sharkNumArray[y]++;
		}
	
	}
	
	
}




Iterator it=myShepherd.getAllEncountersNoQuery();
while(it.hasNext()) {
	Encounter tempEnc=(Encounter)it.next();
	

	
	

	numPhotos=numPhotos+tempEnc.getAdditionalImageNames().size();
	//if(tempEnc.getLocationCode().startsWith("1a1")) {
	//	numNingalooPhotos+=tempEnc.getAdditionalImageNames().size();
	//}
	
	
	//calculate the number of submitter contributors
	if((tempEnc.getSubmitterEmail()!=null)&&(!tempEnc.getSubmitterEmail().equals(""))) {
		//check for comma separated list
		if(tempEnc.getSubmitterEmail().indexOf(",")!=-1) {
			//break up the string
			StringTokenizer stzr=new StringTokenizer(tempEnc.getSubmitterEmail(),",");
			while(stzr.hasMoreTokens()) {
				String token=stzr.nextToken();
				if (contributors.indexOf(token)==-1) {
					contributors.append(token);
					numContributors++;
					//if(tempEnc.getLocationCode().startsWith("1a1")) {numNingalooContributors++;}
					if(tempEnc.getYear()>=2008){numContributors2008to2010++;}
					
				}
			}
		}
		else if (contributors.indexOf(tempEnc.getSubmitterEmail())==-1) {
			contributors.append(tempEnc.getSubmitterEmail());
			numContributors++;
			//if(tempEnc.getLocationCode().startsWith("1a")) {numNingalooContributors++;}
			if(tempEnc.getYear()>=2008){numContributors2008to2010++;}
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
					contributors.append(token);
					numContributors++;
					//if(tempEnc.getLocationCode().startsWith("1a")) {numNingalooContributors++;}
					if(tempEnc.getYear()>=2008){numContributors2008to2010++;}
				}
			}
		}
		else if (contributors.indexOf(tempEnc.getPhotographerEmail())==-1) {
			contributors.append(tempEnc.getPhotographerEmail());
			numContributors++;
			//if(tempEnc.getLocationCode().startsWith("1a")) {numNingalooContributors++;}
			if(tempEnc.getYear()>=2008){numContributors2008to2010++;}
		}
	}
	
	
	if((tempEnc.getSpots()!=null)&&(tempEnc.getSpots().size()>0)) {
		numLeftPatterns++;
	}
	if((tempEnc.getRightSpots()!=null)&&(tempEnc.getRightSpots().size()>0)) {
		numRightPatterns++;
	}
	
	//count encounters per years
	for(int r=0;r<yearDiff;r++){
		String yearString=(new Integer(2003+r)).toString();
		if(tempEnc.getEncounterNumber().substring(0,8).indexOf(yearString)!=-1){
			encNumArray[r]++;
			//calculate the utilization rates
			if(tempEnc.getIndividualID()!=null){
				encNumUtilizedArray[r]++;
			}	
		}
		else if(tempEnc.getDWCDateAdded()!=null){
			//GregorianCalendar greg=new GregorianCalendar();
			DateTimeFormatter parser    = ISODateTimeFormat.dateTimeParser();
        	DateTimeFormatter formatter = ISODateTimeFormat.dateTimeNoMillis();

        	DateTime dateTimeHere     = parser.parseDateTime(tempEnc.getDWCDateAdded());
			if (dateTimeHere.getYear()==(2003+r)){
				encNumArray[r]++;
				//calculate the utilization rates
				if(tempEnc.getIndividualID()!=null){
					encNumUtilizedArray[r]++;
				}	
				
				
			}
			//greg.setTimeInMillis();
			
		}
	}
	

	
	//examine ningaloo length data
	/*
	if((tempEnc.getLocationCode().startsWith("1a"))&&(tempEnc.getYear()>=1995)&&(tempEnc.getYear()<=nowYear)&&((tempEnc.getSizeAsDouble()!=null)&&(tempEnc.getSize()>0))) {
		int thisYearDiff=tempEnc.getYear()-1995;
		avgLength[thisYearDiff]=avgLength[thisYearDiff]+tempEnc.getSize();
		numLengthEncounters[thisYearDiff]++;
	}
	*/
	
	
}
double[] identifiedAnnualLengths=new double[(nowYear-1995+1)];
int[] numIdentifiedAnnualLengths=new int[(nowYear-1995+1)];
double[] philopatricAnnualLengths=new double[(nowYear-1995+1)];
int[] numPhilopatricAnnualLengths=new int[(nowYear-1995+1)];
double[] transientAnnualLengths=new double[(nowYear-1995+1)];
int[] numTransientAnnualLengths=new int[(nowYear-1995+1)];
int[] numSharksUnderSixMeters=new int[(nowYear-1995+1)];
int[] numSharksOverSevenMeters=new int[(nowYear-1995+1)];
for(int sz5=0;sz5<=(nowYear-1995);sz5++) {
	numIdentifiedAnnualLengths[sz5]=0;
	identifiedAnnualLengths[sz5]=0;
	numSharksUnderSixMeters[sz5]=0;
	numSharksOverSevenMeters[sz5]=0;
	philopatricAnnualLengths[sz5]=0;
	numPhilopatricAnnualLengths[sz5]=0;
	transientAnnualLengths[sz5]=0;
	numTransientAnnualLengths[sz5]=0;
}
StringBuffer sb_allAvgLengths=new StringBuffer();
StringBuffer sb_philoAvgLengths=new StringBuffer();
StringBuffer sb_allLengthsAllYears=new StringBuffer();

int numSharksSightedMultipleYears=0;
int numSharksSightedOnlyOneYear=0;

it=myShepherd.getAllMarkedIndividuals();
while(it.hasNext()) {
	MarkedIndividual tempShark=(MarkedIndividual)it.next();
	boolean hasLength=false;
	if(tempShark.wasSightedInLocationCode("1a1")) {
	
	if(tempShark.getMaxNumYearsBetweenSightings()>0){numSharksSightedMultipleYears++;}
	else{numSharksSightedOnlyOneYear++;}


	
	//quick all sharks all lengths analysis
	Vector encs=tempShark.getEncounters();
	int vecSize=encs.size();
	for(int fd=0;fd<vecSize;fd++){
		Encounter enc=(Encounter)encs.get(fd);
		
		if(enc.getLocationCode().startsWith("1a1")) {
			if(enc.getMatchedBy().equals("Visual inspection")){numNingalooVisualMatches++;}
			else if(enc.getMatchedBy().equals("Pattern match")){numNingalooPatternMatches++;}
		}	
		
		
		if((enc.getLocationCode().equals("1a1"))&&((enc.getSizeAsDouble()!=null)&&(enc.getSize()>0))&&(enc.getYear()>=1995)&&(enc.getYear()<=nowYear)){
			if(tempShark.getMaxNumYearsBetweenSightings()>0) {
				sb_allLengthsAllYears.append(tempShark.getName()+"\t"+enc.getSize()+"\t"+enc.getYear()+"\t"+"p"+"<br>");
			}
			else {
				sb_allLengthsAllYears.append(tempShark.getName()+"\t"+enc.getSize()+"\t"+enc.getYear()+"\t"+"n"+"<br>");
			}
		}
	}
	
	//end all sharks all lengths analysis
	
	
		double averagePeriodLength=0;
		int numPeriodLengths=0;
		for(int sz4=1995;sz4<=nowYear;sz4++) {
			if(tempShark.averageLengthInYear(sz4)>0) {
				hasLength=true;
				identifiedAnnualLengths[sz4-1995]+=tempShark.averageLengthInYear(sz4);
				numIdentifiedAnnualLengths[sz4-1995]++;
				averagePeriodLength+=tempShark.averageLengthInYear(sz4);
				numPeriodLengths++;	
				if(tempShark.getMaxNumYearsBetweenSightings()>0) {
					philopatricAnnualLengths[sz4-1995]+=(averagePeriodLength/numPeriodLengths);
					numPhilopatricAnnualLengths[sz4-1995]++;
					//sb_allAvgLengths.append(tempShark.getName()+"&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;"+((int)(averagePeriodLength/numPeriodLengths))+"<br>");
					//sb_philoAvgLengths.append(tempShark.getName()+"&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;"+((int)(averagePeriodLength/numPeriodLengths))+"<br>");
				}else{
					transientAnnualLengths[sz4-1995]+=(averagePeriodLength/numPeriodLengths);
					numTransientAnnualLengths[sz4-1995]++;
					//sb_allAvgLengths.append(tempShark.getName()+"&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;"+((int)(averagePeriodLength/numPeriodLengths))+"<br>");
				}
				if((tempShark.averageLengthInYear(sz4))<=6.0){numSharksUnderSixMeters[sz4-1995]++;}
				else if((tempShark.averageLengthInYear(sz4))>6.0){numSharksOverSevenMeters[sz4-1995]++;}			
			} //end if
		}//end for
		
		//look at he philopatric subset of Exmouth
		if((hasLength)&&(tempShark.getMaxNumYearsBetweenSightings()>0)) {
					//sb_allAvgLengths.append(tempShark.getName()+"&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;"+(averagePeriodLength/numPeriodLengths)+"<br>");
					sb_philoAvgLengths.append(tempShark.getName()+"&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;"+(averagePeriodLength/numPeriodLengths)+"<br>");
		}else if(hasLength){
					sb_allAvgLengths.append(tempShark.getName()+"&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;"+(averagePeriodLength/numPeriodLengths)+"<br>");
		}
				

		
		
		
	}//end if
}
%>
<br>
<%
for(int y=0;y<yearDiff;y++){%>
	Encounters added in year <%=(2003+y)%>*: <%=encNumArray[y]%> (<%=(100*encNumUtilizedArray[y]/encNumArray[y])%>% identified)<br>

<%}%>
<p><em>*These are encounters added in that year but NOT necessarily reported for that year (i.e. an animal sighted in 2006 but reported in 2007 is added to the 2007 value). </em></p>
<br>
<%
for(int y=0;y<yearDiff;y++){%>
	Sharks newly identified in year <%=(2003+y)%>*: <%=sharkNumArray[y]%><br>

<%}%>
<p><em>*These are sharks newly identified in that year but NOT necessarily reported or resighted for that year (i.e., an animal sighted in 2006 but identified by members in 2008 is added to the 2008 value). </em></p>
<br>
<p>Number of photos in the library: <%=numPhotos%></p>
<p>Number of left-side patterns in the library: <%=numLeftPatterns%></p>
<p>Number of right-side patterns in the library: <%=numRightPatterns%></p>
<p>Number of contributors (based on unique email accounts): <%=numContributors%></p>

<%

}
catch(Exception e) {
	out.println("Exception caught!");
	e.printStackTrace();
}
myShepherd.rollbackDBTransaction();
myShepherd.closeDBTransaction();

%>
<jsp:include page="../footer.jsp" flush="true" />
</div>
</div><!-- end page -->
</div><!--end wrapper -->
</body>
</html>


