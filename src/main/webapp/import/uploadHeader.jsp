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
            .sliderOverlay {
                position: float;
            }
    	</style>
        <link rel="stylesheet" href="<%=urlLoc %>/import/tableFeedback.css" />
        <div class="container maincontent">

            <div class="sliderOverlay">
                <button>Back</button>
                <button>Forward</button>
            </div>

        <h1 class="import-header">Bulk Data Import</h1>
        <p class="import-explanation"><%=message %></p>

        <div id="importTableLegend">
            <strong>The following colors indicate the state of an imported excel field:</strong>
            <div class="importFieldSuccess" title="Success"></div><p> Green indicates that a value was found and extracted.</p>
            <div class="importFieldBlank" title="Blank Field"></div><p> Blue indicates that a feild was found, but contained nothing.</p>
            <div class="importFieldNull" title="Null Field"></div><p> Yellow indicates that a feild was not present, or that nothing could be determined about it.</p>
            <div class="importFieldError" title="Error"></div><p> Red indicates that a feild was found and there was a problem getting it's value.</p>
        </div> 
