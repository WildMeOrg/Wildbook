<%@ page 
		contentType="text/html; charset=utf-8" 
		language="java"
     	import="org.ecocean.CommonConfiguration,org.ecocean.ContextConfiguration"
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
                    <small>This software is distributed under the GPL v3 license and is intended to support mark-recapture field studies.
                  <br> <a href="http://www.wildme.org/wildbook" target="_blank">Wildbook v.<%=ContextConfiguration.getVersion() %></a> </small>
                  </p>
                  <a href="http://www.wildme.org/wildbook" class="col-sm-4 col-md-4 col-lg-4" title="This site is Powered by Wildbook">
                     <img src="<%=urlLoc %>/images/logo_wildbook.jpg" alt=" logo" class="pull-right" />
                      
                      
                    	
                  </a>
                </div>
              </div>
            </div>
            
            <script>
				  (function(i,s,o,g,r,a,m){i['GoogleAnalyticsObject']=r;i[r]=i[r]||function(){
				  (i[r].q=i[r].q||[]).push(arguments)},i[r].l=1*new Date();a=s.createElement(o),
				  m=s.getElementsByTagName(o)[0];a.async=1;a.src=g;m.parentNode.insertBefore(a,m)
				  })(window,document,'script','//www.google-analytics.com/analytics.js','ga');
				
				  ga('create', 'UA-30944767-5', 'auto');
				  ga('send', 'pageview');

			</script>
            
        </footer>
        <!-- /footer -->
    </body>
</html>