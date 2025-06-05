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
        <div class="container footer-container footer-font" style="padding-top: 40px; padding-bottom: 50px;">
            <div class="footer-row">
                <div class="py-2">
                    <a target="_blank" href="mailto:info@tech4conservation.org" class="footer-link text-reset px-2 footer-text">Request an Account</a>
                    <a target="_blank" href="/resetPassword.jsp" class="footer-link footer-text">Password Reset</a>
                </div>
                <div class="py-2">
                    <a target="_blank" href="http://community.arguswild.ai/" class="footer-link text-reset px-2 footer-text">Community Support Forum</a>
                </div>
                <div class="py-2">
                    <a target="_blank" href="mailto:info@tech4conservation.org" class="footer-link text-reset px-2 footer-text">General Inquiries</a>
                    <a target="_blank" href="https://github.com/Tech-4-Conservation" class="footer-link text-reset px-2 footer-text">GitHub</a>
                </div>
            </div>
            <div class="footer-powered-by py-3 text-center">
                <span>Powered by</span> 
                <a target="_blank" href="http://www.tech4conservation.org" class="footer-link text-reset px-2 footer-text">Tech 4 Conservation</a>
            </div>
        </div>



        <!-- /footer -->
    </body>
</html>
