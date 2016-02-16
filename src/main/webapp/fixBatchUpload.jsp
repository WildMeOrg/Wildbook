<!DOCTYPE html PUBLIC "-//W3C//DTD XHTML 1.0 Transitional//EN"
        "http://www.w3.org/TR/xhtml1/DTD/xhtml1-transitional.dtd">
<%@ page contentType="text/html; charset=utf-8" language="java" import="org.joda.time.LocalDateTime,
org.joda.time.format.DateTimeFormatter,
org.joda.time.format.ISODateTimeFormat,java.net.*,java.io.*,java.util.*, java.io.FileInputStream, java.io.File, java.io.FileNotFoundException, org.ecocean.*,org.ecocean.servlet.*,javax.jdo.*, java.lang.StringBuffer, java.util.Vector, java.util.Iterator, java.lang.NumberFormatException"%>

<%

String context="context0";
context=ServletUtilities.getContext(request);

	Shepherd myShepherd=new Shepherd(context);

// pg_dump -Ft sharks > sharks.out

//pg_restore -d sharks2 /home/webadmin/sharks.out


%>

<html>
<head>
<title>Fix Some Fields</title>

</head>


<body>
<%

myShepherd.beginDBTransaction();

//build queries

Extent encClass=myShepherd.getPM().getExtent(Encounter.class, true);
Query encQuery=myShepherd.getPM().newQuery(encClass);
Iterator<Encounter> allEncs;


%><%!public Double degStrToDouble(String DMS) {
  int i=0;
  String deg = "";
  while (Character.isDigit(DMS.charAt(i))) {
    deg += DMS.charAt(i);
    i += 1;
  };
  while (!Character.isDigit(DMS.charAt(i))) {
    i += 1;
  }
  String min = "";
  while (Character.isDigit(DMS.charAt(i))) {
    min += DMS.charAt(i);
    i += 1;
  };
  while (!Character.isDigit(DMS.charAt(i))) {
    i += 1;
  }
  String sec = "";
  while (Character.isDigit(DMS.charAt(i))) {
    sec += DMS.charAt(i);
    i += 1;
  };
  int D = Integer.parseInt(deg);
  int M = Integer.parseInt(min);
  int S = Integer.parseInt(sec);
  Double mag = D + (M/60.0) + (S/3600.0);
  int sign = 1;
  while (!Character.isLetter(DMS.charAt(i))) {
    i += 1;
  }
  if (Character.isLetter(DMS.charAt(i))) {
    char c = DMS.charAt(i);
    if ( c=='S' || c=='s' || c=='W' || c=='w' ) sign = -1;
  }
  return mag*sign;
}
%><%


Extent sharkClass=myShepherd.getPM().getExtent(MarkedIndividual.class, true);
Query sharkQuery=myShepherd.getPM().newQuery(sharkClass);
Iterator<MarkedIndividual> allSharks;

HashMap<String,String[]> coordStrings = new HashMap<String,String[]>();

coordStrings.put("BI",		new String[]{"32° 37’ 30 S", 	"152° 20’ 20 E"});
coordStrings.put("BS",		new String[]{"32° 28’ 00 S", 	"152° 33’ 00 E"});
coordStrings.put("CH",		new String[]{"30° 14’ 52 S",	"153° 21’ 36 E"});
coordStrings.put("FR",		new String[]{"30° 56’ 25 S", 	"153° 05’ 45 E"});
coordStrings.put("JR",		new String[]{"28° 36’ 50 S", 	"153° 37’ 35 E"});
coordStrings.put("LR",    new String[]{"32° 12’ 32 S",  "152° 34’ 05 E"});
coordStrings.put("LS",		new String[]{"32° 28’ 35 S", 	"152° 32’ 50 E"});
coordStrings.put("MI",		new String[]{"36° 14’ 30 S", 	"150° 13’ 35 E"});
coordStrings.put("MP",		new String[]{"33° 57’ 45 S", 	"151° 15’ 50 E"});
coordStrings.put("PF",		new String[]{"32° 14’ 25 S", 	"152° 36’ 05 E"});
coordStrings.put("SR",		new String[]{"32° 28’ 00 S", 	"152° 33’ 00 E"});
coordStrings.put("SS",		new String[]{"30° 12’ 30 S", 	"153° 17’ 00 E"});
coordStrings.put("TB",		new String[]{"32° 09’ 10 S", 	"152° 32’ 20 E"});
coordStrings.put("WR",		new String[]{"25° 54’ 40 S", 	"153° 12’ 20 E"});
coordStrings.put("TG",		new String[]{"35° 45’ 20 S", 	"150° 15’ 15 E"});
coordStrings.put("BT",		new String[]{"32° 10’ 75 S",	"152° 31' 31 E"});
coordStrings.put("FC",		new String[]{"33° 24’ 128 S", "151° 32’ 184 E"});
coordStrings.put("FL",		new String[]{"26° 59’ 00 S", 	"153° 29’ 05 E"});
coordStrings.put("GI",		new String[]{"30° 54’ 45 S", 	"153° 05’ 10 E"});
coordStrings.put("SK",		new String[]{"32° 24’ 30 S", 	"152° 32’ 20 E"});
coordStrings.put("NB",		new String[]{"33° 54’ 05 S", 	"151° 16’ 20 E"});
coordStrings.put("CG",		new String[]{"31° 40’ 55 S", 	"152° 54’ 35 E"});
coordStrings.put("DP",		new String[]{"36° 10′ 0″ S",	"150° 8′ 0″ E"});
coordStrings.put("FT",		new String[]{"27° 08’ 00 S", 	"153° 33’ 30 E"});
coordStrings.put("ST",		new String[]{"32° 26’ 41 S", 	"152° 32’ 20 E"});
coordStrings.put("CC",		new String[]{"27° 07’ 00 S", 	"153° 28’ 30 E"});
coordStrings.put("LF",		new String[]{"33° 44’ 10 S", 	"151° 19’ 30 E"});
coordStrings.put("NS",		new String[]{"29° 55’ 05 S", 	"153° 23’ 00 E"});
coordStrings.put("DD",		new String[]{"35° 2’ 797 S",	"150° 50' 437 E"});
coordStrings.put("MR",		new String[]{"31° 46’ 05 S", 	"152° 48’ 25 E"});
coordStrings.put("FB",		new String[]{"33° 47' 955 S",	"151° 17' 914  E"});

