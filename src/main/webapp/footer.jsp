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
        <div class="container d-flex flex-column align-items-center footer-font footer-container"
            
        >
        <div class="footer-row">
            <div class="col-xs-12 col-lg-4 align-items-center py-2 footer-col">
                <a href="https://www.wildme.org/donate.html" class="footer-link text-reset px-2 footer-text">Donate</a>
                <a href="https://docs.wildme.org/product-docs/en/wildbook/getting-started-with-wildbook/" class="footer-link footer-text" >Documentation</a>
            </div>
            <div class="col-xs-12 col-lg-4 align-items-center py-2 footer-col">
                <a href="https://community.wildme.org/" class="footer-link text-reset px-2 footer-text">Community Forum</a>
                <a href="https://github.com/WildMeOrg" class="footer-link text-reset px-2 footer-text">GitHub</a>
            </div>
            <div class="col-xs-12 col-lg-4 align-items-center py-2 footer-col">
                <a href="https://www.instagram.com/conservationxlabs" class="footer-link text-reset px-2 footer-text">Instagram</a>
                <a href="https://www.facebook.com/ConservationXLabs" class="footer-link text-reset px-2 footer-text">Facebook</a>
                <a href="https://twitter.com/conservationx" class="footer-link text-reset px-2 footer-text">X (Twitter)</a>
                <a href="https://www.linkedin.com/company/conservationxlabs/" class="footer-link text-reset px-2 footer-text">LinkedIn</a>
            </div>
        </div>
        <div class="footer-text" style="padding-left: 30%">2024 © Conservation X Labs | All Rights Reserved</div>
    </div>
        <!-- /footer -->
    </body>
</html>
