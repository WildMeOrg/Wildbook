<%@page contentType="text/html; charset=utf-8" language="java" %>
<%@ page import="java.util.*" %>
<%@ page import="org.ecocean.CommonConfiguration" %>
<%@ page import="org.ecocean.ShepherdProperties" %>
<%@ page import="org.ecocean.servlet.ServletUtilities" %>
<%
  String context = ServletUtilities.getContext(request);
  String langCode = ServletUtilities.getLanguageCode(request);
  Properties props = ShepherdProperties.getProperties("footer.properties", langCode, context);

  String urlLoc = "//" + CommonConfiguration.getURLLocation(request);
%>

        <!-- footer -->
        <footer class="page-footer">
         <h2 class="section-header"><%=props.getProperty("partnershipWith")%></h2>
            <div class="container-fluid">
              <div class="container main-section">
                      <ul class="list-unstyled partner-list large">
          <li>
            <a href="http://www.marinemegafauna.org/" class="partner" title="<%=props.getProperty("logo.main.title")%>">
              <img src="<%=urlLoc %>/cust/mantamatcher/img/partnerlogo_mmf.svg" alt=" logo" />
            </a>
          </li>
        </ul>

        <h3 class="footer-section-header"><%=props.getProperty("shareWith")%></h3>
        
        <ul class="list-unstyled partner-list">
          <li>
            <a href="http://www.iobis.org/" title="<%=props.getProperty("logo.obis.title")%>">
              <img src="<%=urlLoc %>/cust/mantamatcher/img/partnerlogo_obis.jpg" alt=" logo" />
            </a>
          </li>
          <li>
            <a href="http://www.gbif.org/" title="<%=props.getProperty("logo.gbif.title")%>">
              <img src="<%=urlLoc %>/cust/mantamatcher/img/partnerlogo_gbif.svg" alt=" logo" />
            </a>
          </li>
          <li>
            <a href="http://www.coml.org/" title="<%=props.getProperty("logo.coml.title")%>">
              <img src="<%=urlLoc %>/cust/mantamatcher/img/partnerlogo_censusofmarinelife.jpg" alt=" logo" />
            </a>
          </li>
        </ul>

        <hr />

                <div class="row">
                  <p class="col-sm-8 col-md-8 col-lg-8">
                    <small><%=props.getProperty("disclaimer")%></small>
                  </p>

                </div>
              </div>
            </div>
        </footer>
        <!-- /footer -->
    </body>
</html>