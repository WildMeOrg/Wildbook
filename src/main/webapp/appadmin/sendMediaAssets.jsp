<!DOCTYPE html PUBLIC "-//W3C//DTD XHTML 1.0 Transitional//EN"
        "http://www.w3.org/TR/xhtml1/DTD/xhtml1-transitional.dtd">
<%@ page contentType="text/html; charset=utf-8" language="java" import="org.joda.time.LocalDateTime,
org.joda.time.format.DateTimeFormatter,
org.joda.time.format.ISODateTimeFormat,java.net.*,
org.ecocean.grid.*,org.ecocean.media.*,org.ecocean.*,
org.ecocean.identity.*,
org.ecocean.ia.IA,
org.ecocean.ia.Task,
org.ecocean.ia.plugin.WildbookIAM,
org.ecocean.RestClient,
org.json.JSONObject,
java.io.*,java.util.*,java.net.*, java.io.FileInputStream, java.io.File, java.io.FileNotFoundException, org.ecocean.*,org.ecocean.servlet.*,javax.jdo.*, java.lang.StringBuffer, java.util.Vector, java.util.Iterator, java.lang.NumberFormatException"%>
<%
String context="context0";
context=ServletUtilities.getContext(request);
Shepherd myShepherd=new Shepherd(context);
myShepherd.setAction("sendMediaAssets.jsp");
%>
<html>
<head>
<title>Send MediaAssets</title>
</head>
<body>
  <h1>Send media assets with unity features only.</h1>
<ul>
<%
String tskid = "";
int count = 0;
try {
    //myShepherd.beginDBTransaction();
    ArrayList<MediaAsset> mas = myShepherd.getAllMediaAssetsAsArray();
    List<MediaAsset> toSendMas = new ArrayList<>();
    for (MediaAsset ma : mas) {
        //ArrayList<Annotation> anns = new ArrayList<>();
        if (!"complete".equals(ma.getDetectionStatus())) {
            ArrayList<Annotation> anns = ma.getAnnotations();
            if (anns.size()>0) {
                boolean validToSend = true;
                for (Annotation ann : anns) {
                    if (ma.getParent(myShepherd)!=null) {
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

      MediaAsset ma = myShepherd.getMediaAsset("2");

      List<MediaAsset> lma = new ArrayList<>();
//    for (int i=0;i<5;i++) {
//    	lma.clear();
//	MediaAsset ma = toSendMas.get(i);
//	System.out.println("METADATA: "+ma.getMetadata());
//    	lma.add(ma);
//    	Task tsk = IA.intakeMediaAssets(myShepherd, lma);
//    	System.out.println(tsk.getId());
//    }
    System.out.println("==========================================================");
    Task topTask = new Task(Util.generateUUID());
    myShepherd.storeNewTask(topTask);
    System.out.println("New TopTask ID = "+topTask.getId());
    lma.add(ma);
    Task tsk = IA.intakeMediaAssets(myShepherd,lma);
    topTask.addChild(tsk);
    System.out.println("New intake MA taskId = "+tsk.getId());
    //myShepherd.getPM().refresh(ma);
    List<Task> allTasksForMA = ma.getRootIATasks(myShepherd);
    for (Task rootTask : allTasksForMA) {
	System.out.println("Saved Root Task: "+rootTask.getId());
    }
    List<Annotation> newAnns = ma.getAnnotations();
    Task tsk2 = IA.intakeAnnotations(myShepherd,newAnns);
    myShepherd.storeNewTask(tsk2);
    System.out.println("New intake Annd taskId = "+tsk2.getId());
    tsk.addChild(tsk2);
    tsk2.setParent(tsk);
    myShepherd.beginDBTransaction();
    myShepherd.getPM().refresh(ma);
    //tskid = tsk2.getId(); 
    System.out.println("===========================================================");


} catch (Exception e) {
	System.out.println("!!! ----------- > An error occurred sending media assets to IA...");
	e.printStackTrace();
	myShepherd.rollbackDBTransaction();
} finally {
	myShepherd.closeDBTransaction();
	myShepherd=null;
}

%>

<p>Task id for this test MA sent <%=tskid%></p>
<p>Sending <%=count%> media assets to get detection run!</p>

</ul>
</body>
</html>

