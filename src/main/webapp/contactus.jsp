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
        <h1 class="intro">Contact us</h1>
        <p>The Wild Me team welcomes your comments and questions.</p>
	
	<h2>Technical Support</h2>
	<p>For technical support with Flukebook, please join us at the <a href="https://community.wildme.org">Wildbook Community Forum</a>.</p>

	<h2>Account Request</h2>
	<p>To request a Flukebook account, <a href="https://us7.list-manage.com/contact-form?u=c5af097df0ca8712f52ea1768&form_id=335cfeba915bbb2a6058d6ba705598ce">click here.</a></p>

<h2>General Information</h2>
<p>For more information about the Wild Me Lab of Conservation X Labs, please check out <a href="https://www.wildme.org">https://www.wildme.org</a> or email us at info at wildme dot org.</p>

      <!-- end maintext -->
      </div>
	  
<jsp:include page="footer.jsp" flush="true"/>