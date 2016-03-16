<%@ page contentType="text/html; charset=iso-8859-1" language="java" %>
<%@ page import="org.ecocean.*,org.ecocean.servlet.ServletUtilities, org.ecocean.security.Collaboration, java.util.Properties, java.util.Date, java.util.List, java.text.SimpleDateFormat, java.io.*" %>


<%


String context="context0";

//get language
String langCode = ServletUtilities.getLanguageCode(request);

//load user props
Properties props=ShepherdProperties.getProperties("users.properties", langCode,context);



  	
  	
  Shepherd myShepherd = new Shepherd(context);
  	//get the available user roles
  	List<String> roles=CommonConfiguration.getIndexedPropertyValues("role",context);
	List<String> roleDefinitions=CommonConfiguration.getIndexedPropertyValues("roleDefinition",context);
	int numRoles=roles.size();
  	int numRoleDefinitions=roleDefinitions.size();


	User thisUser = null;
	if (request.getUserPrincipal() != null) {
		thisUser = myShepherd.getUser(request.getUserPrincipal().getName());
	}

//handle some cache-related security
  response.setHeader("Cache-Control", "no-cache"); //Forces caches to obtain a new copy of the page from the origin server
  response.setHeader("Cache-Control", "no-store"); //Directs caches not to store the page under any circumstance
  response.setDateHeader("Expires", 0); //Causes the proxy cache to see the page as "stale"
  response.setHeader("Pragma", "no-cache"); //HTTP 1.0 backward compatibility
%>

    <jsp:include page="header.jsp" flush="true" />

       <div class="container maincontent">


<%
		if (thisUser == null) return;
		String rootWebappPath = getServletContext().getRealPath("/");
		File webappsDir = new File(rootWebappPath).getParentFile();
		File shepherdDataDir = new File(webappsDir, CommonConfiguration.getDataDirectoryName(context));
		File collabLogFile = new File(shepherdDataDir, "/users/" + thisUser.getUsername() + "/collaboration.log");
		String h = "";
		if (collabLogFile.exists()) {
			//long since = new Date().getTime() - (10 * 24*60*60*1000);
			SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
			BufferedReader br = new BufferedReader(new FileReader(collabLogFile));
			try {
				StringBuilder sb = new StringBuilder();
				String line = br.readLine();
				while (line != null) {
					String[] fields = line.split("\\t");
					long msec = Long.parseLong(fields[0]);
//12345	test3	ff95c37a-d08a-4460-a43e-2ee9155ba81d	http://wildme.org/batchupload/encounters/searchResults.jsp?state=unapproved	State is one of the following: unapproved <br />
					String row = "<div class=\"logrow\"><span>" + sdf.format(new Date(msec)) + "</span><span>user: <b>" + fields[1] + "</b></span>";
					row += "<span>Encounter: <a href=\"encounters/encounter.jsp?number=" + fields[2] + "\">" + fields[2] + "</a></span>";
					row += "<span class=\"details\">Query: <a href=\"" + fields[3] + "\">" + fields[4] + "</a></div>";
					h = row + h;
					line = br.readLine();
				}
			} finally {
				br.close();
			}
			out.println("<div class=\"collab-log\"><h1>Queries by collaborators</h1><div >" + h + "</div></div>");
		}

%>
    	
    	</div>
    	
      <jsp:include page="footer.jsp" flush="true"/>

<%
myShepherd.rollbackDBTransaction();
myShepherd.closeDBTransaction();
%>


