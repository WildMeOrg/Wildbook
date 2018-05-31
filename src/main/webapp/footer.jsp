<%@ page
		contentType="text/html; charset=utf-8"
		language="java"
     	import="org.ecocean.CommonConfiguration,org.ecocean.ContextConfiguration"
%>
        <%
        String urlLoc = "//" + CommonConfiguration.getURLLocation(request);
        %>

        <!-- footer -->
		
        <footer class="page-footer" >

		<hr>

            <div class="container-fluid" style="background-color: white;" >
              <div class="container main-section">

			  <div class="row">
				     <!-- Your Company Name -->
		<a href="http://www.ncaquariums.com" target="_blank"> <img src="<%=urlLoc %>/images/ncaquariums/footerlogo1.jpg" alt="NC Aquarium"> </a>
		<a href="http://www.sezarc.org" target="_blank"> <img src="<%=urlLoc %>/images/ncaquariums/footerlogo2.jpg" target="_blank" alt="NC Aquarium"> </a>
		<a href="https://www.blueelementsimaging.com" target="_blank"> <img src="<%=urlLoc %>/images/ncaquariums/footerlogo3.jpg" alt="NC Aquarium"> </a>
		<a href="http://www.wildme.org" target="_blank"> <img src="<%=urlLoc %>/images/ncaquariums/footerlogo4.jpg" alt="NC Aquarium"> </a>
		<a href="http://mnzoo.org" target="_blank"> <img src="<%=urlLoc %>/images/ncaquariums/footerlogo5.jpg" alt="NC Aquarium"> </a>
		<a href="https://www.georgiaaquarium.org/" target="_blank"> <img src="<%=urlLoc %>/images/ncaquariums/footerlogo6.jpg" alt="NC Aquarium"> </a>
		<!-- Copyright -->

			  </div>
			  
                <div class="row">
                   
                    <img src="<%=urlLoc %>/images/WildBook_logo_72dpi-01.png" alt=" logo" class="pull-left" style="height: 100px;"/><p class="col-sm-8" style="margin-top:40px;"> <a href="http://www.wildbook.org">Wildbook v.<%=ContextConfiguration.getVersion() %></a> is distributed under the GPL v2 license and is intended to support mark-recapture field studies. <a href="http://ncaquariums.wildbook.org/userAgreement.jsp" target="_blank">Use of this site is governed by our User Agreement.</a>
                  
                  </p>
                           
							
         



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
