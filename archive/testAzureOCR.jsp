<!DOCTYPE html PUBLIC "-//W3C//DTD XHTML 1.0 Transitional//EN"
        "http://www.w3.org/TR/xhtml1/DTD/xhtml1-transitional.dtd">
<%@ page contentType="text/html; charset=utf-8" language="java" import="org.joda.time.LocalDateTime,
org.joda.time.format.DateTimeFormatter,
org.joda.time.format.ISODateTimeFormat,java.net.*,
org.ecocean.grid.*,
java.io.*,java.util.*, java.io.FileInputStream, 
java.io.File, java.io.FileNotFoundException, org.ecocean.*,
org.ecocean.servlet.*,org.ecocean.media.*,javax.jdo.*, 
java.lang.StringBuffer, java.util.Vector, java.util.Iterator, 
java.lang.NumberFormatException,
org.ecocean.ai.nlp.*,
org.ecocean.ai.nmt.azure.*,
org.ecocean.ai.ocr.azure.*,
org.ecocean.ai.ocr.google.*"%>

<%

String context="context0";
context=ServletUtilities.getContext(request);

Shepherd myShepherd=new Shepherd(context);

%>

<html>
<head>
<title>Azure OCR Testing</title>

</head>


<body>

<h3>Azure OCR Testing</h3>

<%
ArrayList<MediaAsset> results = new List<>();
String resultString = "";
try {
    	String filter="SELECT FROM org.ecocean.media.MediaAsset WHERE store instanceof org.ecocean.media.YouTubeAssetStore";
		
		Query query = myShepherd.getPM().newQuery(filter);
        query.setRange(1,6);

		Collection c = (Collection) (query.execute());
		results=new ArrayList<MediaAsset>(c);
		ArrayList<MediaAsset> assets=new ArrayList<MediaAsset>(10);
		query.closeAll();

        System.out.println("Got "+results.size()+" MediaAssets from the query SELECT FROM org.ecocean.media.MediaAsset WHERE store instanceof org.ecocean.media.YouTubeAssetStore, when the setRange values were (1,11)");
		

		if (results.size()>0) {
            for(int i=0;i<10;i++){
                MediaAsset mas=results.get(i);
                ArrayList<MediaAsset> frames= YouTubeAssetStore.findFrames(mas, myShepherd);
                if ((frames!=null)&&(frames.size()>0)) {
                    assets.addAll(frames);
                }
            }
        }


        resultString = AzureOcr.detectText(assets, "en");
		
} catch (Exception e) {

%>

    <p>Reported error: <%=e.getMessage() %> <%=e.getStackTrace().toString() %></p>

<%

	e.printStackTrace();

}


//	myShepherd.rollbackDBTransaction();
	myShepherd.closeDBTransaction();

%>

<br><hr>
<h4>Results:</h4><br>
<p><%= resultString %></p>



</body>
</html>
