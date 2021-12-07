<%@ page contentType="text/html; charset=utf-8" language="java"
     import="org.ecocean.*,
java.util.ArrayList,
java.util.List,
java.io.BufferedReader,
java.io.IOException,
java.io.InputStream,
java.io.InputStreamReader,
org.joda.time.DateTime,
java.io.File,
java.nio.file.Files,
org.json.JSONObject,
org.json.JSONArray,
org.apache.commons.lang3.StringUtils,
org.ecocean.movement.SurveyTrack,
org.ecocean.servlet.ServletUtilities,

org.ecocean.media.*
              "
%>




<%

String context = ServletUtilities.getContext(request);
Shepherd myShepherd = new Shepherd(context);
myShepherd.beginDBTransaction();

File dataFile = new File("/tmp/cr.data");
List<String> lines = Files.readAllLines(dataFile.toPath(), java.nio.charset.Charset.defaultCharset());
AssetStore astore = AssetStore.getDefault(myShepherd);
Keyword crKeyword = myShepherd.getOrCreateKeyword("CR Image");

String iaClass = "mantaCR";

int count = 0;
int skipped = 0;
int stopAfter = 10000;

int commitFrequency = 40;

for (String maFpath : lines) {
    if (maFpath == null) continue;
    String[] maFpathArray = maFpath.split("\t");
    if (maFpathArray.length < 2) continue;
    //out.println("<p>(" + maFpathArray[0] + ":" + maFpathArray[1] + ")</p>");
    int mId = 0;
    try {
        mId = Integer.parseInt(maFpathArray[0]);
    } catch (Exception ex) {}
    if (mId < 1) continue;

    JSONObject params = new JSONObject();
    params.put("path", maFpathArray[1]);
    MediaAsset exists = astore.find(params, myShepherd);
    if (exists != null) {
        System.out.println("crAnnot: skipping path=" + maFpathArray[1] + "; exists in " + exists);
        skipped++;
        System.out.println(skipped+" skipped so far");
        continue;
    }

    MediaAsset parent = MediaAssetFactory.load(mId, myShepherd);
    if (parent == null) continue;
    //this is making some big assumptions about the parent only having one annot to the encounter!
    ArrayList<Annotation> anns = parent.getAnnotations();
    if (Util.collectionIsEmptyOrNull(anns)) continue;
    Encounter enc = null;
    for (Annotation ann : anns) {
        enc = ann.findEncounter(myShepherd);
        if (enc != null) break;
    }
    if (enc == null) {
        System.out.println("crAnnot: failed to find enc for " + parent);
        continue;
    }

    MediaAsset ma = new MediaAsset(astore, params);
    ////ma.setParentId(mId);  //no, this is TOO wacky
    ma.addDerivationMethod("crParentId", mId);  //lets do this instead
    ma.addLabel("CR");
    ma.addKeyword(crKeyword);
    ma.updateMinimalMetadata();

    MediaAssetFactory.save(ma, myShepherd);
    System.out.println(count+" saved annots");
    Annotation ann = new Annotation(iaClass, ma);
    System.out.println(" created annot");
    ann.setMatchAgainst(true);
    ann.setIAClass(iaClass);
    System.out.println(" modified annot");
    enc.addAnnotation(ann);
    System.out.println(" added annot to enc");
    myShepherd.getPM().makePersistent(ann);
    System.out.println(" made persistent");

    out.println("<p>(" + count + ") <a target=\"_new\" href=\"https://www.mantamatcher.org/obrowse.jsp?type=MediaAsset&id=" + ma.getId() + "\">" + ma + "</a> --> " + ann + " attached to " + enc + "</p>");
    System.out.println("crAnnot[" + count + "]: added " + ma + " to " + enc + " via " + ann);

    count++;
    if (count % commitFrequency == 0) {
        myShepherd.updateDBTransaction();
        System.out.println(count+" committing");
    }
    if (count > stopAfter) break;
}

myShepherd.commitDBTransaction();
myShepherd.closeDBTransaction();



%>



