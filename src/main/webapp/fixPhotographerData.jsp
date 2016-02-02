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





Extent sharkClass=myShepherd.getPM().getExtent(MarkedIndividual.class, true);
Query sharkQuery=myShepherd.getPM().newQuery(sharkClass);
Iterator<MarkedIndividual> allSharks;


HashMap<String, String> shortToLong = new HashMap<String,String>();
HashMap<String, String> shortToEmail = new HashMap<String,String>();
HashMap<String,String> nameToEmail = new HashMap<String,String>();

try{

  shortToLong.put("ARO",	"A. Roche");
  shortToLong.put("BBI",	"Ben Birt");
  shortToLong.put("CHA",	"C. Hamilton");
  shortToLong.put("CDA",	"C. Day");
  shortToLong.put("CUN",	"C. Unger");
  shortToLong.put("DKE",	"D. Kez");
  shortToLong.put("DSC",	"D. Schroff");
  shortToLong.put("DSI",	"D. Silcock");
  shortToLong.put("DSL",	"D. Slezac");
  shortToLong.put("DSM",	"D. Smith");
  shortToLong.put("DOU",	"DOUTS");
  shortToLong.put("EBR",	"E. Brookes");
  shortToLong.put("FAR",	"Fardell");
  shortToLong.put("GHI",	"G. Hine");
  shortToLong.put("GMC",	"G. McMartin");
  shortToLong.put("GTO",	"G. Toland");
  shortToLong.put("GLU",	"GLUG");
  shortToLong.put("FFD",	"Feet First Dive");
  shortToLong.put("HPO",	"H. Porter");
  shortToLong.put("ISI",	"I. Signorelli");
  shortToLong.put("JBE",	"J. Bennett");
  shortToLong.put("JJE",	"J. Jeayes");
  shortToLong.put("JKA",	"J. Kato");
  shortToLong.put("JRE",	"J. Regan");
  shortToLong.put("JSW",	"J. Swift");
  shortToLong.put("JWE",	"J. Weinman");
  shortToLong.put("KCU",	"K. Cullen");
  shortToLong.put("KJA",	"K. Jacson");
  shortToLong.put("KMC",	"K. McCleery");
  shortToLong.put("KRA",	"K. Raubenheimer");
  shortToLong.put("LBR",	"L. Brodie");
  shortToLong.put("LCL",	"L. Clarke");
  shortToLong.put("MGR",	"M. Gray");
  shortToLong.put("MHA",	"M. Harwood");
  shortToLong.put("MMA",	"M. Harwood");
  shortToLong.put("MPA",	"M. Parsons");
  shortToLong.put("NCO",	"N. Coombes");
  shortToLong.put("NKE",	"N. Kerhsler");
  shortToLong.put("NSP",	"N. Spargo");
  shortToLong.put("OKR",	"O. Kristensen");
  shortToLong.put("PCR",	"P. Craig");
  shortToLong.put("PHI",	"Peter Hitchins");
  shortToLong.put("PKR",	"P. Krattinger");
  shortToLong.put("PMC",	"P. McGee");
  shortToLong.put("PPF",	"P. Pflugl");
  shortToLong.put("PSH",	"P. Sharp");
  shortToLong.put("PSI",	"P. Simpson");
  shortToLong.put("RDA",	"R. Davis");
  shortToLong.put("RJO",	"R. Johnson");
  shortToLong.put("RLI",	"R. Ling");
  shortToLong.put("RNA",	"R. Nagy");
  shortToLong.put("RPR",	"R. Proctor");
  shortToLong.put("RRA",	"R. Ramaley");
  shortToLong.put("RVZ",	"R. Van Zalm");
  shortToLong.put("RHO",	"Rod Hodgkins");
  shortToLong.put("RDC",	"Ryde Dive Club");
  shortToLong.put("SBA",	"S. Barker");
  shortToLong.put("SCO",	"S. Coutts");
  shortToLong.put("SMI",	"S. Mittag");
  shortToLong.put("SAM",	"Sam");
  shortToLong.put("SST",	"Silke Stuckenbrock");
  shortToLong.put("WBA",	"W. Barry");
  shortToLong.put("WB2",	"W. Basford");
  shortToLong.put("WRD",	"WRDC");
  shortToLong.put("SPO",	"Scott Portelli");
  shortToLong.put("AGR", "A. Green");
  shortToLong.put("BBA",	"B. Barker");
  shortToLong.put("IST",	"Isabelle Stratton");
  shortToLong.put("CAR",	"Cardno Ecology Lab");
  shortToLong.put("BMC",	"Ben McCullum");
  shortToLong.put("BRD",	"Brad from Pro Dive");

  shortToEmail.put("RRA", "rnramaley@gmail.com");
  shortToEmail.put("AGR", "ajhgreen@gmail.com");
  shortToEmail.put("BBA", "bbarker@internode.on.net");

  nameToEmail.put("A. Roche",	"anitar@bigpond.net.au");
  nameToEmail.put("Ben Birt",	"benbirt2@hotmail.com");
  nameToEmail.put("C. Hamilton", "chris@yambabeach.net.au");
  nameToEmail.put("C. Day",	"chris.p.day.s@gmail.com");
  nameToEmail.put("C. Unger",	"cjunger@bigpond.com");
  nameToEmail.put("D. Silcock",	"don.silcock@ge.com");
  nameToEmail.put("D. Slezac",	"dcslez@yahoo.com.au");
  nameToEmail.put("E. Brookes",	"elly.brookes@gmail.com");
  nameToEmail.put("G. Toland",	"g.toland54@gmail.com");
  nameToEmail.put("GLUG",	"glug88@gmail.com");
  nameToEmail.put("Feet First Dive", "enquiries@feetfirstdive.com.au");
  nameToEmail.put("H. Porter",	"huw@huwporter.com");
  nameToEmail.put("I. Signorelli",	"ivan.signorelli@gmail.com");
  nameToEmail.put("J. Bennett",	"jennibennett@gmail.com");
  nameToEmail.put("John Granbury",	"john@professionaldiveservices.com.au");
  nameToEmail.put("J. Jeayes",	"john.jeayes@gmail.com");
  nameToEmail.put("J. Kato",	"scubaninja.j@gmail.com");
  nameToEmail.put("J. Regan",	"jregan@jcregan.com");
  nameToEmail.put("J. Swift",	"johngoby@ozemail.com.au");
  nameToEmail.put("J. Weinman",	"bwds@tpg.com.au");
  nameToEmail.put("K. Cullen",	"kevin@breseight.com.au");
  nameToEmail.put("Kevin Hitchins",	"info@southwestrocksdive.com.au");
  nameToEmail.put("K. Jackson",	"kjackson_au@optusnet.com.au");
  nameToEmail.put("K. McCleery",	"krisinmaui@gmail.com");
  nameToEmail.put("K. Raubenheimer",	"k_raubenheimer@hotmail.com");
  nameToEmail.put("L. Brodie",	"lbrodie52@optusnet.com.au");
  nameToEmail.put("L. Clarke",	"lynda@lyndaclarke.net");
  nameToEmail.put("M. Gray",	"mgray@gotalk.net.au");
  nameToEmail.put("M. Harwood",	"maree_harwood@hotmail.com");
  nameToEmail.put("M. Parsons",	"mnparso@gmail.com");
  nameToEmail.put("N. Coombes",	"nige77@hotmail.com");
  nameToEmail.put("N. Kerhsler",	"niccikershler@gmail.com");
  nameToEmail.put("N. Spargo",	"nspargo@mac.com");
  nameToEmail.put("O. Kristensen",	"iibm5210@bigpond.net.au");
  nameToEmail.put("P. Craig",	"PeterC@ggs.vic.edu.au");
  nameToEmail.put("Peter Hitchins",	"info@southwestrocksdive.com.au");
  nameToEmail.put("P. Krattinger",	"pkrattiger1@hotmail.com");
  nameToEmail.put("P. McGee",	"petermcgee28@gmail.com");
  nameToEmail.put("P. Sharp",	"petermcgee28@gmail.com");
  nameToEmail.put("P. Simpson",	"peter@pandcjoinery.com.au");
  nameToEmail.put("R. Davis",	"rebecca@saveoursharks.com.au");
  nameToEmail.put("R. Nagy",	"rnramaley@gmail.com");
  nameToEmail.put("R. Proctor",	"rpprocter@yahoo.co.uk");
  nameToEmail.put("R. Ramaley",	"rnramaley@gmail.com");
  nameToEmail.put("R. Van Zalm",	"rob.vanderzalm@gmail.com");
  nameToEmail.put("Rod Hodgkins",	"rodh@ccconsulting.com.au");
  nameToEmail.put("S. Barker",	"seanmbarker@hotmail.com");
  nameToEmail.put("S. Mittag",	"simonmittag@me.com");
  nameToEmail.put("Silke Stuckenbrock",	"silkephoto@hotmail.com");
  nameToEmail.put("WRDC",	"ajhgreen@gmail.com");
  nameToEmail.put("Scott Portelli",	"scott.portelli@swimmingwithgentlegiants.com");
  nameToEmail.put("A. Green",	"ajhgreen@gmail.com");
  nameToEmail.put("B. Barker",	"bbarker@internode.on.net");
  nameToEmail.put("Isabelle Stratton",	"glug88@gmail.com");
  nameToEmail.put("Nick Dawkins",	"nick@nickdawkins.com");

allEncs=myShepherd.getAllEncounters(encQuery);
allSharks=myShepherd.getAllMarkedIndividuals(sharkQuery);

int numIssues=0;
/*
DateTimeFormatter fmt = ISODateTimeFormat.date();
DateTimeFormatter parser1 = ISODateTimeFormat.dateOptionalTimeParser();
*/
/*
while(allSharks.hasNext()){
  MarkedIndividual sharky=allSharks.next();
  try {
      myShepherd.commitDBTransaction();
      myShepherd.beginDBTransaction();
    }
  }
  catch(Exception e){
    numIssues++;
    %>
    <%=sharky.getIndividualID() %> has an issue with month. <br />
    <%
  }
}
*/

while(allEncs.hasNext()){


	Encounter sharky=allEncs.next();

	try{
    String shortName = sharky.getPhotographerName();
    if (shortName != null && !shortName.isEmpty() && shortToLong.containsKey(shortName)) {
      String longName = shortToLong.get(shortName);
      sharky.setPhotographerName(longName);
      %><%=sharky.getIndividualID() %> photographer changed from <%=shortName %> to <%=longName%><br /><%
    }
    if (shortName != null && !shortName.isEmpty() && shortToEmail.containsKey(shortName)) {
      String email = shortToEmail.get(shortName);
      sharky.setPhotographerEmail("email");
      %><%=sharky.getIndividualID() %> email changed to <%=email%><br /><%
    }
  	myShepherd.commitDBTransaction();
  	myShepherd.beginDBTransaction();
  	}
	catch(Exception e){
		numIssues++;
		%>
		<%=sharky.getCatalogNumber() %> has an issue with setting user id <br />
		<%
	}
}


myShepherd.commitDBTransaction();
myShepherd.closeDBTransaction();
myShepherd=null;
%>


<p>Done successfully!</p>
<p><%=numIssues %> issues found.</p>


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
