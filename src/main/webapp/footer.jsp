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
      org.apache.commons.text.WordUtils,
      org.ecocean.security.Collaboration
      "
%>
        <%
String context="context0";
context=ServletUtilities.getContext(request);
String langCode=ServletUtilities.getLanguageCode(request);
Properties props = new Properties();
props = ShepherdProperties.getProperties("footer.properties", langCode, context);


String urlLoc = "//" + CommonConfiguration.getURLLocation(request);


        %>

        <!-- footer -->
        <div class="container d-flex flex-column align-items-center " 
          style="height: 200px; 
          background-color: #CDE0FE; 
          padding-left: 20%; 
          padding-top:10px;
          margin: 0;
          width: 100vw;
          
          ">
        <div>
          <div class="col-xs-12 col-lg-4 align-items-center py-2" style="display: flex; flex-direction: column; ">
              <a href="https://www.wildme.org/donate.html" class="text-reset px-2" style="text-decoration: none">Donate</a>
              <a href="https://docs.wildme.org/product-docs/en/wildbook/getting-started-with-wildbook/" class="text-reset px-2"style="text-decoration: none">Documentation</a>
          </div>
          <div class="col-xs-12 col-lg-4 align-items-center py-2" style="display: flex; flex-direction: column; text-decoration: none;">
              <a href="https://community.wildme.org/" class="text-reset px-2"style="text-decoration: none">Community Forum</a>
              <a href="https://github.com/WildMeOrg" class="text-reset px-2"style="text-decoration: none">GitHub</a>
          </div>
          <div class="col-xs-12 col-lg-4 align-items-center py-2" style="display: flex; flex-direction: column; text-decoration: none;">
              <a href="https://www.instagram.com/conservationxlabs" class="text-reset px-2"style="text-decoration: none">Instagram</a>
              <a href="https://www.facebook.com/ConservationXLabs" class="text-reset px-2"style="text-decoration: none">Facebook</a>
              <a href="https://twitter.com/conservationx" class="text-reset px-2"style="text-decoration: none">X (Twitter)</a>
              <a href="https://www.linkedin.com/company/conservationxlabs/" class="text-reset px-2"style="text-decoration: none">LinkedIn</a>
          </div>
        
</div>
          <div style="padding-left: 30%">2024 © Conservation X Labs | All Rights Reserved</div>
  </div>
        <!-- /footer -->
    </body>
</html>
