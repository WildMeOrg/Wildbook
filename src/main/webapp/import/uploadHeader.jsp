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
        message =  "<h1 class=\"import-header\">Bulk Data Import</h1>";
        if (!committing) {
        	message += "<strong>REVIEW ONLY: </strong> To ensure data integrity, this is a dry run of your import that will not modify the African Carnivore Wildbook  database. When you are satisfied with the results printed on this page, commit your import with the button at the bottom of the page.";
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

            <!-- <div class="sliderOverlay">
                <button>Back</button>
                <button>Forward</button>
            </div> -->

        <p class="import-explanation"><%=message %></p>

        <%
        if (!committing) {
        %>

        <strong>The following colors indicate the state of an imported excel field:</strong>
        <div id="importTableLegend">
            <div class="col-sm-12 col-md-6 col-lg-6 col-xl-6"> 
                <div class="importFieldSuccess" title="Success"></div><p> Green indicates that a value was found and extracted.</p>
                <div class="importFieldBlank" title="Blank Field"></div><p> Blue indicates that a field was found, but contained nothing.</p>
            </div>
            <div class="col-sm-12 col-md-6 col-lg-6 col-xl-6">
                <div class="importFieldNull" title="Null Field"></div><p> Yellow indicates that a field was not present, or that nothing could be determined about it.</p>
                <div class="importFieldError" title="Error"></div><p> Red indicates that a field was found and there was an error getting it's value. Most common errors involve letters or non decimal punctuation in number fields.
                If you see this on a Encounter.mediaAsset field, it is almost certainly because of a slightly incorrect image name. Verify the field for capitalization and punctuation then try again.</p>
            </div>
        </div>
        
        <%
        }
        %> 
