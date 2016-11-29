<!DOCTYPE html PUBLIC "-//W3C//DTD XHTML 1.0 Transitional//EN"
        "http://www.w3.org/TR/xhtml1/DTD/xhtml1-transitional.dtd">
<%@ page contentType="text/html; charset=utf-8" language="java" import="org.joda.time.LocalDateTime,org.joda.time.DateTime,
org.joda.time.format.DateTimeFormatter,
org.joda.time.format.ISODateTimeFormat,java.net.*,
org.ecocean.grid.*,
java.io.*,java.util.*, java.io.FileInputStream, java.io.File, java.io.FileNotFoundException, org.ecocean.*,org.ecocean.servlet.*,org.ecocean.media.*,javax.jdo.*, java.lang.StringBuffer, java.util.Vector, java.util.Iterator, java.lang.NumberFormatException, org.json.JSONObject"%>

<%!


  public String getIndivNameFromDirName(String fishDirName) {
    if (fishDirName==null) return null;
    return fishDirName.split("_")[0].trim();
  }

  public int getBestIndex(Long millis, Map<Integer, Long> timeRankMap, long tolerance) {

    long bestDistance = Long.MAX_VALUE;
    int bestIndex = -1;
    if (millis == null) return bestIndex;
    try {
    for (Integer index : timeRankMap.keySet()) {

      long diff = Math.abs(millis.longValue() - timeRankMap.get(index).longValue());

      if ( diff <= tolerance && diff < bestDistance) {
        bestDistance = diff;
        bestIndex = index.intValue();
      }
    }
    }
    catch (Exception e) {
      System.out.println("ERROR: getBestIndex on input ("+millis+", "+timeRankMap+", "+tolerance+")");
      e.printStackTrace();
    }

    return bestIndex;

  }

  public void parseFishFolder(File fishDir, String indID, Shepherd myShepherd, boolean committing, AssetStore as, JspWriter out, HttpServletRequest request) throws IOException {

    if (!fishDir.isDirectory()) return;

    String dirName = fishDir.getName();

    boolean isTopLevel = dirName.toLowerCase().contains("fish");

    if (isTopLevel && indID==null) indID = getIndivNameFromDirName(dirName);

    boolean isLeft = dirName.toLowerCase().contains("left");
    boolean isRight = dirName.toLowerCase().contains("right");
    boolean isSided = (isLeft || isRight);

    out.println("<p>Parsing indiv. "+indID+" in Folder: "+dirName+"</p>");
    out.println("<p>isTopLevel="+isTopLevel+" isLeft="+isLeft+" isRight="+isRight+" isSided="+isSided+"</p><ul>");

    String indivName = fishDir.getName().split("_")[0].trim();
    String[] dirContents = fishDir.list();

    for (String fname : dirContents) {

      File subFile = new File(fishDir, fname);
      out.println("<li>"+fname+" isPic = "+isPic(subFile)+"</li>");

      if (isPic(subFile)) try { parseFishPic(subFile, indID, isSided, isLeft, myShepherd, committing, as, out, request);
      } catch (Exception e) {
        out.println("<li>ERROR on parseFishPic("+fname+")</li></ul>");
        e.printStackTrace();
      }
      else parseFishFolder(subFile, indID, myShepherd, committing, as, out, request);

    }
    out.println("</ul> <p>Done setting up fish folder "+dirName+"</p>");
  }

  public boolean isPic(File maybePic) throws IOException {
    String fname = maybePic.getName();
    int lastDot = fname.lastIndexOf(".");
    if (lastDot < 0 || lastDot == (fname.length()-1)) return false;
    String ext = maybePic.getName().substring(lastDot).toLowerCase();
    return (ext.equals(".png") || ext.equals(".jpg") || ext.equals(".jpeg"));

  }

  public void parseFishPic(File fishPic, String indID, boolean isSided, boolean isLeft, Shepherd myShepherd, boolean committing, AssetStore as, JspWriter out, HttpServletRequest request) throws IOException, org.datanucleus.api.rest.orgjson.JSONException {

    String sideString = isSided ? (isLeft ? "left" : "right") : "none";

    out.println("<ul>");

    out.println("<li>Parsing fish "+indID+" side = "+sideString+" in pic "+fishPic.getName()+"</li>");


    JSONObject params = as.createParameters(fishPic);
    MediaAsset ma = new MediaAsset(as, params);
    ma.addLabel("_original");
    if (committing) {
      ma.copyIn(fishPic);
      myShepherd.getPM().makePersistent(ma);
      ma.updateStandardChildren(myShepherd);
      ma.updateMinimalMetadata();
    }

    addMaToInd(ma, indID, myShepherd, committing, out, request);

    org.datanucleus.api.rest.orgjson.JSONObject maJson = new org.datanucleus.api.rest.orgjson.JSONObject();
    maJson = ma.sanitizeJson(request, maJson);
    out.println("<li>Media Asset = "+maJson+"</li>");



    out.println("</ul>");


  }

  public void addMaToInd(MediaAsset ma, String indID, Shepherd myShepherd, boolean committing, JspWriter out, HttpServletRequest request) throws IOException, org.datanucleus.api.rest.orgjson.JSONException {

    String species = "Stereolepis gigas";
    Annotation ann = new Annotation(species, ma);
    if (committing) myShepherd.getPM().makePersistent(ann);
    Encounter enc = new Encounter(ann);
    enc.setIndividualID(indID);
    if (committing) myShepherd.storeNewEncounter(enc, Util.generateUUID());

    out.println("<li>Encounter = "+enc.sanitizeJson(request, new org.datanucleus.api.rest.orgjson.JSONObject())+"</li>");

    if (myShepherd.isMarkedIndividual(indID)) {
      MarkedIndividual ind = myShepherd.getMarkedIndividual(indID);
      ind.addEncounter(enc, "context0");
      if (committing) {
        myShepherd.commitDBTransaction();
        myShepherd.beginDBTransaction();
      }
    } else {

      MarkedIndividual ind = new MarkedIndividual(indID, enc);
      if (committing) myShepherd.storeNewMarkedIndividual(ind);

    }
  }


  public int getBestIndex(Long millis, Map<Integer, Long> timeRankMap) {
    long oneWeekInMilliseconds = 604800000;
    return getBestIndex(millis, timeRankMap, oneWeekInMilliseconds * 10);
  }

  public boolean isDirectoryWithFiles(File dir) {
    try {
      return (dir.isDirectory() && dir.list().length > 0);
    } catch (Exception e) {}
    return false;
  }

