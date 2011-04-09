<!DOCTYPE html PUBLIC "-//W3C//DTD XHTML 1.0 Transitional//EN"
        "http://www.w3.org/TR/xhtml1/DTD/xhtml1-transitional.dtd">
<%@ page contentType="text/html; charset=utf-8" language="java"
	import="java.util.Calendar,java.util.GregorianCalendar,java.util.Properties, java.io.FileInputStream, java.io.File, java.io.FileNotFoundException, java.util.StringTokenizer, org.ecocean.*"%>


<%

//handle some cache-related security
response.setHeader("Cache-Control","no-cache"); //Forces caches to obtain a new copy of the page from the origin server
response.setHeader("Cache-Control","no-store"); //Directs caches not to store the page under any circumstance
response.setDateHeader("Expires", 0); //Causes the proxy cache to see the page as "stale"
response.setHeader("Pragma","no-cache"); //HTTP 1.0 backward compatibility 


//set up our calendar limits
String locCode="NONE";
if((request.getParameter("locCode")!=null)&&(!request.getParameter("locCode").equals(""))) {
				locCode=request.getParameter("locCode");
}

//let's load encounterSearch.properties
String langCode="en";
if(session.getAttribute("langCode")!=null){langCode=(String)session.getAttribute("langCode");}
Properties calprops=new Properties();
calprops.load(getClass().getResourceAsStream("/bundles/"+langCode+"/calendar.properties"));
	


%>

<html>

<head>

<title><%=CommonConfiguration.getHTMLTitle() %></title>
<meta http-equiv="Content-Type" content="text/html; charset=utf-8" />
<meta name="Description"
	content="<%=CommonConfiguration.getHTMLDescription() %>" />
<meta name="Keywords"
	content="<%=CommonConfiguration.getHTMLKeywords() %>" />
<meta name="Author" content="<%=CommonConfiguration.getHTMLAuthor() %>" />
<link href="<%=CommonConfiguration.getCSSURLLocation(request) %>"
	rel="stylesheet" type="text/css" />

<link rel="shortcut icon"
	href="<%=CommonConfiguration.getHTMLShortcutIcon() %>" />

</head>

<script src="codebase/dhtmlxscheduler.js?v=091201"
	type="text/javascript" charset="utf-8"></script>
<script src="codebase/ext/dhtmlxscheduler_agenda_view.js?v=091201"
	type="text/javascript" charset="utf-8"></script>
<script src="codebase/ext/dhtmlxscheduler_year_view.js?v=091201"
	type="text/javascript" charset="utf-8"></script>
<script src="codebase/ext/dhtmlxscheduler_readonly.js"
	type="text/javascript" charset="utf-8"></script>


<link rel="stylesheet" href="codebase/dhtmlxscheduler.css"
	type="text/css" media="screen" title="no title" charset="utf-8">
<link rel="stylesheet" href="codebase/ext/dhtmlxscheduler_ext.css"
	type="text/css" title="no title" charset="utf-8"><script
	type="text/javascript" charset="utf-8">
	function init() {

		  var format = scheduler.date.date_to_str("");
		  scheduler.templates.event_bar_date=function(date){
		     return format(date);
		  }
		  scheduler.templates.event_bar_text=function(start,end,event){
			   return event.text+"<br>";
		  }
		
		scheduler.config.xml_date="%Y-%m-%d %H:%i";
		scheduler.config.dblclick_create = false;
		scheduler.config.readonly_form = true;
		scheduler.config.date_step = "5";
		scheduler.attachEvent("onBeforeDrag", function (event_id, mode, native_event_object){
		      return false;
		});
		scheduler.attachEvent("onClick", function (event_id, native_event_object){
			var myLink='http://'+'<%=CommonConfiguration.getURLLocation(request)%>'+'/encounters/encounter.jsp?number='+event_id;
			window.open(myLink,'mywindow','')
		});
		
		scheduler.config.show_loading=true;
		<%
		
		
		String dateString="";
		if(request.getParameter("scDate")!=null){
			dateString=request.getParameter("scDate");
		}
		else{
			
			Calendar cal=Calendar.getInstance();
			int nowYear = cal.get(Calendar.YEAR);
			int nowMonth = cal.get(Calendar.MONTH)+1;
			Shepherd myShepherd = new Shepherd();
			myShepherd.beginDBTransaction();
			try{
				
				nowYear = myShepherd.getLastSightingYear();
				nowMonth = myShepherd.getLastMonthOfSightingYear(nowYear);
				
			}
			catch(Exception e){
				e.printStackTrace();
			}
			finally{
				myShepherd.rollbackDBTransaction();
				myShepherd.closeDBTransaction();
			}

			dateString=(new Integer(nowMonth)).toString()+"/1/"+(new Integer(nowYear)).toString();
		}

		%>
			var date = new Date();
			var str = "<%=dateString%>";
			var dateArray = str.split("/")
			date.setFullYear(parseInt(dateArray[2]));
			date.setMonth(parseInt(dateArray[0])-1);  // months indexed as 0-11, substract 1
			date.setDate(parseInt(dateArray[1])); 
	
			scheduler.init('scheduler_here', date,"month");
			scheduler.setLoadMode("month");
			scheduler.load("../CalendarXMLServer2?locCode=<%=locCode%>");
		
	}
</script>

<body onload="init();"
	style='padding: 0; text-align: center; margin: 0 auto; overflow: auto;'>
<div id="wrapper" style=''>
<div id="page" style=''>
<jsp:include page="../header.jsp" flush="true">
	<jsp:param name="isAdmin" value="<%=request.isUserInRole(\"admin\")%>" />
</jsp:include>

<div id="main" style='overflow: auto;'>

<div id="maincol-calendar" style='overflow: auto; z-index: 0;'>
<div id="maintext" style='overflow: auto; z-index: 0;'>

<h1><%=calprops.getProperty("title") %></h1>

<div align="center" id="scheduler_here" class="dhx_cal_container"
	style='width: 810px; height: 800px; overflow: auto; margin-left: auto; margin-right: auto; position: relative; z-index: 0;'>
<div align="center" class="dhx_cal_navline" style='z-index: 0;'>

<div class="dhx_cal_prev_button" style='z-index: 0;'>&nbsp;</div>
<div class="dhx_cal_next_button" style='z-index: 0;'>&nbsp;</div>

<div class="dhx_cal_date" style='z-index: 0;'></div>

<div class="dhx_cal_tab" name="month_tab"
	style="right: 204px; z-index: 0;"></div>


</div>
<div class="dhx_cal_header"></div>
<div class="dhx_cal_data" style="overflow: auto;"></div>
</div>


</div>
<!-- end maintext --> <jsp:include page="../footer.jsp" flush="true" />

</div>
<!-- end main-wide --></div>
<!-- end page --></div>
<!--end wrapper -->
</body>
</html>

