<!DOCTYPE html PUBLIC "-//W3C//DTD XHTML 1.0 Transitional//EN"
        "http://www.w3.org/TR/xhtml1/DTD/xhtml1-transitional.dtd">
<%@ page contentType="text/html; charset=utf-8" language="java" import="org.joda.time.LocalDateTime,
org.joda.time.format.DateTimeFormatter,
org.joda.time.format.ISODateTimeFormat,java.net.*,java.io.*,java.util.*, java.io.FileInputStream, java.io.File, java.io.FileNotFoundException, org.ecocean.*,org.ecocean.servlet.*,javax.jdo.*, java.lang.StringBuffer, java.util.Vector, java.util.Iterator, java.lang.NumberFormatException,org.apache.commons.io.filefilter.PrefixFileFilter,org.ecocean.mmutil.FileUtilities,javax.jdo.*, java.lang.StringBuffer, java.util.Vector, java.util.Iterator, java.lang.NumberFormatException,java.util.HashSet"%>

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
String text = "";

//setup data dir
System.out.println("Beginning directory creation...");
String rootWebappPath = getServletContext().getRealPath("/");
System.out.println("\twebapp path:\t"+rootWebappPath);
File webappsDir = new File(rootWebappPath).getParentFile();
System.out.println("\twebapps dir:\t"+webappsDir.getAbsolutePath());
String dataDirName = CommonConfiguration.getDataDirectoryName(context);
System.out.println("\tdata dir name:\t"+dataDirName);
//File shepherdDataDir = new File("/data/ncaquariums_data_dir");
File shepherdDataDir = new File(webappsDir, CommonConfiguration.getDataDirectoryName(context));
//if(!shepherdDataDir.exists()){shepherdDataDir.mkdirs();}
System.out.println("\tdata dir absolute:\t"+shepherdDataDir.getAbsolutePath());
System.out.println("\tdata dir canonical:\t"+shepherdDataDir.getCanonicalPath());
File tempSubdir = new File(webappsDir, "temp");
//if(!tempSubdir.exists()){tempSubdir.mkdirs();}
//System.out.println("\ttemp subdir:\t"+tempSubdir.getAbsolutePath());
System.out.println("Finished directory creation.\n");

myShepherd.beginDBTransaction();

//build queries

Extent encClass=myShepherd.getPM().getExtent(Encounter.class, true);
Query encQuery=myShepherd.getPM().newQuery(encClass);
Iterator<Encounter> allEncs;





Extent sharkClass=myShepherd.getPM().getExtent(MarkedIndividual.class, true);
Query sharkQuery=myShepherd.getPM().newQuery(sharkClass);
Iterator<MarkedIndividual> allSharks;

