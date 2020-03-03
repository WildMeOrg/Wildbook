<%@ page contentType="text/html; charset=utf-8" language="java"
         import="org.ecocean.CommonConfiguration,java.util.Properties, org.ecocean.servlet.ServletUtilities, org.ecocean.NoteField, org.ecocean.Shepherd " %>
<%

  //setup our Properties object to hold all properties
  
  String langCode = ServletUtilities.getLanguageCode(request);

  //set up the file input stream
  //FileInputStream propsInputStream=new FileInputStream(new File((new File(".")).getCanonicalPath()+"/webapps/ROOT/WEB-INF/classes/bundles/"+langCode+"/submit.properties"));
  //props.load(propsInputStream);
  
  

  
  String context=ServletUtilities.getContext(request);
Shepherd myShepherd = new Shepherd(context);
        request.setAttribute("pageTitle", "Kitizen Science &gt; Mobile Devices");

%>
<jsp:include page="header.jsp" flush="true"/>
<div class="container maincontent">


<%= NoteField.buildHtmlDiv("0ea7e37f-5b1f-43d9-a153-4b0ad4d732f2", request, myShepherd) %>
<div class="org-ecocean-notefield-default" id="default-0ea7e37f-5b1f-43d9-a153-4b0ad4d732f2">
<p>
Processing submissions on small screens or mobile devices can cause problems with image display.
</p>

<p>
A desktop browser is recommended.
</p>
</div>


</div>


<jsp:include page="footer.jsp" flush="true" />
