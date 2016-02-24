<%@ page contentType="text/html; charset=utf-8" language="java" %>
<%@ page import="java.util.Properties" %>
<%@ page import="org.ecocean.*" %>
<%@ page import="org.ecocean.servlet.ServletUtilities" %>
<%
  String context = ServletUtilities.getContext(request);
  String langCode = ServletUtilities.getLanguageCode(request);
  Properties props = ShepherdProperties.getProperties("removeEmailAddress.properties", langCode, context);

  //handle some cache-related security
  response.setHeader("Cache-Control", "no-cache"); //Forces caches to obtain a new copy of the page from the origin server
  response.setHeader("Cache-Control", "no-store"); //Directs caches not to store the page under any circumstance
  response.setDateHeader("Expires", 0); //Causes the proxy cache to see the page as "stale"
  response.setHeader("Pragma", "no-cache"); //HTTP 1.0 backward compatibility

  //load our variables for the submit page
  String warning = props.getProperty("warning");
	String hashedEmail="NONE";
	if(request.getParameter("hashedEmail")!=null){hashedEmail=request.getParameter("hashedEmail");}	  



%>
 <jsp:include page="header.jsp" flush="true"/>
        <div class="container maincontent">
      
                <h1 class="intro"><%=props.getProperty("removeTitle")%></h1>

                <p><%=warning %></p>
              
                <p>&nbsp;</p>

              
                <table width="720" border="0" cellpadding="5" cellspacing="0">
                  <tr>
                    <td align="right" valign="top">
                      <form name="remove_email" method="post" action="/RemoveEmailAddress">
                        <input name="hashedEmail" type="hidden" value="<%=hashedEmail%>" /> 
                        <input name="yes" type="submit" id="yes" value="<%=props.getProperty("remove")%>" />
                       </form>
                    </td>
                 
                    <td align="left" valign="top">
                      <form name="form2" method="post" action="/index.jsp">
                               <input name="no" type="submit" id="no" value="<%=props.getProperty("cancel")%>" />
                      </form>
                    </td>
                  </tr>
                </table>
       

        </div>
        
      <jsp:include page="footer.jsp" flush="true"/>
