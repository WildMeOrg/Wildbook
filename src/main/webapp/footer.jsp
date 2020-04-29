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


String urlLoc = "//" + CommonConfiguration.getURLLocation(request);


        %>

        <!-- footer -->
        <footer class="page-footer">

            <div class="container-fluid">
              <div class="container main-section">

                <div class="row">
                  <div class="col-sm-6" style="margin-top:40px;">
                    <small>This software is distributed under the GPL v2 license and is intended to support mark-recapture field studies.
                  <br> <a href="http://www.wildme.org/wildbook" target="_blank">Wildbook v.<%=ContextConfiguration.getVersion() %></a> </small>
                  </div>
                  <div class="col-sm-6">


                    <a href="http://www.wildbook.org" class="col-sm-4" title="This site is Powered by Wildbook">
                      <img src="<%=urlLoc %>/images/WildBook_logo_72dpi-01.png" alt=" logo" class="pull-right" style="height: 150px;"/>
                    </a>
                </div>
                </div>
              </div>
            </div>

        </footer>
        <!-- /footer -->
    </body>
</html>
