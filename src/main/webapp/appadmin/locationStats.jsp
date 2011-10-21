<!DOCTYPE html PUBLIC "-//W3C//DTD XHTML 1.0 Transitional//EN" "http://www.w3.org/TR/xhtml1/DTD/xhtml1-transitional.dtd">
<%@ page contentType="text/html; charset=iso-8859-1" language="java" import="java.util.Collection,java.util.ArrayList,org.joda.time.DateTime,org.joda.time.format.DateTimeFormatter,org.joda.time.format.ISODateTimeFormat,java.util.StringTokenizer,java.lang.StringBuffer,java.util.GregorianCalendar,org.ecocean.*, javax.jdo.*, java.lang.StringBuffer, java.util.Vector, java.util.Iterator" %>
<%
Shepherd myShepherd=new Shepherd();

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
<title>ECOCEAN Library - Location General Statistics</title>
<meta http-equiv="Content-Type" content="text/html; charset=iso-8859-1">
<link href="http://<%=CommonConfiguration.getURLLocation(request)%>/css/ecocean.css" rel="stylesheet" type="text/css" />
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

<%
String locCode="1a1";
if(request.getParameter("locCode")!=null){locCode=request.getParameter("locCode");}
%>

<p><h1 class="intro">Location-specific Statistics for <%=locCode%></h1></p>

<%
myShepherd.beginDBTransaction();

int startYear=1995;
try{
	if(request.getParameter("startYear")!=null){startYear=(new Integer(request.getParameter("startYear"))).intValue();}
}
catch(Exception e){e.printStackTrace();}


