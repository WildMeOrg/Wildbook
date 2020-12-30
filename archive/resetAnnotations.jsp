<!DOCTYPE html PUBLIC "-//W3C//DTD XHTML 1.0 Transitional//EN"
        "http://www.w3.org/TR/xhtml1/DTD/xhtml1-transitional.dtd">
<%@ page contentType="text/html; charset=utf-8" language="java" import="org.joda.time.LocalDateTime,
org.joda.time.format.DateTimeFormatter,
org.joda.time.format.ISODateTimeFormat,java.net.*,
org.ecocean.grid.*,org.ecocean.ia.*,java.util.*,
org.ecocean.media.*,
java.io.*,java.util.*, java.io.FileInputStream, java.io.File, java.io.FileNotFoundException, org.ecocean.*,org.ecocean.servlet.*,javax.jdo.*, java.lang.StringBuffer, java.util.Vector, java.util.Iterator, java.lang.NumberFormatException"%>


<%!

public static ArrayList<Encounter> findByAnnotation(Annotation annot, Shepherd myShepherd) {
    String queryString = "SELECT FROM org.ecocean.Encounter WHERE annotations.contains(ann) && ann.id =='" + annot.getId() + "'";
    Query query = myShepherd.getPM().newQuery(queryString);
    Collection c = (Collection)query.execute();
    ArrayList<Encounter> encs=new ArrayList<Encounter>(c);
    query.closeAll();
    return encs;
}

%>


<%

String context="context0";
context=ServletUtilities.getContext(request);

Shepherd myShepherd=new Shepherd(context);

boolean commit=false;

%>

<html>
<head>
<title>Fix Some Fields</title>

</head>


<body>

<%

myShepherd.beginDBTransaction();

int pending=0;
int duped=0;


try {
	
	Query q=myShepherd.getPM().newQuery("SELECT FROM org.ecocean.Encounter WHERE catalogNumber != null && ( submitterID == 'FeliciaVachon' )");
	Collection c=(Collection)q.execute();
	ArrayList<Encounter> al=new ArrayList<Encounter>(c);
	
	for(Encounter enc:al){
		
		ArrayList<Annotation> annots=enc.getAnnotations();
		for(Annotation annot:annots){
			MediaAsset ma=annot.getMediaAsset();
			try{
			if ((ma.getFeatures() != null) && (ma.getFeatures().size() > 1)) {
				duped++;
				
				List<Feature> feats=ma.getFeatures();
				
				for(int i=0;i<feats.size();i++){
					if(i>0){
						if(commit){
							ma.removeFeature(feats.get(i));
							myShepherd.updateDBTransaction();
							System.out.println("Removing feature");
						}
					}
					
				}
				ArrayList<Encounter> encs=findByAnnotation(annot,myShepherd);
				for(int i=0;i<encs.size();i++){
					if(i>0){
						if(commit){
							enc.removeAnnotation(annot);
							if(enc.getAnnotations().size()==0){myShepherd.getPM().deletePersistent(enc);}
							myShepherd.updateDBTransaction();
							System.out.println("Removing encounter");
						}
					}
				}
				
				
			}
			else if(!annot.isTrivial()&&commit)annot.revertToTrivial(myShepherd);
			
			if(ma.getDetectionStatus()!=null&&ma.getDetectionStatus().equals("pending"))pending++;
			if(commit){
				ma.setDetectionStatus(null);
				myShepherd.updateDBTransaction();
			}
			}
			catch(Exception f){
				f.printStackTrace();
			}
		}
		
	}
	%>
	<p>Duped: <%=duped %></p>
	<%
	
}
catch(Exception e){
	e.printStackTrace();
}
finally{
	myShepherd.rollbackDBTransaction();
	myShepherd.closeDBTransaction();

}

%>



</body>
</html>
