<%@ page contentType="text/plain; charset=utf-8" language="java"
     import="org.ecocean.*,
org.ecocean.servlet.ServletUtilities,
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
JSONObject rtn = new JSONObject();

if (parent == null) {
    myShepherd.rollbackDBTransaction();
    rtn.put("success", false);
    rtn.put("error", "invalid MediaAsset id=" + id);
} else {
    parent.redoAllChildren(myShepherd);
    myShepherd.rollbackDBTransaction();
    rtn.put("success", true);
} 

out.println(rtn.toString(4));
%>

