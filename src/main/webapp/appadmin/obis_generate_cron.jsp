<%@ page contentType="text/html; charset=utf-8" language="java"
     import="org.ecocean.*,
org.ecocean.opendata.*
              "
%>




<%

String context = "context0";
Shepherd myShepherd = new Shepherd(context);

OBISSeamap os = new OBISSeamap(context);
os.init();
os.generate(

    "SELECT FROM org.ecocean.Occurrence WHERE source.matches('SpotterConserveIO:ci:.*') && encounters.contains(enc) && enc.state == \"approved\" VARIABLES org.ecocean.Encounter enc",

    "SELECT * FROM \"ENCOUNTER\" JOIN \"OCCURRENCE_ENCOUNTERS\" on (\"CATALOGNUMBER_EID\" = \"CATALOGNUMBER\") JOIN \"OCCURRENCE\" on (\"OCCURRENCEID_OID\" = \"OCCURRENCE\".\"OCCURRENCEID\") WHERE \"OCCURRENCE\".\"SOURCE\" like 'SpotterConserveIO:ci:%' AND \"ENCOUNTER\".\"STATE\" = 'approved';"


);

%>



