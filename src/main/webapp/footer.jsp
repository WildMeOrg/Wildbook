<%@ page 
		contentType="text/html; charset=utf-8" 
		language="java"
     	import="org.ecocean.CommonConfiguration"
%>
        <%
        String urlLoc = "http://" + CommonConfiguration.getURLLocation(request);
        %>
        
        <!-- footer -->
        <footer class="page-footer">
    
            <div class="container-fluid">
              <div class="container main-section">

        <hr />

                <div class="row">
                  <p class="col-sm-8 col-md-8 col-lg-8">
                    <small>This software is distributed under the GPL v3 license and is intended to support mark-recapture field studies.</small>
                  </p>
                  <a href="http://www.wildme.org/wildbook" class="col-sm-4 col-md-4 col-lg-4" title="This site is Powered by Wildbook">
                      <img src="<%=urlLoc %>/images/logo_wildbook.jpg" alt=" logo" class="pull-right" />
                  </a>
                </div>
              </div>
            </div>
        </footer>
        <!-- /footer -->
    </body>
</html>