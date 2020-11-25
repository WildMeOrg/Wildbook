<!DOCTYPE html PUBLIC "-//W3C//DTD XHTML 1.0 Transitional//EN"
        "http://www.w3.org/TR/xhtml1/DTD/xhtml1-transitional.dtd">
<%@ page contentType="text/html; charset=utf-8" language="java" import="org.joda.time.LocalDateTime,
org.joda.time.format.DateTimeFormatter,
org.joda.time.format.ISODateTimeFormat,java.net.*,
org.ecocean.grid.*,org.ecocean.ia.*,java.util.*,
org.ecocean.media.*,org.ecocean.servlet.importer.ImportTask,
java.io.*,java.util.*, java.io.FileInputStream, java.io.File, java.io.FileNotFoundException, org.ecocean.*,org.ecocean.servlet.*,javax.jdo.*, java.lang.StringBuffer, java.util.Vector, java.util.Iterator, java.lang.NumberFormatException"%>

<%!

public ArrayList<String> getIAClassesForSpecies(String genus, String specificEpithet, Shepherd myShepherd){
	
	Query q=myShepherd.getPM().newQuery("SELECT DISTINCT iaClass FROM org.ecocean.Annotation where enc.annotations.contains(this) && enc.genus=='"+genus+"' && enc.specificEpithet=='"+specificEpithet+"' VARIABLES org.ecocean.Encounter enc");
	Collection c=(Collection)q.execute();
	ArrayList<String> al=new ArrayList<String>(c);
	q.closeAll();
	return al;
}

public Long countIAClassInstances(String genus, String specificEpithet, String iaClass, Shepherd myShepherd){
	Long myValue=new Long(0);
	if(iaClass==null || iaClass.equals("null")){
		Query q2=myShepherd.getPM().newQuery("SELECT count(this) FROM org.ecocean.Annotation where iaClass==null && enc.annotations.contains(this) && enc.genus=='"+genus+"' && enc.specificEpithet=='"+specificEpithet+"' VARIABLES org.ecocean.Encounter enc");
		myValue=(Long) q2.execute();
		q2.closeAll();
	}
	else{
		Query q2=myShepherd.getPM().newQuery("SELECT count(this) FROM org.ecocean.Annotation where iaClass=='"+iaClass+"' && enc.annotations.contains(this) && enc.genus=='"+genus+"' && enc.specificEpithet=='"+specificEpithet+"' VARIABLES org.ecocean.Encounter enc");
		myValue=(Long) q2.execute();
		q2.closeAll();
		
	}
	return myValue;
}

public Long countMatchableIAClassInstances(String genus, String specificEpithet, String iaClass, Shepherd myShepherd){
	Long myValue=new Long(0);
	if(iaClass==null || iaClass.equals("null")){
		Query q2=myShepherd.getPM().newQuery("SELECT count(this) FROM org.ecocean.Annotation where iaClass==null && matchAgainst == true && enc.annotations.contains(this) && enc.genus=='"+genus+"' && enc.specificEpithet=='"+specificEpithet+"' VARIABLES org.ecocean.Encounter enc");
		myValue=(Long) q2.execute();
		q2.closeAll();
	}
	else{
		Query q2=myShepherd.getPM().newQuery("SELECT count(this) FROM org.ecocean.Annotation where iaClass=='"+iaClass+"' && matchAgainst == true && enc.annotations.contains(this) && enc.genus=='"+genus+"' && enc.specificEpithet=='"+specificEpithet+"' VARIABLES org.ecocean.Encounter enc");
		myValue=(Long) q2.execute();
		q2.closeAll();
		
	}
	return myValue;
}

