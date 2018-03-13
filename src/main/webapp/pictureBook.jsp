<%@ page contentType="text/html; charset=utf-8" language="java"
         import="org.ecocean.servlet.ServletUtilities,
org.ecocean.*,
java.util.Properties,
java.util.Collection,
java.util.Vector,
java.util.ArrayList,
java.util.List,
org.datanucleus.api.rest.orgjson.JSONArray,
org.datanucleus.api.rest.RESTUtils,
org.datanucleus.api.jdo.JDOPersistenceManager,
org.datanucleus.api.rest.orgjson.JSONObject" %>



  <%

  String context="context0";
  context=ServletUtilities.getContext(request);
  
  Properties props = new Properties();
  String langCode=ServletUtilities.getLanguageCode(request);
  props = ShepherdProperties.getProperties("pictureBook.properties", langCode,context);

  int startNum = 1;
  int endNum = 10;

  Shepherd myShepherd = new Shepherd(context);
  myShepherd.setAction("pictureBook.jsp");

  int numResults = 0;

  Vector<MarkedIndividual> rIndividuals = new Vector<MarkedIndividual>();
  myShepherd.beginDBTransaction();
  
  try {
  
  String order ="";
  MarkedIndividualQueryResult result = IndividualQueryProcessor.processQuery(myShepherd, request, order);
  rIndividuals = result.getResult();
	numResults = rIndividuals.size();
	System.out.println("PictureBook: returned "+numResults+" individuals");

  if (numResults < endNum) endNum = numResults;
  %>
	
	<jsp:include page="header.jsp" flush="true"/>
	
	<script src="javascript/core.js"></script>
	<script src="javascript/classes/Base.js"></script>
s
	<div class="container maincontent">

		<h1 class="intro"> <%=props.getProperty("title")%> </h1>
	
	<p class="resultSummary">
	<table width="810" border="0" cellspacing="0" cellpadding="0">
	  <tr>
	    <td align="left">

	      <p><strong><%=props.getProperty("matchingMarkedIndividuals")%>
	      </strong>: <span id="count-total"> <%=numResults%> </span>
	      </p>
	      <%myShepherd.beginDBTransaction();%>
	      <p><strong><%=props.getProperty("totalMarkedIndividuals")%>
	      </strong>: <%=(myShepherd.getNumMarkedIndividuals())%>
	      </p>
	    </td>
	    <%

    %>
  </tr>
</table>
<%if (request.getParameter("noQuery") == null) {%>
<table>
  <tr>
    <td align="left">

      <p><strong><%=props.getProperty("queryDetails")%>
      </strong></p>

      <p class="caption"><strong><%=props.getProperty("prettyPrintResults") %>
      </strong><br/>
        <%=result.getQueryPrettyPrint().replaceAll("locationField", props.getProperty("location")).replaceAll("locationCodeField", props.getProperty("locationID")).replaceAll("verbatimEventDateField", props.getProperty("verbatimEventDate")).replaceAll("Sex", props.getProperty("sex")).replaceAll("Keywords", props.getProperty("keywords")).replaceAll("alternateIDField", (props.getProperty("alternateID"))).replaceAll("alternateIDField", (props.getProperty("size")))%>
      </p>

      <p class="caption"><strong><%=props.getProperty("jdoql")%>
      </strong><br/>
        <%=result.getJDOQLRepresentation()%>
      </p>

    </td>
  </tr>
</table>
<%}%>
</p>

<style>
@media print {
		div.pictureBook-page {page-break-before: always;}
		hr.pictureBook-pagebreak {
			display: none;
		}
	}
	.pictureBook-table {
		white-space: nowrap; /*prevents cells from being 2 rows of text tall*/
	}
	.pictureBook-table, .pictureBook-table th, .pictureBook-table td {
		border: 1px solid black;
	}
	.pictureBook-table th, .pictureBook-table td {
		padding-left: 0.8em;
		padding-right: 0.8em;
		padding-top: 0.1em;
		padding-bottom: 0.1em;
	}
	td.checkboxInput {
		text-align: center; /* center checkbox horizontally */
    vertical-align: middle; /* center checkbox vertically */
	}
</style>

<div class="pictureBook-container">
	<%
	int count=0;
	for (MarkedIndividual mark: rIndividuals) {

		count++;
		if (count>endNum) break;

		String id = mark.getIndividualID();
		String altID = mark.getAlternateID();

		List<String> desiredKeywords = new ArrayList<String>();
		desiredKeywords.add("Tail Fluke");
		desiredKeywords.add("Right Dorsal Fin");
		desiredKeywords.add("Left Dorsal Fin");


		// commented out so that things are still fast while programmings---but this is tested and works
		// this call is pretty slow
		//ArrayList<JSONObject> exemplarImages = mark.getExemplarImagesWithKeywords(request, desiredKeywords);

		%>
		<hr class="pictureBook-pagebreak">
		<div class="pictureBook-page">
			<h3>Individual ID: <%=id%></h3>


			<%
			int encsPerTableLimit=10;
			Encounter[] encs = mark.getDateSortedEncounters(encsPerTableLimit);
			int numEncs = encs.length;
			%>
			<h4 class="pictureBook-tableHeader">Sighting History</h4> <em><%=numEncs %> on table</em>
			<table class="pictureBook-table">
				<tr class="pictureBook-hr">
					<th>Date</th>
					<th>Biopsy</th>
					<th>S. skin</th>
					<th>Location</th>
					<th>Fluke photo</th>
					<th>Nickname</th>
					<th>Sex</th>
					<th>Satellite Tag</th>
				</tr>
				<% // 1 row per encounter
				for (Encounter enc: encs) {
					if (enc==null) continue;
					String dateStr = enc.getShortDate();
					if ("Unknown".equals(dateStr)) dateStr = "";
					boolean biopsy = enc.hasDynamicProperty("Biopsy collected"); // value set during ImportAcces.java
					boolean sloughedSkin = enc.hasDynamicProperty("Sloughed skin"); // value set during ImportAcces.java
					String location = enc.getLocationID();
					int flukePhoto = -1;
					String nickname = enc.getAlternateID();
					if (!Util.stringExists(nickname)) nickname = mark.getNickName();
					if ("Unassigned".equals(nickname)) nickname = "";
					String sex = enc.getSex();
					if (Util.shouldReplace(mark.getSex(), sex)) sex = mark.getSex();
					boolean satelliteTag = enc.hasDynamicProperty("satelliteTag");

					%>
					<tr class="pictureBook-tr">
						<td><%=dateStr%></td>
						<td class="checkboxInput"><input type="checkbox" onclick="return false;" <%= biopsy ? "checked" : ""%> /></td>
						<td class="checkboxInput"><input type="checkbox" onclick="return false;" <%= sloughedSkin ? "checked" : ""%> /></td>
						<td><%=location%></td>
						<td><%=flukePhoto%></td>
						<td><%=nickname%></td>
						<td><%=sex%></td>
						<td class="checkboxInput"><input type="checkbox" onclick="return false;" <%= satelliteTag ? "checked" : ""%> /></td>
					</tr>
					<%

				}

				%>
			</table>
		</div>
		<%
	}
	%>
</div>

	<%
    }
    catch(Exception e){
    	System.out.println("Exception on pictureBook.jsp!");
    	e.printStackTrace();
    %>
    
    <p>Exception on page!</p>
    <p><%=e.getMessage() %></p>
    
    <%	
    }
    finally{
      myShepherd.rollbackDBTransaction();
      myShepherd.closeDBTransaction();
    }

%>

</div>

<jsp:include page="footer.jsp" flush="true"/>