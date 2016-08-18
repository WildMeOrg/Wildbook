<%@ page contentType="text/html; charset=utf-8" language="java"
   import="org.ecocean.*,
java.util.Map,
java.util.List,
java.io.BufferedReader,
java.io.IOException,
java.io.InputStream,
java.io.InputStreamReader,
java.io.File,
org.datanucleus.api.rest.orgjson.JSONObject,

org.ecocean.media.*,
javax.jdo.Query
            "
%>




<%

Shepherd myShepherd=null;
myShepherd = new Shepherd("context0");
String urlLoc = "http://" + CommonConfiguration.getURLLocation(request);

//WBQuery wbq = ((WBQuery) (myShepherd.getPM().getObjectById(myShepherd.getPM().newObjectIdInstance(WBQuery.class, 1), true)));

/*
I've left this big comment block in because it shows a large query that can be translated: exJSON2

JSONObject exJSON = new JSONObject("{\"class\":\"org.ecocean.Encounter\",\"query\": {\"sex\":\"male\"}}");

JSONObject exJSON2 = new JSONObject("{\"class\": \"org.ecocean.Encounter\",\"query\": {\"location\": \"The Big Lagoon\",\"sex\": {\"$ne\": \"female\"},\"maxDate\": {\"$gte\": \"2013-05-01T00:00:00\"},\"minDate\": {\"$lt\": \"2013-06-01T00:00:00\"}}}");

WBQuery wbq = new WBQuery(exJSON);
WBQuery wbq2 = new WBQuery(exJSON2);

String toJDOQL = wbq.toJDOQL();
String toJDOQL2 = wbq2.toJDOQL();


//List<Object> all = wbq.doQuery(myShepherd);
out.println("<p>Test 1:<ul>");
out.println("<li>Original JSON: "+exJSON.toString()+"</li>");
out.println("<li>Translated: "+toJDOQL+"</li></ul>");
Query q1 = wbq.toQuery(myShepherd);
out.println("</br>Query info: <ul>");
out.println("<li>JDOQL: "+q1.JDOQL+"</li>");
out.println("<li>SQL: "+q1.SQL+"</li>");
// the following line checks that the query is valid
q1.compile();
List<Object> res = wbq.doQuery(myShepherd);
int numResults = res.size();
out.println("<li>size: "+numResults+"</li>");
out.println("</ul></p>");


out.println("<p>Test 2:<ul>");
out.println("<li>Original JSON: "+exJSON2.toString()+"</li>");
out.println("<li>Translated: "+toJDOQL2+"</li></ul></p>");

Query q2 = wbq2.toQuery(myShepherd);
out.println("</br><p> Query info: <ul>");
// the following line checks that the query is valid

q2.compile();
List<Object> res2 = wbq2.doQuery(myShepherd);
int numResults2 = res2.size();
out.println("<li>size: "+numResults+"</li>");
out.println("</ul></p>");
*/


%>
<div class="results">

</div>

<script src="<%=urlLoc %>/tools/jquery/js/jquery.min.js"></script>
<script>

  // returns the first 100 MediaAssets in the db
  // var testQuery = {class: 'org.ecocean.media.MediaAsset', query: {}, range: 100};
  var testQuery = {class: 'org.ecocean.Encounter', query: {sex: {$ne: "male"}}, range: 30, rangeMin:15};
  // var testQuery = {class: 'org.ecocean.Encounter', query: {sex: {$ne: "male"}}};

  $(".results").append("<p>Query = "+JSON.stringify(testQuery)+"</p>");
  /*

  // Stringify the query so it can be passed to java
  $(".results").append("<p>Query = "+testString+"</p>");
  // ... but attach that string as a named variable because HTTP posts have named variables
  // debug arg is purely for debugging the query thing
  // var args = {stringifiedJSONQuery: testString, debug:true};
  */
  var args = testQuery;
  // now just use $.post("TranslateQuery", args, callbackFunctionOnReturned(data))
  $.post( "TranslateQuery", args, function( data ) {
    $(".results").append( "Data Loaded: " + data );
  });

/*
  var xhr = new XMLHttpRequest();
  xhr.onreadystatechange = function() {
      if (xhr.readyState == 4) {
          var data = xhr.responseText;
          $(".results").append("here's the data: "+data);
      }
  }
  xhr.open('GET', 'TranslateQuery', true);
  xhr.send(testString);
  */
</script>
