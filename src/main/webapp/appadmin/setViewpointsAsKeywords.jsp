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
myShepherd.setAction("setViewpointsAsKeywords.jsp");
String orgName = request.getParameter("orgName");
String commit = request.getParameter("commit");
String limitStr = request.getParameter("limit");
//String filter = "this.submitterOrganization == 'Olive Ridley Project' && this.dwcDateAddedLong <= '1569542400000' && this.dwcDateAddedLong >= '1569369600000' ";
String filter = "this.submitterID == 'bpct' ";
if (orgName!=null) {
    filter = "this.submitterOrganization == '"+orgName+"' ";
}
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

System.out.println("-------------------> Specified organization is "+orgName+" commit="+commit);
System.out.println("-----------------> Got "+encs.size()+" encs as candidates...");


%>

<html>
<head>
<title>Setting missed viewpoint labels</title>

</head>

<body>
<ul>
<%

Integer limit = 0;
int count = 0;
int countSent = 0;
int countTrivial = 0;
int countSet = 0;
try {
        System.out.println("limit = "+limitStr);
        for (Encounter enc : encs) {
            if (limitStr!=null) {
                limit = Integer.valueOf(limitStr);
            }
            if (count>limit) break;
	    System.out.println("CURRENT ENCOUNTER: "+enc.getCatalogNumber()); 
            count++;
            System.out.println("count = "+count);
            boolean isTrivial = false;
            for (MediaAsset ma: enc.getMedia()) {
                List<Annotation> anns = ma.getAnnotations();
                    System.out.println("Has annotations..."+anns.size());
                    if (anns.size()>0) {
                            for (Annotation ann : anns) {
                                String vp = ann.getViewpoint();
                                System.out.println("---------------> current vp: "+vp);
                                if (vp==null||"null".equals(vp)) continue;

                                Taxonomy tax =  ann.getTaxonomy(myShepherd);
                                String kwName = RestKeyword.getKwNameFromIaViewpoint(vp, tax, context);
                                if (kwName==null||"null".equals(kwName)) continue;
                                ArrayList<Keyword> kws = ma.getKeywords();
                                boolean hasKeyword = false;
                                for (Keyword kw : kws) {
                                    System.out.println("comparing "+kw.getReadableName()+" and "+kwName);
                                    if (kw.getReadableName().equals(kwName)) {
                                        hasKeyword = true;
                                        System.out.println("has keyword!!!");
                                    }
                                }

                                if (hasKeyword) countSet++; 

                                if ("true".equals(commit)&&!hasKeyword) {
                                    // is it a keyword? if not, create. 
                                    Keyword kw = null;
                                    if (!myShepherd.isKeyword(kwName)) {
                                        kw = new Keyword(kwName);
                                        myShepherd.storeNewKeyword(kw);
                                    } else {
                                        kw = myShepherd.getKeyword(kwName);
                                    }
                                    if (kw!=null) {
                                        System.out.println("adding and setting keyword "+kw.getReadableName());
                                    } else {
                                        System.out.println("KEYWORD STILL NULL!");
                                    }
                                    myShepherd.beginDBTransaction();
                                    ma.addKeyword(kw);
                                    myShepherd.commitDBTransaction();                                     


                                }
                                
                            }
 
                        } 
                }
        }
        myShepherd.commitDBTransaction();
} catch (Exception e){
	myShepherd.rollbackDBTransaction();
	e.printStackTrace();
}
System.out.println("------> Total encs processed: "+count+" Total viewpoint kw's that need set: "+countSet);
myShepherd.closeDBTransaction();
%>

</ul>

</body>
</html>

