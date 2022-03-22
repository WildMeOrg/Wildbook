<!DOCTYPE html PUBLIC "-//W3C//DTD XHTML 1.0 Transitional//EN"
        "http://www.w3.org/TR/xhtml1/DTD/xhtml1-transitional.dtd">
<%@ page contentType="text/html; charset=utf-8" language="java" import="org.joda.time.LocalDateTime,
java.net.*,
org.datanucleus.api.rest.orgjson.JSONObject,
org.ecocean.identity.IBEISIA,
org.json.JSONArray,
org.ecocean.media.MediaAsset,
org.json.JSONException,
org.ecocean.Util,
java.util.zip.GZIPOutputStream,
org.ecocean.cache.*,
java.io.*,java.util.*, java.io.FileInputStream, java.io.File, java.io.FileNotFoundException, org.ecocean.*,org.ecocean.servlet.*,javax.jdo.*, java.lang.StringBuffer, java.util.Vector, java.util.Iterator, java.lang.NumberFormatException"
%>

<%!

void tryCompress(HttpServletRequest req, HttpServletResponse resp, JSONObject jo, boolean useComp) throws IOException, JSONException {
	//System.out.println("??? TRY COMPRESS ??");
    //String s = scrubJson(req, jo).toString();
    String s = jo.toString();
    if (!useComp || (s.length() < 3000)) {  //kinda guessing on size here, probably doesnt matter
        resp.getWriter().write(s);
    } else {
        resp.setHeader("Content-Encoding", "gzip");
    OutputStream o = resp.getOutputStream();
    GZIPOutputStream gz = new GZIPOutputStream(o);
        gz.write(s.getBytes());
        gz.flush();
        gz.close();
        o.close();
    }
}

%>

<%

String context="context0";
context=ServletUtilities.getContext(request);

Shepherd myShepherd=new Shepherd(context);



%>

<html>
<head>
<title>Fix Some Fields</title>

</head>


<body>
<ul>
<%



String cacheName="wbiaQueue";
JSONObject rtn = new JSONObject();

try {
	


	QueryCache qc=QueryCacheFactory.getQueryCache(context);
	if(qc.getQueryByName(cacheName)!=null && System.currentTimeMillis()<qc.getQueryByName(cacheName).getNextExpirationTimeout() && request.getParameter("refresh")==null){
		rtn=Util.toggleJSONObject(qc.getQueryByName(cacheName).getJSONSerializedQueryResult());
	}
	else{
			URL wbiaQueueUrl = IBEISIA.iaURL(context, "api/engine/job/status/");
		   	rtn = Util.toggleJSONObject(RestClient.get(wbiaQueueUrl));
	        CachedQuery cq=new CachedQuery(cacheName,Util.toggleJSONObject(rtn), false, myShepherd);
	        cq.nextExpirationTimeout=System.currentTimeMillis()+10000;
	        qc.addCachedQuery(cq);
	}
	

	//OK, let's get some metrics
	JSONObject inspectMe =rtn.getJSONObject("response").getJSONObject("json_result");
	int numJobs=0;
	int numCompletedJobs = 0;
	int numWorkingJobs = 0;
	int numQueuedJobs = 0;
	int numErrorJobs = 0;
	int numDetectionJobs = 0;
	int numIDJobs = 0;
	int sizeIDJobQueue=0;
	int sizeDetectionJobQueue=0;
	Iterator<String> keys=inspectMe.keys();
	while(keys.hasNext()){
		String jobID=keys.next();
		numJobs++;
		JSONObject job=inspectMe.getJSONObject(jobID);
		boolean working = false;
		boolean queued = false;
		if(job.getString("status").equals("completed"))numCompletedJobs++;
		if(job.getString("status").equals("working")){
			numWorkingJobs++;
			working = true;
		}
		if(job.getString("status").equals("queued")){
			numQueuedJobs++;
			queued=true;
		}
		if(job.getString("status").equals("error"))numErrorJobs++;
		if(job.getString("function").startsWith("start_detect")){
			numDetectionJobs++;
			if(working||queued)sizeDetectionJobQueue++;
		}
		if(job.getString("function").startsWith("start_identify")){
			numIDJobs++;
			if(working||queued)sizeIDJobQueue++;
		}
	}
	%>
	<li>Num jobs: <%=numJobs %></li>
	<li>Num completed jobs: <%=numCompletedJobs %></li>
	<li>Num working jobs: <%=numWorkingJobs %></li>
	<li>Num queued jobs: <%=numQueuedJobs %></li>
	<li>Num error jobs: <%=numErrorJobs %></li>
	<li>Size detection job queue: <%=sizeDetectionJobQueue %></li>
	<li>Size ID job queue: <%=sizeIDJobQueue %></li>
	<%

  
  
}
catch(Exception e){
	e.printStackTrace();
}
finally{

}

%>


</ul>

</body>
</html>
