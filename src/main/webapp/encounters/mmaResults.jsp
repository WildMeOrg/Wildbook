<%--
	~ The Shepherd Project - A Mark-Recapture Framework
	~ Copyright (C) 2011 Jason Holmberg
	~
	~ This program is free software; you can redistribute it and/or
	~ modify it under the terms of the GNU General Public License
	~ as published by the Free Software Foundation; either version 2
	~ of the License, or (at your option) any later version.
	~
	~ This program is distributed in the hope that it will be useful,
	~ but WITHOUT ANY WARRANTY; without even the implied warranty of
	~ MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.	See the
	~ GNU General Public License for more details.
	~
	~ You should have received a copy of the GNU General Public License
	~ along with this program; if not, write to the Free Software
	~ Foundation, Inc., 51 Franklin Street, Fifth Floor, Boston, MA	02110-1301, USA.
--%>
<link href="../css/mma.css" rel="stylesheet" type="text/css"/>
<%@page contentType="text/html; charset=utf-8" language="java"
        import="org.ecocean.CommonConfiguration"
        import="org.ecocean.Encounter"
        import="org.ecocean.Shepherd"
        import="org.ecocean.ShepherdProperties"
        import="org.ecocean.mmutil.*"
        import="org.ecocean.mmutil.MMAResultsProcessor.MMAMatch"
        import="org.ecocean.servlet.MantaMatcher"
        import="org.ecocean.servlet.ServletUtilities"
        import="java.io.*"
        import="java.net.*"
        import="java.text.*"
        import="java.util.*"
%>
<%@taglib uri="http://java.sun.com/jsp/jstl/core" prefix="c" %>
<%@taglib uri="http://java.sun.com/jsp/jstl/fmt" prefix="fmt" %>
<%!
  private static final String convertFileToURL(String dataDirUrlPrefix, File file) throws UnsupportedEncodingException {
    File dir = file.getParentFile();
    String dirStr = dir.getAbsolutePath().replace(File.separatorChar, '/');
    dirStr = dirStr.replaceFirst("^.*/encounters/", "");
    return String.format("%s/%s/%s", dataDirUrlPrefix, dirStr, URLEncoder.encode(file.getName(), "UTF-8"));
  }

  private static final String findKeyFromValue(String val, Map<String, String> map) {
    assert map != null;
    if (val == null)
      return null;
    for (Map.Entry<String, String> me : map.entrySet()) {
      if (me.getValue().equals(val))
        return me.getKey();
    }
    return null;
  }
