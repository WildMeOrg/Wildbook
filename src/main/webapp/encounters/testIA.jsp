<%@ page contentType="text/html; charset=iso-8859-1" language="java"
         import="org.ecocean.servlet.ServletUtilities,
org.ecocean.identity.IdentityServiceLog, org.ecocean.media.MediaAsset,
java.util.ArrayList,org.ecocean.Annotation, org.ecocean.Encounter,org.json.JSONArray, org.json.JSONObject,java.net.URL, org.ecocean.servlet.ServletUtilities, org.ecocean.identity.IBEISIA,
org.ecocean.identity.IdentityServiceLog, org.dom4j.Document, org.dom4j.Element,org.dom4j.io.SAXReader, org.ecocean.*, org.ecocean.grid.MatchComparator, org.ecocean.grid.MatchObject, java.io.File, java.util.Arrays, java.util.Iterator, java.util.List, java.util.Vector, java.nio.file.Files, java.nio.file.Paths, java.nio.file.Path" %>
<%

String context="context0";
context=ServletUtilities.getContext(request);

//handle some cache-related security
response.setHeader("Cache-Control", "no-cache"); //Forces caches to obtain a new copy of the page from the origin server
response.setDateHeader("Expires", 0); //Causes the proxy cache to see the page as "stale"
response.setHeader("Pragma", "no-cache"); //HTTP 1.0 backward compatibility

//let's set up references to our file system components
String rootDir = getServletContext().getRealPath("/");
File webappsDir = new File(rootDir).getParentFile();
File dataDir = new File(webappsDir, CommonConfiguration.getDataDirectoryName(context));
File encountersDir=new File(dataDir.getAbsolutePath()+"/encounters");

Shepherd myShepherd = new Shepherd(context);
myShepherd.setAction("testIA.jsp"); 

ArrayList<Annotation> anns = new ArrayList<Annotation>();
Iterator<Annotation> annIt = myShepherd.getAllAnnotationsNoQuery();
Annotation thisAnn = null;
while (annIt.hasNext()) {
	thisAnn = annIt.next();
	anns.add(thisAnn);
}

JSONObject result = new JSONObject();

result = IBEISIA.sendAnnotations(anns);

boolean stop = false;
int seconds = 1;
while (result == null && stop == false) {
	Thread.sleep(1000);
	System.out.println("Waiting ... "+seconds+" seconds elapsed...");
	seconds+=1;
	if (seconds == 1200) {
		stop = true;
	}
} 

System.out.println(result.toString());