HashMap<String,Double[]> coords = new HashMap<String,Double[]>();


try{

for (String key : coordStrings.keySet() ){
  Double lat = degStrToDouble(coordStrings.get(key)[0]);
  Double longitude = degStrToDouble(coordStrings.get(key)[1]);
  coords.put(key, new Double[]{lat, longitude});
}
for (String key : coords.keySet()) {
  %> <br/> <%=key%> coords = ( <%=coords.get(key)[0]%>, <%=coords.get(key)[1]%> ) <%
}


allEncs=myShepherd.getAllEncounters(encQuery);
allSharks=myShepherd.getAllMarkedIndividuals(sharkQuery);

int numIssues=0;

DateTimeFormatter fmt = ISODateTimeFormat.date();
DateTimeFormatter parser1 = ISODateTimeFormat.dateOptionalTimeParser();


HashMap<String,String> fullLocationMap = new HashMap<String,String>();
boolean moreLocationIDs=true;
int siteNum=0;
String abr; String full;
while(moreLocationIDs) {
   String currentLocationID = "locationID"+siteNum;
   if (CommonConfiguration.getProperty(currentLocationID,context)!=null) {
	   full = CommonConfiguration.getProperty(currentLocationID,context);
     abr = full.substring(0,2);
	   fullLocationMap.put(abr, full);
	   siteNum++;
   } else {
     moreLocationIDs=false;
   }
}
int count = 0;
boolean commit = true;
int limit = 20000;

while(allEncs.hasNext() && count < limit){


	Encounter sharky=allEncs.next();
	try{
  	if(sharky.getDynamicPropertyValue("batch")!=null && sharky.getDynamicPropertyValue("batch").equals("true")) {
      count++;

      %>
      <%=sharky.getCatalogNumber() %> being fixed!  <br />
      <%

      if (commit) {
        sharky.setSubmitterID("Spot a Shark");
        sharky.setGenus("Carcharius");
        sharky.setSpecificEpithet("taurus");
      }

      String shortID = sharky.getCatalogNumber().substring(0,2);
      if (fullLocationMap.containsKey(shortID)) {
        String longID = fullLocationMap.get(shortID);
        %>
        <%=sharky.getCatalogNumber() %> set location to: <%=longID %>. <br />
        <%
        if (commit) {sharky.setLocationID(longID);}
      }

      if (coords.containsKey(shortID)) {
        %>
        <%=sharky.getCatalogNumber() %> setting coords to ( <%=coords.get(shortID)[0] %>, <%=coords.get(shortID)[1] %>) <br />
        <%
        if (commit) {
          sharky.setDecimalLatitude(coords.get(shortID)[0]);
          sharky.setDecimalLongitude(coords.get(shortID)[1]);
        }
      }

      myShepherd.commitDBTransaction();
      myShepherd.beginDBTransaction();

  	}
  }
		catch(Exception e){
			numIssues++;
			%>
			<%=sharky.getCatalogNumber() %> has an issue with locID. <br />
			<%
		}
	}

myShepherd.commitDBTransaction();
	myShepherd.closeDBTransaction();
	myShepherd=null;
%>


<p>Done successfully!</p>
<p><%=numIssues %> issues found.</p>
<p><%=count %> files changed.</p>


<%

}
catch(Exception ex) {

	System.out.println("!!!An error occurred on page fixSomeFields.jsp. The error was:");
	ex.printStackTrace();
	//System.out.println("fixSomeFields.jsp page is attempting to rollback a transaction because of an exception...");
	encQuery.closeAll();
	encQuery=null;
	//sharkQuery.closeAll();
	//sharkQuery=null;
	myShepherd.rollbackDBTransaction();
	myShepherd.closeDBTransaction();
	myShepherd=null;

}
%>


</body>
</html>