%>
<%
System.out.println("Starting mmaResults.jsp.");
  String context = ServletUtilities.getContext(request);
  String langCode = ServletUtilities.getLanguageCode(request);
  Properties bundle = ShepherdProperties.getProperties("mmaResults.properties", langCode, context);
  Shepherd myShepherd = new Shepherd(context);
  myShepherd.setAction("mmaResults.jsp");
  myShepherd.beginDBTransaction();
  try{
	  // Get map for implementing i18n of pigmentation.
	  Map<String, String> mapPig = CommonConfiguration.getIndexedValuesMap("patterningCode", context);
	  // Get set of all LocationIDs.
	  Set<String> allLocationIDs = new HashSet<String>(CommonConfiguration.getIndexedPropertyValues("locationID", context));
	
	  response.setHeader("Cache-Control", "no-cache"); //Forces caches to obtain a new copy of the page from the origin server
		response.setHeader("Cache-Control", "no-store"); //Directs caches not to store the page under any circumstance
		response.setDateHeader("Expires", 0); //Causes the proxy cache to see the page as "stale"
		response.setHeader("Pragma", "no-cache"); //HTTP 1.0 backward compatibility
	
	  MantaMatcherScan scan = (MantaMatcherScan)request.getSession().getAttribute(MantaMatcher.REQUEST_KEY_SCAN);
	  MMAResultsProcessor.MMAResult results = (MMAResultsProcessor.MMAResult)request.getSession().getAttribute(MantaMatcher.REQUEST_KEY_RESULTS);
	  if (results == null) {
	   	System.out.println("results are null in mmaResults.jsp!");
	  }
	  else{
		  System.out.println("results are: "+results.toString());
	  }
	  String dispLocIDs="";
	  try{
	  	dispLocIDs = scan.getLocationIdString(null, null, ", ");
	  	if (scan.getLocationIds().equals(allLocationIDs)) {
	    	dispLocIDs = bundle.getProperty("location.global");
	  	}
	  }
	  catch(Exception e){
		  e.printStackTrace();
	  }
	  	
	  DateFormat DF = new SimpleDateFormat("yyyy-MM-dd HH:mm Z");
	  // Derive data folder info.
	//  String rootDir = getServletContext().getRealPath("/");
	//  File dataDir = new File(ServletUtilities.dataDir(context, rootDir));
	  // URL prefix of the encounters folder (for image links).
	  //String dir = "/" + CommonConfiguration.getDataDirectoryName(context) + "/encounters";
	  //String dataDirUrlPrefix = CommonConfiguration.getServerURL(request, dir);
	  // Format string for encounter page URL (with placeholder).
	  String pageUrlFormatEnc = "//" + CommonConfiguration.getURLLocation(request) + "/encounters/encounter.jsp?number=%s";
	  String pageUrlFormatInd = "//" + CommonConfiguration.getURLLocation(request) + "/individuals.jsp?number=%s";
	%>
	
	<jsp:include page="../header.jsp" flush="true">
	  <jsp:param name="isAdmin" value="<%=request.isUserInRole(\"admin\")%>"/>
	</jsp:include>
	
	  <div class="container maincontent">
	
	
	<!------------------------------------------------------------------------->
	
	<%
	  if (results.getMapTests().isEmpty()) {
	    String encUrl = String.format(pageUrlFormatEnc, results.getTestEncounterNumber());
	%>
	  <p><%=bundle.getProperty("noMatches") %></p>
	  <p><a href="<%=encUrl%>"><% out.print(MessageFormat.format(bundle.getProperty("noMatches.text.encounter"), results.getTestEncounterNumber())); %></a></p>
	<%
	System.out.println("No matches!");
	  }
	  else{
		  System.out.println("results.getMapTests().size() is: "+results.getMapTests());
	  }
	  for (Map.Entry<String, List<MMAMatch>> mmaTest : results.getMapTests().entrySet()) {
	    String pathCR = mmaTest.getKey();
	    System.out.println("pathCR: "+pathCR);
	    File fCR = new File(pathCR);
	    String encId = request.getParameter("encounterNumber");
	    System.out.println("encId: "+encId);
		    if(myShepherd.isEncounter(encId)){
		    	System.out.println("#2");
			    Encounter enc = myShepherd.getEncounter(encId);
			    System.out.println("#3");
			    String nameCR = fCR.getName();
			    System.out.println("nameCR: "+nameCR);
			    String name = nameCR.substring(0, nameCR.indexOf("_CR"));
			    System.out.println("name: "+name);
			    //String linkCR = convertFileToURL(dataDirUrlPrefix, fCR);
			    String linkCR = pathCR.replaceAll("/var/lib/tomcat8/webapps/","https://www.mantamatcher.org/");
			    System.out.println("linkCR: "+linkCR);
			    String linkEH = linkCR.replace("_CR", "_EH");
			    System.out.println("linkEH: "+linkEH);
			    String encUrl = String.format(pageUrlFormatEnc, encId);
			    System.out.println("encURL: "+encUrl);
			    boolean encIsAssigned = (enc.getIndividual() != null);
			    System.out.println("encIsAssigned: "+encIsAssigned);
			%>
			
			<div id="mma-queryImage">
			  <a href="<% out.print(encUrl); %>" target="_blank"><img src="<% out.print(linkEH.replaceAll("/var/lib/tomcat8/webapps/shepherd_data_dir/", "")); %>" class="mma-queryImg"/></a>
			  <p><% out.print(name); %></p>
			</div>
			
			<div id="mma-desc">
			  <table>
			    <tr>
			      <th><% out.print(bundle.getProperty("version")); %></th>
			      <td class="mma-version"><% out.print(MessageFormat.format(bundle.getProperty("version.val"), results.getVersion())); %></td>
			    </tr>
			    <tr>
			      <th><% out.print(bundle.getProperty("queryEncounter")); %></th>
			      <td class="mma-queryEncounter"><a href="<% out.print(encUrl); %>"><% out.print(results.getTestEncounterNumber()); %></a></td>
			    </tr>
			    <tr>
			      <th><% out.print(bundle.getProperty("locations")); %></th>
			      <td class="mma-queryEncounter"><% out.print(dispLocIDs); %></td>
			    </tr>
			    <tr>
			      <th><% out.print(bundle.getProperty("dateOfScan")); %></th>
			      <td class="mma-date"><% out.print(DF.format(results.getDate())); %></td>
			    </tr>
			    <tr>
			      <th><% out.print(bundle.getProperty("matchCount")); %></th>
			      <td class="mma-count"><% out.print(results.getMapTests().isEmpty() ? "&nbsp;" : results.getMapTests().entrySet().iterator().next().getValue().size()); %></td>
			    </tr>
			    <tr>
			      <th><% out.print(bundle.getProperty("confidence")); %><br/><span class="mma-small"><% out.print(bundle.getProperty("desc.worstBest")); %></span></th>
			      <td class="mma-confidence"><% out.print(String.format("%.6f", results.getConfidence())); %></td>
			    </tr>
			  </table>
			</div>
			<div id="mma-results">
			  <table id="mma-resultsTable">
			    <tr>
			      <th><%=bundle.getProperty("table.column.rank") %></th>
			      <th><%=bundle.getProperty("table.column.similarity") %><br/><span class="mma-small"><% out.print(bundle.getProperty("table.column.similarityDesc")); %></span></th>
			      <th><%=bundle.getProperty("table.column.matchDetails") %></th>
			      <th><%=bundle.getProperty("table.column.matchedImage") %><br/><span class="mma-small"><% out.print(bundle.getProperty("desc.newWindow")); %></span></th>
			      <th><% out.print(bundle.getProperty("table.column.queryImage")); %><br/><span class="mma-small"><% out.print(encIsAssigned ? MessageFormat.format(bundle.getProperty("desc.assignedTo"), enc.getIndividual().getIndividualID()) : bundle.getProperty("desc.unassigned")); %></span></th>
			    </tr>
			<%
			  if (!mmaTest.getValue().isEmpty()) {
			    for (MMAMatch match : mmaTest.getValue()) {
			      if (match.getFileRef() == null)
			          continue;
			      Encounter encMatch = myShepherd.getEncounter(match.getMatchEncounterNumber());
			      if (encMatch == null)
			        continue;
			      String indUrl = null;
			      boolean indMatch = false;
			      if (encMatch.getIndividual() != null && !"".equals(encMatch.getIndividual().getIndividualID()) && !"Unassigned".equals(encMatch.getIndividual().getIndividualID())) {
			        indUrl = String.format(pageUrlFormatInd, encMatch.getIndividual().getIndividualID());
			        if (encIsAssigned && encMatch.getIndividual()!=null && encMatch.getIndividual().getIndividualID().equals(enc.getIndividual().getIndividualID()))
			          indMatch = true;
			      }
			      String keyPig = findKeyFromValue(encMatch.getPatterningCode(), mapPig);
			      String pigMatch = keyPig == null ? keyPig : bundle.getProperty(keyPig);
			
			      Map<String, File> mmMap = MantaMatcherUtilities.getMatcherFilesMap(match.getFileRef());
			      File match_fCR = mmMap.get("CR");
			      String match_encId = match_fCR.getParentFile().getName();
			      //String match_linkCR = convertFileToURL(dataDirUrlPrefix, match_fCR);
			      String match_linkCR = match_fCR.getAbsolutePath().replaceAll("/var/lib/tomcat8/webapps/","https://www.mantamatcher.org/");
			      String match_linkEH = match_linkCR.replace("_CR.", "_EH.");
			      String match_encUrl = String.format(pageUrlFormatEnc, match_encId);
			%>
			<%  if (indMatch) { %>
			    <tr class="ind-match">
			<%  } else { %>
			    <tr>
			<%  } %>
			      <td class="rank"><% out.print(match.getRank()); %></td>
			      <td class="similarity"><% out.print(String.format("%.6f", match.getScore())); %></td>
			      <td class="filename">
			        <table id="mma-resultDetailsTable">
			<%      if (indUrl != null) { %>
			          <tr><th><% out.print(bundle.getProperty("individual")); %></th><td><a href="<% out.print(indUrl); %>" target="_blank"><% out.print(encMatch.getIndividual().getDisplayName()); %></a></td></tr>
			<%      } else { %>
			          <tr><th><% out.print(bundle.getProperty("individual")); %></th><td>&nbsp;</td></tr>
			<%      } %>
			          <tr><th><% out.print(bundle.getProperty("encounter.date")); %></th><td><span class="nowrap"><% out.print(encMatch.getDate()); %></span></td></tr>
			          <tr><th><% out.print(bundle.getProperty("pigmentation")); %></th><td><span class="nowrap"><% out.print(pigMatch == null ? "&nbsp;" : pigMatch); %></span></td></tr>
			<%      if (indUrl != null && !encIsAssigned) { %>
			          <tr><td colspan="2">
			            <form action="../IndividualAddEncounter" method="post">
			              <input type="hidden" name="number" value="<% out.print(enc.getCatalogNumber()); %>"/>
			              <input type="hidden" name="individual" value="<% out.print(encMatch.getIndividual().getIndividualID()); %>"/>
			              <input type="hidden" name="matchType" value="Pattern match"/>
			              <input type="submit" name="submit" value="<% out.print(MessageFormat.format(bundle.getProperty("assign"), encMatch.getIndividual().getDisplayName())); %>" title="<% out.print(bundle.getProperty("assign.title")); %>"/>
			            </form>
			          </td></tr>
			<%      } %>
			        </table>
			      </td>
			      <td class="matchedImage"><a href="<% out.print(match_encUrl); %>" target="_blank"><img src="<% out.print(match_linkEH.replaceAll("/var/lib/tomcat8/webapps/shepherd_data_dir/", "")); %>" class="mma-matchImg"/></a></td>
			      <td class="queryImage"><a href="<% out.print(encUrl); %>"><img src="<% out.print(linkEH.replaceAll("/var/lib/tomcat8/webapps/shepherd_data_dir/", "")); %>" class="mma-queryImg"/></a></td>
			    </tr>
			<%
			    }
			  } else {
			%>
			    <tr>
			      <td class="noMatches" colspan="5"><%=bundle.getProperty("noMatches") %></td>
			    </tr>
			<%
			  }
			%>
			  </table>
			</div>
			<%
		  }
	  }
	%>
	
	<%
	  if (!results.getMapTests().isEmpty()) {
	%>
	<div id="mma-footer">
	  <p id="mma-copyright"><% out.print(bundle.getProperty("copyright")); %></p>
	</div>
	<%
	  }
	%>
	
	
	<!------------------------------------------------------------------------->
	
	<%
  }
  catch(Exception e){e.printStackTrace();}
  finally{
  	myShepherd.rollbackDBTransaction();
  	myShepherd.closeDBTransaction();
  }
%>


  </div>

  <jsp:include page="../footer.jsp" flush="true"/>
