<%@ page contentType="text/html; charset=utf-8" language="java"
         import="org.ecocean.servlet.ServletUtilities,
         org.ecocean.*,
         java.util.Calendar, 
         java.util.Properties,
         org.joda.time.format.DateTimeFormatter,
		 org.joda.time.format.ISODateTimeFormat,
		 org.joda.time.DateTime" %>


<%

String context="context0";
context=ServletUtilities.getContext(request);


  //handle some cache-related security
  response.setHeader("Cache-Control", "no-cache"); //Forces caches to obtain a new copy of the page from the origin server
  response.setHeader("Cache-Control", "no-store"); //Directs caches not to store the page under any circumstance
  response.setDateHeader("Expires", 0); //Causes the proxy cache to see the page as "stale"
  response.setHeader("Pragma", "no-cache"); //HTTP 1.0 backward compatibility


//set up our calendar limits
  String locCode = "NONE";
  if ((request.getParameter("locCode") != null) && (!request.getParameter("locCode").equals(""))) {
    locCode = request.getParameter("locCode");
  }

//let's load encounterSearch.properties
  //String langCode = "en";
  String langCode=ServletUtilities.getLanguageCode(request);
  
  Properties calprops = new Properties();
  //calprops.load(getClass().getResourceAsStream("/bundles/" + langCode + "/calendar.properties"));
  calprops = ShepherdProperties.getProperties("calendar.properties", langCode, context);



%>

<style type="text/css">
   #tabmenu {
    color: #000;
    border-bottom: 1px solid #CDCDCD;
    margin: 12px 0px 0px 0px;
    padding: 0px;
    z-index: 1;
    padding-left: 10px
  }

  #tabmenu li {
    display: inline;
    overflow: hidden;
    list-style-type: none;
  }

  #tabmenu a, a.active {
    color: #000;
    background: #E6EEEE;
     
    border: 1px solid #CDCDCD;
    padding: 2px 5px 0px 5px;
    margin: 0;
    text-decoration: none;
    border-bottom: 0px solid #FFFFFF;
  }

  #tabmenu a.active {
    background: #8DBDD8;
    color: #000000;
    border-bottom: 1px solid #8DBDD8;
  }

  #tabmenu a:hover {
    color: #000;
    background: #8DBDD8;
  }

  #tabmenu a:visited {
    
  }

  #tabmenu a.active:hover {
    color: #000;
    border-bottom: 1px solid #8DBDD8;
  }
</style>


 <jsp:include page="../header.jsp" flush="true"/>


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
      type="text/css" title="no title" charset="utf-8">
<script
  type="text/javascript" charset="utf-8">
  function init() {

    var format = scheduler.date.date_to_str("");
    scheduler.templates.event_bar_date = function(date) {
      return format(date);
    }
    scheduler.templates.event_bar_text = function(start, end, event) {
      return event.text + "<br>";
    }

    scheduler.config.xml_date = "%Y-%m-%d %H:%i";
    scheduler.config.dblclick_create = false;
    scheduler.config.readonly_form = true;
    scheduler.config.date_step = "5";
    scheduler.attachEvent("onBeforeDrag", function (event_id, mode, native_event_object) {
      return false;
    });
    scheduler.attachEvent("onClick", function (event_id, native_event_object) {
      var myLink = '//' + '<%=CommonConfiguration.getURLLocation(request)%>' + '/encounters/encounter.jsp?number=' + event_id;
      window.open(myLink, 'mywindow', '')
    });

    scheduler.config.show_loading = true;
  <%


     String dateString="";
     if(request.getParameter("scDate")!=null){
       dateString=request.getParameter("scDate");
     }
     else if(((request.getParameter("datepicker1")!=null))&&(!request.getParameter("datepicker1").trim().equals(""))){
    	 DateTimeFormatter parser = ISODateTimeFormat.dateTimeParser();
         org.joda.time.DateTime date1 = parser.parseDateTime(request.getParameter("datepicker1"));
        
    	 int thisMonth=1;
    	 if(date1.getMonthOfYear()>1){thisMonth=date1.getMonthOfYear();}
    	 dateString=thisMonth+"/1/"+date1.getYear();
       }
     else{
       Calendar cal=Calendar.getInstance();
       int nowYear = cal.get(Calendar.YEAR);
       int nowMonth = cal.get(Calendar.MONTH)+1;
       dateString=(new Integer(nowMonth)).toString()+"/1/"+(new Integer(nowYear)).toString();
     }

     %>
    var date = new Date();
    var str = "<%=dateString%>";
    var dateArray = str.split("/")
    date.setFullYear(parseInt(dateArray[2]));
    date.setMonth(parseInt(dateArray[0]) - 1);  // months indexed as 0-11, substract 1
    date.setDate(parseInt(dateArray[1]));

    scheduler.init('scheduler_here', date, "month");
    scheduler.setLoadMode("month");
    scheduler.load("../CalendarXMLServer?<%=request.getQueryString() %>");

  }
</script>

<div class="container maincontent">

    

      <h1><%=calprops.getProperty("titleSearch") %></h1>

<%
if(request.getParameter("scDate")==null){
%>

      <ul id="tabmenu">

        <li><a
          href="../encounters/searchResults.jsp?<%=request.getQueryString() %>"><%=calprops.getProperty("table")%>
        </a></li>
        <li><a
          href="../encounters/thumbnailSearchResults.jsp?<%=request.getQueryString() %>"><%=calprops.getProperty("matchingImages")%>
        </a></li>
        <li><a
          href="../encounters/mappedSearchResults.jsp?<%=request.getQueryString() %>"><%=calprops.getProperty("mappedResults")%>
        </a></li>
        <li><a class="active"><%=calprops.getProperty("resultsCalendar")%>
        </a></li>
              <li><a
     href="../encounters/searchResultsAnalysis.jsp?<%=request.getQueryString() %>"><%=calprops.getProperty("analysis")%>
   </a></li>
        <li><a
     href="../encounters/exportSearchResults.jsp?<%=request.getQueryString() %>"><%=calprops.getProperty("export")%>
   </a></li>

      </ul>
      <p></p>
      
      <%
}
      %>

      <div id="maincol-calendar" style='overflow: auto; z-index: 0;'>
        <div id="maintext" style='overflow: auto; z-index: 0;'>

        
          <div align="center" id="scheduler_here" class="dhx_cal_container"
               style='width: 810px; height: 800px; overflow: auto; margin-left: auto; margin-right: auto; position: relative; z-index: 0;'>
            	
            	<div align="center" class="dhx_cal_navline" style='z-index: 0;'>

              		<div class="dhx_cal_prev_button" style='z-index: 0;'>&nbsp;</div>
              		<div class="dhx_cal_next_button" style='z-index: 0;'>&nbsp;</div>

              		<div class="dhx_cal_date" style='z-index: 0;'></div>

              		<div class="dhx_cal_tab" name="month_tab" style="right: 204px; z-index: 0;"></div>


            	</div>
         <div class="dhx_cal_header"></div>
      <div class="dhx_cal_data" style="overflow: auto;"></div>
    </div>
</div>

        </div>
        </div>

<jsp:include page="../footer.jsp" flush="true" />

<script>

init();
</script>