String[] badIDLiteralsArray = new String[] {"MP15MAR11UNL010", "SR11APR09UNR010", "b7a3c674-1177-4c6c-a815-cda1be912d78", "NB12DEC30FNL002", "MP14JUN05UNR012", "JR09DEC16UNR001", "JR10JUN20UNR001", "MP15MAR11UNR035", "1b2457cf-4c75-4005-8461-b5ea5b8c9a3e", "MP15MAR11UNL008", "ca19607f-2219-40c6-837b-af533a42503d", "MP15MAR11UNL050", "MP15MAR11UNR025", "SR10FEB20UNR029", "MP15MAR11UNL007", "daac71ef-8deb-4498-8291-6871fbd987ec", "MP15MAR11UNL046", "MP15MAR11UNL022", "ed2bf648-4644-41fe-856a-b71e1c0d9782", "MP15MAR11UNR015", "c0c0be76-8975-445d-87f5-301e97815f94", "MP15MAR11UNL006", "FR10DEC22UNR002", "MP14MAR22UNL020", "MP15MAR11UNL031", "MP11MAR15UNL026", "FR09MAR19UNL002", "SR11JAN02UNL001", "MP10JUN24MNL010", "MP15MAR11UNL041", "FR10NOV24UNL038", "MP14APR11UNR027", "MP14APR11UNR018", "MP14APR11UNR009", "FR08NOV01UNR007", "MP14APR11UNR022", "MP07JUL11UNL005", "MP14APR11UNR007", "MP14APR11UHL041", "MP14APR11UHL037", "MP14APR11UNR004", "SR11APR09UNR043", "MP07JUL11UNR002", "NB12DEC30FNL001", "MP09MAR03UNR013", "MP06APR01UNL004", "b40cf3b4-bce4-46ea-a92c-57cc8553503b", "MP12APR09UML022", "MP09MAR05UNL002", "MP11JAN26UNL007", "MP14MAY23UHR008", "16842aee-b858-4419-9ddf-b0b783269d55", "1c9a3f3d-9e7b-48ff-8220-cdcc0c2b8b66", "6b276713-4b9e-4d14-89aa-e82dd360eb34", "23927086-07ae-4a23-9101-6d87db2e8820", "b43761e0-e3bb-4e1f-803d-e0ee9c53dd45", "eac897d6-645d-427f-8235-c29476b9cf75", "7c012faf-4c26-4674-b77c-3bbb988afdbc", "0932d6fe-ef9d-4b69-a201-e1b4f7beb977", "28aab679-46d0-4da9-b831-2384fe8a35d2", "045a4bf4-cc33-49c4-a445-3de6b489b7ea", "6ddff058-a9e4-42fe-bad3-4dd08c3f699f", "292c15a6-a4fa-4876-8f62-cc240da89271", "0dc2f35a-5bd3-4559-a595-00fd4bdf6ec7", "bfe3fb5f-73a7-4ec4-876f-16ea574db919", "81bc3fac-93d9-486a-bfe4-d932695f93b4", "33fc4803-0aef-4cbb-b01d-551bf0977ad3", "d79c662e-07e6-4157-ae12-be15d3959581", "b55e1336-e840-4ce4-925e-2963809448a2", "1d958024-567f-44f2-b0ef-beb6b045c47d", "FR11SEP01MNL023", "MP14JUN05UNR021", "MP14JUN05UNR006", "MP14JUN05UNL052", "MP14JUN05MMR016", "MP14JUN05UHL048", "DP13JAN15UNL002", "JR10JUN20UNL003", "JR10JUN20UNL004", "JR10JUN20UNR002", "JR10JUN20UNL001", "MP15MAR11UNR020", "MP15MAR11UNL009", "MP15MAR11UNL042", "MP15MAR11UNL033", "MP15MAR11UNL049", "MP15MAR11UNR016", "MP15MAR11UNL018", "MP15MAR11UNR001", "MP15MAR11UNR010", "MP15MAR11UNL011", "MP15MAR11UNR008", "MP15MAR11UNR003", "MP15MAR11UNL023", "MP15MAR11UNL048", "MP15MAR11UNL004", "MP15MAR11UNL039", "MP15MAR11UNR017", "MP15MAR11UNL012", "MP15MAR11UNL035", "MP15MAR11UNR019", "MP15MAR11UNR026", "MP15MAR11UNL038", "MP15MAR11UNR018", "MP15MAR11UNL034", "MP15MAR11UNR009", "MP15MAR11UNR021", "MP15MAR11UNL015", "MP15MAR11UNL036", "MP15MAR11UNR029", "MP15MAR11UNR037", "MP15MAR11UNL043", "MP15MAR11UNL044", "MP15MAR11UNR014", "MP15MAR11UNL016", "MP15MAR11UNL001", "MP15MAR11UNR034", "MP15MAR11UNR006", "MP15MAR11UNR031", "MP15MAR11UNR023", "MP15MAR11UNR022", "MP15MAR11UNL013", "MP15MAR11UNL047", "MP15MAR11UNL014", "MP15MAR11UNR007", "MP15MAR11UNL045", "MP15MAR11UNL021", "MP15MAR11UNR012", "MP15MAR11UNL017", "MP14APR11UNR008", "MP14APR11UNR035", "MP14APR11UNL007", "MP14APR11UHR012", "MP14APR11UNL006", "MP14APR11UNR015", "MP14APR11UNL038", "MP14APR11UNL016", "MP14APR11UNL021", "MP14APR11UHL012", "MP14APR11UNR005", "MP14APR11UOL014", "MP14APR11UNR021", "MP14APR11UNL039", "MP14APR11UNL005", "MP14APR11UNR017", "MP14APR11UNR032", "MP14APR11UNL013", "MP14APR11UOL028", "MP14APR11UNR019", "MP14APR11UNL003", "MP14APR11UNR030", "MP14APR11UNR016", "MP14APR11UNR031", "MP14APR11UNL020", "MP14APR11UHR026", "MP14APR11UNL033", "MP14APR11UNR014", "MP14APR11UNR003", "MP14APR11UOR006", "MP14APR11UNR033", "MP14APR11UNR020", "MP14JUN05UNR022", "MP15MAR11UNL005", "MP14APR11UNR023", "MP12APR09UNL053", "MP14JUN05UNL046", "MP15JUL24UNL011", "MP14JUL24UNL003", "MP14FEB16UNL028", "MP14JUN05UNR013", "JR09DEC16UNL002", "JR10JUN20UNL002", "MP15MAR11UNL032", "MP15MAR11UNR032", "MP15MAR11UNL002", "MP15MAR11UNL051", "MP15MAR11UNR004", "MP15MAR11UNL040", "MP15MAR11UNR036", "MP15MAR11UNL020", "MP15MAR11UNL030", "MP15MAR11UNL003", "MP15MAR11UNR038", "MP14APR11UNR034", "MP14APR11UNR029", "MP14APR11UNR028", "MP14APR11UNL015", "MP14APR11UNL011", "MP14APR11UNR002", "MP14APR11UHL030", "MP14APR11UNR024", "MP14APR11UNR013", "MP14APR11UNR025", "FL15AUG12FML001", "SR11JAN01UNL001", "MP14MAY22UNL010", "JR09DEC22UNL022", "FR09DEC03UNR028", "SR11APR09UNR037", "MP09JAN26UNL012", "FR11JUN18UNL019", "SR11APR09UNR045", "SR11APR09UNR042", "SR11JAN01UNL005", "MP09JUN07UNL014", "MP12APR09UML032", "MP05MAY25UNR001", "NB13JAN12UNR001"};
// build a hashset out of the above for quick lookup
HashSet<String> badIDLiterals = new HashSet<String>();
for (String badID : badIDLiteralsArray) {
  badIDLiterals.add(badID);
}

