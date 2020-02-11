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
myShepherd.beginDBTransaction();

String username = null;
User user = null;
boolean indocetUser = false;

try {
  if(request.getUserPrincipal()!=null){
    user = myShepherd.getUser(request);
    username = (user!=null) ? user.getUsername() : null;
    indocetUser = (user!=null && user.hasAffiliation("indocet"));
  }
}
catch(Exception e){
  System.out.println("Exception on indocetCheck in footer.jsp:");
  e.printStackTrace();
  myShepherd.closeDBTransaction();
}
finally{
  myShepherd.rollbackDBTransaction();
  myShepherd.closeDBTransaction();
}
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
                    <%if (indocetUser) {%>
                      <p>
                      <a href="https://www.ffem.fr" class="col-sm-4" title="Funded in part by FFEM">
                        <img src="<%=urlLoc %>/cust/indocet/logo_FFEM.png" alt=" logo" style="margin-top:40px;" />
                      </a>
                      <a href="commissionoceanindien.org" class="col-sm-4" title="Funded in part by COI">
                        <img src="<%=urlLoc %>/cust/indocet/logo_COI.png" alt=" logo" style="margin-top:40px;" />
                      </a>
                    </p>
                    <%}%>

                    <a href="http://www.wildbook.org" class="col-sm-4" title="This site is Powered by Wildbook">
                      <img src="<%=urlLoc %>/images/WildBook_logo_72dpi-01.png" alt=" logo" class="pull-right" style="
  											height: 150px;
  										"/>
                    </a>
                </div>
                </div>
              </div>
            </div>

        </footer>
        <!-- /footer -->
    </body>
</html>
