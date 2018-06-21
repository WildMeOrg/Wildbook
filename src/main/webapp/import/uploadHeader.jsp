<%@ page
		contentType="text/html; charset=utf-8"
		language="java"
     	import="org.ecocean.CommonConfiguration,org.ecocean.ContextConfiguration"
%>
        <%
        String urlLoc = "//" + CommonConfiguration.getURLLocation(request);

        // This file is for window-dressing at the top of the (java-servlet) uploader at WebImport.java

        String commitStr = request.getParameter("commit");
        boolean committing = (commitStr!=null);
        String message = "";
        if (!committing) {
        	message = "<strong>REVIEW ONLY: </strong> To ensure data integrity, this is a dry run of your import that will not modify the Flukebook database. When you are satisfied with the results printed on this page, commit your import with the button at the bottom of the page.";
 		} else {
 			message = "<strong>Committing.</strong> When this page is done, your import is complete and you can find your data on Flukebook.";
	 	}
        %>
        <style>
			.import-explanation {
			}
			.import-header {
				margin-top: 0px;
			}
    	</style>

        <div class="container maincontent">

          <h1 class="import-header">Bulk Data Import</h1>
          <p class="import-explanation"><%=message %></p>