public Long countACMIDIAClassInstances(String genus, String specificEpithet, String iaClass, Shepherd myShepherd){
	Long myValue=new Long(0);
	if(iaClass==null || iaClass.equals("null")){
		Query q2=myShepherd.getPM().newQuery("SELECT count(this) FROM org.ecocean.Annotation where iaClass==null && acmId != null && enc.annotations.contains(this) && enc.genus=='"+genus+"' && enc.specificEpithet=='"+specificEpithet+"' VARIABLES org.ecocean.Encounter enc");
		myValue=(Long) q2.execute();
		q2.closeAll();
	}
	else{
		Query q2=myShepherd.getPM().newQuery("SELECT count(this) FROM org.ecocean.Annotation where iaClass=='"+iaClass+"' && acmId != null && enc.annotations.contains(this) && enc.genus=='"+genus+"' && enc.specificEpithet=='"+specificEpithet+"' VARIABLES org.ecocean.Encounter enc");
		myValue=(Long) q2.execute();
		q2.closeAll();
		
	}
	return myValue;
}

public HashMap<String, Long> getMediaAssetDetectionStatusesForSpecies(String genus, String specificEpithet, Shepherd myShepherd){
	HashMap<String, Long> map=new HashMap<String, Long>();
	Query q=myShepherd.getPM().newQuery("SELECT DISTINCT detectionStatus FROM org.ecocean.media.MediaAsset where enc.annotations.contains(annot) && annot.features.contains(feat) && feat.asset==this && enc.genus=='"+genus+"' && enc.specificEpithet=='"+specificEpithet+"' VARIABLES org.ecocean.Encounter enc; org.ecocean.Annotation annot; org.ecocean.media.Feature feat");
	Collection c=(Collection)q.execute();
	ArrayList<String> al=new ArrayList<String>(c);
	q.closeAll();
	for(String str:al){
		//System.out.println(str);
		Long myValue=new Long(0);
		if(str==null || str.equals("null")){
			Query q2=myShepherd.getPM().newQuery("SELECT count(this) FROM org.ecocean.media.MediaAsset where enc.annotations.contains(annot) && annot.features.contains(feat) && feat.asset==this && detectionStatus==null && enc.genus=='"+genus+"' && enc.specificEpithet=='"+specificEpithet+"' VARIABLES org.ecocean.Encounter enc; org.ecocean.Annotation annot; org.ecocean.media.Feature feat");
			myValue=(Long) q2.execute();
			q2.closeAll();
			map.put("null", myValue);
		}
		else{
			Query q2=myShepherd.getPM().newQuery("SELECT count(this) FROM org.ecocean.media.MediaAsset where enc.annotations.contains(annot) && annot.features.contains(feat) && feat.asset==this && detectionStatus=='"+str+"' && enc.genus=='"+genus+"' && enc.specificEpithet=='"+specificEpithet+"' VARIABLES org.ecocean.Encounter enc; org.ecocean.Annotation annot; org.ecocean.media.Feature feat");
			myValue=(Long) q2.execute();
			q2.closeAll();
			map.put(str, myValue);
		}
		//System.out.println(str+":"+myValue);
	}
	return map;
}

public HashMap<String, Long> getMediaAssetDetectionStatusesForSpeciesByACMID(String genus, String specificEpithet, Shepherd myShepherd){
	HashMap<String, Long> map=new HashMap<String, Long>();
	Query q=myShepherd.getPM().newQuery("SELECT DISTINCT detectionStatus FROM org.ecocean.media.MediaAsset where enc.annotations.contains(annot) && annot.features.contains(feat) && feat.asset==this && enc.genus=='"+genus+"' && enc.specificEpithet=='"+specificEpithet+"' VARIABLES org.ecocean.Encounter enc; org.ecocean.Annotation annot; org.ecocean.media.Feature feat");
	Collection c=(Collection)q.execute();
	ArrayList<String> al=new ArrayList<String>(c);
	q.closeAll();
	for(String str:al){
		//System.out.println(str);
		Long myValue=new Long(0);
		if(str==null || str.equals("null")){
			Query q2=myShepherd.getPM().newQuery("SELECT count(this) FROM org.ecocean.media.MediaAsset where acmId!=null && enc.annotations.contains(annot) && annot.features.contains(feat) && feat.asset==this && detectionStatus==null && enc.genus=='"+genus+"' && enc.specificEpithet=='"+specificEpithet+"' VARIABLES org.ecocean.Encounter enc; org.ecocean.Annotation annot; org.ecocean.media.Feature feat");
			myValue=(Long) q2.execute();
			q2.closeAll();
			map.put("null", myValue);
		}
		else{
			Query q2=myShepherd.getPM().newQuery("SELECT count(this) FROM org.ecocean.media.MediaAsset where acmId!=null && enc.annotations.contains(annot) && annot.features.contains(feat) && feat.asset==this && detectionStatus=='"+str+"' && enc.genus=='"+genus+"' && enc.specificEpithet=='"+specificEpithet+"' VARIABLES org.ecocean.Encounter enc; org.ecocean.Annotation annot; org.ecocean.media.Feature feat");
			myValue=(Long) q2.execute();
			q2.closeAll();
			map.put(str, myValue);
		}
		//System.out.println(str+":"+myValue);
	}
	return map;
}

