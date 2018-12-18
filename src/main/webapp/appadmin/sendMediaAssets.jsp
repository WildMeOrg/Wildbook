<!DOCTYPE html PUBLIC "-//W3C//DTD XHTML 1.0 Transitional//EN"
        "http://www.w3.org/TR/xhtml1/DTD/xhtml1-transitional.dtd">
<%@ page contentType="text/html; charset=utf-8" language="java" import="org.joda.time.LocalDateTime,
org.joda.time.format.DateTimeFormatter,
org.joda.time.format.ISODateTimeFormat,java.net.*,
org.ecocean.grid.*,org.ecocean.media.*,
org.ecocean.identity.*,
org.ecocean.ia.IA,
org.ecocean.ia.Task,
org.ecocean.RestClient,
org.json.JSONObject,
java.io.*,java.util.*,java.net.*, java.io.FileInputStream, java.io.File, java.io.FileNotFoundException, org.ecocean.*,org.ecocean.servlet.*,javax.jdo.*, java.lang.StringBuffer, java.util.Vector, java.util.Iterator, java.lang.NumberFormatException"%>
<%
String context="context0";
context=ServletUtilities.getContext(request);
Shepherd myShepherd=new Shepherd(context);

%>
<html>
<head>
<title>Send MediaAssets</title>
</head>
<body>
  <h1>Send media assets with unity features only.</h1>
<ul>
<%
int count = 0;
try {

    ArrayList<MediaAsset> mas = myShepherd.getAllMediaAssetsAsArray();
    List<MediaAsset> toSendMas = new ArrayList<>();
    for (MediaAsset ma : mas) {
        //ArrayList<Annotation> anns = new ArrayList<>();
        if (!"complete".equals(ma.getDetectionStatus())) {
            ArrayList<Annotation> anns = ma.getAnnotations();
            if (anns.size()>0) {
                boolean validToSend = true;
                for (Annotation ann : anns) {
                    if (ma.getParent()!=null) { 
                        validToSend = false;
                    }
                }
                if (validToSend) {
                    toSendMas.add(ma);
                    count++;
                }
            }
        }
    }

    //MediaAsset ma = myShepherd.getMediaAsset("1829");

    //List<MediaAsset> lma = new ArrayList<>();
    //lma.add(ma);
    //Task tsk = IA.intakeMediaAssets(toSendMas);
    //String tskid = tsk.getId();


} catch (Exception e) {
	System.out.println("!!! ----------- > An error occurred sending media assets to IA...");
	e.printStackTrace();
	myShepherd.rollbackDBTransaction();
} finally {
	myShepherd.closeDBTransaction();
	myShepherd=null;
}

%>

<p>Task id for this test MA sent</p>
<p>Sending <%=count%> media assets to get detection run!</p>

</ul>
</body>
</html>
