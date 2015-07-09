<%@ page 
		contentType="text/html; charset=utf-8" 
		language="java"
     	import="org.ecocean.CommonConfiguration"
%>
        <%
        String urlLoc = "http://" + CommonConfiguration.getURLLocation(request);
        %>
        
        <!-- footer -->
        <footer class="page-footer">
         <h2 class="section-header">Created in partnership with</h2>
            <div class="container-fluid">
              <div class="container main-section">
                      <ul class="list-unstyled partner-list large">
          <li>
            <a href="http://http://www.marinemegafauna.org/" class="partner" title="Visit Marine Megafauna Foundation website">
              <img src="<%=urlLoc %>/cust/mantamatcher/img/partnerlogo_mmf.svg" alt=" logo" />
            </a>
          </li>
        </ul>

        <h3 class="footer-section-header">We share our data with</h3>
        
        <ul class="list-unstyled partner-list">
          <li>
            <a href="http://www.iobis.org/" title="Global Biodiversity Information Facility">
              <img src="<%=urlLoc %>/cust/mantamatcher/img/partnerlogo_obis.jpg" alt=" logo" />
            </a>
          </li>
          <li>
            <a href="http://www.gbif.org/" title="Ocean Biogeographic Information System">
              <img src="<%=urlLoc %>/cust/mantamatcher/img/partnerlogo_gbif.svg" alt=" logo" />
            </a>
          </li>
          <li>
            <a href="http://www.coml.org/" title="Census of Marine life">
              <img src="<%=urlLoc %>/cust/mantamatcher/img/partnerlogo_censusofmarinelife.jpg" alt=" logo" />
            </a>
          </li>
        </ul>

        <hr />

                <div class="row">
                  <p class="col-sm-8 col-md-8 col-lg-8">
                    <small>This software is distributed under the GPL v3 license and is intended to support mark-recapture field studies. Open source and commercially licensed products used in this framework are listed here.</small>
                  </p>
                  <a href="http://www.wildme.org/wildbook" class="col-sm-4 col-md-4 col-lg-4" title="This site is Powered by Wildbook">
                      <img src="<%=urlLoc %>/images/logo_wildbook.jpg" alt=" logo" class="pull-right" />
                  </a>
                </div>
              </div>
            </div>
        </footer>
        <!-- /footer -->
    </body>
</html>