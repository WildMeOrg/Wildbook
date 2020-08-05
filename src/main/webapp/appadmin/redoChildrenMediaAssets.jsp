<%@ page contentType="text/plain; charset=utf-8" language="java"
     import="org.ecocean.*,
org.ecocean.media.*,
org.json.JSONObject
              "
%>




<%

String id = request.getParameter("id");
String context = ServletUtilities.getContext(request);
Shepherd myShepherd = new Shepherd(context);
myShepherd.setAction("redoChildrenMediaAssets.jsp");
myShepherd.beginDBTransaction();
MediaAsset parent = MediaAssetFactory.load(Integer.parseInt(id), myShepherd);

if (parent == null) {
    myShepherd.rollbackDBTransaction();
    out.println("invalid MediaAsset id=" + id);
} else {
    parent.redoAllChildren(Shepherd myShepherd) throws IOException {
    myShepherd.rollbackDBTransaction();
    out.println("worked?");
} 
%>