try{

GregorianCalendar cal=new GregorianCalendar();
int nowYear=cal.get(1);
int yearDiff=nowYear-startYear+1;

ArrayList<MarkedIndividual> localIndies=myShepherd.getAllMarkedIndividualsFromLocationID(locCode); 

%>

<p>Number of marked individuals: <%=localIndies.size()%></p>

<%
    int numEnc=0;
    Collection c;

    String filter = "!this.unidentifiable && this.locationID == '"+locCode+"'";
        Extent encClass2 = myShepherd.getPM().getExtent(Encounter.class, true);
    Query acceptedEncounters2 = myShepherd.getPM().newQuery(encClass2, filter);
    try {
      c = (Collection) (acceptedEncounters2.execute());
      ArrayList list = new ArrayList(c);
      numEnc=list.size();
    } 
    catch (Exception npe) {}

%>

<p>Number of approved and unapproved encounters: <%=numEnc%></p>

<%
    int numUnident=0;
    Collection c2;
    String filter2 = "this.unidentifiable && this.locationID == '"+locCode+"'";
    Extent encClass3 = myShepherd.getPM().getExtent(Encounter.class, true);
    Query acceptedEncounters3 = myShepherd.getPM().newQuery(encClass3, filter2);
    try {
      c2 = (Collection) (acceptedEncounters3.execute());
      ArrayList list = new ArrayList(c2);
      numUnident=list.size();
    } 
    catch (Exception npe) {}

%>

<p>Number of unidentifiable encounter: <%=numUnident%></p>

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
double[] avgLength=new double[(nowYear-startYear+1)];
int[] numLengthEncounters=new int[avgLength.length];
for(int sz=0;sz<numLengthEncounters.length;sz++) {
	numLengthEncounters[sz]=0;
}
//end ningaloo stats


/**
Iterator it2=myShepherd.getAllMarkedIndividuals();
while(it2.hasNext()) {
	MarkedIndividual tempShark=(MarkedIndividual)it2.next();	
	String year = tempShark.getDateTimeCreated();	
	for(int y=0;y<yearDiff;y++){		
		int checkYear = startYear+y;
		String sCheckYear = Integer.toString(checkYear);
		if(year.startsWith(sCheckYear)){
			sharkNumArray[y]++;
		}	
	}	
}
*/




    Collection c5;
    String filter5 = "this.locationID == '"+locCode+"'";
    Extent encClass5 = myShepherd.getPM().getExtent(Encounter.class, true);
    Query acceptedEncounters5 = myShepherd.getPM().newQuery(encClass5, filter5);
    ArrayList list5=new ArrayList();
    try {
      c5 = (Collection) (acceptedEncounters5.execute());
      list5 = new ArrayList(c5);
    } 
    catch (Exception npe) {}
   Iterator it=list5.iterator();



while(it.hasNext()) {
	Encounter tempEnc=(Encounter)it.next();
	

	
	

	numPhotos=numPhotos+tempEnc.getAdditionalImageNames().size();
	if(tempEnc.getLocationCode().startsWith(locCode)) {
		numNingalooPhotos+=tempEnc.getAdditionalImageNames().size();
	}
	
	
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
					if(tempEnc.getLocationCode().startsWith(locCode)) {numNingalooContributors++;}
					if(tempEnc.getYear()>=2008){numContributors2008to2010++;}
					
				}
			}
		}
		else if (contributors.indexOf(tempEnc.getSubmitterEmail())==-1) {
			contributors.append(tempEnc.getSubmitterEmail());
			numContributors++;
			if(tempEnc.getLocationCode().startsWith(locCode)) {numNingalooContributors++;}
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
					if(tempEnc.getLocationCode().startsWith(locCode)) {numNingalooContributors++;}
					if(tempEnc.getYear()>=2008){numContributors2008to2010++;}
				}
			}
		}
		else if (contributors.indexOf(tempEnc.getPhotographerEmail())==-1) {
			contributors.append(tempEnc.getPhotographerEmail());
			numContributors++;
			if(tempEnc.getLocationCode().startsWith(locCode)) {numNingalooContributors++;}
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
		String yearString=(new Integer(startYear+r)).toString();
		if(tempEnc.getYear()==(startYear+r)){
			encNumArray[r]++;
			//calculate the utilization rates
			if(!tempEnc.isAssignedToMarkedIndividual().equals("Unassigned")){
				encNumUtilizedArray[r]++;
			}	
		}
	}
	

	
	//examine ningaloo length data
	if((tempEnc.getLocationCode().startsWith(locCode))&&(tempEnc.getYear()>=startYear)&&(tempEnc.getYear()<=nowYear)&&((tempEnc.getSizeAsDouble()!=null)&&(tempEnc.getSize()>0))) {
		int thisYearDiff=tempEnc.getYear()-startYear;
		avgLength[thisYearDiff]=avgLength[thisYearDiff]+tempEnc.getSize();
		numLengthEncounters[thisYearDiff]++;
	}
	
	
	
}
double[] identifiedAnnualLengths=new double[(nowYear-startYear+1)];
int[] numIdentifiedAnnualLengths=new int[(nowYear-startYear+1)];
double[] philopatricAnnualLengths=new double[(nowYear-startYear+1)];
int[] numPhilopatricAnnualLengths=new int[(nowYear-startYear+1)];
double[] transientAnnualLengths=new double[(nowYear-startYear+1)];
int[] numTransientAnnualLengths=new int[(nowYear-startYear+1)];
int[] numSharksUnderSixMeters=new int[(nowYear-startYear+1)];
int[] numSharksOverSevenMeters=new int[(nowYear-startYear+1)];
for(int sz5=0;sz5<=(nowYear-startYear);sz5++) {
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

//here
//it=myShepherd.getAllMarkedIndividuals();

ArrayList<MarkedIndividual> localIndies2=myShepherd.getAllMarkedIndividualsFromLocationID(locCode);
it=localIndies2.iterator();


while(it.hasNext()) {
	MarkedIndividual tempShark=(MarkedIndividual)it.next();
	boolean hasLength=false;
	if(tempShark.wasSightedInLocationCode(locCode)) {
	
	if(tempShark.getMaxNumYearsBetweenSightings()>0){numSharksSightedMultipleYears++;}
	else{numSharksSightedOnlyOneYear++;}


	
	//quick all sharks all lengths analysis
	Vector encs=tempShark.getEncounters();
	int vecSize=encs.size();
	for(int fd=0;fd<vecSize;fd++){
		Encounter enc=(Encounter)encs.get(fd);
		
		if(enc.getLocationCode().startsWith(locCode)) {
			if(enc.getMatchedBy().equals("Visual inspection")){numNingalooVisualMatches++;}
			else if(enc.getMatchedBy().equals("Pattern match")){numNingalooPatternMatches++;}
		}	
		
		
		if((enc.getLocationCode().equals(locCode))&&((enc.getSizeAsDouble()!=null)&&(enc.getSize()>0))&&(enc.getYear()>=startYear)&&(enc.getYear()<=nowYear)){
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
		for(int sz4=startYear;sz4<=nowYear;sz4++) {
			if(tempShark.averageLengthInYear(sz4)>0) {
				hasLength=true;
				identifiedAnnualLengths[sz4-startYear]+=tempShark.averageLengthInYear(sz4);
				numIdentifiedAnnualLengths[sz4-startYear]++;
				averagePeriodLength+=tempShark.averageLengthInYear(sz4);
				numPeriodLengths++;	
				if(tempShark.getMaxNumYearsBetweenSightings()>0) {
					philopatricAnnualLengths[sz4-startYear]+=(averagePeriodLength/numPeriodLengths);
					numPhilopatricAnnualLengths[sz4-startYear]++;
					//sb_allAvgLengths.append(tempShark.getName()+"&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;"+((int)(averagePeriodLength/numPeriodLengths))+"<br>");
					//sb_philoAvgLengths.append(tempShark.getName()+"&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;"+((int)(averagePeriodLength/numPeriodLengths))+"<br>");
				}else{
					transientAnnualLengths[sz4-startYear]+=(averagePeriodLength/numPeriodLengths);
					numTransientAnnualLengths[sz4-startYear]++;
					//sb_allAvgLengths.append(tempShark.getName()+"&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;"+((int)(averagePeriodLength/numPeriodLengths))+"<br>");
				}
				if((tempShark.averageLengthInYear(sz4))<=6.0){numSharksUnderSixMeters[sz4-startYear]++;}
				else if((tempShark.averageLengthInYear(sz4))>6.0){numSharksOverSevenMeters[sz4-startYear]++;}			
			} //end if
		}//end for
		
		//look at he philopatric subset of 
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
	Encounters in year <%=(startYear+y)%>*: <%=encNumArray[y]%> 
	<%
	if(encNumArray[y]>0){
	%>
		(<%=(100*encNumUtilizedArray[y]/encNumArray[y])%>% identified)
	<%
	}
	%>
	<br />
<%}%>
<br />


<p><strong>Location statistics</strong>
<p>Number of location photos: <%=numNingalooPhotos%></p>
<p>Number of contributors to the location study (based on unique email accounts): <%=numNingalooContributors%></p>
<p>Number of location Sharks sighted in only one year: <%=numSharksSightedOnlyOneYear%></p>
<p>Number of location Sharks sighted in multiple years:  <%=numSharksSightedMultipleYears%></p>
<p>Average annual reported encounter lengths:</p>
<ul>
<%
for(int sz2=0;sz2<avgLength.length;sz2++){
%>
	<li><%=(startYear+sz2)%> average length(m): <%=avgLength[sz2]/numLengthEncounters[sz2]%> (<%=numLengthEncounters[sz2]%>)</li>
<%}%>
</ul>
<p>Average annual reported lengths based on identified sharks:</p>
<ul>
<%
for(int sz3=startYear;sz3<=nowYear;sz3++) {
%>
	<li><%=sz3%> average identified shark length: <%=(identifiedAnnualLengths[(sz3-startYear)]/numIdentifiedAnnualLengths[(sz3-startYear)])%> (<%=numIdentifiedAnnualLengths[(sz3-startYear)]%>)
		<%
			double mean=(identifiedAnnualLengths[(sz3-startYear)]/numIdentifiedAnnualLengths[(sz3-startYear)]);
			
			//now we need to calculate the std dev
			
			//Iterator stdDevIt=myShepherd.getAllMarkedIndividuals();
			ArrayList<MarkedIndividual> localIndies3=myShepherd.getAllMarkedIndividualsFromLocationID(locCode);
			Iterator stdDevIt=localIndies3.iterator();
			double stdDev=0;
			while(stdDevIt.hasNext()) {
				MarkedIndividual tempShark=(MarkedIndividual)stdDevIt.next();
				boolean hasLength=false;
				if((tempShark.wasSightedInYear(sz3, locCode))&&(tempShark.averageMeasuredLengthInYear(sz3, true)>0)) {
					stdDev+=(tempShark.averageMeasuredLengthInYear(sz3, true)-mean)*(tempShark.averageMeasuredLengthInYear(sz3, true)-mean);
				
				
				}
			
			}
			stdDev=Math.sqrt(stdDev/numIdentifiedAnnualLengths[(sz3-startYear)]);
			double stdError=stdDev/Math.sqrt(numIdentifiedAnnualLengths[(sz3-startYear)]);
	
		%>
		
		</li>
<%}%>
</ul>
<p>Distributions:<br><%=sb_allAvgLengths.toString()%></p>

<p>Average annual reported lengths based on  philopatric sharks ():</p>
<ul>
<%
for(int sz3=startYear;sz3<=nowYear;sz3++) {
%>
	<li><%=sz3%> average identified shark length: <%=(philopatricAnnualLengths[(sz3-startYear)]/numPhilopatricAnnualLengths[(sz3-startYear)])%> (<%=numPhilopatricAnnualLengths[(sz3-startYear)]%>)
		<%
			double mean=(identifiedAnnualLengths[(sz3-startYear)]/numIdentifiedAnnualLengths[(sz3-startYear)]);
			
			//now we need to calculate the std dev
			ArrayList<MarkedIndividual> localIndies4=myShepherd.getAllMarkedIndividualsFromLocationID(locCode);
			
			Iterator stdDevIt=localIndies4.iterator();
			double stdDev=0;
			while(stdDevIt.hasNext()) {
				MarkedIndividual tempShark=(MarkedIndividual)stdDevIt.next();
				boolean hasLength=false;
				if((tempShark.wasSightedInYear(sz3, locCode))&&(tempShark.averageMeasuredLengthInYear(sz3, true)>0)&&(tempShark.getMaxNumYearsBetweenSightings()>0)) {
					stdDev+=(tempShark.averageMeasuredLengthInYear(sz3, true)-mean)*(tempShark.averageMeasuredLengthInYear(sz3, true)-mean);
				
				
				}
			
			}
			stdDev=Math.sqrt(stdDev/numIdentifiedAnnualLengths[(sz3-startYear)]);
			double stdError=stdDev/Math.sqrt(numIdentifiedAnnualLengths[(sz3-startYear)]);
	
		%>
		</li>
<%}%>
</ul>
<p>Distributions:<br><%=sb_philoAvgLengths.toString()%></p>

<p>Average annual reported lengths based on  single-sighting sharks ():</p>
<ul>
<%
for(int sz3=startYear;sz3<=nowYear;sz3++) {
%>
	<li><%=sz3%> average identified shark length: <%=(transientAnnualLengths[(sz3-startYear)]/numTransientAnnualLengths[(sz3-startYear)])%> (<%=numTransientAnnualLengths[(sz3-startYear)]%>)
			<%
				
				double mean=(identifiedAnnualLengths[(sz3-startYear)]/numIdentifiedAnnualLengths[(sz3-startYear)]);
			
			//now we need to calculate the std dev
			ArrayList<MarkedIndividual> localIndies4=myShepherd.getAllMarkedIndividualsFromLocationID(locCode);
						
			
			Iterator stdDevIt=localIndies4.iterator();
			
			double stdDev=0;
			while(stdDevIt.hasNext()) {
				MarkedIndividual tempShark=(MarkedIndividual)stdDevIt.next();
				boolean hasLength=false;
				if((tempShark.wasSightedInYear(sz3, locCode))&&(tempShark.averageMeasuredLengthInYear(sz3, true)>0)&&(tempShark.getMaxNumYearsBetweenSightings()==0)) {
					stdDev+=(tempShark.averageMeasuredLengthInYear(sz3, true)-mean)*(tempShark.averageMeasuredLengthInYear(sz3, true)-mean);
				
				
				}
			
			}
			stdDev=Math.sqrt(stdDev/numIdentifiedAnnualLengths[(sz3-startYear)]);
			double stdError=stdDev/Math.sqrt(numIdentifiedAnnualLengths[(sz3-startYear)]);
	
		%>
		</li>

<%}%>
</ul>

<p>Num sharks six meters or under ():</p>
<ul>
<%
for(int sz6=startYear;sz6<=nowYear;sz6++) {
%>
	<li><%=sz6%>: <%=numSharksUnderSixMeters[(sz6-startYear)]%></li>
<%}%>
</ul>
<p>Num sharks over six meters:</p>
<ul>
<%
for(int sz6=startYear;sz6<=nowYear;sz6++) {
%>
	<li><%=sz6%>: <%=numSharksOverSevenMeters[(sz6-startYear)]%></li>
<%}
%>
<p><strong>All sharks, all years, <%=startYear%>-<%=nowYear%></strong></p>
<%=sb_allLengthsAllYears.toString()%>

<%
/*
%>
</ul>
<p>&nbsp;</p>
<hr>
<p>The following statistics are used for capture-related length analysis and may NOT contain valuable data.</p>
<table>
<%
int total=0;
Vector caps=new Vector();

		double[] firstLengths=new double[1000];
		for(int p=0;p<1000;p++) {
			firstLengths[p]=0;
		} //end for
		
		double[] secondLengths=new double[1000];
		for(int p=0;p<1000;p++) {
			secondLengths[p]=0;
		} //end for
		
		double[] thirdLengths=new double[1000];
		for(int p=0;p<1000;p++) {
			thirdLengths[p]=0;
		} //end for
		
		double[] fourthLengths=new double[1000];
		for(int p=0;p<1000;p++) {
			fourthLengths[p]=0;
		} //end for
		
		double[] fifthLengths=new double[1000];
		for(int p=0;p<1000;p++) {
			fifthLengths[p]=0;
		} //end for
		
		double[] sixthLengths=new double[1000];
		for(int p=0;p<1000;p++) {
			sixthLengths[p]=0;
		} //end for
		
		double[] seventhLengths=new double[1000];
		for(int p=0;p<1000;p++) {
			seventhLengths[p]=0;
		} //end for
		
		double[] eighthLengths=new double[1000];
		for(int p=0;p<1000;p++) {
			eighthLengths[p]=0;
		} //end for
		
		double[] ninthLengths=new double[1000];
		for(int p=0;p<1000;p++) {
			ninthLengths[p]=0;
		} //end for

try{

//TBD
	//Extent sharkClass=myShepherd.getPM().getExtent(MarkedIndividual.class, true);
	//Query query=myShepherd.getPM().newQuery(sharkClass);
	
	ArrayList<MarkedIndividual> localIndies7=myShepherd.getAllMarkedIndividualsFromLocationID(locCode); 

	
	Iterator allSharks=localIndies7.iterator();

	while (allSharks.hasNext()) {
	
		//initialize our capture array
		int[] captures=new int[13];
		for(int p=0;p<13;p++) {
			captures[p]=0;
		} //end for
		
	
		MarkedIndividual sharky=(MarkedIndividual)allSharks.next();
		if(sharky.wasSightedInLocationCode(locCode)) {
			boolean hasLength=false;

				

				
				//get the encounters
				Encounter[] encs=sharky.getDateSortedEncounters(true);
				int encsLength=encs.length-1;
				for(int j=encsLength;j>=0;j--) {
					Encounter enc=encs[j];
					if((enc.getSizeAsDouble()!=null)&&(enc.getSize()>0)) {hasLength=true;}
					int year=enc.getYear();
					boolean foundThisYear=false;
					boolean onlyOnce=false;
					for(int q=0;q<13;q++) {
						
						if((captures[q]==0)&&(!foundThisYear)){
							captures[q]=year;foundThisYear=true;
							onlyOnce=true;
						}
						else if(captures[q]==year) {foundThisYear=true;}
						
						//add up some lengths
						if((enc.getSize()>0)&&(foundThisYear)&&(!onlyOnce)){
							onlyOnce=true;
							switch (q) {
								case 0:  assignLength(firstLengths, enc.getSize()); break;
            					case 1:  assignLength(secondLengths, enc.getSize()); break;
            					case 2:  assignLength(thirdLengths, enc.getSize()); break;
            					case 3:  assignLength(fourthLengths, enc.getSize()); break;
            					case 4:  assignLength(fifthLengths, enc.getSize()); break;
            					case 5:  assignLength(sixthLengths, enc.getSize()); break;
            					case 6:  assignLength(seventhLengths, enc.getSize()); break;
            					case 7:  assignLength(eighthLengths, enc.getSize()); break;
            					case 8:  assignLength(ninthLengths, enc.getSize()); break;
								default: break;
        					}
						}
						
						
					} //end for
				} //end for
				if(hasLength) {
					total++;
				%>
				<tr><td><%=sharky.getName()%>:
					<%for(int t=0;t<13;t++) {
						if(captures[t]>0){%>
							<%=captures[t]%>&nbsp;
						<%}
					}%>
				</td></tr>
			<%	}

		caps.add(captures);
		} //end if
	} //end while
	
}
catch(Exception e) {
	e.printStackTrace();
}
%>
</table>
<p>&nbsp; </p>
<p>Total sharks with at least one length measurement: <%=total%></p>
<p>&nbsp; </p>
<%
myShepherd.rollbackDBTransaction();
myShepherd.closeDBTransaction();
int numEntries=0;
int totalYears=0;
double final2=0;
%>
<p>Average years between first and second captures:  
<%
numEntries=0;
totalYears=0;

for(int r=0;r<caps.size();r++) {
	int[] tempArray=(int[])caps.get(r);
	if((tempArray[1]>0)&&(tempArray[0]>0)){
		numEntries++;
		totalYears=totalYears+(tempArray[1]-tempArray[0]);
	}
}
final2=(totalYears/numEntries);
%>
<%=final2%> (<%=totalYears%>\<%=numEntries%>)</p>
<p>Average years between second and third captures:  
<%
numEntries=0;
totalYears=0;
for(int r=0;r<caps.size();r++) {
	int[] tempArray=(int[])caps.get(r);
	if((tempArray[2]>0)&&(tempArray[1]>0)){
		numEntries++;
		totalYears=totalYears+(tempArray[2]-tempArray[1]);
	}
}
final2=(totalYears/numEntries);
%>
<%=final2%> (<%=totalYears%>\<%=numEntries%>)</p>
<p>Average years between third and fourth captures:  
<%
numEntries=0;
totalYears=0;
for(int r=0;r<caps.size();r++) {
	int[] tempArray=(int[])caps.get(r);
	if((tempArray[3]>0)&&(tempArray[2]>0)){
		numEntries++;
		totalYears=totalYears+(tempArray[3]-tempArray[2]);
	}
}
final2=(totalYears/numEntries);
%>
<%=final2%> (<%=totalYears%>\<%=numEntries%>)</p>
<p>Average years between fourth and fifth captures:  
<%
numEntries=0;
totalYears=0;
for(int r=0;r<caps.size();r++) {
	int[] tempArray=(int[])caps.get(r);
	if((tempArray[4]>0)&&(tempArray[3]>0)){
		numEntries++;
		totalYears=totalYears+(tempArray[4]-tempArray[3]);
	}
}
final2=(totalYears/numEntries);
%>
<%=final2%> (<%=totalYears%>\<%=numEntries%>)</p>
<p>Average years between fifth and sixth captures:  
<%
numEntries=0;
totalYears=0;
for(int r=0;r<caps.size();r++) {
	int[] tempArray=(int[])caps.get(r);
	if((tempArray[5]>0)&&(tempArray[4]>0)){
		numEntries++;
		totalYears=totalYears+(tempArray[5]-tempArray[4]);
	}
}
final2=(totalYears/numEntries);
%>
<%=final2%> (<%=totalYears%>\<%=numEntries%>)</p>
<p>Average years between sixth and seventh captures:  
<%
numEntries=0;
totalYears=0;
for(int r=0;r<caps.size();r++) {
	int[] tempArray=(int[])caps.get(r);
	if((tempArray[6]>0)&&(tempArray[5]>0)){
		numEntries++;
		totalYears=totalYears+(tempArray[6]-tempArray[5]);
	}
}
final2=(totalYears/numEntries);
%>
<%=final2%> (<%=totalYears%>\<%=numEntries%>)</p>
<p>Average years between seventh and eighth captures:  
<%
numEntries=0;
totalYears=0;
for(int r=0;r<caps.size();r++) {
	int[] tempArray=(int[])caps.get(r);
	if((tempArray[7]>0)&&(tempArray[6]>0)){
		numEntries++;
		totalYears=totalYears+(tempArray[7]-tempArray[6]);
	}
}
final2=(totalYears/numEntries);
%>
<%=final2%> (<%=totalYears%>\<%=numEntries%>)</p>
<p>Average years between eighth and ninth captures:  
<%
numEntries=0;
totalYears=0;
for(int r=0;r<caps.size();r++) {
	int[] tempArray=(int[])caps.get(r);
	if((tempArray[8]>0)&&(tempArray[7]>0)){
		numEntries++;
		totalYears=totalYears+(tempArray[8]-tempArray[7]);
	}
}
final2=(totalYears/numEntries);
%>
<%=final2%> (<%=totalYears%>\<%=numEntries%>)</p>

<p>&nbsp;</p>
<p><strong>Capture average lengths</strong> </p>
<p>1st: <%=getAverage(firstLengths)%> (<%=getNumLengths(firstLengths)%>)</p>
<p>2nd: <%=getAverage(secondLengths)%> (<%=getNumLengths(secondLengths)%>)</p>
<p>3rd: <%=getAverage(thirdLengths)%> (<%=getNumLengths(thirdLengths)%>)</p>
<p>4th: <%=getAverage(fourthLengths)%> (<%=getNumLengths(fourthLengths)%>)</p>
<p>5th: <%=getAverage(fifthLengths)%> (<%=getNumLengths(fifthLengths)%>)</p>
<p>6th: <%=getAverage(sixthLengths)%> (<%=getNumLengths(sixthLengths)%>)</p>
<p>7th: <%=getAverage(seventhLengths)%> (<%=getNumLengths(seventhLengths)%>)</p>
<p>8th: <%=getAverage(eighthLengths)%> (<%=getNumLengths(eighthLengths)%>)</p>
<p>9th: <%=getAverage(ninthLengths)%> (<%=getNumLengths(ninthLengths)%>)</p>

<p><strong>



<%
*/

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


