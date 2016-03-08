<%@ page contentType="text/html; charset=utf-8" language="java" %>
<%@ page import="java.util.Properties" %>
<%@ page import="org.ecocean.*" %>
<%@ page import="org.ecocean.servlet.ServletUtilities" %>
<%
  String context = ServletUtilities.getContext(request);
  String langCode = ServletUtilities.getLanguageCode(request);
  Properties props = ShepherdProperties.getProperties("admin.properties", langCode, context);

  Shepherd myShepherd = new Shepherd(context);

//handle some cache-related security
  response.setHeader("Cache-Control", "no-cache"); //Forces caches to obtain a new copy of the page from the origin server
  response.setHeader("Cache-Control", "no-store"); //Directs caches not to store the page under any circumstance
  response.setDateHeader("Expires", 0); //Causes the proxy cache to see the page as "stale"
  response.setHeader("Pragma", "no-cache"); //HTTP 1.0 backward compatibility
%>

    <jsp:include page="../header.jsp" flush="true" />

  
<div class="container maincontent">
     

      <h1><%=props.getProperty("title")%></h1>
     
      <table width="600" border="1">
        <tr>
          <td>
            <p><font size="+1"><%=props.getProperty("restore.title")%></font></p>

            <form name="restoreEncounter" method="post"
                  action="../ResurrectDeletedEncounter">
              <p><%=props.getProperty("restore.encounter")%> <input name="number" type="text" id="number"
                                          size="20" maxlength="50"> <br> <input name="Restore"
                                                                                type="submit"
                                                                                id="Restore"
                                                                                value="<%=props.getProperty("restore.submit")%>"></p>
            </form>
          </td>
        </tr>
      </table>
      <p></p>
      <table width="600" border="1">
        <tr>
          <td>
            <p><font size="+1"><%=props.getProperty("updateEmail.title")%></font></p>

            <form name="updateEmail" method="post" action="../UpdateEmailAddress">
              <p><%=props.getProperty("updateEmail.old")%> <input name="findEmail" type="text"
                                           id="findEmail" size="25" maxlength="50">

              <p><%=props.getProperty("updateEmail.new")%> <input name="replaceEmail" type="text"
                                           id="replaceEmail" size="25" maxlength="50"> <br> <input
                name="Update" type="submit" id="Update" value="<%=props.getProperty("updateEmail.submit")%>"></p>
            </form>
          </td>
        </tr>
      </table>
      <p>&nbsp;</p>
      <table width="600" border="1">
        <tr>
          <td>
            <p><font size="+1"><%=props.getProperty("swapLocationId.title")%></font></p>

            <p class="style1"><%=props.getProperty("swapLocationId.warning")%></p>

            <form name="massSwapLocCode" method="post" action="../MassSwapLocationCode">
              <p><%=props.getProperty("swapLocationId.old")%> <input name="oldLocCode" type="text"
                                           id="oldLocCode" size="10" >

              <p><%=props.getProperty("swapLocationId.new")%> <input name="newLocCode" type="text"
                                           id="newLocCode" size="10" > <br/>
                <br> <input name="Update" type="submit" id="Update"
                            value="<%=props.getProperty("swapLocationId.submit")%>"></p>
            </form>
          </td>
        </tr>
      </table>
      <p>&nbsp;</p>
      <table width="600" border="1">
        <tr>
          <td>
            <p><font size="+1"><%=props.getProperty("exposeToGBIF.title")%></font></p>

            <form name="exposeGBIF" method="post" action="../MassExposeGBIF">

              <input name="Expose to GBIF" type="submit" id="Expose to GBIF"
                     value="<%=props.getProperty("exposeToGBIF.submit")%>">
              </p></form>
          </td>
        </tr>
      </table>
      <p>&nbsp;</p>
      <table width="600" border="1">
        <tr>
          <td>
            <p><font size="+1"><%=props.getProperty("setLocationIdForMatching.title")%></font></p>

            <p class="style1"><%=props.getProperty("setLocationIdForMatching.warning")%></p>

            <form name="massSetLocationCodeFromLocationString" method="post"
                  action="../MassSetLocationCodeFromLocationString">
              <p><%=props.getProperty("setLocationIdForMatching.search")%> <input
                name="matchString" type="text" id="matchString" size="50"
                maxlength="999">

              <p><%=props.getProperty("setLocationIdForMatching.id")%> <input name="locCode" type="text"
                                                 id="locCode" size="10" maxlength="10"> <br/>
                <br> <input name="Update" type="submit" id="Update"
                            value="<%=props.getProperty("setLocationIdForMatching.submit")%>"></p>
            </form>
          </td>
        </tr>
      </table>

      <p>&nbsp;</p>
      <table width="600" border="1">
        <tr>
          <td>
            <p><font size="+1"><%=props.getProperty("setInformForMatching.title")%></font></p>

            <p><font size="+1"><%=props.getProperty("setInformForMatching.text")%></font></p>

            <form name="massSetInformOthers" method="post"
                  action="../MassSetInformOthers">
              <p><%=props.getProperty("setInformForMatching.search")%> <input
                name="matchString" type="text" id="matchString" size="50"
                maxlength="100"/>

              <p><%=props.getProperty("setInformForMatching.emails")%> <input
                name="informEmail" type="text" id="informEmail" size="50"
                maxlength="999"> <br/>
                <br /> <input name="Update" type="submit" id="Update"
                            value="<%=props.getProperty("setInformForMatching.submit")%>"></p>
            </form>
          </td>
        </tr>
      </table>

      <p>&nbsp;</p>
      <table width="600" border="1">
        <tr>
          <td>
            <p><img src="../images/Warning_icon.png" width="25px" height="*" align="absmiddle" />  <font size="+1"><%=props.getProperty("deleteAll.title")%></font>
            <br /><br /><%=props.getProperty("deleteAll.warning")%>
            </p>

            <form onsubmit="return confirm('<%=props.getProperty("deleteAll.confirm")%>');" name="deleteAll" method="post" action="../DeleteAllDataPermanently">

              <input name="deleteAllData" type="submit" id="deleteAllData" value="<%=props.getProperty("deleteAll.submit")%>">
              </p></form>
          </td>
        </tr>
      </table>
      
</div>


      <jsp:include page="../footer.jsp" flush="true"/>



