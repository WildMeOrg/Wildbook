<!DOCTYPE html PUBLIC "-//W3C//DTD XHTML 1.0 Transitional//EN" "http://www.w3.org/TR/xhtml1/DTD/xhtml1-transitional.dtd">
<%@ page contentType="text/html; charset=iso-8859-1" language="java" import="org.joda.time.DateTime,org.joda.time.format.DateTimeFormatter,org.joda.time.format.ISODateTimeFormat,java.util.StringTokenizer,java.lang.StringBuffer,java.util.GregorianCalendar,org.ecocean.*, javax.jdo.*, java.lang.StringBuffer, java.util.Vector, java.util.Iterator" %>
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
<title>ECOCEAN Library - Statistics</title>
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
<p><h1 class="intro">ECOCEAN Library Statistics</h1></p>

<%
myShepherd.beginDBTransaction();

try{

GregorianCalendar cal=new GregorianCalendar();
int nowYear=cal.get(1);
//int nowYear=2007;
int yearDiff=nowYear-2003+1;

%>

<p><strong>General statistics</strong></p>
<p>Number of sharks in the library: <%=myShepherd.getNumMarkedIndividuals()%></p>
<p>Number of approved and unapproved encounters in the library: <%=myShepherd.getNumEncounters()%></p>
<p>Number of unidentifiable encounters in the library: <%=myShepherd.getNumUnidentifiableEncounters()%></p>
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
	if(tempEnc.getLocationCode().startsWith("1a1")) {
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
					if(tempEnc.getLocationCode().startsWith("1a1")) {numNingalooContributors++;}
					if(tempEnc.getYear()>=2008){numContributors2008to2010++;}
					
				}
			}
		}
		else if (contributors.indexOf(tempEnc.getSubmitterEmail())==-1) {
			contributors.append(tempEnc.getSubmitterEmail());
			numContributors++;
			if(tempEnc.getLocationCode().startsWith("1a")) {numNingalooContributors++;}
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
					if(tempEnc.getLocationCode().startsWith("1a")) {numNingalooContributors++;}
					if(tempEnc.getYear()>=2008){numContributors2008to2010++;}
				}
			}
		}
		else if (contributors.indexOf(tempEnc.getPhotographerEmail())==-1) {
			contributors.append(tempEnc.getPhotographerEmail());
			numContributors++;
			if(tempEnc.getLocationCode().startsWith("1a")) {numNingalooContributors++;}
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
			if(!tempEnc.isAssignedToMarkedIndividual().equals("Unassigned")){
				encNumUtilizedArray[r]++;
			}	
		}
	}
	

	
	//examine ningaloo length data
	if((tempEnc.getLocationCode().startsWith("1a"))&&(tempEnc.getYear()>=1995)&&(tempEnc.getYear()<=nowYear)&&((tempEnc.getSizeAsDouble()!=null)&&(tempEnc.getSize()>0))) {
		int thisYearDiff=tempEnc.getYear()-1995;
		avgLength[thisYearDiff]=avgLength[thisYearDiff]+tempEnc.getSize();
		numLengthEncounters[thisYearDiff]++;
	}
	
	
	
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
<p><em>*These are encounters added in that year but NOT necessarily reported for that year (i.e. a shark sighted in 2006 but reported in 2007 is added to the 2007 value). </em></p>
<br>
<%
for(int y=0;y<yearDiff;y++){%>
	Sharks newly identified in year <%=(2003+y)%>*: <%=sharkNumArray[y]%><br>

<%}%>
<p><em>*These are sharks newly identified in that year but NOT necessarily reported or resighted for that year (i.e. a shark sighted in 2006 but identified by ECOCEAN Library members in 2008 is added to the 2008 value). </em></p>
<br>
<p>Number of photos in the library: <%=numPhotos%></p>
<p>Number of left-side patterns in the library: <%=numLeftPatterns%></p>
<p>Number of right-side patterns in the library: <%=numRightPatterns%></p>
<p>Number of contributors to the Library (based on unique email accounts): <%=numContributors%></p>
<p>Number of contributors to the Library (based on unique email accounts) since 2008: <%=numContributors2008to2010%></p>
<br>
<p><strong>Ningaloo statistics</strong>
<p>Number of North Ningaloo photos: <%=numNingalooPhotos%></p>
<p>Number of contributors to the North Ningaloo study (based on unique email accounts): <%=numNingalooContributors%></p>
<p>Number of North Ningaloo Pattern matches: <%=numNingalooPatternMatches%></p>
<p>Number of North Ningaloo Visual ID matches:  <%=numNingalooVisualMatches%></p>
<p>Number of North Ningaloo Sharks sighted in only one year: <%=numSharksSightedOnlyOneYear%></p>
<p>Number of North Ningaloo Sharks sighted in multiple years:  <%=numSharksSightedMultipleYears%></p>
<p>Average annual reported encounter lengths (Exmouth+Coral Bay):</p>
<ul>
<%
for(int sz2=0;sz2<avgLength.length;sz2++){
%>
	<li><%=(1995+sz2)%> average length(m): <%=avgLength[sz2]/numLengthEncounters[sz2]%> (<%=numLengthEncounters[sz2]%>)</li>
<%}%>
</ul>
<p>Average annual reported lengths based on identified sharks (Exmouth):</p>
<ul>
<%
for(int sz3=1995;sz3<=nowYear;sz3++) {
%>
	<li><%=sz3%> average identified shark length: <%=(identifiedAnnualLengths[(sz3-1995)]/numIdentifiedAnnualLengths[(sz3-1995)])%> (<%=numIdentifiedAnnualLengths[(sz3-1995)]%>)
		<%
			double mean=(identifiedAnnualLengths[(sz3-1995)]/numIdentifiedAnnualLengths[(sz3-1995)]);
			
			//now we need to calculate the std dev
			Iterator stdDevIt=myShepherd.getAllMarkedIndividuals();
			double stdDev=0;
			while(stdDevIt.hasNext()) {
				MarkedIndividual tempShark=(MarkedIndividual)stdDevIt.next();
				boolean hasLength=false;
				if((tempShark.wasSightedInYear(sz3, "1a1"))&&(tempShark.averageMeasuredLengthInYear(sz3, true)>0)) {
					stdDev+=(tempShark.averageMeasuredLengthInYear(sz3, true)-mean)*(tempShark.averageMeasuredLengthInYear(sz3, true)-mean);
				
				
				}
			
			}
			stdDev=Math.sqrt(stdDev/numIdentifiedAnnualLengths[(sz3-1995)]);
			double stdError=stdDev/Math.sqrt(numIdentifiedAnnualLengths[(sz3-1995)]);
	
		%>
		
		</li>
<%}%>
</ul>
<p>Distributions:<br><%=sb_allAvgLengths.toString()%></p>

