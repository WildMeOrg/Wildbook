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

<p><a href="https://www.bpctrust.org/"><img src="cust/mantamatcher/img/BPC_logo_dog.jpg" width="200px" height="*" /></a></p>

<h3>Wild Me</h3>

<p><a href="https://www.wildme.org/"><img src="images/wild-me-logo-high-resolution.png" width="200px" height="*" /></a></p>

<h3>Wildbook</h3>

<p><a href="https://www.wildbook.org/"><img src="images/WildBook_logo_300dpi-04.png" width="200px" height="*" /></a></p>


   
<!-- end maintext -->
</div>


