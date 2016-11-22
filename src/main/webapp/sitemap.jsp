<%@ page contentType="text/html; charset=utf-8" language="java"
         import="org.ecocean.servlet.ServletUtilities,org.ecocean.CommonConfiguration, org.ecocean.Encounter,org.ecocean.MarkedIndividual, org.ecocean.Shepherd, javax.jdo.Extent, javax.jdo.Query,java.io.File" %>
<%@ page import="java.io.FileInputStream" %>
<%@ page import="java.util.Iterator" %>
<%@ page import="java.util.Properties" %>
<%
String context="context0";
context=ServletUtilities.getContext(request);
  //setup our Properties object to hold all properties


  //Shepherd
  Shepherd myShepherd = new Shepherd(context);
  myShepherd.setAction("sitemap.jsp");






%>

    <jsp:include page="header.jsp" flush="true" />


    <div class="container maincontent">
          <h1><%=CommonConfiguration.getHTMLTitle(context) %>
            Sitemap</h1>
      
        <ul>
          <li><a href="//<%=CommonConfiguration.getURLLocation(request)%>">Home</a></li>
    
          <li><a
            href="//<%=CommonConfiguration.getURLLocation(request)%>/submit.jsp">Report
            an encounter</a></li>
          <li><a
            href="//<%=CommonConfiguration.getURLLocation(request)%>/encounters/searchResults.jsp">All
            Encounters</a></li>
          <li><a
            href="//<%=CommonConfiguration.getURLLocation(request)%>/individualSearchResults.jsp">All
            Marked Individuals</a></li>
          <li><a
            href="//<%=CommonConfiguration.getURLLocation(request)%>/encounters/thumbnailSearchResults.jsp?noQuery=true">Image
            thumbnails</a></li>

</ul>
          <h2>All encounters</h2>
        
        <%


          Extent encClass = myShepherd.getPM().getExtent(Encounter.class, true);
          Query encQuery = myShepherd.getPM().newQuery(encClass);
          Extent sharkClass = myShepherd.getPM().getExtent(MarkedIndividual.class, true);
          Query sharkQuery = myShepherd.getPM().newQuery(sharkClass);
          myShepherd.beginDBTransaction();
          try {
            Iterator<Encounter> it2 = myShepherd.getAllEncounters(encQuery);
            while (it2.hasNext()) {
              Encounter tempEnc2 = it2.next();
        %> <a
        href="//<%=CommonConfiguration.getURLLocation(request)%>/encounters/encounter.jsp?number=<%=tempEnc2.getEncounterNumber()%>"><%=tempEnc2.getEncounterNumber()%>
      </a> <%
        }
      %>
       
          <h2>All marked individuals</h2>
       
        <%
          Iterator<MarkedIndividual> it3 = myShepherd.getAllMarkedIndividuals(sharkQuery);
          while (it3.hasNext()) {
            MarkedIndividual tempShark = it3.next();
        %> <a
        href="//<%=CommonConfiguration.getURLLocation(request)%>/individuals.jsp?number=<%=tempShark.getName()%>"><%=tempShark.getName()%>
      </a> <%
          }


        } catch (Exception e) {
        }
        myShepherd.rollbackDBTransaction();
        myShepherd.closeDBTransaction();
        encQuery.closeAll();
        encQuery = null;
        sharkQuery.closeAll();
        sharkQuery = null;


      %>
      </div>
   
      
      
    <jsp:include page="footer.jsp" flush="true"/>

