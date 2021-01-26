<!DOCTYPE html PUBLIC "-//W3C//DTD XHTML 1.0 Transitional//EN"
        "http://www.w3.org/TR/xhtml1/DTD/xhtml1-transitional.dtd">
<%@ page contentType="text/html; charset=utf-8" language="java" import="org.joda.time.LocalDateTime,
org.joda.time.format.DateTimeFormatter,
org.joda.time.format.ISODateTimeFormat,java.net.*,
org.ecocean.grid.*,
org.ecocean.servlet.ServletUtilities,
java.io.*,java.util.*,
java.io.FileInputStream,
java.io.File,
java.io.FileNotFoundException,
org.ecocean.*,
org.ecocean.servlet.*,
javax.jdo.*,
java.lang.StringBuffer,
java.util.Vector,
java.util.Iterator,
org.ecocean.servlet.importer.*,
org.json.JSONObject,
java.lang.NumberFormatException"%>

<%
String context="context0";
context=ServletUtilities.getContext(request);
Shepherd myShepherd=new Shepherd(context);
String urlLoc = "//" + CommonConfiguration.getURLLocation(request);
%>

<jsp:include page="../header.jsp" flush="true"/>
    <script src="<%=urlLoc %>/tools/simplePagination/jquery.simplePagination.js"></script>
    <link type="text/css" rel="stylesheet" href="<%=urlLoc %>/tools/simplePagination/simplePagination.css"/>
    <div id="match-results"><i>searching....</i></div>
    <div id="pagination-section"></div>

    </div>

    <%
    myShepherd.beginDBTransaction();
    try{
      %>

      <script type="text/javascript">
        $(document).ready(function() {
          console.log("got here hi hi hi");
          let paginationHtml = '';
          let iterray = [];
          for(let i =0; i<30; i++){
            paginationHtml += '<div class="match-item">' + i + '</div>';
            iterray.push(i);
          }
          $('#match-results').empty();
          $('#match-results').append(paginationHtml);

          // populatePaginator(iterray);
        });

        function populatePaginator(iterator){
          console.log("entered populatePaginator");
          //pagination
          let items = $('.match-item');
          console.log("items are: ");
          console.log(items);
          let numItems = iterator.length;
          console.log("numItems is: " + numItems);
          let perPage = 3; //TODO maybe change later?
          items.slice(perPage).hide();

          $('#pagination-section').pagination({
            items: numItems,
            itemsOnPage: perPage,
            cssStyle: "light-theme",
            onPageClick: function(pageNumber) {
              console.log("onPageClick entered. pageNumber is: " + pageNumber);
              var showFrom = perPage * (pageNumber - 1);
              console.log("showFrom is: " + showFrom);
              var showTo = showFrom + perPage;
              console.log("showTo is: " + showTo);
              items.hide().slice(showFrom, showTo).show();
            }
          });
          console.log("got here 2");
          //end pagination
        }
      </script>
      <%


        Encounter targetEncounter = myShepherd.getEncounter("5e2ade7e-3821-4c25-bac9-9e33dce6a961"); //c8d1aae2-a6f8-4c18-a96e-a090c97988e1
        targetEncounter.setState("processing");
        myShepherd.updateDBTransaction();

        //reset decisions on encounters
        System.out.println("got here 1");
        List<Decision> oldDecisions = myShepherd.getDecisionsForEncounter(targetEncounter);
        System.out.println("got here 2");
        if(oldDecisions!=null && oldDecisions.size()>0){
          System.out.println("oldDecisions.size() is: " + oldDecisions.size());
          for(Decision currentDecision: oldDecisions){
            System.out.println("got here 3");
            myShepherd.throwAwayDecision(currentDecision);
          }
          myShepherd.updateDBTransaction();
        }
        System.out.println("got here 4");
        List<Decision> checkDecisions = myShepherd.getDecisionsForEncounter(targetEncounter);
        System.out.println("got here 5");
        if(checkDecisions==null || checkDecisions.size()<1){
          System.out.println("things are going according to plan");
          String property = "match";

          JSONObject value = new JSONObject();
          String matchCandidateCatalogNumber1 = "c8bc29c8-7dba-412e-a4f1-3aed0a5c19c5";
          String matchCandidateCatalogNumber2 = "56950464-9348-493f-a8a7-cbf019af583a";
          value.put("id", matchCandidateCatalogNumber1);
          User user1 = myShepherd.getUserByUUID("f37d7426-27e7-4133-a205-dac746824436");
          User user2 = myShepherd.getUserByUUID("29827461-582e-4bf1-9b3d-453bd4d0cd56");
          User user3 = myShepherd.getUserByUUID("6c51eb42-8964-4ac1-97b6-2a1c1bad4628");
          User user4 = myShepherd.getUserByUUID("60edc960-ac45-4710-b28a-679501a0bc48");
          User user5 = myShepherd.getUserByUUID("6c887eab-3928-47d2-8d47-011e8d589caf");
          User user6 = myShepherd.getUserByUUID("0b616fdd-ccf9-40e6-bbd9-b93724b12014");
          User user7 = myShepherd.getUserByUUID("0c205984-0105-469a-8efc-4829cc774914");

          System.out.println("decision got here 1");
          Decision dec = new Decision(user1, targetEncounter, property, value);
          myShepherd.getPM().makePersistent(dec);
          System.out.println("decision got here 1.5");
          myShepherd.updateDBTransaction();
          Decision.updateEncounterStateBasedOnDecision(myShepherd, targetEncounter);
          System.out.println("decision got here 2");
          System.out.println("decision got here 3");
          System.out.println("decision got here 4");
          dec = new Decision(user2, targetEncounter, property, value);
          myShepherd.getPM().makePersistent(dec);
          myShepherd.updateDBTransaction();
          Decision.updateEncounterStateBasedOnDecision(myShepherd, targetEncounter);

          dec = new Decision(user3, targetEncounter, property, value);
          myShepherd.getPM().makePersistent(dec);
          myShepherd.updateDBTransaction();
          Decision.updateEncounterStateBasedOnDecision(myShepherd, targetEncounter);

          dec = new Decision(user4, targetEncounter, property, value);
          myShepherd.getPM().makePersistent(dec);
          myShepherd.updateDBTransaction();
          Decision.updateEncounterStateBasedOnDecision(myShepherd, targetEncounter);

          value.put("id", matchCandidateCatalogNumber2);

          dec = new Decision(user5, targetEncounter, property, value);
          myShepherd.getPM().makePersistent(dec);
          myShepherd.updateDBTransaction();
          Decision.updateEncounterStateBasedOnDecision(myShepherd, targetEncounter);

          dec = new Decision(user6, targetEncounter, property, value);
          myShepherd.getPM().makePersistent(dec);
          myShepherd.updateDBTransaction();
          Decision.updateEncounterStateBasedOnDecision(myShepherd, targetEncounter);

          // dec = new Decision(user7, targetEncounter, property, value);
          // myShepherd.getPM().makePersistent(dec);
          // myShepherd.updateDBTransaction();
          // Decision.updateEncounterStateBasedOnDecision(myShepherd, targetEncounter);

          checkDecisions = myShepherd.getDecisionsForEncounter(targetEncounter);
          if(checkDecisions!=null){
            System.out.println("after populating, checkDecisions.size() is: " + checkDecisions.size());
          }
        } else{
          System.out.println("things are NOT going according to plan");
        }


        //To force a finished state
        // String newState = "finished";
        // targetEncounter.setState(newState);
        // myShepherd.updateDBTransaction();
        //
        // Encounter targetEncounter2 = myShepherd.getEncounter("ed3d828e-baf1-43f1-8130-4b24b0441463");
        // targetEncounter2.setState(newState);
        // myShepherd.updateDBTransaction();


        myShepherd.commitDBTransaction();
      	myShepherd.beginDBTransaction();


      }finally{
        myShepherd.rollbackDBTransaction();
      	myShepherd.closeDBTransaction();
      }

    %>
<jsp:include page="../footer.jsp" flush="true"/>
