<!DOCTYPE html PUBLIC "-//W3C//DTD XHTML 1.0 Transitional//EN"
        "http://www.w3.org/TR/xhtml1/DTD/xhtml1-transitional.dtd">
<%@ page contentType="text/html; charset=utf-8" language="java" import="org.joda.time.LocalDateTime,
org.joda.time.format.DateTimeFormatter,
org.joda.time.format.ISODateTimeFormat,java.net.*,java.io.*,java.util.*, java.io.FileInputStream, java.io.File, java.io.FileNotFoundException, org.ecocean.*,org.ecocean.servlet.*,javax.jdo.*, java.lang.StringBuffer, java.util.Vector, java.util.Iterator, java.lang.NumberFormatException,org.apache.commons.io.filefilter.PrefixFileFilter,org.ecocean.mmutil.FileUtilities,javax.jdo.*, java.lang.StringBuffer, java.util.Vector, java.util.Iterator, java.lang.NumberFormatException"%>

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
  <%!
  // some util functions
  // the image file is in a folder whose name is somewhat difficult to derive
  // this will return a File f, and f.exists() might be true or false depending on the success of the search
  static File getEncDataFolder(File imgDir, Encounter enc) {
    String fName = "";
    String imgName = enc.getCatalogNumber();
    String photographer = enc.getPhotographerName();
    if (imgName != null && photographer != null) {
      // the data folder naming convention used in our data is:
      fName = imgName.substring(0,9) + photographer;
    }
    File dataFolder = new File(imgDir, fName);
    if (!dataFolder.exists()) {
      // oddly, some folder names just have underscores at the beginning
      dataFolder = new File(imgDir, '_'+fName);
      System.out.println("\tfName = _"+fName);
    } else {
      System.out.println("\tfName = "+fName);
    }
    return dataFolder;
  }

  static File getEncPicture(File imgDir, Encounter enc) {
    File dataFolder = getEncDataFolder(imgDir, enc);
    File pFile = new File(dataFolder, enc.getCatalogNumber()+".jpg");
    return pFile;
  }


  //should find some folders that #1 misses
  // defaults to the nameless empty dir, in the parent imgDir you supplied
  static File getEncDataFolder2(File imgDir, Encounter enc) {
    String imgName = enc.getCatalogNumber()+".jpg";
    String dirPrefix = imgName.substring(0,9);

    // generate list of all possible folder names (concatenating two lists of possibilities)
    String[] possFolders1 = imgDir.list( new PrefixFileFilter(dirPrefix));
    String[] possFolders2 = imgDir.list( new PrefixFileFilter("_"+dirPrefix));
    String[] possibleFolders = new String[possFolders1.length + possFolders2.length];
    System.arraycopy(possFolders1, 0, possibleFolders, 0, possFolders1.length);
    System.arraycopy(possFolders2, 0, possibleFolders, possFolders1.length, possFolders2.length);

    // Check each possible folder, and return whichever one has the right image in it
    for (String fName: possibleFolders) {
      File testF = new File(imgDir, fName);
      if (testF.exists() && testF.isDirectory()) {
        File outF = new File(testF, imgName);
        if (outF.exists() && outF.isFile()) {
          System.out.println(enc.getCatalogNumber()+" is in folder "+fName+" and has image "+imgName);
          return testF;
        }
      }
    }
    return new File(imgDir, "");
  }
  public String cleanDirName(String old) {
    if (old.length()>0 && old.charAt(0)=='_') {
      return old.substring(1,old.length());
    }
    return old;
  }

  public boolean isJpg(String fname) {
    return (fname.length()>2&&fname.substring(fname.length()-3,fname.length()).equals("jpg"));
  }

  public String getEncID(String imgName) {
    String encID = imgName;
    if ( encID.length()>3 && encID.charAt(encID.length()-4)=='.' ) {
      encID = encID.substring(0,encID.length()-4);
    }
    if (imgName.startsWith("extractRight")) {
      encID = encID.substring(12);
    } else if (imgName.startsWith("extract")) {
      encID = encID.substring(7);
    }

    return encID;
  }

  // this is needed to parse each .fgp file
  private boolean checkFileType(DataInputStream data) throws IOException {
    byte[] b = new byte[4];
    // read in first 4 bytes, and check file type.
    data.read(b, 0, 4);
    if (((char) b[0] == 'I' && (char) b[1] == 'f' && (char) b[2] == '0' && (char) b[3] == '1') == false) {
      return false;
    }
    return true;
  }


  public boolean hasFgp(String jpgName, File imgDir) {
    File fgp = new File(imgDir, jpgName.replaceAll(".jpg",".fgp"));
    return fgp.exists();
  }

  // I'll let this function live here until I can download the latest version of com.reijns.I3S
  public ArrayList<SuperSpot> loadFgpSpots(DataInputStream data, int n) throws IOException {
    return loadFgpSpots(data, n, false, false);
  }
  // if spacingZeroes, there is a 0.0 between each pair of doubles
  public ArrayList<SuperSpot> loadFgpSpots(DataInputStream data, int n, boolean verbose, boolean spacingZeroes) throws IOException {
    ArrayList<SuperSpot> out = new ArrayList<SuperSpot>();
    for (int i=0; i<n; i++) {
      double x = data.readDouble();
      double y = data.readDouble();
      if (spacingZeroes) data.readDouble();
      if (Double.isNaN(x) || Double.isNaN(y)) {
        throw new IOException("Caught IOException on parsing fgp file: a spot coordinate was NaN.");
      };
      if (verbose) System.out.println("\t\tSpot: ("+x+", "+y+")");
      out.add(new SuperSpot(x,y));
    }
    return out;
  }

  static String printSpotList(ArrayList<SuperSpot> spotList) {
    String out = "";
    for (SuperSpot s : spotList) {
      out += "\n("+s.getCentroidX()+", "+s.getCentroidY()+")";
    }
    return out;
  }
  static File getEncDBFolder (File dataDir, String encID) {
    String subDir = "encounters/";
    if (encID!=null && encID.length()>1) {
      //subDir += encID.charAt(0) + "/" + encID.charAt(1) + "/";
    }
    subDir += encID;
    File out = new File(dataDir, subDir);
    out.mkdirs();
    return out;
  }
  static File getEncDBFolder (File dataDir, Encounter enc) {
    return getEncDBFolder(dataDir, enc.getCatalogNumber());
  }
  %>