%>


<%

String context="context0";
context=ServletUtilities.getContext(request);

Shepherd myShepherd=new Shepherd(context);



%>

<jsp:include page="../header.jsp" flush="true" />

<div class="container maincontent">
<h1>Annotation iaClasses and MediaAsset States by Species</h1>
      <p>The species list below allows you to inspect the Annotation.isClass values assigned per species as well as the MediaAsset.detectState values set in the database.</p>
	<p><em>Tip:</em> Annotation.iaClass values not defined in IA.json will be ignored during matching. Check to ensure that the values listed are expected for each species. Different machine learning detector models return different iaCLass values.</p>
	<p><em>Tip:</em> MediaAsset.detectStatus values of null have not been sent to the detector while large numbers of MediaAssets with detectionState=processing may indicate a detection pipeline failure that should be resolved. The ideal is to have all MediaAssets with detectionState=complete.</p>
<%

myShepherd.beginDBTransaction();

int pending=0;
int duped=0;


try {
	
	List<String> species=CommonConfiguration.getIndexedPropertyValues("genusSpecies", context);
	
	for(String str:species){
			%>
			<h3><%=str %></h3>
			<h4>Annotations iaClass Distribution</h4>
			<ul>
			<%
			StringTokenizer st=new StringTokenizer(str.trim()," ");
			String genus="";
			if(st.hasMoreTokens())genus=st.nextToken();
			String specificEpithet="";
			if(st.hasMoreTokens()) specificEpithet=st.nextToken();
			ArrayList<String> iaClasses= getIAClassesForSpecies(genus, specificEpithet, myShepherd);
			for(String iaClass:iaClasses){
				Long num=countIAClassInstances(genus, specificEpithet, iaClass, myShepherd);
				%>
				<li><%=iaClass %>: <%=num %>
					<ul>
						<li>matchAgainst=true: <%=countMatchableIAClassInstances(genus, specificEpithet, iaClass, myShepherd) %></li>
						<li>Have acmID: <%=countACMIDIAClassInstances(genus, specificEpithet, iaClass, myShepherd) %></li>
					</ul>
				
				</li>
				<%
			}
		%>
		</ul>
		<h4>MediaAsset Detection States</h4>
		<ul>
		<%
		HashMap<String, Long> map=getMediaAssetDetectionStatusesForSpecies(genus, specificEpithet, myShepherd);
		HashMap<String, Long> mapACMID=getMediaAssetDetectionStatusesForSpeciesByACMID(genus, specificEpithet, myShepherd);
		
		//System.out.println("keySet: "+map.keySet().toString());
		Iterator<String> iter=map.keySet().iterator();
		while(iter.hasNext()){
			String key=iter.next();
			//System.out.println("key:" +key);
			%>
			<li><%=key %>: <%=map.get(key) %>
				<ul><li>Have acmID: <%=mapACMID.get(key) %></li></ul>
			</li>
			<%
		}
		%>
		</ul>
		<hr />
	<%
	}

}
catch(Exception e){
	e.printStackTrace();
}
finally{
	myShepherd.rollbackDBTransaction();
	myShepherd.closeDBTransaction();

}

%>



</div>


      <jsp:include page="../footer.jsp" flush="true"/>
