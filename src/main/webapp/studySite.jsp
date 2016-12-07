<%@ page contentType="text/html; charset=utf-8" language="java"
  import="
    org.ecocean.servlet.ServletUtilities,
    org.ecocean.*,
    org.ecocean.datacollection.*,
    javax.jdo.Extent,
    javax.jdo.Query,
    java.io.File,
    java.util.List,
    java.util.ArrayList,
    java.util.Properties,
    java.util.Enumeration,
    java.lang.reflect.Method,
    org.ecocean.security.Collaboration" %>


<jsp:include page="header.jsp" flush="true"/>
<!-- IMPORTANT style import for table printed by ClassEditTemplate.java -->
<link rel="stylesheet" href="css/classEditTemplate.css" />
<script src="javascript/timepicker/jquery-ui-timepicker-addon.js"></script>
<script type="text/javascript" src="javascript/classEditTemplate.js"></script>




<%

  String context="context0";
  context=ServletUtilities.getContext(request);
  Shepherd myShepherd = new Shepherd(context);

  //handle some cache-related security
  response.setHeader("Cache-Control", "no-cache"); //Forces caches to obtain a new copy of the page from the origin server
  response.setHeader("Cache-Control", "no-store"); //Directs caches not to store the page under any circumstance
  response.setDateHeader("Expires", 0); //Causes the proxy cache to see the page as "stale"
  response.setHeader("Pragma", "no-cache"); //HTTP 1.0 backward compatibility

  //setup data dir
  String rootWebappPath = getServletContext().getRealPath("/");
  File webappsDir = new File(rootWebappPath).getParentFile();
  File shepherdDataDir = new File(webappsDir, CommonConfiguration.getDataDirectoryName(context));
  Properties props = new Properties();
  String langCode=ServletUtilities.getLanguageCode(request);

  String studySiteID = request.getParameter("number");
  int nFieldsPerSubtable = 8;

  System.out.println("beginning studySite.jsp!");


  StudySite sitey;
  if (studySiteID!=null) {
    sitey = myShepherd.getStudySite(studySiteID);
    System.out.println("myShepherd grabbed StudySite #"+studySiteID);
  }
  else {
    System.out.println("new StudySite error!: myShepherd failed to find a StudySite # upon loading studySite.jsp");
    sitey = new StudySite(Util.generateUUID()); // TODO: fix this case
    myShepherd.storeNewStudySite(sitey);
    studySiteID = sitey.getID();
  }

  String[] studySiteFieldGetters = new String[]{"getName", "getTypeOfSite", "getLatitude","getLongitude", "getComments"};

  String[] studySiteFieldDTGetters = new String[]{"getDate", "getDateEnd"};


  String saving = request.getParameter("save");



  boolean needToSave = (saving != null);


  if (needToSave) {
    System.out.println("");
    System.out.println("STUDYSITE.JSP: Saving updated info...");
    Enumeration en = request.getParameterNames();


    while (en.hasMoreElements()) {
      String pname = (String) en.nextElement();
      String value = request.getParameter(pname);
      System.out.println("parsing parameter "+pname);
      if (pname.indexOf("stu:") == 0) {
        String methodName = "set" + pname.substring(4,5).toUpperCase() + pname.substring(5);
        String getterName = "get" + methodName.substring(3);
        System.out.println("StudySite.jsp: about to call ClassEditTemplate.updateObjectField("+sitey+", "+methodName+", "+value+");");
        ClassEditTemplate.updateObjectField(sitey, methodName, value);
      }

      else if (pname.indexOf("stu-dp-") == 0) {
        // looks like stu-dp-dsNUM: _____. now to parse the NUM
        String beforeColon = pname.split(":")[0];
        String dpID = beforeColon.substring(7);
        System.out.println("  looks like a change was detected on DataPoint "+dpID);
        DataPoint dp = myShepherd.getDataPoint(dpID);
        System.out.println("  now I have dp and its labeled string = "+dp.toLabeledString());
        System.out.println("  its old value = "+dp.getValueString());
        System.out.println("checkone");
        dp.setValueFromString(value);
        System.out.println("checktwo");
        System.out.println("  its new value = "+dp.getValueString());
      }
      else if (pname.indexOf("dat-") == 0) {
        String beforeColon = pname.split(":")[0];
        String dpID = beforeColon.substring(4);
        System.out.println("  Found a change on datasheet "+dpID);
      }
    }
    myShepherd.commitDBTransaction();
    System.out.println("STUDYSITE.JSP: Transaction committed");
    System.out.println("");
  }
