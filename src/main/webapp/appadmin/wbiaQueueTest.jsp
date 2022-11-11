<!DOCTYPE html PUBLIC "-//W3C//DTD XHTML 1.0 Transitional//EN"
        "http://www.w3.org/TR/xhtml1/DTD/xhtml1-transitional.dtd">
<%@ page contentType="text/html; charset=utf-8" language="java" import="org.joda.time.LocalDateTime,
java.net.*,
org.ecocean.ia.*,
org.ecocean.servlet.ServletUtilities
"
%>



<%

String context="context0";
context=ServletUtilities.getContext(request);



%>

<html>
<head>
<title>Fix Some Fields</title>

</head>


<body>
<ul>
<%




try {
	


	
	%>

	<li>Size detection job queue: <%=WbiaQueueUtil.getSizeDetectionJobQueue(false) %></li>
	<li>Size ID job queue: <%=WbiaQueueUtil.getSizeIDJobQueue(false) %></li>
	<li>Number working jobs: <%=WbiaQueueUtil.getNumWorkingJobs(false) %></li>
	<li>Number queued jobs: <%=WbiaQueueUtil.getNumQueuedJobs(false) %></li>
	<li>Job status: <%=WbiaQueueUtil.getStatusWBIAJob("00a3c654-8a16-49c9-a2ee-4f50f2ffcc91", false) %></li>
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
