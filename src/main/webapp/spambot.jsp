<%@ page contentType="text/html; charset=utf-8" language="java"
         import="org.ecocean.servlet.ServletUtilities,org.ecocean.*, java.util.Properties" %>
<%

  //setup our Properties object to hold all properties
  Properties props = new Properties();
  //String langCode = "en";
  String langCode=ServletUtilities.getLanguageCode(request);
  
  String context="context0";
  context=ServletUtilities.getContext(request);


%>

    <jsp:include page="header.jsp" flush="true" />

 
         <div class="container maincontent">

      
          <h1>Failed submission</h1>
        
        <p>You have reached this page because your encounter report was
          rejected. There are two reasons this might occur:</p>
        <ol>
          <li>Our system correctly or incorrectly detected a false
            submission by a spambot. There are many attempts by automated Internet
            programs called &quot;spambots&quot; to post unrelated content on web sites. To prevent inappropriate content, we have
            filters that attempt to block spambots.
          </li>
          <li>An unknown problem was encountered.</li>
        </ol>
        <p>We apologize in advance if you believe you have reached this page
          in error and have a genuine whale shark encounter to report. As an
          alternative, please email your photos and encounter information (date,
          time, size, location, etc.) to:</p>

        <p>We appreciate your effort and your help in our research!</p>


        <p>
          <script type="text/javascript"
                  src="http://www.google.com/coop/cse/brand?form=searchbox_001757959497386081976%3An08dpv5rq-m"></script>
          <!-- Google CSE Search Box Ends --></p>
      </div>
      <!-- end maintext --></div>
    <!-- end maincol -->
    <jsp:include page="footer.jsp" flush="true"/>
  </div>
  <!-- end page --></div>
<!--end wrapper -->
</body>
</html>