<p>Average annual reported lengths based on Exmouth philopatric sharks (Exmouth):</p>
<ul>
<%
for(int sz3=1995;sz3<=nowYear;sz3++) {
%>
	<li><%=sz3%> average identified shark length: <%=(philopatricAnnualLengths[(sz3-1995)]/numPhilopatricAnnualLengths[(sz3-1995)])%> (<%=numPhilopatricAnnualLengths[(sz3-1995)]%>)
		<%
			double mean=(identifiedAnnualLengths[(sz3-1995)]/numIdentifiedAnnualLengths[(sz3-1995)]);
			
			//now we need to calculate the std dev
			Iterator stdDevIt=myShepherd.getAllMarkedIndividuals();
			double stdDev=0;
			while(stdDevIt.hasNext()) {
				MarkedIndividual tempShark=(MarkedIndividual)stdDevIt.next();
				boolean hasLength=false;
				if((tempShark.wasSightedInYear(sz3, "1a1"))&&(tempShark.averageMeasuredLengthInYear(sz3, true)>0)&&(tempShark.getMaxNumYearsBetweenSightings()>0)) {
					stdDev+=(tempShark.averageMeasuredLengthInYear(sz3, true)-mean)*(tempShark.averageMeasuredLengthInYear(sz3, true)-mean);
				
				
				}
			
			}
			stdDev=Math.sqrt(stdDev/numIdentifiedAnnualLengths[(sz3-1995)]);
			double stdError=stdDev/Math.sqrt(numIdentifiedAnnualLengths[(sz3-1995)]);
	
		%>
		</li>
<%}%>
</ul>
<p>Distributions:<br><%=sb_philoAvgLengths.toString()%></p>

<p>Average annual reported lengths based on Exmouth single-sighting sharks (Exmouth):</p>
<ul>
<%
for(int sz3=1995;sz3<=nowYear;sz3++) {
%>
	<li><%=sz3%> average identified shark length: <%=(transientAnnualLengths[(sz3-1995)]/numTransientAnnualLengths[(sz3-1995)])%> (<%=numTransientAnnualLengths[(sz3-1995)]%>)
			<%
				
				double mean=(identifiedAnnualLengths[(sz3-1995)]/numIdentifiedAnnualLengths[(sz3-1995)]);
			
			//now we need to calculate the std dev
			Iterator stdDevIt=myShepherd.getAllMarkedIndividuals();
			double stdDev=0;
			while(stdDevIt.hasNext()) {
				MarkedIndividual tempShark=(MarkedIndividual)stdDevIt.next();
				boolean hasLength=false;
				if((tempShark.wasSightedInYear(sz3, "1a1"))&&(tempShark.averageMeasuredLengthInYear(sz3, true)>0)&&(tempShark.getMaxNumYearsBetweenSightings()==0)) {
					stdDev+=(tempShark.averageMeasuredLengthInYear(sz3, true)-mean)*(tempShark.averageMeasuredLengthInYear(sz3, true)-mean);
				
				
				}
			
			}
			stdDev=Math.sqrt(stdDev/numIdentifiedAnnualLengths[(sz3-1995)]);
			double stdError=stdDev/Math.sqrt(numIdentifiedAnnualLengths[(sz3-1995)]);
	
		%>
		</li>

<%}%>
</ul>

<p>Num sharks six meters or under (Exmouth):</p>
<ul>
<%
for(int sz6=1995;sz6<=nowYear;sz6++) {
%>
	<li><%=sz6%>: <%=numSharksUnderSixMeters[(sz6-1995)]%></li>
<%}%>
</ul>
<p>Num sharks over six meters(Exmouth):</p>
<ul>
<%
for(int sz6=1995;sz6<=nowYear;sz6++) {
%>
	<li><%=sz6%>: <%=numSharksOverSevenMeters[(sz6-1995)]%></li>
<%}
%>
<p><strong>All sharks, all years, 1995-<%=nowYear%></strong></p>
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

	Extent sharkClass=myShepherd.getPM().getExtent(MarkedIndividual.class, true);
	Query query=myShepherd.getPM().newQuery(sharkClass);
	Iterator allSharks=myShepherd.getAllMarkedIndividuals(query);

	while (allSharks.hasNext()) {
	
		//initialize our capture array
		int[] captures=new int[13];
		for(int p=0;p<13;p++) {
			captures[p]=0;
		} //end for
		
	
		MarkedIndividual sharky=(MarkedIndividual)allSharks.next();
		if(sharky.wasSightedInLocationCode("1a")) {
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


