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
org.ecocean.ia.plugin.WildbookIAM,
java.io.*,java.util.*, java.io.FileInputStream, java.io.File, java.io.FileNotFoundException, org.ecocean.*,org.ecocean.servlet.*,javax.jdo.*, java.lang.StringBuffer, java.util.Vector, java.util.Iterator, java.lang.NumberFormatException"%>

<%
String context="context0";
context=ServletUtilities.getContext(request);

Shepherd myShepherd=new Shepherd(context);
myShepherd.setAction("generateStandardChildren.jsp");
String orgName = request.getParameter("orgName");
String commit = request.getParameter("commit");
String limitStr = request.getParameter("limit");
String filter = "this.submitterID == 'seanmbarker'";
//String filter = "this.submitterID == 'ORP' && this.catalogNumber == '064ca892-ff33-4769-bdae-46a032575719' ";
//String filter = "this.catalogNumber == '064ca892-ff33-4769-bdae-46a032575719' ";

if (orgName!=null) {
    filter = "this.submitterOrganization == '"+orgName+"' ";
}
Collection c = null;
List<Encounter> encs = null;
try {
    Extent encClass = myShepherd.getPM().getExtent(Encounter.class, true);
    Query acceptedEncounters = myShepherd.getPM().newQuery(encClass, filter);
    //acceptedEncounters.setRange(0,30);
    c = (Collection) (acceptedEncounters.execute());
    encs = new ArrayList<>(c);          
    //int size=c.size();
    acceptedEncounters.closeAll();
        
} catch (Exception e) {
    System.out.println("Exception retrieving encounters...");
    e.printStackTrace();
} 

System.out.println("-------------------> Specified organization is "+orgName+" commit="+commit);
//System.out.println("-----------------> Got "+encs.size()+" encs as candidates... Should be around 9000 (for ORP import query)");


%>

<html>
<head>
<title>Sending Organization Batch to Detection</title>

</head>

<body>
<ul>
<%

//int limit = 0;
int count = 0;
try {
        //System.out.println("COMMITING!");
        System.out.println("limit = "+limitStr);
	    ArrayList<MediaAsset> allMas = new ArrayList<MediaAsset>();

        System.out.println("ENCOUNTERS TO DELETE : "+encs.size());

        if ("true".equals(commit)) {
            for (Encounter enc : encs) {

                if (limitStr!=null&&count<Integer.valueOf(limitStr)) {
                    ArrayList<Annotation> anns = enc.getAnnotations();
                    for (Annotation ann : anns) {
                        enc.removeAnnotation(ann);
                        myShepherd.updateDBTransaction();
                        myShepherd.throwAwayAnnotation(ann);
                        myShepherd.updateDBTransaction();
                    }
        
                    Occurrence occ = myShepherd.getOccurrence(enc);
                    if (occ!=null) {
                        occ.removeEncounter(enc);
                        myShepherd.updateDBTransaction();
                        if(occ.getEncounters().size()==0) {
                          myShepherd.throwAwayOccurrence(occ);
                          myShepherd.updateDBTransaction();
                        }
                    }
                   
                    MarkedIndividual mark = myShepherd.getMarkedIndividualQuiet(enc.getIndividualID());
                    if(mark!=null) {
                        mark.removeEncounter(enc);
                        myShepherd.updateDBTransaction();
                        if(mark.getEncounters().size()==0) {
                          myShepherd.throwAwayMarkedIndividual(mark);
                          myShepherd.updateDBTransaction();
                        }
                    }

                    try {
                        myShepherd.throwAwayEncounter(enc);
                    } 
                    catch (Exception e) {
                        System.out.println("Exception on throwAwayEncounter!!");
                        e.printStackTrace();
                    }
         
                    myShepherd.updateDBTransaction();


                } else {
                    break;
                }

                count++;
        }


        }
        myShepherd.commitDBTransaction();
} catch (Exception e){
	myShepherd.rollbackDBTransaction();
	e.printStackTrace();
}

myShepherd.closeDBTransaction();
%>

</ul>

</body>
</html>
