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
<p><em>in the context <code><%=context%></code></em></p>
<ul>
<%

myShepherd.beginDBTransaction();

int numFixes=0;

try {

	String rootDir = getServletContext().getRealPath("/");
	String baseDir = ServletUtilities.dataDir(context, rootDir).replaceAll("dev_data_dir", "caribwhale_data_dir");

  Iterator allEncounters=myShepherd.getAllEncountersNoQuery();

  boolean committing=true;

  %>
  <h2>Context <%=context%> has AssetStores:</h2>
  <ul>
  <%

  List<AssetStore> stores = AssetStoreFactory.getStores(myShepherd);
  for (int i=0; i< stores.size(); i++) {
    AssetStore astore = stores.get(i);
    // cmon storeStr! cmon astore!
    String storeStr = astore.toString();
    %><li><%=storeStr%>:<ul><%
      LocalAssetStore localAss = (LocalAssetStore) astore;
      %><li>old id = <%=localAss.getId()%> </li>

      %><li>webRoot = <%=localAss.webRoot()%> </li>
    </ul></li> <%
  }



  %></ul>  <ul>
    <%
    String dataDirName = myShepherd.getDataDirectoryName();
    String dataDir = ServletUtilities.dataDir(context, rootDir);
    String urlLoc = ("http://" + CommonConfiguration.getURLLocation(request)).split("/wildbook")[0];


    String dataUrl = urlLoc + "/" + dataDirName;
    %>
    <li><p> dataDir = <%=dataDir%> and dataUrl = <%=dataUrl%></p>
    <%
    LocalAssetStore as = new LocalAssetStore("Default Local AssetStore mk III", new File(dataDir).toPath(), dataUrl, true);
    %><li>webRoot = <%=as.webRoot()%> </li><%

    if (committing) {
      myShepherd.beginDBTransaction();
      myShepherd.getPM().makePersistent(as);
      myShepherd.commitDBTransaction();
    }
    %><li>new id = <%=as.getId()%> </li>


    %></ul><%

  // while(allEncounters.hasNext()){
  //
  //   Encounter enc=(Encounter)allEncounters.next();
  //
  //
  // 	numFixes++;
  // 		myShepherd.beginDBTransaction();
  //   }
  // }

  /*
  Iterator allMAs=myShepherd.getAllEncounters();
  while(allMAs.hasNext()){

		Encounter ma = (Encounter) allMAs.next();
    numFixes++;

    %><p>enc <%=ma.getCatalogNumber()%><%
    ma.setState("approved");
    %> set state <%=ma.getState()%></p><%
    if (committing) {

      myShepherd.commitDBTransaction();
      myShepherd.beginDBTransaction();
    }
    %></ul><%

	}*/


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
