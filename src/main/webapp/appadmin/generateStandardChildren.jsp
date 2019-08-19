

<!DOCTYPE html PUBLIC "-//W3C//DTD XHTML 1.0 Transitional//EN"
        "http://www.w3.org/TR/xhtml1/DTD/xhtml1-transitional.dtd">
<%@ page contentType="text/html; charset=utf-8" language="java" import="org.joda.time.LocalDateTime,
org.joda.time.format.DateTimeFormatter,
org.joda.time.format.ISODateTimeFormat,java.net.*,
org.ecocean.grid.*,
org.ecocean.datacollection.*,
java.util.*,
org.ecocean.media.*,
org.ecocean.*,
javax.jdo.*,
java.io.*,java.util.*, java.io.FileInputStream, java.io.File, java.io.FileNotFoundException, org.ecocean.*,org.ecocean.servlet.*,javax.jdo.*, java.lang.StringBuffer, java.util.Vector, java.util.Iterator, java.lang.NumberFormatException"%>

<%
String context="context0";
context=ServletUtilities.getContext(request);

Shepherd myShepherd=new Shepherd(context);
myShepherd.setAction("generateStandardChildren.jsp");
String orgName = request.getParameter("orgName");
String commit = request.getParameter("commit");
String limitStr = request.getParameter("limit");
String filter = "this.submitterOrganization == 'Olive Ridley Project' ";
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
<title>Generating Standard Children For Organization</title>

</head>

<body>
<ul>
<%

//int limit = 0;
int count = 0;
try {
    if ("true".equals(commit)) {
        System.out.println("limit = "+limitStr);
        for (Encounter enc : encs) {
            //System.out.println("count = "+count);
            if (limitStr!=null&&count<Integer.valueOf(limitStr)) {
                List<MediaAsset> mas = enc.getMedia(); 
                System.out.println("has media? -----> "+mas.toString());
                for (MediaAsset ma : mas) {
                    List<MediaAsset> kids = ma.findChildren(myShepherd);
                    if ((kids == null) || (kids.size() < 1)) {
                        ma.setMetadata();
                        ma.updateStandardChildren(myShepherd);
                        System.out.println("Updated Standard children on MA id#"+ma.getId());
                    }
                }
            } else {
                break;
            } 
            count++;
        }
        myShepherd.commitDBTransaction();
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