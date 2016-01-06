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
     
		<p>We welcome your comments and questions, though we are currently unable to respond to every email.</p>
		<p><strong>Media questions</strong></p>
		<p>If you are a journalist, writer or documentary filmmaker, we want to hear from you.</p>
		<p>Wild Me is always looking for opportunities to better tell the story of whale sharks and the growing body of research and discoveries made possible by dedicated scientists, volunteers, and the general public.</p>
		<p>Please contact us at <em>media at whaleshark dot org</em>.</p>
		<p><strong>Web site questions</strong></p>
		<p>If you have technical questions about this web site, please contact <em>webmaster at whaleshark dot org</em>. </p>
		<p><strong>General questions</strong></p>
		<p>If you have general questions about whale sharks or our research, please contact <em>info at whaleshark dot org</em>.  </p>
	
        <p>The team welcomes your comments and questions.</p>

<p>Please email us, and one of us will respond as quickly as possible.</p>
   
      <!-- end maintext -->
      </div>

    <jsp:include page="footer.jsp" flush="true"/>

