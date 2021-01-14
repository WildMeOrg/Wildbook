<!DOCTYPE html PUBLIC "-//W3C//DTD XHTML 1.0 Transitional//EN"
        "http://www.w3.org/TR/xhtml1/DTD/xhtml1-transitional.dtd">
<%@ page contentType="text/html; charset=utf-8" language="java" import="org.joda.time.LocalDateTime,
org.joda.time.format.DateTimeFormatter,
org.joda.time.format.ISODateTimeFormat,java.net.*,java.io.*,java.util.*, java.io.FileInputStream, java.io.File, java.io.FileNotFoundException, org.ecocean.*,org.ecocean.servlet.*,org.ecocean.mmutil.FileUtilities,javax.jdo.*, java.lang.StringBuffer, java.util.Vector, java.util.Iterator, java.lang.NumberFormatException"%>

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

// some util functions
<%!
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





try {

  int nAdded = 0;
  int nDuplicates = 0;

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

     %>
     <%=text%><%


     boolean printEncs = false;
     boolean uploading = true;

    File[] listOfFiles = sharkImgs.listFiles();
    %> The list of shark image directories has been made. It contains <%=listOfFiles.length%> items. <%
    int loadLimit = 15000;

    for (int i = 0; i < listOfFiles.length && i<loadLimit; i++) {
      if (i==259) {
        %></br> 259 is <%=listOfFiles[i].getName()%><%
        continue;
      }

      if (listOfFiles[i].isFile()) {
        if(printEncs){%></br></br> File <%=listOfFiles[i].getName()%><%}
      } else if (listOfFiles[i].isDirectory()) {
        if(printEncs){%></br> Directory <%=listOfFiles[i].getName()%><%}



        File thisDir = listOfFiles[i];
        String dirName = cleanDirName(thisDir.getName());
        if(printEncs){%> </br> cleaned name = <%=dirName%><%}

        String locString = dirName.substring(0,2);
        if(printEncs){%> </br>locationID = <%=locString%> <%}

        String photographer = dirName.substring(dirName.length()-3,dirName.length());
        if(printEncs){%> </br>photographer = <%=photographer %> <%}

        String yearStr = dirName.substring(2,4);
        int year = 0;
        try {year = Integer.parseInt(yearStr);}
        catch (NumberFormatException e) {}
        year+=2000;

        String monthStr = "";
        monthStr = dirName.substring(4,7);
        int month = -1;
        // you can't 'switch' on a String in our version of jsp!?!?
        if (monthStr.equals("JAN")) {month = 1;}
        else if(monthStr.equals("FEB")) {month = 2;}
        else if(monthStr.equals("MAR")) {month = 3;}
        else if(monthStr.equals("APR")) {month = 4;}
        else if(monthStr.equals("MAY")) {month = 5;}
        else if(monthStr.equals("JUN")) {month = 6;}
        else if(monthStr.equals("JUL")) {month = 7;}
        else if(monthStr.equals("AUG")) {month = 8;}
        else if(monthStr.equals("SEP")) {month = 9;}
        else if(monthStr.equals("OCT")) {month = 10;}
        else if(monthStr.equals("NOV")) {month = 11;}
        else if(monthStr.equals("DEC")) {month = 12;}

        String dayStr = dirName.substring(7,9);
        int day = 0;
        try {day = Integer.parseInt(dayStr);}
        catch (NumberFormatException e) {}


        if(printEncs){%> </br> = <%=" " %> <%}

        File[] leafFiles = thisDir.listFiles();
        if(printEncs){%>  contains <%=leafFiles.length%> items. <%}
        int nImages = 0;
        for (int j = 0; j < leafFiles.length; j++) {
          String fname = leafFiles[j].getName();
          // check to see if OK to import (jpg with corresponding .fgp)
          if (isJpg(fname) && leafFiles[j].isFile() && hasFgp(fname, thisDir)) {
            // OK, here is the parsing of the individual encounter
            if(printEncs){%></br>Processing image <%=fname%><%}
            nImages++;


            String encID = getEncID(fname);
            if(printEncs){%> </br> encID = <%=encID %> <%}

            if(myShepherd.isEncounter(encID)) {
              if(printEncs){%> </br> DUPLICATE ENCOUNTER <%}
              nDuplicates++;
              continue;
            }

            myShepherd.beginDBTransaction();
            Encounter enc = new Encounter();
            enc.setCatalogNumber(encID);
            //persist it
            if (uploading) {
              myShepherd.rollbackDBTransaction();
              myShepherd.storeNewEncounter(enc, encID);
              myShepherd.beginDBTransaction();
              System.out.println("\tEncounter added to DB.");
              enc.addComments("<p><em>" + request.getRemoteUser() + " on "
                  + (new java.util.Date()).toString() + "</em><br>"
                  + "batchUpload process set ID to" + encID +"</p>");
            }
            if (printEncs) {
              %> </br> encID = <%=enc.getEncounterNumber() %> <%
            }

            // bookkeeping
            enc.setIndividualID("Unassigned");
            enc.setState("unapproved");
            enc.setDynamicProperty("batch","true");

            String strOutputDateTime = ServletUtilities.getDate();
            enc.setDWCDateLastModified(strOutputDateTime);
            enc.setDWCDateAdded(strOutputDateTime);



            //let's create co-occurrences
            int encIDLength=encID.length();
            String occurrenceID=encID.substring(0,encIDLength-5);
            Occurrence occur=new Occurrence();
            if(myShepherd.isOccurrence(occurrenceID)){
              if (printEncs) {%> </br><%=enc.getEncounterNumber() %> has existing occurrence <%=occurrenceID%><%}
              if (uploading) {
                occur=myShepherd.getOccurrence(occurrenceID);
                occur.addEncounter(enc);
              }
            }
            else{
              if (printEncs) {%> </br><%=enc.getEncounterNumber() %> creates new occurrence <%=occurrenceID%><%}
              if (uploading) {
                occur=new Occurrence(occurrenceID, enc);
                myShepherd.commitDBTransaction();
                myShepherd.storeNewOccurrence(occur);
                myShepherd.beginDBTransaction();
              }
            }



            // add folder-wide fields
            enc.setLocationID(locString);
            enc.setYear(year);
            enc.addComments("<p><em>" + request.getRemoteUser() + " on " + (new java.util.Date()).toString() + "</em><br>" + "batchUpload process set year to " + year + ".</p>");
            if (printEncs) {
              %> </br> year = <%=enc.getYear() %> <%
            }

            enc.setMonth(month);
            enc.addComments("<p><em>" + request.getRemoteUser() + " on " + (new java.util.Date()).toString() + "</em><br>" + "atchUpload process set month to " + month + ".</p>");
            if (printEncs) {
              %> </br> month = <%=enc.getMonth() %> <%
            }

            enc.setDay(day);
            enc.addComments("<p><em>" + request.getRemoteUser() + " on " + (new java.util.Date()).toString() + "</em><br>" + "batchUpload process set day to " + day + ".</p>");
            if (printEncs) {
              %> </br> day = <%=enc.getDay() %> <%
            }

            char sexChar = encID.charAt(9);
            String sex = "unknown";
            if (sexChar=='M') {sex = "male";}
            else if (sexChar=='F') {sex = "female";}
            enc.setSex(sex);
            if(printEncs){%> </br>sexChar = <%=sexChar %> and sex = <%=enc.getSex()%><%}


            String hook = encID.substring(10,11);
            enc.setDynamicProperty("Hookmark", hook);
            enc.addComments("<p><em>" + request.getRemoteUser() + " on "
                + (new java.util.Date()).toString() + "</em><br>"
                + "batchUpload process set hookmark to "
                + hook + ".</p>");
            if(printEncs){%> </br>hookmark = <%=enc.getDynamicPropertyValue("Hookmark") %><%}

            String flank = (encID.substring(encID.length()-4,encID.length()-3));
            boolean isLeftFlank = (flank.equals("L"));
            enc.setDynamicProperty("flank", flank);
            enc.addComments("<p><em>" + request.getRemoteUser() + " on " + (new java.util.Date()).toString() + "</em><br>" + "batchUpload process set flank to " + flank + ".</p>");
            if(printEncs){%> </br>flank = <%=enc.getDynamicPropertyValue("flank")%><%}

            // now for the messy stuff: linking jpg & fpg

            boolean hasFgp = hasFgp(fname, thisDir);
            if(printEncs) {%></br> hasFgp = <%=hasFgp%><%}
            File fgpFile = new File(thisDir, fname.replaceAll(".jpg",".fgp"));
            if(printEncs) {%><%=("</br> fgp file name " + fgpFile.getName())%><%}

            FileInputStream fStream = new FileInputStream(fgpFile);

            DataInputStream spotData = new DataInputStream(new BufferedInputStream(fStream));

            SinglePhotoVideo pictureSPV = new SinglePhotoVideo();
            boolean loadPicture = false;

            try {
              checkFileType(spotData);

              ArrayList<SuperSpot> reference_spots = loadFgpSpots(spotData, 3, false, false);

              // The next value in the fgp file encodes the number of points.
              int nPoints = spotData.readInt();
              // seems like, from catalina.out, there are spacing zeroes after parsing N
              ArrayList<SuperSpot> spots = loadFgpSpots(spotData, nPoints, false, true);
              // are normed_spots needed for anything?
              // ArrayList<SuperSpot> normed_spots = loadFgpSpots(spotData, nPoints);
              // Now load spots into encounter object; defaults to left side if no flank info
              if ((flank!=null)&&(flank.equals("R"))){
                enc.setNumRightSpots(nPoints);
                if(printEncs){%> <br/>n R-spots: <%=nPoints%><%;}
                enc.setRightReferenceSpots(reference_spots);
                if(printEncs){%> <br/>R-reference spots: <%=printSpotList(reference_spots)%><%}
                enc.setRightSpots(spots);
                if(printEncs){%> <br/>R-spots: <%=printSpotList(spots)%><%}
                enc.hasRightSpotImage = true;
              }
              else {
                enc.setNumLeftSpots(nPoints);
                if(printEncs){%> <br/>n spots: <%=nPoints%><%;}
                enc.setLeftReferenceSpots(reference_spots);
                if(printEncs){%> <br/>reference spots: <%=printSpotList(reference_spots)%><%}
                enc.setSpots(spots);
                if(printEncs){%> <br/>spots: <%=printSpotList(spots)%><%}
                enc.hasSpotImage = true;
              }
              System.out.println("FGP file parsed and added to encounter.");


            } catch (Exception e) {
              %></br> exception parsing fgp on encounter <%=encID%><%
              System.out.println();
            } finally {
              if (spotData!=null) { spotData.close(); }
            }
            if (fStream!=null) { fStream.close(); }
            enc.addComments("<p><em>" + request.getRemoteUser() + " on " + (new java.util.Date()).toString() + "</em><br>" + "batchUpload process added spots from " + fname.replaceAll(".jpg",".fgp")+".</p>");

            // need to copy image
            try {
              File oldPicture = new File(thisDir, fname);
              String extractName;
              if ((flank!=null)&&(flank.equals("R"))){
                extractName = "extractRight"+encID+".jpg";
              }
              else {
                extractName = "extract"+encID+".jpg";
              }
              File encounterFolder = getEncDBFolder(shepherdDataDir, encID);
              File newPicture = new File(encounterFolder, encID+".jpg");
              File extractFile = new File(encounterFolder, extractName);
              if (!newPicture.exists()) {
                if(uploading){FileUtilities.copyFile(oldPicture, newPicture);}
                if (printEncs){%><br/>image copied to <%=newPicture.getCanonicalPath()%><%}
              } else {
                if (printEncs){%><br/>image copy already found in <%=newPicture.getCanonicalPath()%><%}
              }

              if (!extractFile.exists()) {
                FileUtilities.copyFile(oldPicture, extractFile);
              }

              if ((flank!=null)&&(flank.equals("R"))){
                enc.setRightSpotImageFileName(extractFile.getName());

              } else {
                enc.setSpotImageFileName(extractFile.getName());
              }

              pictureSPV = new SinglePhotoVideo(encID, newPicture);
              enc.addSinglePhotoVideo(pictureSPV);
              loadPicture = true;
              if (printEncs){%><br/>SPV image: <%=pictureSPV.getFilename()%><%}
              enc.addComments("<p><em>" + request.getRemoteUser() + " on " + (new java.util.Date()).toString() + "</em><br>" + "batchUpload process added photo " + pictureSPV.getFilename() + ".</p>");
            } catch (Exception e) {
              %><br/>EXCEPTION on image copy for encounter  <%=encID%><%
            }

            // refresh encounter's media assets
            String baseDir = shepherdDataDir.getCanonicalPath();
            System.out.println("\tRefreshing asset formats with baseDir = "+baseDir);
            try {
              if (uploading) {
                myShepherd.commitDBTransaction();
                %><br/>Storing encounter <%=encID%><%
              }

              if (loadPicture) {
                if (printEncs){%><br/>Refreshing asset formats with baseDir = <%=baseDir%><%}
                if (uploading) {
                  enc.refreshAssetFormats(context, baseDir, pictureSPV, true);
                }
                nAdded++;
              }
            }
            catch (Exception e) {
              %> <br/> Exception on enc.refreshAssetFormats on <%=encID%><%
              myShepherd.rollbackDBTransaction();
            }



            myShepherd.closeDBTransaction();
          }
          /*
          if (leafFiles[j].isFile()) {
            %><%=("</br> File " + fname)%><%
          }
          else if (leafFiles[j].isDirectory()) {
            %><%=("</br> Directory " + leafFiles[j].getName()+"</br>")%><%
          }
          */
        }

        %> </br></br></br>folder <%=i %></br>nImages = <%=nImages %> <%
        %> </br>nDuplicates = <%=nDuplicates %> <%
        %> </br>nAdded = <%=nAdded %> <%
      }
    }
    %> </br>nDuplicates = <%=nDuplicates %> <%
  }
  %> </br>_______________________________________
</br>FINAL</br>_______________________________________ <%
  %> </br>nDuplicates = <%=nDuplicates %> <%
  %> </br>nAdded = <%=nAdded %> <%

} catch (Exception e) {
   text += "</br> Exception: "+e.toString();
}

%>


</body>
</html>
