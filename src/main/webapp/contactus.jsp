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


        <div class="row">
          <div class="col-xs-12 col-sm-6 col-md-6 col-lg-6">
            <p>
              In case of questions about the Fire salamander project - <a
                href="https://www.uni-bielefeld.de/biologie/crc212/projects/A04.html">The Department of Behavioural Ecology at
                Bielefeld University</a> /
              <a href="https://ekvv.uni-bielefeld.de/pers_publ/publ/PersonDetail.jsp?personId=5514336">Barbara Caspers</a>
        
            </p>
            <p>
              In case of questions about the program - <a href="https://www.wildme.org/#/">Wild Me</a>
            </p>
          </div>
          <div class="col-xs-12 col-sm-6 col-md-6 col-lg-6">
            <img src="images/behavioral_ecology_logo.png" alt="NC3 logo" class="overview-img" />
          </div>
        </div>


      <!-- end maintext -->
      </div>