%>



<div class="container maincontent">
  <form method="post" onsubmit="return classEditTemplate.checkBeforeDeletion()" action="studySite.jsp?number=<%=studySiteID%>" id="classEditTemplateForm">


<div class="row">
  <div class="col-xs-12">
    <h1>StudySite</h1>
    <p class="studySiteidlabel"><em>id <%=studySiteID%></em><p>

      <table class="studySite-field-table edit-table">
        <%

        if (sitey.getName()!=null) {
          try {
            System.out.println("sitey has a name; let's see if I can query on it");
            StudySite sitey2 = myShepherd.getStudySiteByName(sitey.getName());
            System.out.println("sitey2 exists = "+(sitey2!=null));
            System.out.println("sitey2 id = "+sitey2.getID());
            System.out.println("sitey2 name = "+sitey2.getName());

          }
          catch (Exception e) {
            System.out.println("Exception on getStudySiteByName!");
            e.printStackTrace();
          }
        }

        Method locationIDMeth = sitey.getClass().getMethod("getLocationID");

        ArrayList<String> possLocationsAList = CommonConfiguration.getSequentialPropertyValues("locationID", context);
        String[] possLocations = possLocationsAList.toArray(new String[possLocationsAList.size()]);

        ClassEditTemplate.printOutClassFieldModifierRow((Object) sitey, locationIDMeth, possLocations, out);



        for (String getterName : studySiteFieldGetters) {
          Method studySiteMeth = sitey.getClass().getMethod(getterName);
          if (ClassEditTemplate.isDisplayableGetter(studySiteMeth)) {
            ClassEditTemplate.printOutClassFieldModifierRow((Object) sitey, studySiteMeth, out);
          }
        }

        for (String getterName : studySiteFieldDTGetters) {
          Method studySiteMeth = sitey.getClass().getMethod(getterName);
          if (ClassEditTemplate.isDisplayableGetter(studySiteMeth)) {
            ClassEditTemplate.printOutDateTimeModifierRow((Object) sitey, studySiteMeth, out);
          }
        }

        %>
      </table>
    </div>
  </div>

  <div class="row">
    <div class="col-sm-12">
      </hr>
      <div class="submit" style="position:relative">
        <input type="submit" name="save" value="Save" />
        <span class="note" style="position:absolute;bottom:9"></span>
      </div>
    </div>

  </div>
</form>


</div>

<style>

  table.studySite-field-table {
    table-layout: fixed;
    margin-bottom: 2em;
  }

</style>

<script>

$(document).ready(function() {

  $('.eggButton').click(function() {

    var dataSheetRow = $(this).closest('.row.dataSheet');
    var dataSheetNum = classEditTemplate.extractIntFromString(dataSheetRow.attr('id'));
    var lastTable = dataSheetRow.find('table.studySite-field-table').last();
    var eggDiamTemplate = lastTable.find('tr.sequential').first();
    var eggWeightTemplate = lastTable.find('tr.sequential').last();


    var oldFieldName = $(eggWeightTemplate).find('td.fieldName').html();
    console.log("oldFieldName = "+oldFieldName);
    var eggNum = classEditTemplate.extractIntFromString(oldFieldName) + 1;
    var newEggDiamRow = classEditTemplate.createNumberedRowFromTemplate(eggDiamTemplate, eggNum, dataSheetNum);
    //var newEggWeightRow = classEditTemplate.createEggWeightFromTemplate(lastTableRow, eggNum, dataSheetNum);
    var newEggWeightRow = classEditTemplate.createNumberedRowFromTemplate(eggWeightTemplate, eggNum, dataSheetNum);

    lastTable = classEditTemplate.updateSubtableIfNeeded(lastTable);
    lastTable.append(newEggDiamRow);
    lastTable = classEditTemplate.updateSubtableIfNeeded(lastTable);
    lastTable.append(newEggWeightRow);

  })

}
)

</script>





<jsp:include page="../footer.jsp" flush="true"/>
