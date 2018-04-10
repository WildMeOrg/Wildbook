<%@ page contentType="text/html; charset=iso-8859-1" language="java"
         import="org.ecocean.servlet.ServletUtilities,org.ecocean.CommonConfiguration, org.ecocean.Keyword, org.ecocean.Shepherd" %>
<%@ page import="javax.jdo.Extent" %>
<%@ page import="javax.jdo.Query" %>
<%@ page import="java.util.Iterator" %>
<%

	String context=ServletUtilities.getContext(request);
  Shepherd myShepherd = new Shepherd(context);
  myShepherd.setAction("kwAdmin.jsp");
  Extent allKeywords = myShepherd.getPM().getExtent(Keyword.class, true);
  Query kwQuery = myShepherd.getPM().newQuery(allKeywords);

//handle some cache-related security
  response.setHeader("Cache-Control", "no-cache"); //Forces caches to obtain a new copy of the page from the origin server
  response.setHeader("Cache-Control", "no-store"); //Directs caches not to store the page under any circumstance
  response.setDateHeader("Expires", 0); //Causes the proxy cache to see the page as "stale"
  response.setHeader("Pragma", "no-cache"); //HTTP 1.0 backward compatibility
%>

    <jsp:include page="../header.jsp" flush="true" />
     
     <div class="container maincontent">
      <%
        myShepherd.beginDBTransaction();
      %>

      <h1><img src="../images/keyword_icon_large.gif" width="50" height="50"
                     hspace="3" vspace="3" align="absmiddle"/> Image Keyword
              Administration
          
      </h1>

      <p>There are currently <%=myShepherd.getNumKeywords()%> keywords
        defined in the database.</p>
      <table width="720" border="1" cellpadding="3" bordercolor="#000000"
             bgcolor="#CCCCCC">
        <tr>
          <td>
            <p><strong>Add a new keyword</strong></p>

            <form action="../KeywordHandler" method="post" name="addNew" id="addNew">

              <p>New keyword description (visible to users): 
              <input name="readableName" type="text" id="readableName" size="40" maxlength="40"> 
              <br />
              Example: <font face="Courier New, Courier, mono">scar, fin, 1st dorsal</font></p>
				<input name="action" type="hidden" id="action" value="addNewWord" />
              <p><input type="submit" name="Submit" value="Add"></p>
            </form>
          </td>
        </tr>
      </table>
      <p>&nbsp;</p>
      <table width="720" border="1" cellpadding="3" bordercolor="#000000"
             bgcolor="#CCCCCC">
        <tr>
          <td>
            <p><strong>Remove a keyword</strong></p>

            <form action="../KeywordHandler" method="post" name="removeWord"
                  id="removeWord">
              <p>Keyword to remove: <select name="keyword" id="keyword">

                <%
                  int totalKeywords = myShepherd.getNumKeywords();
                  Iterator<Keyword> keys = myShepherd.getAllKeywords(kwQuery);
                  for (int n = 0; n < totalKeywords; n++) {
                    Keyword word = keys.next();
                %>

                <option value="<%=word.getIndexname()%>"><%=word.getReadableName()%>
                </option>
                <%}%>

              </select> 
              <input name="action" type="hidden" id="action" value="removeWord"></p>
              <p><input name="Submit2" type="submit" id="Submit2" value="Remove"></p>
            </form>
          </td>
        </tr>
      </table>
      <p>&nbsp;</p>
      <table width="720" border="1" cellpadding="3" bordercolor="#000000"
             bgcolor="#CCCCCC">
        <tr>
          <td>
            <p><strong>Rename a keyword</strong></p>

            <form action="../KeywordHandler" method="post" name="renameWord"
                  id="remnameWord">
              <p>Keyword to rename: <select name="keyword" id="keyword">

                <%

                  keys = myShepherd.getAllKeywords(kwQuery);
                  for (int w = 0; w < totalKeywords; w++) {
                    Keyword word = keys.next();
                %>

                <option value="<%=word.getReadableName()%>"><%=word.getReadableName()%>
                </option>
                <%}%>

              </select>
              <p>New keyword description (visible to users): <input
                name="newName" type="text" id="newName" size="40" maxlength="40"></p>
              <input name="action" type="hidden" id="action" value="rename"></p>
              <p><input name="Submit2" type="submit" id="Submit2" value="Rename"></p>
            </form>
          </td>
        </tr>
      </table>

      <%
        kwQuery.closeAll();
        myShepherd.rollbackDBTransaction();
        myShepherd.closeDBTransaction();
        kwQuery = null;
        myShepherd = null;
      %>
      
       </div>
     
      
      <jsp:include page="../footer.jsp" flush="true"/>
   