try{

  File d = new File(".");
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

allEncs=myShepherd.getAllEncounters(encQuery);
allSharks=myShepherd.getAllMarkedIndividuals(sharkQuery);

int numIssues=0;

DateTimeFormatter fmt = ISODateTimeFormat.date();
DateTimeFormatter parser1 = ISODateTimeFormat.dateOptionalTimeParser();

int numMissing=0;
int numWithFiles=0;
int nAdded=0;
int count=0;
int nullImgs=0;
boolean printEncs=true;
boolean uploading=true;
ArrayList<String> badEncs = new ArrayList<String>();

int nBadFound = 0;
int nBadWSpots = 0;

while(allEncs.hasNext()&&count<50000){
  count++;

	Encounter enc=allEncs.next();
  String encID = enc.getCatalogNumber();

  List<SinglePhotoVideo> images=myShepherd.getAllSinglePhotoVideosForEncounter(encID);

  if (badIDLiterals.contains(encID)) {
    %> <br/> <%=encID%> located. <%=images.size()%> SPVs<%
    if (images.size() >= 1) {
      if (uploading) {
        for (SinglePhotoVideo spv : enc.getSinglePhotoVideo()) {
          enc.removeSinglePhotoVideo(spv);
        }
      }
      %> <br/> &nbsp&nbsp&nbsp&nbsp SPV orphans removed!<%
    }
    if (enc.hasSpotImage || enc.hasRightSpotImage) {
      %> <br/> &nbsp&nbsp&nbsp&nbsp Spot images found!<%
    }

    if (uploading) {
      enc.nukeAllSpots();
    }

    nBadFound++;
    if (uploading) {
      myShepherd.commitDBTransaction();
      myShepherd.beginDBTransaction();
    }

  }



  // end of "every encounter" loop
}
%>
</br><%=nBadFound%> bad images found

<%
myShepherd.commitDBTransaction();
	myShepherd.closeDBTransaction();
	myShepherd=null;
%>
</br> bad enc IDS: [
<% for (String badID: badEncs) {
  %><%=badID%>,&nbsp<%
}
%>
]
</br>
<p>Done successfully!</p>
<p><%=numIssues %> issues found.</p>


<%
 // I'm going to test the ArrayList<String> literal here




%>
</br>stringified badIDs: <%=Arrays.toString(badIDLiteralsArray)%>
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
