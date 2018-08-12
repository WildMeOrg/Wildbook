<!DOCTYPE html PUBLIC "-//W3C//DTD XHTML 1.0 Transitional//EN"
        "http://www.w3.org/TR/xhtml1/DTD/xhtml1-transitional.dtd">
<%@ page contentType="text/html; charset=utf-8" language="java" import="org.joda.time.LocalDateTime,
org.joda.time.format.DateTimeFormatter,
org.joda.time.format.ISODateTimeFormat,java.net.*,
org.ecocean.grid.*,org.ecocean.media.*,
java.io.*,java.util.*, java.io.FileInputStream, java.io.File, java.io.FileNotFoundException, org.ecocean.*,org.ecocean.servlet.*,javax.jdo.*, java.lang.StringBuffer, java.util.Vector, java.util.Iterator, java.lang.NumberFormatException"%>
<%

String context="context0";
context=ServletUtilities.getContext(request);

Shepherd myShepherd=new Shepherd(context);

// pg_dump -Ft sharks > sharks.out

//pg_restore -d sharks2 /home/webadmin/sharks.out


%>

<html>
<head>
<title>Fix Asset urls</title>

</head>


<body>

  <h1>FIXING ASSET URLS</h1>

<ul>
<%

myShepherd.beginDBTransaction();

//build queries

int numFixes=0;
boolean committing = false;

try{


	String rootDir = getServletContext().getRealPath("/");
	String baseDir = ServletUtilities.dataDir(context, rootDir).replaceAll("dev_data_dir", "caribwhale_data_dir");
	Iterator allEncs=myShepherd.getAllEncounters();

  Iterator allMediaAssets=myShepherd.getAllMediaAssets();


while(allMediaAssets.hasNext()){

	MediaAsset ma =(MediaAsset)allMediaAssets.next();

  //String url = ma.url;
  int id = ma.getId();
  AssetStore store = ma.getStore();
  URL oldUrl = store.webURL(ma);
  %><p>MediaAsset <%=id%> has AssetStore <%=store.toString() %> with webpath <%=oldUrl%></p><%

/*  if ((url != null) && url.contains("flukebook.org")) {

    String

  };
*/

	if(committing){
		numFixes++;

		myShepherd.commitDBTransaction();
		myShepherd.beginDBTransaction();

	}

}



%>




<%
}
catch(Exception ex) {

	System.out.println("!!!An error occurred on page fixSomeFields.jsp. The error was:");
	ex.printStackTrace();
	myShepherd.rollbackDBTransaction();


}
finally{

	myShepherd.closeDBTransaction();
	myShepherd=null;
}
%>

</ul>
<p>Done successfully: <%=numFixes %></p>
</body>
</html>
