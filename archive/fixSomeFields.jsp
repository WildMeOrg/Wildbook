<!DOCTYPE html PUBLIC "-//W3C//DTD XHTML 1.0 Transitional//EN"
        "http://www.w3.org/TR/xhtml1/DTD/xhtml1-transitional.dtd">
<%@ page contentType="text/html; charset=utf-8" language="java" import="org.joda.time.LocalDateTime,
org.joda.time.format.DateTimeFormatter,org.ecocean.ia.plugin.*,
org.joda.time.format.ISODateTimeFormat,java.net.*,org.json.JSONObject,org.apache.commons.io.*,
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


myShepherd.beginDBTransaction();


try{

	//String filter="select from org.ecocean.Annotation where iaClass == 'mantaCR'";
	String filter="select from org.ecocean.Encounter where annotations.contains(annot) && annot.iaClass=='whalesharkCR' VARIABLES org.ecocean.Annotation annot";
	Query q=myShepherd.getPM().newQuery(filter);
	Collection c= (Collection)q.execute();
	ArrayList<Encounter> encs=new ArrayList<Encounter>(c);
	q.closeAll();
	%>
	<p>Num encs: <%=encs.size() %></p>
	<ul>
	<%  
	
	WildbookIAM wim = new WildbookIAM(context);
	
	ArrayList<Annotation> anns=new ArrayList<Annotation>();
	ArrayList<MediaAsset> assets=new ArrayList<MediaAsset>();
	ArrayList<String> acmIds=new ArrayList<String>();
	
    for (Encounter enc:encs) {
    	try{
    		
    		ArrayList<String> matchable=new ArrayList<String>();
    		int dupes=0;
	    	List<Annotation> annots=enc.getAnnotations();
	    	for(Annotation annot:annots){
	    		
	    		MediaAsset asset=annot.getMediaAsset();
	    		if(asset!=null){
		    		String m_acmId=null;
		    		if(asset.getAcmId()!=null)m_acmId=asset.getAcmId();

	    			//if(annot.getAcmId()==null){
	    				
	    				//asset.updateMetadata();
	    				//myShepherd.updateDBTransaction();
	    				
	    				mismatch++;
	    				if(annot.getIAClass()!=null && annot.getIAClass().equals("whalesharkCR")) 
	    				{
	    					
	    					
	    					if(asset.getAcmId()==null){
	    						
	    						assets.add(asset);
	    		    			//if(m_acmId!=null && !acmIds.contains(m_acmId)){		
	    		    					
	    		    					
	    		    			//}
	    		    			
	    						
	    					}
	    					if(annot.getAcmId()==null){
	    						anns.add(annot);
	    					}
	        				
	    					
	        	    		//if(ma!=null && ma.getAcmId()==null){
	        	    		//	assets.add(ma);
	        	    		//}
	    				}
	    				if(m_acmId!=null)acmIds.add(m_acmId);
	    				
	    			//}
    			
	    	}
	    		else{
	    			mismatch++;
	    		}
	    		
	    	} //end for annots

			//myShepherd.updateDBTransaction();
	    }
		catch(Exception ce){
			ce.printStackTrace();
			myShepherd.rollbackDBTransaction();
			myShepherd.beginDBTransaction();
		}
	}//end for encs
    
    
	if(assets.size()>0){
		int counter=0;
		System.out.println("Assets needing acmID" + assets.size());
		%>
		<li><%="Assets needing acmID" + assets.size() %></li>
		<%
		//System.out.println("Send mediaassets for enc: "+enc.getCatalogNumber());
		ArrayList<MediaAsset> sendMe=new ArrayList<MediaAsset>();
		for(MediaAsset asset:assets){
			sendMe.add(asset);
			if(sendMe.size()>=250){
				counter++;
				wim.sendMediaAssets(sendMe, true);
				myShepherd.updateDBTransaction();
				System.out.println("Sent MediAssets batch "+counter+":" + sendMe.size());
				sendMe=new ArrayList<MediaAsset>();
				
			}
		}
		wim.sendMediaAssets(sendMe, true);
		myShepherd.updateDBTransaction();
		System.out.println("Sent MediAssets batch "+counter+":" + sendMe.size());
		
	}
	%>
	<li><%="Annotations needing acmID" + anns.size() %></li>
	<%
	if(anns.size()>0){
		int counter=0;
		System.out.println("Annots needing acmID" + anns.size());
		//System.out.println("Send mediaassets for enc: "+enc.getCatalogNumber());
		ArrayList<Annotation> sendMe=new ArrayList<Annotation>();
		for(Annotation asset:anns){
			sendMe.add(asset);
			if(sendMe.size()>=250){
				System.out.println("Sending Annotations batch "+counter+":" + sendMe.size());
				counter++;
				wim.sendAnnotations(sendMe, true, myShepherd);
				myShepherd.updateDBTransaction();
				System.out.println("Sent Annotations batch "+counter+":" + sendMe.size());
				
				sendMe=new ArrayList<Annotation>();
			}
		}
		wim.sendAnnotations(sendMe, true, myShepherd);
		myShepherd.updateDBTransaction();
		
		
	}
    
    
    
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
</ul>
<p>Annots missing acmIDs: <%=mismatch %></p>
<p>Assets missing acmIDs: <%=mismatchEncs %></p>


</body>
</html>
