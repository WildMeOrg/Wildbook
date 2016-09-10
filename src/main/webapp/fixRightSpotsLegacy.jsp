<%@ page contentType="text/plain; charset=utf-8" language="java"
     import="org.ecocean.*,
org.ecocean.servlet.ServletUtilities,
org.ecocean.Annotation,
java.util.ArrayList,
java.util.Map,
java.util.Iterator,
java.io.BufferedReader,
java.io.IOException,
java.io.InputStream,
java.io.InputStreamReader,
java.lang.RuntimeException,
java.io.File,
org.json.JSONObject,
javax.jdo.Query,

org.ecocean.media.*
              "
%><%

Shepherd myShepherd = new Shepherd("context0");
String rootDir = getServletContext().getRealPath("/");
String baseDir = ServletUtilities.dataDir("context0", rootDir);

baseDir = "/var/lib/tomcat7/webapps/wildbook_data_dir";

myShepherd.beginDBTransaction();


/*
  public String spotImageFileName = "";
  //name of the stored file from which the right-side spots were extracted
  public String rightSpotImageFileName = "";
*/

//Query query = myShepherd.getPM().newQuery("SELECT from org.ecocean.Encounter WHERE spotImageFileName != null || rightSpotImageFileName != null");
//Iterator all = myShepherd.getAllEncounters(query);


AssetStore astore = AssetStore.getDefault(myShepherd);
if (astore == null) throw new RuntimeException("could not find default asset store");

int count = 0;

/*
36201011426
b680b7f8-1243-4b78-b79d-38d2087ceb93
244200611744 
*/

//while ((count < 10) && all.hasNext()) {
while (count < 1) {
	//count++;
	count = 10;


	//String id = "36201011426";
	//String id = "b680b7f8-1243-4b78-b79d-38d2087ceb93";
	String id = "244200611744";
	//Encounter enc = (Encounter)all.next();
	Encounter enc = myShepherd.getEncounter(id);
	out.println("\n" + enc);
	out.println(" [L] " + enc.getSpotImageFileName());
	out.println(" [R] " + enc.getRightSpotImageFileName());
    	ArrayList<MediaAsset> spotMAs = enc.findAllMediaByLabel(myShepherd, "_spotRight");
	boolean hasRightSpotMA = false;
	if ((spotMAs != null) && (spotMAs.size() > 0)) {
		hasRightSpotMA = true;
		for (MediaAsset ma : spotMAs) {
			out.println("  -  " + ma.getFilename() + "   " + ma.getId());
		}
	} else {
		out.println("  -  [no spot MediaAssets]");
	}
	//out.println(enc.getCatalogNumber() + "\t" + );

	if (hasRightSpotMA) {
		out.println("  .  already has a right spot MediaAsset; skipping");
	} else {
		if ((enc.getMedia() == null) || (enc.getMedia().size() < 1)) {
			out.println("  *  no MediaAssets to attach to???");
			continue;
		}
		MediaAsset parent = enc.getMedia().get(0);
System.out.println("parent -> " + parent);
    //public MediaAsset spotImageAsMediaAsset(MediaAsset parent, String baseDir, Shepherd myShepherd) {
        	File fullPath = new File(enc.dir(baseDir) + "/" + enc.getRightSpotImageFileName());
        	if (!fullPath.exists()) {
			out.println("  *  " + "file does not exist: " + fullPath);
			continue;
		}
//System.out.println("trying spotImageAsMediaAsset with file=" + fullPath.toString());
        	org.json.JSONObject sp = astore.createParameters(fullPath);
System.out.println(sp);
        	//MediaAsset ma = astore.find(sp, myShepherd);
		MediaAsset ma = null;
            	try {
                	ma = astore.copyIn(fullPath, sp);
                	ma.addDerivationMethod("historicRightSpotImageConversion", true);
                	//ma.updateMinimalMetadata();
                	ma.addLabel("_spotRight");
                	ma.addLabel("_annotation");
        		ma.setParentId(parent.getId());
                	MediaAssetFactory.save(ma, myShepherd);
//System.out.println("params? " + ma.getParameters());
            	} catch (java.io.IOException ex) {
                	out.println("creating new MediaAsset threw IOException " + ex.toString());
			continue;
		}

		if (ma == null) {
			out.println("  *  ma is null; failed?");
		} else {
			out.println("  +  successfully created " + ma);
		}
	}

}





myShepherd.commitDBTransaction();
//////myShepherd.closeDBTransaction();

%>

=done=





