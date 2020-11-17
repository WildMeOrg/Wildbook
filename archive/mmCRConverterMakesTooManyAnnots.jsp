<!DOCTYPE html PUBLIC "-//W3C//DTD XHTML 1.0 Transitional//EN"
        "http://www.w3.org/TR/xhtml1/DTD/xhtml1-transitional.dtd">
<%@ page contentType="text/html; charset=utf-8" language="java" import="org.joda.time.LocalDateTime,
org.joda.time.format.DateTimeFormatter,
org.joda.time.format.ISODateTimeFormat,java.net.*,org.json.JSONObject,
org.ecocean.grid.*,org.ecocean.media.*,org.ecocean.mmutil.*,org.ecocean.identity.IBEISIA,
java.io.*,java.util.*, java.io.FileInputStream, java.io.File, java.io.FileNotFoundException, org.ecocean.*,org.ecocean.servlet.*,javax.jdo.*, java.lang.StringBuffer, java.util.Vector, java.util.Iterator, java.lang.NumberFormatException"%>


<%!

public boolean convertMediaAsset(Encounter enc, Shepherd myShepherd, String crPath, String context, String viewpoint){
    
	try{
		//this is making some big assumptions about the parent only having one annot to the encounter!
	    String iaClass = "whalesharkCR";
	    Keyword crKeyword = myShepherd.getOrCreateKeyword("CR Image");
	    AssetStore astore = AssetStore.getDefault(myShepherd);
	    //if (Util.collectionIsEmptyOrNull(anns)) return false;

	
	
	    JSONObject params = new JSONObject();
	    params.put("path", crPath);
	    MediaAsset ma = new MediaAsset(astore, params);
	    ////ma.setParentId(mId);  //no, this is TOO wacky
	    //ma.addDerivationMethod("crParentId", parent.getId());  //lets do this instead
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
	    ann.setViewpoint(viewpoint);
	    
	
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
          //System.out.println("    + sending asset to IA");
          //IBEISIA.sendMediaAssetsNew(maList, context);
          //System.out.println("    + asset sent, sending annot");
          //IBEISIA.sendAnnotationsNew(annList, context, myShepherd);
          //System.out.println("    + annot sent.");
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

int left=0;
int right=0;

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
	//String filter="select from org.ecocean.Encounter where catalogNumber!=null";
	String filter="SELECT FROM org.ecocean.Encounter WHERE ( ( annotations.contains(photo0) && photo0.features.contains(feat0) && !feat0.asset.keywords.contains(word0) && ( word0.indexname == 'a8ce962375b743b50175cda6ab240002' )) ) VARIABLES org.ecocean.Annotation photo0;org.ecocean.Keyword word0;org.ecocean.media.Feature feat0";
	Query q=myShepherd.getPM().newQuery(filter);
	Collection c= (Collection)q.execute();
	ArrayList<Encounter> encs=new ArrayList<Encounter>(c);
	q.closeAll();
	
    //setup data dir
    String rootWebappPath = getServletContext().getRealPath("/");
    File webappsDir = new File(rootWebappPath).getParentFile();
    File shepherdDataDir = new File(webappsDir, CommonConfiguration.getDataDirectoryName(context));
    //if(!shepherdDataDir.exists()){shepherdDataDir.mkdirs();}
    File encountersDir=new File(shepherdDataDir.getAbsolutePath()+"/encounters");
    //if(!encountersDir.exists()){encountersDir.mkdirs();}
    

	
	%>
	<p>Num encs: <%=encs.size() %></p>
	<%  
    for (Encounter enc:encs) {
    	try{
    		
    		if(enc.getSpots()!=null && enc.getSpots().size()>0  && enc.getSpotImageFileName()!=null){
    			
    		    File crFile = new File(Encounter.dir(shepherdDataDir, enc.getCatalogNumber()) + "/" + enc.getSpotImageFileName());

    			
   	    	  //File crFile=new File(enc.,enc.getSpotImageFileName());
   	          System.out.println(crFile.getAbsolutePath());
   	          String crPath=crFile.getAbsolutePath().replaceAll("/var/lib/tomcat8/webapps/wildbook_data_dir/", "");
			  %>
			  <p>Converting left encounter <a target="_blank" href="../encounters/encounter.jsp?number=<%=enc.getCatalogNumber() %>"><%=enc.getCatalogNumber() %></a>...
			  <%
			  //if(enc.getCatalogNumber().equals("f0f7da20-8172-4934-b7a3-4c3778f4cab3")){
				  boolean worked=convertMediaAsset(enc, myShepherd, crPath, context, "left");
			  //}
				left++;
    		}
    		
    		if(enc.getRightSpots()!=null && enc.getRightSpots().size()>0 && enc.getRightSpotImageFileName()!=null){
    			
    			File crFile = new File(Encounter.dir(shepherdDataDir, enc.getCatalogNumber()) + "/" + enc.getRightSpotImageFileName());

    			
   	    	  	//File crFile=new File(enc.,enc.getSpotImageFileName());
   	          	System.out.println(crFile.getAbsolutePath());
   	          	String crPath=crFile.getAbsolutePath().replaceAll("/var/lib/tomcat8/webapps/wildbook_data_dir/", "");
			  	%>
			  	<p>Converting right encounter <a target="_blank" href="../encounters/encounter.jsp?number=<%=enc.getCatalogNumber() %>"><%=enc.getCatalogNumber() %></a>...
			  	<%
			  	//if(enc.getCatalogNumber().equals("f0f7da20-8172-4934-b7a3-4c3778f4cab3")){
					  boolean worked=convertMediaAsset(enc, myShepherd, crPath, context, "right");
				//  }
			  	right++;
    			
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

<p>Left: <%=left %></p>
<p>Right: <%=right %></p>

</body>
</html>
