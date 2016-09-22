<%@ page contentType="text/html; charset=utf-8" language="java"
  import="
    org.ecocean.servlet.ServletUtilities,
    org.ecocean.*,
    org.ecocean.datacollection.*,
    javax.jdo.Extent,
    javax.jdo.Query,
    java.io.File,
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

  String nestID = request.getParameter("number");
  int nFieldsPerSubtable = 8;

  System.out.println("beginning nest.jsp!");


  Nest nestie;
  if (nestID!=null) {
    nestie = myShepherd.getNest(nestID);
    System.out.println("myShepherd grabbed Nest #"+nestID);
  }
  else {
    System.out.println("NEWNEST: myShepherd failed to find a Nest # upon loading nest.jsp");
    nestie = new Nest(Util.generateUUID()); // TODO: fix this case
    myShepherd.storeNewNest(nestie);
    nestID = nestie.getID();
  }

  String[] nestFieldGetters = new String[]{"getName", "getLocationID", "getLocationNote","getLatitude","getLongitude"};


  String saving = request.getParameter("save");
  String newNestingSheet = request.getParameter("newNestingSheet");
  String newEmergenceSheet = request.getParameter("newEmergenceSheet");

  int nDataSheets = nestie.countSheets();
  int sheetToRemove = -1;
  for (int i=0; i<nDataSheets; i++) {
    String removeSheetI = request.getParameter("removeSheet"+i);
    if (removeSheetI!=null) {
      sheetToRemove = i;
      break;
    }
  }


  boolean needToSave = (saving != null || newNestingSheet!=null || sheetToRemove>=0);

  if (newNestingSheet !=null) {
    System.out.println("*X*X*XX*X*X*Printing a new sheet!");
    nestie.addConfigDataSheet(context);
  }

  if (newEmergenceSheet !=null) {
    System.out.println("*X*X*XX*X*X*Printing a new sheet!");
    nestie.addConfigDataSheet(context, "emergence");
  }

  if (sheetToRemove >= 0) {
    nestie.remove(sheetToRemove);
  }

  if (needToSave) {
    System.out.println("");
    System.out.println("NEST.JSP: Saving updated info...");
    Enumeration en = request.getParameterNames();


    while (en.hasMoreElements()) {
      String pname = (String) en.nextElement();
      String value = request.getParameter(pname);
      System.out.println("parsing parameter "+pname);
      if (pname.indexOf("nes:") == 0) {
        String methodName = "set" + pname.substring(4,5).toUpperCase() + pname.substring(5);
        String getterName = "get" + methodName.substring(3);
        System.out.println("Nest.jsp: about to call ClassEditTemplate.updateObjectField("+nestie+", "+methodName+", "+value+");");
        ClassEditTemplate.updateObjectField(nestie, methodName, value);
      }
      else if (pname.indexOf("nes-dp-new:") == 0) {
        String afterColon = pname.split(":")[1];
        String dataSheetNumStr = afterColon.substring(2,afterColon.indexOf("-"));
        int dataSheetNum = Integer.parseInt(dataSheetNumStr);

        // Create a new datapoint and add it to the appropriate sheet
        // populate that datapoint's value with "value"
        //Amount newEggDP = new Amount

        boolean isDiamNotWeight = (pname.indexOf("diam")>-1);
        //nestie.addNewEgg(dataSheetNum);
      }
      else if (pname.indexOf("nes-dp-") == 0) {
        // looks like nes-dp-dsNUM: _____. now to parse the NUM
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
    System.out.println("NEST.JSP: Transaction committed");
    System.out.println("");
  }
%>



<div class="container maincontent">
  <form method="post" onsubmit="return classEditTemplate.checkBeforeDeletion()" action="nest.jsp?number=<%=nestID%>" id="classEditTemplateForm">


<div class="row">
  <div class="col-xs-12">
    <h1>Nest</h1>
    <p class="nestidlabel"><em>id <%=nestID%></em><p>

      <table class="nest-field-table edit-table">
        <%
        for (String getterName : nestFieldGetters) {
          Method nestMeth = nestie.getClass().getMethod(getterName);
          if (ClassEditTemplate.isDisplayableGetter(nestMeth)) {
            ClassEditTemplate.printOutClassFieldModifierRow((Object) nestie, nestMeth, out);
          }
        }
        %>
      </table>
    </div>
  </div>


      <div class="row">
        <div class="col-sm-12">
          <h2>Data Sheets</h2>
          <input type="submit" name="newNestingSheet" value="Add New Nesting Field Sheet" />
        </div>
        <div class="col-sm-12">
          <input type="submit" name="newEmergenceSheet" value="Add New Emergence Field Sheet" />
        </div>

      </div>
        <%


        for (int i=0; i < nestie.getDataSheets().size(); i++) {
          %> <div class="row dataSheet" id="<%=i%>"> <%
          %> <div class="col-xs-12"> <%
          //DataSheet dSheet = nestie.getDataSheet(nestie.getDataSheets().size()-(i+1)); //reverses order
          DataSheet dSheet = nestie.getDataSheet(i);
          int numEggs = nestie.getEggCount(i);
          System.out.println("DataSheet "+i+" has #eggs = "+numEggs);
          if (numEggs==0) {
            nestie.addNewEgg(i);
          }
          %>
            <h3>Data Sheet <%=i+1%></h2>

            <%
            if (dSheet.getName()!=null && !dSheet.getName().equals(""))
            {
              %>
              <h4><%=dSheet.getName()%></h4>
              <%
            }
            %>
            <input type="submit" onclick="classEditTemplate.markDeleteSheet()" name="removeSheet<%=i%>" value="Remove this Data Sheet" ></input>


            <table class="nest-field-table edit-table" style="float: left">
            <%
            //ClassEditTemplate.printDateTimeSetterRow((Object) dSheet,dSheet.getID(), out);
            %>
            </table>
          </div>
          <%


          int nFields = dSheet.size();
          int nSubtables = Util.getNumSections(nFields, nFieldsPerSubtable);
          int dataPointN = 0;

          for (int tableN=0; tableN < nSubtables; tableN++) {

          %><div class="col col-md-4 nest-table">
          <table class="nest-field-table edit-table" style="float: left"><%
            for (int subTableI=0; dataPointN < nFields && subTableI < nFieldsPerSubtable; dataPointN++, subTableI++) {
              DataPoint dp = dSheet.getData().get(dataPointN);
              ClassEditTemplate.printOutClassFieldModifierRow((Object) nestie, dp, out);
            }
          %></table></div><%
        }
        %>
        <div class="col-md-4">
          <input type="button" name="newEgg<%=i%>" value="Add Egg Measurement" class="eggButton" />
        </div>

        </div> <%

        }
        %>
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

  table.nest-field-table {
    table-layout: fixed;
    margin-bottom: 2em;
  }

</style>

<script>

$(document).ready(function() {

  $('.eggButton').click(function() {

    var dataSheetRow = $(this).closest('.row.dataSheet');
    var dataSheetNum = classEditTemplate.extractIntFromString(dataSheetRow.attr('id'));
    var lastTable = dataSheetRow.find('table.nest-field-table').last();
    var lastTableRow = lastTable.find('tr').last();
    var eggNum = classEditTemplate.extractIntFromString(lastTableRow.find('td.fieldName').html()) + 1;
    var newEggDiamRow = classEditTemplate.createEggDiamFromTemplate(lastTableRow, eggNum, dataSheetNum);
    var newEggWeightRow = classEditTemplate.createEggWeightFromTemplate(lastTableRow, eggNum, dataSheetNum);

    lastTable = classEditTemplate.updateSubtableIfNeeded(lastTable);
    lastTable.append(newEggDiamRow);
    lastTable = classEditTemplate.updateSubtableIfNeeded(lastTable);
    lastTable.append(newEggWeightRow);

  })

}
)

</script>





<jsp:include page="footer.jsp" flush="true"/>
