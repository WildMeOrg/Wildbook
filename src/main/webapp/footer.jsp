<%@ page
		contentType="text/html; charset=utf-8"
		language="java"
     	import="org.ecocean.CommonConfiguration,
      org.ecocean.ContextConfiguration,
      org.ecocean.ShepherdProperties,
      org.ecocean.servlet.ServletUtilities,
      org.ecocean.Shepherd,
      org.ecocean.User,
      java.util.ArrayList,
      java.util.List,
      java.util.Properties,
      org.apache.commons.lang.WordUtils,
      org.ecocean.security.Collaboration
      "
%>
        <%
String context="context0";
context=ServletUtilities.getContext(request);
String langCode=ServletUtilities.getLanguageCode(request);
Properties props = new Properties();
props = ShepherdProperties.getProperties("header.properties", langCode, context);
Shepherd myShepherd = new Shepherd(context);

// 'sets serverInfo if necessary
CommonConfiguration.ensureServerInfo(myShepherd, request);

String urlLoc = "//" + CommonConfiguration.getURLLocation(request);

myShepherd.setAction("footer.jsp");
myShepherd.rollbackAndClose();

        %>

        <!-- footer -->
        <footer class="page-footer">

            <div class="container-fluid">
              <div class="container main-section">

                <div class="row">
                  <div class="col-sm-6" style="margin-top:40px;">
                    <small><p>
                      This software is distributed under the GPL v2 license and is intended to support mark-recapture field studies.
                    </p>
                    <p>
                      The rights to images on Wildbook are held by the contributors of those images. Wild Me reserves only the right to use these images as training data for new computer vision algorithms.
                    </p>
                    <p> <a href="http://www.wildme.org/wildbook" target="_blank">Wildbook v.<%=ContextConfiguration.getVersion() %></a>
                    </p></small>
                  </div>
                  <div class="col-sm-6" style="margin-top:40px;">
                    <small>
                  </div>
                  <div class="col-sm-6">
                    <p style="text-align:right;">
                    <%if (ServletUtilities.useCustomStyle(request, "indocet")) {
                      System.out.println("Footer is using custom Indocet style!!");
                    %>
                      <a href="https://www.ffem.fr" class="col-sm-4" title="Funded in part by FFEM">
                        <img src="<%=urlLoc %>/cust/indocet/logo_FFEM.png" alt=" logo" style="margin-top:40px;" />
                      </a>
                      <a href="commissionoceanindien.org" class="col-sm-4" title="Funded in part by COI">
                        <img src="<%=urlLoc %>/cust/indocet/logo_COI.png" alt=" logo" style="margin-top:40px;" />
                      </a>
                      <a href="http://www.wildbook.org" title="This site is Powered by Wildbook">
                        <img src="<%=urlLoc %>/images/WildBook_logo_72dpi-01.png" alt=" logo" style="height: 150px;"/>
                      </a>

                    <%} else if (ServletUtilities.useCustomStyle(request, "NARW")) {
                      System.out.println("Footer is using custom NARW style!!");
                      %>
                        <img src="<%=urlLoc%>/images/partner-logos/NOAA_logo.svg" alt="NOAA logo" style="height:100px; margin-right:20px;" />
                        <img src="<%=urlLoc%>/images/partner-logos/new-england-aquarium.svg" alt="New England Aquarium logo" style="height:100px;margin-right:20px" />
                        <img src="<%=urlLoc%>/images/partner-logos/deepsense.svg" alt="deepsense.ai logo" style="height:30px;margin:20px" />

                        <a href="http://www.wildbook.org" title="This site is Powered by Wildbook">
                          <img src="<%=urlLoc %>/images/WildBook_logo_72dpi-01.png" alt=" logo" style="height: 150px;"/>
                        </a>
                      <%

                    } else if (ServletUtilities.useCustomStyle(request, "wild dolphin project")) {

                      %>

                        <img src="<%=urlLoc%>/images/partner-logos/wilddolphinproject-logo1.png" alt="Wild Dolphin Projec Logo" style="height:120px; margin-right:20px;" />


                        <a href="http://www.wildbook.org" title="This site is Powered by Wildbook">
                          <img src="<%=urlLoc %>/images/WildBook_logo_72dpi-01.png" alt=" logo" style="height: 150px;"/>
                        </a>

                      <%

                    } else  {
                    %>
                    <a href="http://www.wildbook.org" title="This site is Powered by Wildbook" class="pull-right">
                      <img src="<%=urlLoc %>/images/WildBook_logo_72dpi-01.png" alt=" logo" class="pull-right" style="
  											height: 150px;
  										"/>
                    </a>
                    <%}%>
                    </p>
                </div>
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
