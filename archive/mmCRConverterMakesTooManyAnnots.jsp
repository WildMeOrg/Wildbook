<!DOCTYPE html PUBLIC "-//W3C//DTD XHTML 1.0 Transitional//EN"
        "http://www.w3.org/TR/xhtml1/DTD/xhtml1-transitional.dtd">
<%@ page contentType="text/html; charset=utf-8" language="java" import="org.joda.time.LocalDateTime,
org.joda.time.format.DateTimeFormatter,
org.joda.time.format.ISODateTimeFormat,java.net.*,org.json.JSONObject,
org.ecocean.grid.*,org.ecocean.media.*,org.ecocean.mmutil.*,org.ecocean.identity.IBEISIA,
java.io.*,java.util.*, java.io.FileInputStream, java.io.File, java.io.FileNotFoundException, org.ecocean.*,org.ecocean.servlet.*,javax.jdo.*, java.lang.StringBuffer, java.util.Vector, java.util.Iterator, java.lang.NumberFormatException"%>


<%!

public boolean convertMediaAsset(MediaAsset parent, Shepherd myShepherd, String crPath, String context){

	try{
		//this is making some big assumptions about the parent only having one annot to the encounter!
	    String iaClass = "mantaCR";
	    Keyword crKeyword = myShepherd.getOrCreateKeyword("CR Image");
	    AssetStore astore = AssetStore.getDefault(myShepherd);
	    ArrayList<Annotation> anns = parent.getAnnotations();
	    if (Util.collectionIsEmptyOrNull(anns)) return false;
	    Encounter enc = null;
	    for (Annotation ann : anns) {
	        enc = ann.findEncounter(myShepherd);
	        if (enc != null) break;
	    }


	    JSONObject params = new JSONObject();
	    params.put("path", crPath);
	    MediaAsset ma = new MediaAsset(astore, params);
	    ////ma.setParentId(mId);  //no, this is TOO wacky
	    ma.addDerivationMethod("crParentId", parent.getId());  //lets do this instead
	    ma.addLabel("CR");
	    ma.addKeyword(crKeyword);
	    ma.updateMinimalMetadata();
	    ma.setDetectionStatus("complete");

	    MediaAssetFactory.save(ma, myShepherd);
	    //System.out.println(count+" saved annots");
	    /*Annotation ann = new Annotation(iaClass, ma);
	    System.out.println(" created annot");
	    ann.setMatchAgainst(true);
	    ann.setIAClass(iaClass);
	    System.out.println(" modified annot");
	    enc.addAnnotation(ann);
	    */

	    Feature ft = null;
	    FeatureType.initAll(myShepherd);
	    JSONObject fparams = new JSONObject();
	    fparams.put("x", 0);
	    fparams.put("y", 0);
	    fparams.put("width", ma.getWidth());
	    fparams.put("height", ma.getHeight());
	    fparams.put("_manualAnnotation", System.currentTimeMillis());
	    ft = new Feature("org.ecocean.boundingBox", fparams);
	    ma.addFeature(ft);
	    Annotation ann = new Annotation(null, ft, iaClass);
	    ann.setMatchAgainst(true);
	    ann.setIAClass(iaClass);


	    //System.out.println(" added annot to enc");
	    myShepherd.getPM().makePersistent(ann);
	    //System.out.println(" made persistent");
	    myShepherd.updateDBTransaction();
	    enc.addAnnotation(ann);
	    ma.updateStandardChildren(myShepherd);
	    myShepherd.updateDBTransaction();

        // we need to intake mediaassets so they get acmIds and are matchable
        ArrayList<MediaAsset> maList = new ArrayList<MediaAsset>();
        maList.add(ma);
        ArrayList<Annotation> annList = new ArrayList<Annotation>();
        annList.add(ann);
        try {
          System.out.println("    + sending asset to IA");
          IBEISIA.sendMediaAssetsNew(maList, context);
          System.out.println("    + asset sent, sending annot");
          IBEISIA.sendAnnotationsNew(annList, context, myShepherd);
          System.out.println("    + annot sent.");
        } catch (Exception e) {
          e.printStackTrace();
          System.out.println("hit above exception while trying to send CR ma & annot to IA");
        }
        System.out.println("    + done processing new CR annot");


	    return true;
	}
	catch(Exception e){
		e.printStackTrace();
	}
	return false;

}


%>

<%

String context="context0";
context=ServletUtilities.getContext(request);

Shepherd myShepherd=new Shepherd(context);

int numFixes=0;

%>

<html>
<head>
<title>Fix Some Fields</title>

