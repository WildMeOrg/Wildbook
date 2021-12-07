<%@ page contentType="text/html; charset=utf-8" language="java"
   import="org.ecocean.*,
java.util.Map,
java.util.List,
java.util.ArrayList,
java.util.Iterator,
java.util.Collection,
java.util.Vector,
java.io.BufferedReader,
java.io.IOException,
java.io.InputStream,
java.io.InputStreamReader,
org.ecocean.identity.IBEISIA,
java.io.File,
org.datanucleus.api.rest.orgjson.JSONObject,

org.ecocean.media.*,
javax.jdo.Query
            "
%>




<%


// this is kind of meant to be hand-held a bit (and currently is very spot-specific for whaleshark.org)

String context = "context0";
Shepherd myShepherd = new Shepherd(context);
String urlLoc = "http://" + CommonConfiguration.getURLLocation(request);

Query query = myShepherd.getPM().newQuery("javax.jdo.query.SQL", "SELECT \"ID\", \"PARENTID\" FROM \"MEDIAASSET\" WHERE \"DERIVATIONMETHOD\" like '%spotTool%' ORDER BY \"ID\"");
query.setClass(MediaAsset.class);
List<MediaAsset> mas = (List<MediaAsset>) query.execute();

ArrayList<MediaAsset> sendMas = new ArrayList<MediaAsset>();
ArrayList<Annotation> sendAnns = new ArrayList<Annotation>();
List<String> viewpoints = new ArrayList<String>();

int count = 0;
for (MediaAsset spot : mas) {
    count++;
    if (count < 30) continue;
    if (count > 50) break;
    if (spot.getParentId() == null) continue;
    MediaAsset parent = MediaAssetFactory.load(spot.getParentId(), myShepherd);
    if (parent == null) continue;
    ArrayList<Annotation> anns = parent.getAnnotations();
    if ((anns == null) || (anns.size() != 1)) continue;
    Annotation ann = anns.get(0);
    String name = ann.findIndividualId(myShepherd);
    if (name == null) continue;
    MarkedIndividual indiv = myShepherd.getMarkedIndividual(name);
    String sex = null;
    if (indiv != null) sex = indiv.getSex();
    sendMas.add(parent);
    sendAnns.add(ann);
    String facing = null;
    if (spot.hasLabel("_spotRight")) {
        facing = "right";
    } else if (spot.hasLabel("_spotLeft")) {
        facing = "left";
    }
    out.println("<hr /><p>(" + count + ") " + parent + " [" + facing + "]</p><p>" + spot + "</p><b>[" + name + "]</b> " + ann);
    viewpoints.add(facing);
//out.println("<p style=\"color: blue;\">" + parent.getKeywords() + "</p><p>" + spot.getLabels() + "</p>");
    System.out.println(count + ") " + facing + " -> [" + name + "] " + parent);
}

out.println("<hr />" + IBEISIA.sendMediaAssets(sendMas, context));
out.println("<hr />" + IBEISIA.sendAnnotations(sendAnns, context));
for (int i = 0 ; i < viewpoints.size() ; i++) {
    out.println("<p>" + sendAnns.get(i).getId() + " -> " + viewpoints.get(i) + "</p>");
    IBEISIA.iaSetViewpointForAnnotUUID(sendAnns.get(i).getId(), viewpoints.get(i), context);
}

%>
