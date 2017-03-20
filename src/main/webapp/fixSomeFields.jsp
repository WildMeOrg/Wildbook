<!DOCTYPE html PUBLIC "-//W3C//DTD XHTML 1.0 Transitional//EN"
        "http://www.w3.org/TR/xhtml1/DTD/xhtml1-transitional.dtd">
<%@ page contentType="text/html; charset=utf-8" language="java" import="org.joda.time.LocalDateTime,
org.joda.time.format.DateTimeFormatter,
org.joda.time.format.ISODateTimeFormat,java.net.*,
org.ecocean.grid.*, org.ecocean.media.*,
java.io.*,java.util.*, java.io.FileInputStream, java.io.File, java.io.FileNotFoundException, org.ecocean.*,org.ecocean.servlet.*,javax.jdo.*, java.lang.StringBuffer, java.util.Vector, java.util.Iterator, java.lang.NumberFormatException"%>

<%

String context="context0";
context=ServletUtilities.getContext(request);

Shepherd myShepherd=new Shepherd(context);



%>

<html>
<head>
<title>Fix Some Fields</title>

</head>


<body>
<h1>Fixing some fields</h1>
<ul>
<%

myShepherd.beginDBTransaction();

int numFixes=0;

try {

	String rootDir = getServletContext().getRealPath("/");
	String baseDir = ServletUtilities.dataDir(context, rootDir).replaceAll("dev_data_dir", "caribwhale_data_dir");

  Iterator allEncounters=myShepherd.getAllEncountersNoQuery();

  boolean committing=false;


  // while(allEncounters.hasNext()){
  //
  //   Encounter enc=(Encounter)allEncounters.next();
  //
  //
  // 	numFixes++;
  //
  //   if (committing) {
  //     enc.setState("unapproved");
  // 		myShepherd.commitDBTransaction();
  // 		myShepherd.beginDBTransaction();
  //   }
  // }

  Iterator allMAs=myShepherd.getAllMediaAssets();
  while(allMAs.hasNext()){

		MediaAsset ma = (MediaAsset) allMAs.next();
    numFixes++;

    %><p>ma <%=ma.getId()%></p><%


    //LocalAssetStore as = (LocalAssetStore) ma.getStore();



    if (committing) {
      ma.updateStandardChildren(myShepherd);
      %><li>updated standard children./li><%

      ma.updateMinimalMetadata();
      %><li>updated minimal metadata./li><%

      myShepherd.commitDBTransaction();
      myShepherd.beginDBTransaction();
    }
    %></ul><%


	}


}
catch(Exception e){
	myShepherd.rollbackDBTransaction();
}
finally{
	myShepherd.closeDBTransaction();

}

%>

</ul>
<p>Done successfully: <%=numFixes %></p>

</body>
</html>
