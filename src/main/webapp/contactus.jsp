<%@ page contentType="text/html; charset=utf-8" language="java"
         import="org.ecocean.CommonConfiguration,java.util.Properties, org.ecocean.servlet.ServletUtilities" %>
<%

  //setup our Properties object to hold all properties
  
  String langCode = ServletUtilities.getLanguageCode(request);

  //set up the file input stream
  //FileInputStream propsInputStream=new FileInputStream(new File((new File(".")).getCanonicalPath()+"/webapps/ROOT/WEB-INF/classes/bundles/"+langCode+"/submit.properties"));
  //props.load(propsInputStream);
  
  

  
  String context=ServletUtilities.getContext(request);
        request.setAttribute("pageTitle", "Kitizen Science &gt; Contact Us");

%>
<jsp:include page="header.jsp" flush="true"/>
<div class="container maincontent">

<h1><img src="images/contact_greycat.jpg" width="175" height="300" hspace="10" vspace="10" align="right" />Contact Us</h1>
<p>Contact us via email at kitizenscience@gmail.com.</p>

</div>


<jsp:include page="footer.jsp" flush="true" />
