<%@ page contentType="text/html; charset=utf-8" language="java"
     import="org.ecocean.*,
java.util.Map,
java.io.BufferedReader,
java.io.IOException,
java.io.InputStream,
java.io.InputStreamReader,
java.io.File,
org.json.JSONObject,

org.ecocean.servlet.ServletUtilities,
org.ecocean.media.*
              "
%>




<%

Shepherd myShepherd=null;
myShepherd = new Shepherd("context0");
myShepherd.beginDBTransaction();

String rootDir = getServletContext().getRealPath("/");
String baseDir = ServletUtilities.dataDir("context0", rootDir);

//first, load your AssetStore.  change the type accordingly, as well as the ID
int id = 999;
AssetStore as = null;
try {
	as = ((AssetStore) (myShepherd.getPM().getObjectById(myShepherd.getPM().newObjectIdInstance(AssetStore.class, id), true)));
} catch (Exception ex) {
	out.println("<h1>error loading AssetStore id=" + id + "</h1>");
	return;
}

out.println("<h2>" + as + "</h2><p>original configuration: <b>" + as.getConfig() + "</b></p>");


// here is where you set the new values you want!  note this varies by AssetStore type, so be careful?
AssetStoreConfig newConfig = new AssetStoreConfig();
newConfig.put("root", "/var/lib/tomcat7/webapps/wildbook_data_dir");
newConfig.put("webroot", "http://example.com/wildbook_data_dir");

out.println("<p>new configuration: <b>" + newConfig + "</b></p>");


if (request.getParameter("execute") != null) {
	System.out.println("editAssetStore.jsp changing " + as + " config from [" + as.getConfig() + "] to [" + newConfig + "]");
	as.setConfig(newConfig);
	out.println("<p><b>success</b>: " + as.getConfig() + "</p>");
	myShepherd.commitDBTransaction();

} else {
	out.println("<p>acceptable?  <a href=\"?execute\">execute changes</a></p>");
	myShepherd.rollbackDBTransaction();
}

%>

