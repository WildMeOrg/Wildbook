<!DOCTYPE html PUBLIC "-//W3C//DTD XHTML 1.0 Transitional//EN"
        "http://www.w3.org/TR/xhtml1/DTD/xhtml1-transitional.dtd">
<%@ page contentType="text/html; charset=utf-8" language="java" import="org.joda.time.LocalDateTime,org.joda.time.DateTime,
org.joda.time.format.DateTimeFormatter,
org.joda.time.format.ISODateTimeFormat,java.net.*,
org.ecocean.grid.*,
java.io.*,java.util.*, java.io.FileInputStream, java.io.File, java.io.FileNotFoundException, org.ecocean.*,org.ecocean.servlet.*,org.ecocean.media.*,javax.jdo.*, java.lang.StringBuffer, java.util.Vector, java.util.Iterator, java.lang.NumberFormatException, org.json.JSONObject"%>

<%!
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
    } catch (Exception e) {
      System.out.println("ERROR: getBestIndex on input ("+millis+", "+timeRankMap+", "+tolerance+")");
      e.printStackTrace();
    }
    return bestIndex;
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
int numPhotosMatched = 0;
int numPhotosNotMatched = 0;
boolean committing=true;
Map<String,String> dirToIndivName = new HashMap<String,String>();
dirToIndivName.put("testing!!!!!!!!!","testIndy");
dirToIndivName.put("CHISPAS", "Chispa");
dirToIndivName.put("DALILA", "Dalila?");
dirToIndivName.put("ELECTRA", "Electro");
dirToIndivName.put("Era", "Eral");
dirToIndivName.put("GALA", "Galo");
dirToIndivName.put("Germanal", "German");
dirToIndivName.put("HEROE", "Felina");
dirToIndivName.put("Hada", "Charqueña");
dirToIndivName.put("JALEA", "Jaleo");
dirToIndivName.put("JUNCOSA", "Junco");
dirToIndivName.put("Jacana","Jacara");
dirToIndivName.put("Jason","Jasione");
dirToIndivName.put("Listo","Litos");
//dirToIndivName.put("","");
%><p>Directory name to Individual name map: <%=dirToIndivName %> <%
try {
	String rootDir = getServletContext().getRealPath("/");
	String baseDir = ServletUtilities.dataDir(context, rootDir).replaceAll("dev_data_dir", "wildbook_data_dir");
  //String pathToUpdateFile="C:\\splash\\CRC SPLASHID additional sightings.mdb";
  //String rootDir="C:/apache-tomcat-8.0.24/webapps";
  String lynxPhotoPath="/data/lynx_photos/individuals";
  //String rootURL="http://localhost:8080";
  String rootURL="http://lynx.wildbook.org";
  //String splashImagesDirPath="C:/Users/jholmber/Dropbox/RingedSeal/DISCOVERY_DATA";
  //String splashImagesDirPath="/data/RingedSeal/DISCOVERY_DATA";
  String urlToThumbnailJSPPage=rootURL+"wildbook/resetThumbnail.jsp";
  String assetStorePath="/data/wildbook_data_dir";
  String assetStoreURL="http://lynx.wildbook.org/wildbook_data_dir";
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
  <p>Lynx Photo Path = <%=lynxPhotoPath%></p>
  <p>Asset Store URL = <%=assetStoreURL%></p>
  <p>Encounters Dir = <%=encountersDirPath%></p>
  <%
	Iterator allEncs=myShepherd.getAllEncounters();
  File lynxDir = new File(lynxPhotoPath);
  %><p>Lynx Dir is directory: <%=lynxDir.isDirectory()%></p> <%
  if (lynxDir.isDirectory()) {
    String[] subDirs = lynxDir.list();
    %><p>num subdirs: <%=subDirs.length%></p><%
    %><p>lynx directory contents:<ul><%
    if (committing) myShepherd.beginDBTransaction();
    for (String subDir : subDirs) {
      String indivName = Util.utf8ize(subDir);
      MarkedIndividual indy = myShepherd.getMarkedIndividualWithNameJostling(indivName, dirToIndivName);
      boolean isIndividual = (indy!=null);
      %><%=subDir%> isIndividual = <%=isIndividual%><%
      File indivDir = new File(lynxDir, subDir);
      if (!indivDir.isDirectory()) {
        %><p>NON-DIRECTORY: <%=subDir%></p><%
        continue;
      }
//      if (!isIndividual) continue;
      if (!isIndividual) {
        if (isDirectoryWithFiles(indivDir)) nonIndividualDirectories.add(subDir);
        // make an annotation for each photo
        // make an encounter for each annotation
        // make an individual from the encounters
        continue;
      }
      Encounter[] sortedEncs = indy.getDateSortedEncounters();
      Map<Integer, Long> encTimeMap = new HashMap<Integer, Long>();
      %><%=subDir%> encounter time map: <ul><%
      for (int i = 0; i < sortedEncs.length; i++) {
        Long thisMillis = sortedEncs[i].getDateInMilliseconds();
        if (thisMillis == null) continue;
        encTimeMap.put(new Integer(i), thisMillis);
        DateTime thisDT = new DateTime(thisMillis);
        %><li><%=i%>: <%=thisMillis%> (<%=thisDT%>)</li><%
      }
      %></ul><%
      int numEncs = 0;
      if (sortedEncs!=null) numEncs = sortedEncs.length;
      %><li><%=subDir%> is a directory, isIndividual=<%=isIndividual%> with <%=numEncs%> encounters and contents<ul><%
      String[] indivPhotos = indivDir.list();
      for (String photoName : indivPhotos) {
        File photo = new File(indivDir, photoName);
        %><li><%=photoName%> is file: <%= !photo.isDirectory()%><ul><%
        if (photo.isDirectory()) continue;
  // unindenting for legibility: here is central code block where we
  // will transform photo into a MediaAsset, then find the corresponding Encounter and attach to it.
  JSONObject params = as.createParameters(photo);
  MediaAsset ma = null;
  Long millisModified = null;
  DateTime modified = null;
  //params.put("path", photo.getAbsolutePath());
  %><li>params = <%=params.toString()%></li><%
  try {
    ma = new MediaAsset(as, params);
    %><li>Created a MediaAsset with parameters <%=ma.getParameters().toString()%></li><%
  } catch (Exception e) {
    %><li>Was NOT able to create a MediaAsset!</li><%
  }
  try {
    millisModified = photo.lastModified();
    modified = new DateTime(millisModified);
    %><li>parsed DateTime = <%=modified%></li><%
  } catch (Exception e) {
    %><li>Was NOT able to parse DateTime!</li><%
  }
  if (millisModified == null || ma == null) continue;
  ma.addLabel("_original");
  if (committing) {
    ma.copyIn(photo);
    myShepherd.getPM().makePersistent(ma);
    ma.updateStandardChildren(myShepherd);
    ma.updateMinimalMetadata();
  }
  Annotation ann = new Annotation("Lynx pardinus", ma);
  if (committing) {
    myShepherd.storeNewAnnotation(ann);
  }
  if (ma.getDateTime()==null) ma.setUserDateTime(modified);
  Long dateMillis = null;
  if (ma.getDateTime()!=null) dateMillis = ma.getDateTime().getMillis();
  %><li>and DateTime <%=ma.getDateTime()%> (in millis: <%=dateMillis%>)</li><%
  int bestIndex = getBestIndex(dateMillis, encTimeMap);
  Long bestEncMillis = null;
  DateTime bestEncDateTime = null;
  if (bestIndex>=0) bestEncMillis = encTimeMap.get(new Integer(bestIndex));
  if (bestEncMillis!=null) {
    bestEncDateTime = new DateTime(bestEncMillis);
    numPhotosMatched++;
    // attach media asset to encounter
    Encounter matchedEnc = sortedEncs[bestIndex];
    %><li>Adding to encounter number <%=matchedEnc.getCatalogNumber()%></li><%
    if (committing) matchedEnc.addAnnotation(ann);
  } else {
    numPhotosNotMatched++;
    Encounter newEnc = new Encounter(ann);
    newEnc.setIndividualID(indy.getIndividualID());
    if (committing) myShepherd.storeNewEncounter(newEnc, Util.generateUUID());
    if (committing) indy.addEncounter(newEnc, context);
  }
  %><li>Encounter time map index match:<%=bestIndex%>; bestEncMillis = <%=bestEncMillis%>; bestEncDateTime = <%=bestEncDateTime%></li><%
  %></ul></li><%
      }
      %></ul></li><%
      if (committing) {
        numFixes++;
        myShepherd.commitDBTransaction();
        myShepherd.beginDBTransaction();
      }
    } // end for subDir in lynxDir.subDirs
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