</head>


<body>


<%

int crCount=0;
int matchAgainst=0;
int mismatch=0;
int mmaCompatible=0;
int encHasMatchAgainst=0;
int mismatchEncs =0;

ArrayList<String> misMatchEncArray=new ArrayList<String>();


myShepherd.beginDBTransaction();


try{

	//String filter="select from org.ecocean.Annotation where iaClass == 'mantaCR'";
	String filter="select from org.ecocean.Encounter where catalogNumber!=null";
	Query q=myShepherd.getPM().newQuery(filter);
	Collection c= (Collection)q.execute();
	ArrayList<Encounter> encs=new ArrayList<Encounter>(c);
	q.closeAll();
	%>
	<p>Num encs: <%=encs.size() %></p>
	<%
    for (Encounter enc:encs) {
    	try{
	    	//temp
	    	 //if(enc.getCatalogNumber().equals("b76ac30f-abb9-4cf3-9bfb-14f144883ffe")){


	    	if(enc.getMmaCompatible())mmaCompatible++;
	    	boolean hasMatchAgainst=false;
	    	boolean hasMismatch=false;
	    	List<Annotation> annots=enc.getAnnotations();
	    	for(Annotation annot:annots){
	    		File file=annot.getMediaAsset().localPath().toFile();
				if(MediaUtilities.isAcceptableImageFile(file)){

			      boolean matcherFilesExist=MantaMatcherUtilities.checkMatcherFilesExist(file);
			      if (matcherFilesExist){
			    	  crCount++;


			      }
				  if(annot.getMatchAgainst() && annot.getIAClass() !=null && annot.getIAClass().equals("mantaCR")){
					  matchAgainst++;
					  hasMatchAgainst=true;
				  }
				  if(matcherFilesExist && enc.getMmaCompatible() && (annot.getIAClass()==null || annot.getIAClass().equals("manta_ray_giant") ||  (annot.getIAClass().equals("mantaCR") && !annot.getMatchAgainst()))){
					  mismatch++;
					  hasMismatch=true;

					  //let's convert!
					  Map<String, File> mmFiles = MantaMatcherUtilities.getMatcherFilesMap(annot.getMediaAsset());
				    	   if (mmFiles.get("CR")!=null && mmFiles.get("CR").exists()) {
				    	        File crFile=mmFiles.get("CR");
				    	        //System.out.println(crFile.getAbsolutePath());
				    	        String crPath=crFile.getAbsolutePath().replaceAll("/var/lib/tomcat8/webapps/wildbook_data_dir/", "");
								  %>
								  <p>Converting encounter <a target="_blank" href="../encounters/encounter.jsp?number=<%=enc.getCatalogNumber() %>"><%=enc.getCatalogNumber() %></a>...
								  <%
								  boolean worked=convertMediaAsset(annot.getMediaAsset(), myShepherd, crPath, context);
							  	 //boolean worked=true;
								  if(worked){
							  	 %>
							  	 ...worked!</p>
							  	 <%
							  	 }
							  	 else{
							  		%>
								  	 ...FAILED!</p>
								  	 <%
				    	    }

					  	 }
					  //}

					}
	    		}
	    	} //end for annots
	    	if(hasMatchAgainst){
	    		encHasMatchAgainst++;
	    	}
	    	if(enc.getMmaCompatible() && !hasMatchAgainst){
	    		 misMatchEncArray.add(enc.getCatalogNumber());

	    	}

	    }
		catch(Exception ce){
			ce.printStackTrace();
			myShepherd.rollbackDBTransaction();
			myShepherd.beginDBTransaction();
		}
	}//end for encs


	myShepherd.rollbackDBTransaction();

}
catch(Exception e){
	e.printStackTrace();
	myShepherd.rollbackDBTransaction();
}
finally{
	myShepherd.closeDBTransaction();

}

%>

<p>Num CR files: <%=crCount %></p>
<p>Num matchAgainst Annots: <%=matchAgainst %></p>
<p>mismatch: <%=misMatchEncArray.size() %>
	<ul>
		<%
		for(String sEnc:misMatchEncArray){
		%>
			<li><a target="_blank" href="../encounters/encounter.jsp?number=<%=sEnc %>"><%=sEnc %></a></li>
		<%
		}
		%>

	</ul>
</p>
<p>mmaCompatible Encs: <%=mmaCompatible %></p>
<p>haveMatchablAnnot Encs: <%=encHasMatchAgainst %></p>

<p>Done successfully: <%=numFixes %></p>

</body>
</html>
