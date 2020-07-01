<%@ page contentType="text/html; charset=utf-8" language="java"
     import="org.ecocean.*,
java.util.ArrayList,
java.util.List,
java.io.BufferedReader,
java.io.IOException,
java.io.InputStream,
java.io.InputStreamReader,
java.io.File,
java.util.Iterator,
org.json.JSONObject,
org.ecocean.servlet.ServletUtilities,

org.ecocean.media.*
              "
%>




<%
/*
    use with extreme caution?  really ONLY for migrating pre-MediaAsset wildbooks (i am sorry) to the modern world.

    this works in batches, but you can tweak the min/max numbers below to help do different sizes.
    it iterates thru all encounters (ordered by catalogNumber) and attempts to convert SinglePhotoVideos to MediaAssets.
    it will also make the "standard" child assets (e.g. mid, master, etc) as needed.

    note that your SinglePhotoVideo data has a fullPath property ... it *must be right* (point to the real image in your
    new MediaAsset world) location for that file.  if you need to do a mass change, i would suggest postgresql, like:

    update "SINGLEPHOTOVIDEO" set "FULLFILESYSTEMPATH" = regexp_replace("FULLFILESYSTEMPATH", '/opt/tomcat7', '/var/lib/tomcat8');

    your AssetStore should also reference the same place before you start the process.

    i suggest starting with small batches of maybe 5-10 then jump up to doing big chunks and/or all.
*/

String context = "context0";
Shepherd myShepherd = new Shepherd(context);
String rootDir = getServletContext().getRealPath("/");
String baseDir = ServletUtilities.dataDir(context, rootDir);


int count = 0;
Iterator encIter = myShepherd.getAllEncounters("catalogNumber");
while (encIter.hasNext()) {
    count++;
    Encounter enc = (Encounter)encIter.next();

////// change these to change "batch" start/size
if (count < 100) continue;  //skip anything before this number
if (count > 1000) {  //process stops after this number

    myShepherd.commitDBTransaction();
    System.out.println("spvFix - complete (short circuit)");
    return;
}

    String encId = enc.getCatalogNumber();
    out.println("<p>" + count + ") <b>" + enc + "</b>");
System.out.println("-----------------------------------------------\nenc => " + enc);

    ArrayList<Annotation> anns = enc.getAnnotations();
    if ((anns != null) && (anns.size() > 0)) {
        System.out.println("spvFix(" + count + "): skipping (already done)");
        out.println(" - already has " + anns.size() + " Annotations; skipping</p>");

/*
    } else if (
        (count == 837) ||
        (count == 795) ||
        (count == 663) ||
        (count == 501) ||
        (count == 429) ||
        (count == 368) ||
        (count == 256)
    ) {
        System.out.println("spvFix: hard-skipping " + count);
*/

    } else {
        myShepherd.beginDBTransaction();
        try {
            anns = enc.generateAnnotations(baseDir, myShepherd);
        } catch (Exception ex) {
            System.out.println("spvFix - generateAnnotations() threw " + ex.toString());
            ex.printStackTrace();
            anns = null;
        }
System.out.println("=========== OUT ==========");
        if ((anns != null) && (anns.size() > 0)) {
            out.println("<ul>");
            for (Annotation ann : anns) {
System.out.println("ann => " + ann);
                myShepherd.getPM().makePersistent(ann);
                out.println("<li>" + ann + " <i>");
                MediaAsset ma = ann.getMediaAsset();
System.out.println("ma => " + ma);
                //if ((ma != null) && ma.isMimeTypeMajor("image")) {
                if (ma != null) {
                    String report = "spvFix(" + count + "): enc[" + encId + "] ann[" + ann.getId() + "] ma[" + ma.getId() + "]";
                    AssetStore store = ma.getStore();
System.out.println("store => " + store);
                    //if (store == null) continue;
                    for (String type : store.standardChildTypes()) {
System.out.println("type => " + type);
                        ArrayList<MediaAsset> kids = ma.findChildrenByLabel(myShepherd, "_" + type);
System.out.println("kids => " + kids);
                        if ((kids != null) && (kids.size() > 0)) {
                            out.println(" [" + type + "=" + kids.size() + "]");
                            System.out.println(report + " has " + kids.size() + " of " + type);
                        } else {
                            MediaAsset kid = null;
                            try {
                                kid = ma.updateChild(type);
                            } catch (Exception ex) {
                                System.out.println("spvFix: WARNING " + type + " on " + ma + " threw " + ex.toString());
                            }
                            if (kid == null) {
                                out.println(" [" + type + "-> (failed updateChild) ]");
                                System.out.println(report + " failed updateChild() for " + type);
                            } else {
                                MediaAssetFactory.save(kid, myShepherd);
                                out.println(" [" + type + "->" + kid.getId() + "]");
                                System.out.println(report + " created " + kid.getId() + " for " + type);
                            }
                        }
                    }
                }
                out.println("</i></li>");
            }
            out.println("</ul>");
        }
        out.println("</p>");
        myShepherd.commitDBTransaction();
        myShepherd.beginDBTransaction();
    }

}
//myShepherd.commitDBTransaction();

System.out.println("spvFix: complete!");

%>



