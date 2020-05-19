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
        request.setAttribute("pageTitle", "Kitizen Science &gt; Contact Us");

%>
<jsp:include page="header.jsp" flush="true"/>
<div class="container maincontent">

<%= NoteField.buildHtmlDiv("63c3d6ad-6a3b-4384-9e5f-aabb665f7c7e", request, myShepherd) %>

</div>


<jsp:include page="footer.jsp" flush="true" />
