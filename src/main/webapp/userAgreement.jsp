<%@ page contentType="text/html; charset=utf-8" language="java" %>
<%@ page import="java.util.Properties" %>
<%@ page import="org.ecocean.*" %>
<%@ page import="org.ecocean.servlet.ServletUtilities" %>
<%
  String context="context0";
  context=ServletUtilities.getContext(request);
  String langCode=ServletUtilities.getLanguageCode(request);
  Properties props = ShepherdProperties.getProperties("userAgreement.properties", langCode, context);

  Shepherd myShepherd = new Shepherd(context);
%>

<jsp:include page="header.jsp" flush="true"/>

  <style type="text/css">
    <!--


    .style2 {
      font-size: x-small;
      color: #000000;
    }

    -->
  </style>




<div class="container maincontent">
        
        <%
          if((request.getParameter("username")!=null)&&(request.getParameter("password")!=null)){
          %>
          
          <p style="color:#FF0000;"><strong><em>Our records indicate that you have not yet accepted the Usage Agreement. Acceptance is required to use this resource. Please read the agreement below and click the "Accept" button to proceed or "Reject" to decline and return to the home page.</em></strong></p>
          <%
          }
          %>
        <br />
        <br />
          <h1><a name="user_agreement_mantamatcherthe_wildbook_for_mantas" id="user_agreement_mantamatcherthe_wildbook_for_mantas"><%=props.getProperty("header")%></a></h1>
          <div>
            <p><%=props.getProperty("subheader")%></p>
          </div>
          <h2><a name="usage_agreement" id="usage_agreement"><%=props.getProperty("section0_title")%></a></h2>
          <div>
            <p><%=props.getProperty("section0_text1")%></p>
            <p><%=props.getProperty("section0_text2")%></p>
          </div>
          <h3><a name="definitions" id="definitions"><%=props.getProperty("section1_title")%></a></h3>
          <div>
            <p><%=props.getProperty("section1_text1")%></p>
          </div>
          <h4><a name="a_authorized_users" id="a_authorized_users"><%=props.getProperty("section1a_title")%></a></h4>
          <div>
            <p><%=props.getProperty("section1a_text1")%></p>
          </div>
          <h3><a name="general" id="general"><%=props.getProperty("section2_title")%></a></h3>
          <div>
            <p><%=props.getProperty("section2_text1")%></p>
            <p><%=props.getProperty("section2_text1")%></p>
          </div>
          <h3><a name="usage" id="usage"><%=props.getProperty("section3_title")%></a></h3>
          <div>
            <p><%=props.getProperty("section3_text1")%></p>
          </div>
          <h4><a name="in_general" id="in_general"><%=props.getProperty("section3a_title")%></a></h4>
          <div>
            <p><%=props.getProperty("section3a_text1")%></p>
          </div>
          <h4><a name="good_faith_data_collection_reporting_sharing_and_collaboration" id="good_faith_data_collection_reporting_sharing_and_collaboration"><%=props.getProperty("section3b_title")%></a></h4>
          <div>
            <p><%=props.getProperty("section3b_text1")%></p>
            <p><%=props.getProperty("section3b_text1")%></p>
          </div>
          <h4><a name="publications_using_data_from_the_wildbook" id="publications_using_data_from_the_wildbook"><%=props.getProperty("section3c_title")%></a></h4>
          <div>
            <p><%=props.getProperty("section3c_text1")%></p>
          </div>
          <h4><a name="copyright_and_trademark_protection" id="copyright_and_trademark_protection"><%=props.getProperty("section3d_title")%></a></h4>
          <div>
            <p><%=props.getProperty("section3d_text1")%></p>
          </div>
          <h4><a name="downloading_materials" id="downloading_materials"><%=props.getProperty("section3e_title")%></a></h4>
          <div>
            <p><%=props.getProperty("section3e_text1")%></p>
          </div>
          <h4><a name="third_party_web_sites" id="third_party_web_sites"><%=props.getProperty("section3f_title")%></a></h4>
          <div>
            <p><%=props.getProperty("section3f_text1")%></p>
          </div>
          <h3><a name="intellectual_property_rights" id="intellectual_property_rights"><%=props.getProperty("section4_title")%></a></h3>
          <div>
            <p><%=props.getProperty("section4_text1")%></p>
          </div>
          <h3><a name="account_and_security" id="account_and_security"><%=props.getProperty("section5_title")%></a></h3>
          <div>
            <p><%=props.getProperty("section5_text1")%></p>
          </div>
          <h3><a name="disclaimer_of_warrantylimitation_of_liability" id="disclaimer_of_warrantylimitation_of_liability"><%=props.getProperty("section6_title")%></a></h3>
          <div>
            <p><%=props.getProperty("section6_text1")%></p>
            <p><%=props.getProperty("section6_text1")%></p>
            <p><%=props.getProperty("section6_text1")%></p>
          </div>
          <h3><a name="indemnification" id="indemnification"><%=props.getProperty("section7_title")%></a></h3>
          <div>
            <p><%=props.getProperty("section7_text1")%></p>
          </div>
          <h3><a name="term_and_termination_of_agreement" id="term_and_termination_of_agreement"><%=props.getProperty("section8_title")%></a></h3>
          <div>
            <p><%=props.getProperty("section8_text1")%></p>
          </div>
          <h3><a name="miscellaneous" id="miscellaneous"><%=props.getProperty("section9_title")%></a></h3>
          <div>
            <p><%=props.getProperty("section9_text1")%></p>
            <p><%=props.getProperty("section9_text2")%></p>
            <p><%=props.getProperty("section9_text3")%></p>

            <%
          	if((request.getParameter("username")!=null)&&(request.getParameter("password")!=null)){
          	%>
            <p><strong><%=props.getProperty("notice_user")%></strong></p>
          	<%
          	}
          	else{
          	%>
          	<p><strong><%=props.getProperty("notice_other")%></strong></p>
          	<%
          	
          	
          	}
          	%>
          </div>
          <h1 class="intro">&nbsp;</h1>
          
          <%
          if((request.getParameter("username")!=null)&&(request.getParameter("password")!=null)){
          %>
          <p><table cellpadding="5"><tr><td>
          	<form name="accept_agreement" action="LoginUser" method="post">
          		<input type="hidden" name="username" value="<%=request.getParameter("username")%>" />
          		<input type="hidden" name="password" value="<%=request.getParameter("password")%>" />
          		<input type="submit" id="acceptUserAgreement" name="acceptUserAgreement" value="Accept"/>
          	</form>
          </td>
          <td><form name="reject_agreement" action="index.jsp" method="get">
          		<input type="submit" name="rejectUserAgreement" value="Reject"/>
          	</form></td>
          </tr></table>
          </p>
          <%
          }
          %>
        </div>

    <jsp:include page="footer.jsp" flush="true"/>
