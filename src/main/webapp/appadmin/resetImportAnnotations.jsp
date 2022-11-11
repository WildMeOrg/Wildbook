<!DOCTYPE html PUBLIC "-//W3C//DTD XHTML 1.0 Transitional//EN"
        "http://www.w3.org/TR/xhtml1/DTD/xhtml1-transitional.dtd">
<%@ page contentType="text/html; charset=utf-8" language="java" import="org.joda.time.LocalDateTime,
org.joda.time.format.DateTimeFormatter,
org.joda.time.format.ISODateTimeFormat,java.net.*,
java.util.List, java.util.ArrayList,
org.ecocean.grid.*,
org.ecocean.media.MediaAsset,
java.io.*,java.util.*, java.io.FileInputStream, 
java.io.File, java.io.FileNotFoundException, org.ecocean.*,org.ecocean.servlet.*,javax.jdo.*, 
org.ecocean.servlet.importer.*,
java.lang.StringBuffer, java.util.Vector, java.util.Iterator, java.lang.NumberFormatException"%>

<jsp:include page="../header.jsp" flush="true" />

<div class="container maincontent">

<%
String context="context0";
context=ServletUtilities.getContext(request);
Shepherd myShepherd=new Shepherd(context);

String importTaskID=null;
if(request.getParameter("importTaskID")!=null){
	importTaskID = request.getParameter("importTaskID");
}


int numResetAnnots = 0;
int newAnnotations = 0;
int nulledStatuses = 0;
int numTrivialAnns = 0;
Set<String> mediaAssetAcmIds = new TreeSet<String>();
Set<Integer> mediaAssetIds = new TreeSet<Integer>();
ArrayList<String> clones = new ArrayList<String>();


myShepherd.beginDBTransaction();
try{
	User user = myShepherd.getUser(request);
	if(importTaskID!=null){
	
		//below only to reset an import task
		ImportTask it = myShepherd.getImportTask(importTaskID);
		if(it==null){
		%>
			<p>The designated import task could not be found.</p>
		<%
		}
		else if(request.isUserInRole("admin") || it.getCreator() == user){
			List<Encounter> allEncs = it.getEncounters();
			
			int numEncs = allEncs.size();
			for (int i=0; i<numEncs; i++){
		
				Encounter enc = allEncs.get(i);
				
				if(enc.getRComments()!=null && enc.getRComments().indexOf("cloneWithoutAnnotations") > -1){
					clones.add(enc.getCatalogNumber());
				}
		
				for (Annotation ann: enc.getAnnotations()) {
		
					boolean wasTrivial = ann.isTrivial();
					try {
						MediaAsset ma= ann.getMediaAsset();
					
						//reset non-trivial annots to trivial
						if (!wasTrivial) {

							Annotation newAnn = ann.revertToTrivial(myShepherd, true);
							numResetAnnots++;
							
							myShepherd.storeNewAnnotation(newAnn);
							newAnnotations++;
							
							if (ma==null) continue;
							String oldStatus = ma.getDetectionStatus();
							ma.setDetectionStatus(null);
							if (oldStatus!=null && !oldStatus.equals(ma.getDetectionStatus())) {
								nulledStatuses++;
							}
							mediaAssetIds.add(ma.getId());
							mediaAssetAcmIds.add(ma.getAcmId());
							myShepherd.updateDBTransaction();
						}
						
						
						if(ma.getDetectionStatus()!=null && ann.getIAClass()==null){
							ma.setDetectionStatus(null);
							myShepherd.updateDBTransaction();
						}
						
					} 
					catch (Exception e) {
						e.printStackTrace();
					}
					if (ann.isTrivial()) numTrivialAnns++;
		
			
				}
		
					myShepherd.updateDBTransaction();
				
			}
			
			myShepherd.commitDBTransaction();
			%>
			<h2>Success</h2>
			<p>The import task's annotations have been reset, but no metadata or ID assignments have been changed at the Encounter level. The import task is now ready to be re-run through detection and identification.</p>
			<%
			if(clones.size()>0){
			%>
			<p>The following encounters were cloned and may need to be deleted.</p>
			<ul>
				<%
				for(String clone:clones){
				%>
					<li><a href="../encounters/encounter.jsp?number=<%=clone %>"><%=clone %></a></li>
				<%
				}
				%>
			</ul>
			<%
			}
			%>
			<p><a href="../import.jsp?taskId=<%=importTaskID %>">Return to import task.</a></p>
			<%
			myShepherd.beginDBTransaction();
		}
	}
	else{%>
		<p>No import task was specified, or you do not have the needed permissions.</p>
	<%}
}
catch(Exception e){
	myShepherd.rollbackDBTransaction();
}
finally{
	myShepherd.closeDBTransaction();

}


%>

</div>
<jsp:include page="../footer.jsp" flush="true"/>


