<%@ page language="java"
     import="org.ecocean.Shepherd,
org.ecocean.Util,
org.ecocean.servlet.ServletUtilities,
org.ecocean.CommonConfiguration,
org.json.JSONObject,
java.util.ArrayList,
org.ecocean.media.*
              "
%>




<%

/*
this can use a little nginx magic to allow something like /imagedl/MEDIA_ASSET_ID/some_file_name.jpg to deliver
the *master* image associated with some media asset.  nice for downloading as the original filename.

TODO - should we require the user be logged in here?  i guess that could also be stopped with shiro

	location ~ /imagedl/(.+)/(.+) {
		include proxy_params;
		proxy_pass http://tomcat/dl.jsp?id=$1;
	}

*/

String context = ServletUtilities.getContext(request);

//we bail if not enabled
if (!Util.booleanNotFalse(CommonConfiguration.getProperty("encounterGalleryDownloadLink", context))) {
    response.setContentType("text/html");
    response.setStatus(401);
    out.println("<h1>401 no access</h1>");
    return;
}

Shepherd myShepherd = new Shepherd(context);

String ddir = CommonConfiguration.getDataDirectoryName(context);

String idString = request.getParameter("id");
int id = 0;
if (idString != null) id = Integer.parseInt(idString);
if (id < 1) {
    response.setContentType("text/html");
    response.setStatus(404);
    out.println("<h1>404 Not found</h1>");
    return;
}

MediaAsset ma = MediaAssetFactory.load(id, myShepherd);
if (ma == null) {
    response.setContentType("text/html");
    response.setStatus(404);
    out.println("<h1>404 Not found</h1>");
    return;
}

Integer pid = ma.getParentId();
System.out.println("DL: " + ma + " -> " + pid);
if (pid != null) ma = MediaAssetFactory.load(pid, myShepherd);
if (ma == null) {
    response.setContentType("text/html");
    response.setStatus(404);
    out.println("<h1>404 Not found</h1>");
    return;
}

//TODO now, should we do the *safe* child or the *master* ????  going to go with master for now....
ArrayList<MediaAsset> masters = ma.findChildrenByLabel(myShepherd, "_master");
if ((masters == null) || (masters.size() < 1)) {
    response.setContentType("text/html");
    response.setStatus(404);
    out.println("<h1>404 Not found</h1>");
    return;
}

JSONObject p = masters.get(0).getParameters();
String path = null;
if (p != null) path = p.optString("path");
System.out.println("DL: " + path + " .... " + masters.get(0).webURL().toString());

if (path == null) {
    response.setContentType("text/html");
    response.setStatus(404);
    out.println("<h1>404 Not found</h1>");
    return;
}

response.setHeader("X-Accel-Redirect", "/" + ddir + "/" + path);

//response.setHeader("X-Accel-Redirect", masters.get(0).webURL().toString());
//response.setContentType("text/plain");
//out.println(masters.get(0).getParameters());
//out.println(masters.get(0).webURL());

%>
