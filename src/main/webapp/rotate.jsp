<%@ page contentType="text/plain; charset=utf-8" 
		language="java"
        import="org.ecocean.servlet.ServletUtilities,org.ecocean.*,
org.ecocean.media.*,
org.ecocean.ia.Task,

org.joda.time.DateTime,
org.ecocean.servlet.importer.ImportTask,

org.ecocean.movement.*,

java.net.URL,
java.util.Vector,
java.util.ArrayList,
org.json.JSONObject,
org.json.JSONArray,
java.util.Properties" %><%!

private String cmd(String source, String target, int width, int height, String extra) {
    return "/usr/bin/convert -strip -quality 80 -resize '" + width + "x" + height + ">' " + extra + " '" + source + "' '" + target + "'";
}

%><%

int id = Integer.parseInt(request.getParameter("id"));
String extra = request.getParameter("extra");
if (extra == null) extra = "";

String context = "context0";
Shepherd myShepherd = new Shepherd(context);
myShepherd.setAction("rotate.jsp");
myShepherd.beginDBTransaction();
MediaAsset ma = MediaAssetFactory.load(id, myShepherd);
if (ma == null) {
    out.println("bad id");
    return;
}
if (ma.getParentId() != null) ma = MediaAssetFactory.load(ma.getParentId(), myShepherd);

out.println("# root = " + ma);

String source = ma.localPath().toString();

for (MediaAsset k : ma.findChildren(myShepherd)) {
    if (k.hasLabel("_master")) {
        out.println("\n# " + k + "\n" + cmd(source, k.localPath().toString(), 4096, 4096, extra));
    } else if (k.hasLabel("_thumb")) {
        out.println("\n# " + k + "\n" + cmd(source, k.localPath().toString(), 100, 75, extra));
    } else if (k.hasLabel("_mid")) {
        out.println("\n# " + k + "\n" + cmd(source, k.localPath().toString(), 1024, 768, extra));
    } else if (k.hasLabel("_watermark")) {
        out.println("\n# " + k + "\n" + cmd(source, k.localPath().toString(), 250, 200, extra));
    }

}

/*            case "master":
                action = "maintainAspectRatio";
                width = 4096;
                height = 4096;
                break;
            case "thumb":
                if (!skipCropping) action = "maintainAspectRatio";
                width = 100;
                height = 75;
                break;
            case "mid":
                if (!skipCropping) action = "maintainAspectRatio";
                width = 1024;
                height = 768;
                break;
            case "watermark":
                if (!skipCropping) action = "maintainAspectRatio";
                action = "watermark";
                width = 250;
                height = 200;
                break;
*/
myShepherd.rollbackDBTransaction();
myShepherd.closeDBTransaction();


%>
