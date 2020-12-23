<!DOCTYPE html PUBLIC "-//W3C//DTD XHTML 1.0 Transitional//EN"
        "http://www.w3.org/TR/xhtml1/DTD/xhtml1-transitional.dtd">
<%@ page contentType="text/html; charset=utf-8" language="java" import="org.joda.time.LocalDateTime,
org.joda.time.format.DateTimeFormatter,
org.joda.time.format.ISODateTimeFormat,java.net.*,org.ecocean.media.*,org.ecocean.identity.*,
org.ecocean.grid.*,org.ecocean.ia.*,org.ecocean.ia.plugin.*,
java.io.*,java.util.*, java.io.FileInputStream, java.io.File, java.io.FileNotFoundException, org.ecocean.*,org.ecocean.servlet.*,javax.jdo.*, java.lang.StringBuffer, java.util.Vector, java.util.Iterator, java.lang.NumberFormatException"%>

<%

String context="context0";
context=ServletUtilities.getContext(request);

Shepherd myShepherd=new Shepherd(context);
myShepherd.setAction("testPrematch.jsp");


%>

<html>
<head>
<title>Test Prematch</title>

</head>


<body>

<%

myShepherd.beginDBTransaction();


String filter="SELECT FROM org.ecocean.Annotation WHERE matchAgainst  && acmId != null && enc.catalogNumber != 'abcaa2d0-e20e-4de8-acda-c0aa2213162a' && enc.annotations.contains(this) && enc.genus == 'Megaptera' && enc.specificEpithet == 'novaeangliae' VARIABLES org.ecocean.Encounter enc";





PersistenceManager pm=myShepherd.getPM();
PersistenceManagerFactory pmf = pm.getPersistenceManagerFactory();
FetchPlan fp=pm.getFetchPlan();

//this voodoo via JH will insure that .acmId is on the MediaAssets which are loaded via getMatchingSet() below (for speed)
javax.jdo.FetchGroup grp = myShepherd.getPM().getPersistenceManagerFactory().getFetchGroup(MediaAsset.class, "BIA");
grp.addMember("acmId").addMember("store").addMember("id").addMember("parametersAsString").addMember("parameters").addMember("metadata").addMember("labels").addMember("userLatitude").addMember("userLongitude").addMember("userDateTime").addMember("features");
myShepherd.getPM().getFetchPlan().setGroup("BIA");

//javax.jdo.FetchGroup grp2 = myShepherd.getPM().getPersistenceManagerFactory().getFetchGroup(Annotation.class, "BIA2");
//grp2.addMember("acmId");
//myShepherd.getPM().getFetchPlan().addGroup("BIA2");

long t1 = System.currentTimeMillis();
Query query=myShepherd.getPM().newQuery(filter);
Collection c = (Collection) (query.execute());
ArrayList<Annotation> anns=new ArrayList<Annotation>(c);



try {
	
	
    WildbookIAM plugin = IBEISIA.getPluginInstance(myShepherd.getContext());
    //List<String> iaImageIds = plugin.iaImageIds();
    HashSet<String> iaImageIds = null;
    ArrayList<Annotation> annsToSend = new ArrayList<Annotation>();
    HashSet<String> iaAnnotIds = new HashSet(plugin.iaAnnotationIds());
    ArrayList<MediaAsset> masToSend = new ArrayList<MediaAsset>();
    for (Annotation ann : anns) {
        if (iaAnnotIds.contains(ann.getAcmId())) continue;
        MediaAsset ma = ann.getMediaAsset();
        if (ma == null) continue; //snh #bad
        annsToSend.add(ann);
        //get iaImageIds only if we need it
        if(iaImageIds==null)iaImageIds=new HashSet(plugin.iaImageIds());
        if (iaImageIds.contains(ma.getAcmId())) continue;
        masToSend.add(ma);
    }
	
}
catch(Exception e){
	e.printStackTrace();
}
finally{
	myShepherd.rollbackDBTransaction();
	myShepherd.closeDBTransaction();

}
long t2 = System.currentTimeMillis();

%>

<p>t1: <%=t1 %>,0</p>
<p>t2: <%=t2 %>,<%=t2-t1 %></p>

</body>
</html>
