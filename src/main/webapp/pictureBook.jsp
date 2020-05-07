<%@ page contentType="text/html; charset=utf-8" language="java"
         import="org.ecocean.servlet.ServletUtilities,
org.ecocean.*,
java.util.Properties,
java.util.Collection,
java.util.Vector,
java.util.ArrayList,
java.util.List,
org.datanucleus.api.rest.orgjson.JSONArray,
org.ecocean.security.HiddenIndividualReporter,
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
  int maxPages = 10;

  Shepherd myShepherd = new Shepherd(context);
  myShepherd.setAction("pictureBook.jsp");

  int numResults = 0;
	int count=0;

	int indsWithoutPics=0;

  Vector<MarkedIndividual> rIndividuals = new Vector<MarkedIndividual>();
  myShepherd.beginDBTransaction();

  try {

  String order ="";
  MarkedIndividualQueryResult result = IndividualQueryProcessor.processQuery(myShepherd, request, order);
  rIndividuals = result.getResult();
  HiddenIndividualReporter hiddenData = new HiddenIndividualReporter(rIndividuals, request, myShepherd);
  rIndividuals = hiddenData.securityScrubbedResults(rIndividuals);
	numResults = rIndividuals.size();
	System.out.println("PictureBook: returned "+numResults+" individuals");

  if (numResults < maxPages) maxPages = numResults;
  %>

	<jsp:include page="header.jsp" flush="true"/>

	<!-- not sure why we need backbone or underscore but we get errors without 'em -->
	<script src="javascript/imageDisplayTools.js"></script>

	<div class="container maincontent">

	<h1 class="intro"> <%=props.getProperty("title")%> </h1>


	<p class="disclaimer"><em>Warnings:<ul>
		<li>Please wait for the page to completely load</li>
		<li>Scroll to the bottom of the page before printing, or some images will not render in pdf</li>
	</ul></em></p>

	<p class="instructions"> Your African Carnivore Wildbook  search results have been collated into a printable format. Use your browser's print function to convert this page into a pdf: modern browsers have a "print to pdf" function that will download the page without a physical printer. Page breaks and formatting will appear, allowing you to print this report and take it into the field.</p>

	<p class="resultSummary">
	<table width="810" border="0" cellspacing="0" cellpadding="0">
	  <tr>
	    <td align="left">

	      <p><strong><%=props.getProperty("matchingMarkedIndividuals")%>
	      </strong>: <span id="count-total"> <%=numResults%> </span> (Showing only the <span id="image-count-total"></span> individuals with tagged exemplar images)
	      </p>

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
		body {
			padding-top: 1cm;
			padding-bottom: 1cm;
			height: auto;
  	}
  	div.pictureBook-page {
  		height: auto;
  	}
  	div.pictureBook-images {
  		height: 40%;
  	}
	}
	.clickable-row:hover {
		cursor: pointer;
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
	span.pictureBook-MA {
		display: none;
	}

	html, body {
		height: 100%;
	}

	div.pictureBook-page {
		height: 100vh;
	}
	div.pictureBook-images {
		width: 100%;
		max-height: 50%;
		position: relative;
		display: inline-block;
		overflow: hidden;
		max-height: 50%;
		top: 0
	}
	div.pictureBook-headerImage {
		max-height: 25%;
		position: relative;
	}
	div.pictureBook-headerImage img{
		object-fit: contain;
		max-height: 25vh;
    display: block;
    margin-left: auto;
    margin-right: auto;
    	width: 100%;
	}
	div.pictureBook-subImage img {
		object-fit: contain;
		max-height: 25vh;
	}
	div.pictureBook-images table {
		margin: 0 auto;
	}

	div.pictureBook-subImage {
		max-height: 12.5%;
		position: relative;
	}
	tr.pictureBook-subImages td {
		width: 50%;
	}
	table.pictureBook-table {
    margin-left: auto;
    margin-right: auto;
	}


</style>

<div class="pictureBook-container">
	<%

		List<String> desiredKeywords = new ArrayList<String>();
		desiredKeywords.add("Top");
		desiredKeywords.add("Bottom");
		desiredKeywords.add("Right");
		desiredKeywords.add("Left");

	for (MarkedIndividual mark: rIndividuals) {

		ArrayList<JSONObject> exemplarImages = mark.getBestKeywordPhotos(request, desiredKeywords, true, myShepherd);

		boolean hasHeader = exemplarImages.size()>0;
		boolean haspic2 = exemplarImages.size()>1;
		boolean haspic3 = exemplarImages.size()>2;


		if (!hasHeader) {
			indsWithoutPics++;
			continue; // skip individuals without any images
		}
		count++;
		if (count>maxPages) break;

		String id = mark.getIndividualID();
		String altID = mark.getDisplayName();
		//if (Util.shouldReplace(mark.getNickName(), altID)) altID = mark.getNickName();
		String altIDStr = (Util.stringExists(mark.getNickName())) ? ("<em>("+mark.getNickName()+")</em>") : "";
		System.out.println("PictureBook: proceeded past hasHeader check");


		%>
		<style>
		</style>


		<hr class="pictureBook-pagebreak">
		<div class="pictureBook-page">
			<h3>Individual ID: <a target="_blank" href=<%=mark.getWebUrl(request) %> ><%=altID %></a> <%=altIDStr %> </h3>

			<div class="pictureBook-images">
				<table>
					<tr>
						<td colspan="2">
							<div class="pictureBook-headerImage">
								<% if (hasHeader){
								%><span class="pictureBook-headerMA pictureBook-MA" ><%=exemplarImages.get(0).toString()%>
								</span><%
								} %>
							</div>
						</td>
					</tr>
					<tr class="pictureBook-subImages">
						<td>
							<div class="pictureBook-subImage">
								<% if (haspic2){
								%><span class="pictureBook-headerMA pictureBook-MA" ><%=exemplarImages.get(1).toString()%>
								</span><%
								} %>
							</div>
						</td>
						<td>
						<div class="pictureBook-subImage">
							<% if (haspic3){
							%><span class="pictureBook-headerMA pictureBook-MA" ><%=exemplarImages.get(2).toString()%>
							</span><%
							} %>
						</div>
					</td>
					</tr>
				</table>
			</div>

			<%
			int encsPerTableLimit=8;
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
					<!--<th>Fluke photo</th>-->
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
					<tr class="pictureBook-tr clickable-row" data-href='<%=enc.getWebUrl(request) %>'>
						<td><%=dateStr%></td>
						<td class="checkboxInput"><input type="checkbox" onclick="return false;" <%= biopsy ? "checked" : ""%> /></td>
						<td class="checkboxInput"><input type="checkbox" onclick="return false;" <%= sloughedSkin ? "checked" : ""%> /></td>
						<td><%=location%></td>
						<!--<td><%=flukePhoto%></td>-->
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
	System.out.println("PictureBook: done with java, found "+indsWithoutPics+" individuals with no pictures");

%>

</div>

<script>

	var displayImages = function() {
		$("span.pictureBook-headerMA").each(function() {
			var jsonString = $(this).text();
			var maJson = JSON.parse(jsonString);
			console.log("pictureBook is displaying images for ma"+maJson.id);
			console.log("and majson = "+JSON.stringify(maJson));
			var imgDisplay = maLib.mkImgPictureBook(maJson);
			$(this).parent().append(imgDisplay);
		});
	}

	$( document ).ready(function() {
		displayImages();
		// count is calculated in java but displayed by JS
		$('#image-count-total').html('<%=count%>');

		$(".clickable-row").click(function() {
        window.location = $(this).data("href");
    });
	});
</script>

<jsp:include page="footer.jsp" flush="true"/>
