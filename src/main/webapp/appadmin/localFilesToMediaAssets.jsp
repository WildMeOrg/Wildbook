<%@ page contentType="text/html; charset=utf-8" language="java"
     import="org.ecocean.*,
org.ecocean.servlet.ServletUtilities,
org.ecocean.media.*,
java.util.Map,
java.io.BufferedReader,
java.io.IOException,
java.io.InputStream,
java.io.InputStreamReader,
java.io.File,
java.util.Collection,
java.util.List,
java.util.ArrayList,
org.json.JSONObject,
javax.jdo.Query,
javax.jdo.Extent,
java.util.HashMap,

org.ecocean.Annotation,
org.ecocean.media.*
              "
%>

<%!
	private List<File> inDir(File d, boolean recurse) {
		List<File> files = new ArrayList<File>();
		if (!d.isDirectory()) return files;
		for (final File f : d.listFiles()) {
			if (f.isDirectory()) {
				if (recurse) files.addAll(inDir(f, recurse));
			} else {
				files.add(f);
			}
		}
		return files;
	}
%>

<html><head>
<title>local files -&gt; MediaAssets</title>
</head><body>


<%

//////////// these are the some settings to consider... *some* can be set via url params
File sourceDir = new File("/tmp/testSourceDir");  //TODO change this to somewhere you want to look (cannot be set from url! security breach!)
boolean recurse = true;   //recurse down into subdirs of above
if ((request.getParameter("recurse") != null) && request.getParameter("recurse").toLowerCase().equals("false")) recurse = false;
boolean allowDuplicates = false;   //will not create if already exists
if ((request.getParameter("allowDuplicates") != null) && request.getParameter("allowDuplicates").toLowerCase().equals("true")) allowDuplicates = true;

/*
	this value will correspond roughly to the subdir(s) it gets stored in. magic(?) of MediaAsset!
	note: this likely will influence 'allowDuplicates' value up there, since random(ish) values will amount to different MediaAssets
*/
String grouping = null;  //let AssetStore.createParameters() decide where to put this thing, likely something randomish *per item*
//String grouping = Util.hashDirectories(Util.generateUUID(), "/");  //random one, but will be the same for this whole set
//String grouping = "some/static/value";  //choose your own?  maybe a hint toward what this import is?
if ((request.getParameter("grouping") != null) && (request.getParameter("grouping").indexOf("..") < 0)) grouping = request.getParameter("grouping");

/////////////// END of interesting things to set


	Shepherd myShepherd=null;
	myShepherd = new Shepherd("context0");

        AssetStore astore = AssetStore.getDefault(myShepherd);

	out.println("<p>source <b>" + sourceDir.toString() + "</b> to <b>" + astore + "</b><br />recurse=<b>" + recurse + "</b>, allowDuplicates=<b>" + allowDuplicates + "</b>, grouping=<b>" + grouping + "</b></p>");




FeatureType.initAll(myShepherd);


List<File> files = inDir(sourceDir, recurse);

for (File f : files) {
        JSONObject sp = astore.createParameters(f, grouping);
	sp.put("_localDirect", f.toString());
        MediaAsset ma = astore.find(sp, myShepherd);
	if (ma != null) {
		if (allowDuplicates) {
			System.out.println("NOTE: " + ma.toString() + " already exists matching " + sp.toString() + " but duplicates are being allowed");
		} else {
			out.println("<p><a target=\"_new\" title=\"" + ma.toString() + "\" href=\"obrowse.jsp?type=MediaAsset&id=" + ma.getId() + "\">[" + ma.getId() + "]</a> already exists for " + sp.toString() + "; skipping</p>");
			continue;
		}
	}

System.out.println("trying to create MediaAsset with sp = " + sp);
        try {
            ma = astore.copyIn(f, sp);
        } catch (IOException ioe) {
            out.println("<p>could not create MediaAsset for " + sp.toString() + ": " + ioe.toString() + "</p>");
            continue;
        }
        try {
            ma.updateMetadata();
        } catch (IOException ioe) {
            	//we dont care (well sorta) ... since IOException usually means we couldnt open file or some nonsense that we cant recover from
		System.out.println("could not updateMetadata() on " + ma);
        }
        ma.addLabel("_original");
	myShepherd.beginDBTransaction();
        MediaAssetFactory.save(ma, myShepherd);
	ma.updateStandardChildren(myShepherd);
	myShepherd.commitDBTransaction();
	out.println("<p>created <a target=\"_new\" title=\"" + ma.toString() + "\" href=\"obrowse.jsp?type=MediaAsset&id=" + ma.getId() + "\">" + ma.getId() + "</a></p>");
	System.out.println("localFilesToMediaAssets: " + f.toString() + " --> " + ma.getId());
}


//myShepherd.commitDBTransaction();
myShepherd.closeDBTransaction();



%>


</body></html>
