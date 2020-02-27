<%@ page
		contentType="text/html; charset=utf-8"
		language="java"
     	import="org.ecocean.ShepherdProperties,org.ecocean.CommonConfiguration,org.ecocean.ContextConfiguration,java.util.Properties,org.ecocean.servlet.ServletUtilities"
%>
        <%
        String urlLoc = "//" + CommonConfiguration.getURLLocation(request);
				String context="context0";
				context=ServletUtilities.getContext(request);
				// Make a properties object for lang support.
				Properties props = new Properties();
				// Find what language we are in.
				String langCode = ServletUtilities.getLanguageCode(request);
				// Grab the properties file with the correct language strings.
				props = ShepherdProperties.getProperties("footer.properties", langCode, context);
        %>

        <!-- footer -->
        <footer class="page-footer">

            <div class="container-fluid">
              <div class="container main-section">

                 <div class="row">        
                    <div class="col-xs-3 col-sm-3 col-md-3" style="margin-top:10px;">
                      <a target="_blank" href="http://www.boi.ucsb.edu">                                             
	                      <img class="img-responsive" alt="boi logo" src="<%=urlLoc%>/cust/mantamatcher/img/bass/boi_logo.svg"/>
                      </a>
                    </div>
                    <div class="col-xs-2 col-sm-2 col-md-2" style="margin-top:10px;">
                      <a target="_blank" href="http://lovelab.msi.ucsb.edu/">                      
                        <img class="img-responsive" alt="love lab logo" src="<%=urlLoc%>/cust/mantamatcher/img/bass/love_lab_logo-little.png"/>
                      </a>
                    </div>
                    <div class="col-xs-2 col-sm-2 col-md-2" style="margin-top:10px;">
                  	  <a target="_blank" href="http://www.aquariumofpacific.org/">
                        <img class="img-responsive" alt="aop logo" src="<%=urlLoc%>/cust/mantamatcher/img/bass/Aop_logo.svg"/>
                      </a>
                    </div>
                  	<div class="col-xs-2 col-sm-2 col-md-2" style="margin-top:10px;">
                  	  <a target="_blank" href="http://msi.ucsb.edu/">
                        <img class="img-responsive" alt="msi logo" src="<%=urlLoc%>/cust/mantamatcher/img/bass/msi_logo_centered.png"/>
                      </a>
                    </div>
                    <div class="col-xs-3 col-sm-3 col-md-3" style="margin-top:10px;">
                      <a target="_blank" href="http://www.wildbook.org" title="This site is Powered by Wildbook">
                        <img class="img-responsive" src="<%=urlLoc%>/images/WildBook_logo_footer.png" alt="wildbook logo"/>
                      </a> 
                    </div>
                 </div>
                 
                 <div class="row">
                    <p class="col-sm-12" style="margin-top:10px;">
                      <small><%=props.getProperty("licenceInfo")%>
                      <br> <a href="http://www.wildme.org/wildbook" target="_blank">Wildbook v.<%=ContextConfiguration.getVersion() %></a> </small> 
                    </p>                                   	   
                 </div>
                 
                  
              </div>
            </div>

            <script>
				  (function(i,s,o,g,r,a,m){i['GoogleAnalyticsObject']=r;i[r]=i[r]||function(){
				  (i[r].q=i[r].q||[]).push(arguments)},i[r].l=1*new Date();a=s.createElement(o),
				  m=s.getElementsByTagName(o)[0];a.async=1;a.src=g;m.parentNode.insertBefore(a,m)
				  })(window,document,'script','//www.google-analytics.com/analytics.js','ga');

				  ga('create', 'UA-84279600-2', 'auto');
				  ga('send', 'pageview');

			</script>

        </footer>
        <!-- /footer -->
    </body>
</html>