%>


<%

String context="context0";
context=ServletUtilities.getContext(request);

Shepherd myShepherd=new Shepherd(context);



%>

<html>
<head>
<title>Import Local Media</title>

</head>


<body>
<h1>Importing Local Media</h1>

<ul>
<%

myShepherd.beginDBTransaction();

int numFixes=0;
List<String> nonIndividualDirectories = new ArrayList<String>();

int numPhotosMatched    = 0;
int numPhotosNotMatched = 0;
boolean committing = true;




try {

	String rootDir = getServletContext().getRealPath("/");
	String baseDir = ServletUtilities.dataDir(context, rootDir).replaceAll("dev_data_dir", "wildbook_data_dir");

  //String pathToUpdateFile="C:\\splash\\CRC SPLASHID additional sightings.mdb";
  //String rootDir="C:/apache-tomcat-8.0.24/webapps";

  String bassPhotoPath="/data/spottedbass";
  //String rootURL="http://localhost:8080";
  String rootURL="http://35.164.110.53:8080/wildbook";
  //String splashImagesDirPath="C:/Users/jholmber/Dropbox/RingedSeal/DISCOVERY_DATA";
  //String splashImagesDirPath="/data/RingedSeal/DISCOVERY_DATA";
  String urlToThumbnailJSPPage=rootURL+"wildbook/resetThumbnail.jsp";
  String assetStorePath="/data/wildbook_data_dir";
  String assetStoreURL="http://35.164.110.53:8080/wildbook_data_dir";

  //AssetSyore work
  ////////////////begin local //////////////
  if (committing) myShepherd.beginDBTransaction();
  LocalAssetStore as = new LocalAssetStore("WWF-Lynx-Asset-Store", new File(assetStorePath).toPath(), assetStoreURL, true);
  if (committing) myShepherd.getPM().makePersistent(as);
  if (committing) myShepherd.commitDBTransaction();
////////////////end local //////////////


String encountersDirPath=assetStorePath+"/encounters";


  %>
  <p>Root Dir = <%=rootDir%></p>
  <p>Base Dir = <%=baseDir%></p>
  <p>Asset Store Path = <%=assetStorePath%></p>
  <p>Lynx Photo Path = <%=bassPhotoPath%></p>
  <p>Asset Store URL = <%=assetStoreURL%></p>
  <p>Encounters Dir = <%=encountersDirPath%></p>
  <%



	Iterator allEncs=myShepherd.getAllEncounters();


  File bassDir = new File(bassPhotoPath);
  %><p>Lynx Dir is directory: <%=bassDir.isDirectory()%></p> <%
  if (bassDir.isDirectory()) {
    String[] subDirs = bassDir.list();
    %><p>num subdirs: <%=subDirs.length%></p><%
    %><p>bass directory contents:<%

    if (committing) myShepherd.beginDBTransaction();
    for (String subDir : subDirs) {

      String indivName = getIndivNameFromDirName(subDir);

      MarkedIndividual indy = myShepherd.getMarkedIndividualQuiet(indivName);
      boolean isIndividual = (indy!=null);

      %><p><%=indivName%> isIndividual = <%=isIndividual%></p><%


      File indivDir = new File(bassDir, subDir);
      if (!indivDir.isDirectory()) {
        %><p>NON-DIRECTORY: <%=subDir%></p><%
        continue;
      }
//      if (!isIndividual) continue;

      parseFishFolder(indivDir, null, myShepherd, committing, as, out, request);


      if (!isIndividual) {
        if (isDirectoryWithFiles(indivDir)) nonIndividualDirectories.add(subDir);
      }


      if (committing) {
        numFixes++;
        myShepherd.commitDBTransaction();
        myShepherd.beginDBTransaction();
      }

    } // end for subDir in bassDir.subDirs
    %></ul></p><%
  } else {

  }

	while(allEncs.hasNext()){

		Encounter enc=(Encounter)allEncs.next();
    if (committing) {
      myShepherd.commitDBTransaction();
      myShepherd.beginDBTransaction();
    }

	}
}
catch(Exception e){
	myShepherd.rollbackDBTransaction();
}
finally{
	myShepherd.closeDBTransaction();

}


java.util.Collections.sort(nonIndividualDirectories);

%>

</ul>
<div style="font-family:monospace">
<p>Done successfully: <%=numFixes %></p>
<p>Non Individual Directories: <ul><%
for (String dirName : nonIndividualDirectories) {
  %><li><%=dirName%></li><%
}
%></ul></p>
<p>Num Photos Matched: <%=numPhotosMatched %></p>
<p>Num Photos Not Matched: <%=numPhotosNotMatched %></p>
</div>

</body>
</html>
