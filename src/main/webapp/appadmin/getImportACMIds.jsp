<!DOCTYPE html PUBLIC "-//W3C//DTD XHTML 1.0 Transitional//EN"
        "http://www.w3.org/TR/xhtml1/DTD/xhtml1-transitional.dtd">
<%@ page contentType="text/html; charset=utf-8" language="java" import="org.joda.time.LocalDateTime,
org.joda.time.format.DateTimeFormatter,
org.joda.time.format.ISODateTimeFormat,java.net.*,
org.ecocean.grid.*,
org.ecocean.datacollection.*,
java.util.*,
org.ecocean.media.*,
org.ecocean.ia.*,
org.ecocean.identity.*,
org.ecocean.*,
org.json.JSONObject,
javax.jdo.*,
java.io.*,java.util.*, java.io.FileInputStream, java.io.File, java.io.FileNotFoundException, org.ecocean.*,org.ecocean.servlet.*,javax.jdo.*, java.lang.StringBuffer, java.util.Vector, java.util.Iterator, java.lang.NumberFormatException"%>

<%

String context="context0";
context=ServletUtilities.getContext(request);

Shepherd myShepherd=new Shepherd(context);
myShepherd.setAction("getImportACMIds.jsp");
String filter = "this.submitterOrganization == 'Olive Ridley Project' && this.dwcDateAdded.startsWith('2019-08-19') ";
Collection c = null;
List<Encounter> encs = null;
try {
    Extent encClass = myShepherd.getPM().getExtent(Encounter.class, true);
    Query acceptedEncounters = myShepherd.getPM().newQuery(encClass, filter);
    c = (Collection) (acceptedEncounters.execute());
    encs = new ArrayList<>(c);          
    //int size=c.size();
    acceptedEncounters.closeAll();
        
} catch (Exception e) {
    System.out.println("Exception retrieving encounters...");
    e.printStackTrace();
}
System.out.println();
System.out.println("Retrieving ACM Id's for "+encs.size()+" encounters. ");
System.out.println("Filter = "+filter);
System.out.println("++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++");
System.out.println();

%>

<html>
<head>
<title>Get ACM Ids for Import</title>

</head>

<body>
<ul>
<%


try {
    int hasHead = 0;
    int isTrivial = 0;
    int allAnns = 0;

    List<Annotation> trivs = new ArrayList<>();

    for (Encounter enc : encs) {
    	//System.out.println("Count: "+allAnns);
        List<Annotation> anns = enc.getAnnotations();
        if (anns.size()>0) {
            for (Annotation ann : anns) {
                
                if (ann!=null&&ann.isTrivial()) {
                    isTrivial++;
                    trivs.add(ann);
                    continue;
                }

		        System.out.println(ann.getAcmId());
                String iaClass = ann.getAcmId();
                if (iaClass!=null&&(iaClass.equals("turtle_green+head")||iaClass.equals("turtle_hawksbill+head"))) hasHead++;
                allAnns++; 
            }
        }
    }

    System.out.println();
    System.out.println("Total "+allAnns+" annotations including "+hasHead+" heads and "+isTrivial+" trivial.");
    System.out.println();
    System.out.println("There are "+trivs.size()+" trivial Annotations (annotationID) for import encounters: ");

    for (Annotation ann : trivs) {
        System.out.println(ann.getId());
    }




} catch (Exception e){
	myShepherd.rollbackDBTransaction();
	e.printStackTrace();
}

myShepherd.closeDBTransaction();



%>

</ul>

</body>
</html>