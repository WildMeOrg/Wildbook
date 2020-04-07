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


<h2 class="intro">Contact us </h2>
     
<p><strong>For more information, contact us:</strong></p>

<p><a href="mailto: info@africancarnivore.wildbook.org">info@africancarnivore.wildbook.org </a></p>

<h3>BPCT</h3>

<p><img src="images/bpct.png" width="500px" height="*" /></p>

<h3>Wild Me</h3>

<p><img src="images/wild-me-logo-high-resolution.png" width="500px" height="*" /></p>

<h3>Wildbook&reg;</h3>

<p><img src="images/WildBook_logo_300dpi-04.png" width="500px" height="*" /></p>


   
      <!-- end maintext -->
      </div>