<%


// data dir stuff
System.out.println("Beginning directory creation...");
String rootWebappPath = getServletContext().getRealPath("/");
System.out.println("\twebapp path:\t"+rootWebappPath);
File webappsDir = new File(rootWebappPath).getParentFile();
System.out.println("\twebapps dir:\t"+webappsDir.getAbsolutePath());
String dataDirName = CommonConfiguration.getDataDirectoryName(context);
System.out.println("\tdata dir name:\t"+dataDirName);
//File shepherdDataDir = new File("/data/ncaquariums_data_dir");
File shepherdDataDir = new File(webappsDir, CommonConfiguration.getDataDirectoryName(context));
System.out.println("\tdata dir absolute:\t"+shepherdDataDir.getAbsolutePath());
System.out.println("\tdata dir canonical:\t"+shepherdDataDir.getCanonicalPath());
File tempSubdir = new File(webappsDir, "temp");
System.out.println("Finished directory creation.\n");


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
  shortToLong.put("KHI",	"Kevin Hitchins");
  shortToLong.put("SRA",	"Simon Rajaratnum");

  shortToEmail.put("RRA", "rnramaley@gmail.com");
  shortToEmail.put("AGR", "ajhgreen@gmail.com");
  shortToEmail.put("BBA", "bbarker@internode.on.net");

  nameToEmail.put("Kevin Hitchins", "info@southwestrocksdive.com.au");
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
  MarkedIndividual enc=allSharks.next();
  try {
      myShepherd.commitDBTransaction();
      myShepherd.beginDBTransaction();
    }
  }
  catch(Exception e){
    numIssues++;
    %>
    <%=enc.getIndividualID() %> has an issue with month. <br />
    <%
  }
}
*/

boolean uploading=true;
int nNames=0;
int nEmails=0;
int nNoNames=0;
int noNamesReplaced=0;
int photoShorts = 0;



File d = new File(".");
String text = "";
text += "</br> Current Directory: "+d.getAbsolutePath();
text += "</br> Can Execute: "+d.canExecute();
text += "</br> Can Read: "+d.canRead();
text += "</br> Can Write: "+d.canWrite();
text += "</br>";

File sharkImgs = new File("/data/shark_imgs/");
if(sharkImgs.exists() && sharkImgs.isDirectory()) {
  text += "</br> Shark Images Root Directory: "+sharkImgs.getAbsolutePath();
  text += "</br> Can Execute: "+sharkImgs.canExecute();
  text += "</br> Can Read: "+sharkImgs.canRead();
  text += "</br> Can Write: "+sharkImgs.canWrite();
  text += "</br>";
}
%><%=text%><%

while(allEncs.hasNext()){
	Encounter enc=allEncs.next();
  String encID = enc.getCatalogNumber();

	try{
    String shortName = enc.getPhotographerName();

    if (shortName==null || shortName.equals("")) {
      nNoNames++;

      File encDataFolder = getEncDataFolder2(sharkImgs,enc);
      if (encDataFolder.exists()) {
        photoShorts++;
        String dFolder = encDataFolder.getName();
        String photoShort = dFolder.substring(dFolder.length()-3,dFolder.length());
        %></br><%=encID %> photoShort = <%=photoShort%><%
        if (shortToLong.containsKey(photoShort)) {
          noNamesReplaced++;
          shortName=photoShort;
        }
        else if (!photoShort.equals("mgs")){
          %></br><%=encID %> setting Photographer to photoShort: <%=photoShort%><%
          if (uploading) {
            enc.setPhotographerName(photoShort);
          }
        }
      }
    }

    String longName = "";

    if (shortName != null && !shortName.isEmpty() && shortToLong.containsKey(shortName)) {
      nNames++;
      longName = shortToLong.get(shortName);
      if (uploading) {enc.setPhotographerName(longName);}
      %><%=encID %> photographer changed from <%=shortName %> to <%=longName%><br /><%
    }
    if (shortName != null && !shortName.isEmpty() && nameToEmail.containsKey(longName)) {
      nEmails++;
      String email = nameToEmail.get(longName);
      if (uploading) {
        enc.setPhotographerEmail("email");
        myShepherd.commitDBTransaction();
        myShepherd.beginDBTransaction();
      }
      %><%=enc.getIndividualID() %> email changed to <%=email%><br /><%
    }
  	}
	catch(Exception e){
		numIssues++;
		%>
		<%=enc.getCatalogNumber() %> has an issue with setting user id <br />
		<%
	}
}

%></br>
nNames: <%=nNames%><br/>
nEmals: <%=nEmails%></br>
nNoNames: <%=nNoNames%><br/>
noNamesReplaced: <%=noNamesReplaced%><br/>

photoShorts: <%=photoShorts%><br/>
<%


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
