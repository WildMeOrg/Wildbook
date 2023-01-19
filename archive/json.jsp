e<%@ page contentType="text/html; charset=utf-8" language="java"
           import="org.ecocean.servlet.ServletUtilities,java.util.ArrayList,org.ecocean.*,java.util.Properties,org.slf4j.Logger,org.slf4j.LoggerFactory,org.apache.commons.lang3.StringEscapeUtils, org.json.JSONObject, org.json.JSONArray, org.ecocean.JsonProperties, java.util.Map, java.util.Set, java.util.List, org.ecocean.identity.*" %>

<%
System.out.println("json.jsp hath begun");
String context=ServletUtilities.getContext(request);
Shepherd myShepherd = new Shepherd(context);

String baseUrl = CommonConfiguration.getServerURL(request, request.getContextPath());


%>
<jsp:include page="header.jsp" flush="true"/>

<div class="container maincontent">

  <h1 class="intro">Hello to my dear friends</h1>

		<%
  System.out.println("json.jsp about to call new jsonproperties");

  IAJsonProperties iaJson = new IAJsonProperties();
  System.out.println("we have iaJson, getting jobj");

  JSONObject jobj = iaJson.getJson();
  System.out.println("we have jobj");

  String[] allKeys = JSONObject.getNames(jobj);


  String k = "_global.detect_review";
  String[] ksplit = ".".split(k);
  System.out.println("Splitting string "+k+" got "+ksplit.length+" parts");

    Taxonomy srw = myShepherd.getOrCreateTaxonomy("Megaptera novaeangliae");
    Annotation srwAnnot = myShepherd.getAnnotation("88891526-e86d-49af-befe-652878abbee6");

		%>
  <h2>Testing dolphins:</h2>
  <p>
    <%
    Annotation dolphinAnn = myShepherd.getAnnotation("fb4f04b7-ccbd-4a9a-9c97-9f46810f81e8");
    List<JSONObject> opts = iaJson.identOpts(myShepherd, dolphinAnn);
    %>
    Dolphin opts: <%= opts%>

  </p>




  <h2>Testing sendDetecct:</h2>

  <p>testing getConfigs:
  <%
  JSONArray confs = iaJson.getDetectionConfigs(srw);
  String uRL = iaJson.getDetectionUrl(srw);

  %>
  <br><%=confs.toString() %>
  <br>
  Url = <%=uRL%>
  </p>



  <p>
    <%

    %>
    Getting detect config for taxonomy <%=srw%>
    <%
    JSONObject detArgsNew = iaJson.getDetectionArgs(srw, baseUrl);
     %>
  </p><p>
  <h3>new world:</h3>
    <%=detArgsNew.toString()%>
  </p>



  <h2>Testing identOpts:</h2>
  <p>
    <%
    List<JSONObject> identOpts = IBEISIA.identOpts(myShepherd, srwAnnot);
    List<JSONObject> newIdentOpts = iaJson.identOpts(myShepherd, srwAnnot);
    %>
    <%=identOpts%>
  </p>
  <p>
    <%=newIdentOpts%>
  </p>




  <h2>Testing recursive getters:</h2><ul>

    <%
      String[] keys = {"_global.detect_review", "Megaptera.novaeangliae.whale_humpback+fluke._id_conf", "Megaptera.novaeangliae.whale_fluke._id_conf", "Megaptera.novaeangliae.whale_orca+fin_dorsal._id_conf"};
      for (String key: keys) {
        System.out.println("calling getRecursive on "+key);
        Object val = iaJson.get(key);
        %> <li><%=key%>: <%=val%></li><%
     }
    %>

  </ul>

  <h2>Testing globals, old vs. new</h2>
  <code>
  <table>
    <tr><th>IA.properties key</th><th>IA.json key</th><th>IA.properties value</th><th>IA.json value</th><th>equal?</th></tr>

    <%
      Map<String,String> propsToJson = iaJson.getGlobalBackCompatibleKeyMap();
      Properties iaProp = ShepherdProperties.getProperties("IA.properties", "", context);
      for (String propKey: propsToJson.keySet()) {
        System.out.println("propKey = "+propKey);
        String propVal = iaProp.getProperty(propKey);
        System.out.println("propVal = "+propVal);
        String jsonKey = propsToJson.get(propKey);
        System.out.println("jsonKey = "+jsonKey);
        String jsonVal = iaJson.get(jsonKey).toString();
        System.out.println("jsonVal = "+jsonVal);
        boolean equal = (propVal != null && propVal.equals(jsonVal));
        %>
        <tr><td><%=propKey %></td><td><%=jsonKey %></td><td><%=propVal %></td><td><%=jsonVal %></td></tr><td><%=equal %></td>
        <%
      }

    %>
  </table></code>



</div>

<jsp:include page="footer.jsp" flush="true"/>

