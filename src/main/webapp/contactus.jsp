<%@ page contentType="text/html; charset=utf-8" language="java"
         import="org.ecocean.CommonConfiguration,java.util.Properties, org.ecocean.servlet.ServletUtilities" %>
<%

  //setup our Properties object to hold all properties

  String langCode = ServletUtilities.getLanguageCode(request);

  //set up the file input stream
  //FileInputStream propsInputStream=new FileInputStream(new File((new File(".")).getCanonicalPath()+"/webapps/ROOT/WEB-INF/classes/bundles/"+langCode+"/submit.properties"));
  //props.load(propsInputStream);




  String context=ServletUtilities.getContext(request);

%>
<jsp:include page="header.jsp" flush="true"/>
<div class="container maincontent">
          <h1 class="intro">Contact us </h1>


        <p>The team welcomes your comments and questions.</p>

<p>Please email us, and one of us will respond as quickly as possible.</p>


<h2>Technical Support</h2>

<p>
Questions about GiraffeSpotter accounts, techincal problems, or generally about the
open source <a target="_new" href="https://www.wildme.org/#/wildbook">Wildbook software</a>, please contact
<i>jon AT wildme.org</i>
</p>

      <!-- end maintext -->
      </div>

    <jsp:include page="footer.jsp" flush="true"/>
