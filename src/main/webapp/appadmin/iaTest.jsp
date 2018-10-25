<%@ page contentType="text/html; charset=utf-8" language="java"
     import="org.ecocean.*,
org.ecocean.servlet.ServletUtilities,
java.util.HashMap,
java.io.BufferedReader,
java.io.IOException,
java.io.InputStream,
java.io.InputStreamReader,
java.io.File,
org.ecocean.ia.Task,
org.json.JSONObject,

org.ecocean.identity.IBEISIA,
org.ecocean.media.*
              "
%><html>
<head><title>IA Test</title>
<style>
body {
    font-family: arial, sans;
}
img.small {
    max-height: 200px;
    float:right;
}

.results, .controls {
    padding: 20px;
}
.results {
    background-color: #DFD;
    min-height: 250px;
}
.controls {
    clear: both;
    margin-top: 30px;
}

.error {
    color: red;
}
</style>
<script>
function process() {
    var t = document.getElementById('select-taxonomy').selectedOptions[0].value;
    var f = document.getElementById('select-image').selectedOptions[0].value;
    var url = 'iaTest.jsp?filename=' + encodeURI(f) + '&taxonomy=' + encodeURI(t) + '&_=' + new Date().getTime();
    document.location.href = url;
}
</script>
</head>
<body>
<h1>Test IA</h1>



<%

String COLLAGE = "_COLLAGE_";
String context = ServletUtilities.getContext(request);
Shepherd myShepherd = new Shepherd(context);


String uploadTmpDir = CommonConfiguration.getUploadTmpDir(context);
String filename = request.getParameter("filename");
String taxonomy = request.getParameter("taxonomy");


if (filename != null) {
    String testId = Util.generateUUID();
    Encounter enc = null;
    out.println("<div class=\"results\">");
    File sourceOriginal = new File(uploadTmpDir, filename);
    if (filename.equals(COLLAGE)) {
	String rootDir = getServletContext().getRealPath("/");
        sourceOriginal = new File(rootDir + "/images/iaTestCollage.jpg");
    }
    if (sourceOriginal.exists()) {  //ok, do the test using this now!
	myShepherd.beginDBTransaction();
        FeatureType.initAll(myShepherd);
        AssetStore astore = AssetStore.getDefault(myShepherd);
        File sourceMunged = new File("/tmp", "iaTestImage-" + testId + ".jpg");

        //we do a little futzing with the image now, watermarks it *and* makes it "unique" (sorry JP)
        String[] command = new String[]{
            "/usr/bin/convert",
            sourceOriginal.toString(),
            "-fill", "#DDD",
            "-annotate", "+30+30",
            "Wildbook\u00AE Test Match Image - NOT VALID DATA - FOR TEST PURPOSES ONLY - " + System.currentTimeMillis() + ":" + testId,
            "-gravity", "Center",
            "-append",
            sourceMunged.toString()
        };
        ProcessBuilder pb = new ProcessBuilder();
        pb.command(command);
        try {
            Process proc = pb.start();
            BufferedReader stdInput = new BufferedReader(new InputStreamReader(proc.getInputStream()));
            BufferedReader stdError = new BufferedReader(new InputStreamReader(proc.getErrorStream()));
            String line;
            while ((line = stdInput.readLine()) != null) {
                System.out.println(">>>> " + line);
            }
            while ((line = stdError.readLine()) != null) {
                System.out.println("!!!! " + line);
            }
            proc.waitFor();
            ////int returnCode = p.exitValue();
        } catch (Exception ioe) {
            System.out.println("Trouble running processor [" + command + "]" + ioe.toString());
        }

        MediaAsset ma = null;
        JSONObject sp = astore.createParameters(sourceMunged, "TEST_" + testId);
	sp.put("_iaTest", System.currentTimeMillis());
        try {
            ma = astore.copyIn(sourceMunged, sp);
        } catch (IOException ioe) {
            out.println("<p class=\"error\">could not create MediaAsset for " + sp.toString() + ": " + ioe.toString() + "</p>");
        }

        if (ma == null) {
            myShepherd.rollbackDBTransaction();

        } else {
            out.println("<div><i>creating objects:</i><ul>");
            try {
                ma.updateMetadata();
            } catch (IOException ioe) {
		System.out.println("could not updateMetadata() on " + ma);
            }
            ma.addLabel("_original");
            MediaAssetFactory.save(ma, myShepherd);
	    ma.updateStandardChildren(myShepherd);
	    out.println("<li><a target=\"_new\" title=\"" + ma.toString() + "\" href=\"../obrowse.jsp?type=MediaAsset&id=" + ma.getId() + "\">MediaAsset " + ma.getId() + "</a><img class=\"small\" src=\"" + ma.webURL() + "\" /></li>");
	    System.out.println("iaTest.jsp: " + sourceMunged.toString() + " --> " + ma.getId());
            Annotation ann = new Annotation(taxonomy, ma);
            enc = new Encounter(ann);
            enc.setDateInMilliseconds(System.currentTimeMillis());
            enc.setState("__TEST_ENCOUNTER__");
            enc.addSubmitter(AccessControl.getUser(request, myShepherd));
            enc.addComments("<i style=\"color: red; font-size: 1.5em;\">This is a <b>TEST ENCOUNTER</b> used to test IA functionality.</i>");
            enc.setLocationID("__TEST__");
            enc.setTaxonomyFromString(taxonomy);
	    out.println("<li><a target=\"_new\" title=\"" + ann.toString() + "\" href=\"../obrowse.jsp?type=Annotation&id=" + ann.getId() + "\">Annotation " + ann.getId() + "</a></li>");
	    out.println("<li><a target=\"_new\" title=\"" + enc.toString() + "\" href=\"../obrowse.jsp?type=Encounter&id=" + enc.getCatalogNumber() + "\">Encounter " + enc.getCatalogNumber() + "</a></li>");
            myShepherd.getPM().makePersistent(enc);
	    myShepherd.commitDBTransaction();
	    myShepherd.beginDBTransaction();
            Task task = org.ecocean.ia.IA.intake(myShepherd, ma);
            myShepherd.storeNewTask(task);
	    myShepherd.commitDBTransaction();
	    out.println("<li><a target=\"_new\" title=\"" + task.toString() + "\" href=\"../obrowse.jsp?type=Task&id=" + task.getId() + "\">Task " + task.getId() + "</a></li>");
            out.println("</ul></div>");
            org.ecocean.ShepherdPMF.getPMF(context).getDataStoreCache().evictAll();
        }

    } else {
        out.println("<p class=\"error\">File <b>" + sourceOriginal + "</b> does not exist.</p>");
    }

    out.println("</div>");
}


HashMap<String,Taxonomy> tax = IBEISIA.iaTaxonomyMap(myShepherd);
%>

<div class="controls">

<select id="select-taxonomy">
<%
for (Taxonomy t : tax.values()) {
    out.println("<option>" + t.getScientificName() + "</option>");
}
%>
</select>


<img class="small" src="../images/iaTestCollage.jpg?<%=System.currentTimeMillis()%>" />
<select id="select-image">
<option value="<%=COLLAGE%>">Test Collage (right)</option>
<%
File udir = new File(uploadTmpDir);
for (final File f : udir.listFiles()) {
    if (!f.isDirectory()) {
        out.println("<option>" + f.getName() + "</option>");
    }
}
%>
</select>
<p style="color: #AAA; margin-left: 100px; font-size: 0.8em;">
Dir for test images: <b><%=uploadTmpDir%></b>
</p>


<p>
<input onClick="process()" value="Test" type="button" />
</div>


