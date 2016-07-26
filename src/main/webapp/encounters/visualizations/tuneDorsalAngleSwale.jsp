<%@ page contentType="text/html; charset=utf-8" 
	language="java" 
	import="java.awt.geom.*,
	com.reijns.I3S.*,
	org.apache.commons.math.stat.descriptive.SummaryStatistics,
	java.awt.geom.Point2D.Double,
	org.ecocean.servlet.ServletUtilities,org.ecocean.grid.*,java.awt.Dimension,org.ecocean.*, org.ecocean.servlet.*, java.util.*,javax.jdo.*,
	java.io.File,
	com.fastdtw.timeseries.TimeSeriesBase.*,
	com.fastdtw.dtw.*,
	com.fastdtw.util.Distances,
	com.fastdtw.timeseries.TimeSeriesBase.Builder,
	com.fastdtw.timeseries.*,
	org.ecocean.grid.* ,
	org.ecocean.grid.msm.*,
	org.ecocean.neural.TrainNetwork,
	java.awt.geom.*,
	java.awt.geom.Point2D.Double"
	%>


<%

String context="context0";
context=ServletUtilities.getContext(request);

	
	
String encNum = request.getParameter("enc1");
String encNum2 = request.getParameter("enc2");

int startAngle=1;
int endAngle=180;
ArrayList<java.lang.Double> angleDiffs=new ArrayList<java.lang.Double>();

Shepherd myShepherd = new Shepherd(context);
		  
//let's set up references to our file system components
		 
double bestDiff=-999999;
double bestAngle=0;

try {
	
  //set up the JDO pieces and Shepherd
  myShepherd.beginDBTransaction();
  
  
  for(double thisAngle=startAngle;thisAngle<endAngle;thisAngle=thisAngle+1){
  
		  double msmMatchTotals=0.0;
		  double msmNonmatchTotals=0;
		  int numMatches=0;
		  int numNonmatches=0;
		  ArrayList<Encounter> encs=myShepherd.getAllEncountersForSpeciesWithSpots("Tursiops", "truncatus");
		  
		  //System.out.println("\n\n\n\n Iterating angle: "+thisAngle+" while best angle is "+bestAngle+"  \n\n\n\n\n\n");
		  
		  for(int i=0;i<encs.size();i++){
			  for(int j=1;j<(encs.size()-1);j++){
				  if(numMatches<=100){
					  Encounter enc1=encs.get(i);
					  Encounter enc2=encs.get(j);
					  EncounterLite theEnc=new EncounterLite(enc1,thisAngle);
					  EncounterLite theEnc2=new EncounterLite(enc2,thisAngle);
					  
					  
					  boolean isMatch=false;
					  if( (theEnc.getIndividualID()!=null) && (theEnc2.getIndividualID()!=null) && (!theEnc.getIndividualID().toLowerCase().equals("unassigned")) && (theEnc.getIndividualID().equals(theEnc2.getIndividualID()))){
						  isMatch=true;
					  }
					  
					  double reward=50.0;
			          double epsilon=0.0006;
			          double penalty=0;
					  
					  if(isMatch&&(numMatches<100)){
						  //System.out.println("MATCH");
						  java.lang.Double result=EncounterLite.getSwaleMatchScore(theEnc, theEnc2,penalty, reward, epsilon);
						  msmMatchTotals+=result;
						  numMatches++;
					  }
					  else if(numNonmatches<100){
						  //System.out.println("NON-MATCH");
						  java.lang.Double result=EncounterLite.getSwaleMatchScore(theEnc, theEnc2, penalty, reward, epsilon);
						msmNonmatchTotals+=result;
					  	numNonmatches++;}
				  
			  	  }	  
		  	}
		  }
		  
		  	double myDiff=msmMatchTotals/numMatches-msmNonmatchTotals/numNonmatches;
		  	angleDiffs.add(new java.lang.Double(myDiff));
		  	System.out.println("!!!For angle "+thisAngle+"  diff is: "+myDiff);
		  	
		  	if(myDiff>bestDiff){
		  		bestDiff=myDiff;
		  		bestAngle=thisAngle;
		  		//System.out.println("!!!For angle "+thisAngle+"  setting bestDiff at diff is: "+myDiff);
		  	}
  }

  String langCode=ServletUtilities.getLanguageCode(request);
  Properties encprops = ShepherdProperties.getProperties("encounter.properties", langCode, context);

	    
	%>
	


<p>EncounterLite Best Angular Match Swale result: <%=bestAngle %> with a diff of <%=bestDiff %></p>
<ul>
<%
for(int i=-0;i<angleDiffs.size();i++){
%>
<li>Angle: <%=(i+startAngle) %> and diff: <%=angleDiffs.get(i) %></li>
<%
}
%>
</ul>


<%
}	
//end try
catch(Exception e) {
  e.printStackTrace();
}
finally {
  myShepherd.rollbackDBTransaction();
  myShepherd.closeDBTransaction();
}

%>