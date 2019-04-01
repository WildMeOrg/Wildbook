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
String num = request.getParameter("id");
String start = request.getParameter("start");
String end = request.getParameter("end");
%>
<html>
<head>
<title>Send Media Assets</title>
</head>
<body>
  <h1>Send media assets to IA intake.</h1>
<ul>
<%
try {
    Integer currentInt = Integer.parseInt(start);
    Integer endInt = Integer.parseInt(end);
    if ((currentInt!=null&&endInt!=null)&&currentInt<endInt) {
        while (curentInt <= endInt) {
            MediaAsset ma = myShepherd.getMediaAsset(currentInt);
            ArrayList<Annotation> anns = ma.getAnnotations();
            for (Annotation ann : anns) {
                if (!ann.isTrivial()) {continue;}
                System.out.println("Not trivial, skipping Ann MA with id="+ma.getId());
            }
            List<MediaAsset> lma = new ArrayList<>();
            System.out.println("==========================================================");
            Task topTask = new Task(Util.generateUUID());
            myShepherd.storeNewTask(topTask);
            System.out.println("New TopTask ID = "+topTask.getId());
            lma.add(ma);
            Task tsk = IA.intakeMediaAssets(myShepherd,lma);
            topTask.addChild(tsk);
            System.out.println("New intake MA taskId = "+tsk.getId());
            myShepherd.getPM().refresh(ma);
            currentInt++;
        }
    }
} catch (Exception e) {
	System.out.println("!!! ----------- > An error occurred sending media assets to IA...");
	e.printStackTrace();
	myShepherd.rollbackDBTransaction();
} finally {
	myShepherd.closeDBTransaction();
	myShepherd=null;
}

%>

<p>Sending media asset id=<%=num%></p>

</ul>
</body>
</html>

