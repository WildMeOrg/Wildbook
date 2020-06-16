<%@ page contentType="text/html; charset=utf-8" language="java" %>
<%@ page import="java.util.Iterator" %>
<%@ page import="java.util.Locale" %>
<%@ page import="java.util.Properties" %>
<%@ page import="javax.jdo.Extent" %>
<%@ page import="javax.jdo.Query" %>
<%@ page import="org.ecocean.Keyword" %>
<%@ page import="org.ecocean.Shepherd" %>
<%@ page import="org.ecocean.ShepherdProperties" %>
<%@ page import="org.ecocean.servlet.ServletUtilities" %>
<%@ page import="org.ecocean.StringUtils" %>
<%
  String context = ServletUtilities.getContext(request);
  String langCode = ServletUtilities.getLanguageCode(request);
  Locale locale = new Locale(langCode);
  Properties props = ShepherdProperties.getProperties("admin.properties", langCode, context);

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
<style type="text/css">
.kw-example { font-family: monospace; }
</style>
     
     <div class="container maincontent">
      <%
        myShepherd.beginDBTransaction();
      %>

      <h1><img src="../images/keyword_icon_large.gif" width="50" height="50" hspace="3" vspace="3" align="absmiddle"/> <%=props.getProperty("kw.title")%></h1>

      <p<%=StringUtils.format(locale, props.getProperty("kw.title"), myShepherd.getNumKeywords())%></p>
      <table width="720" border="1" cellpadding="3" bordercolor="#000000" bgcolor="#CCCCCC">
        <tr>
          <td>
            <p><strong><%=props.getProperty("kw.add.title")%></strong></p>

            <form action="../KeywordHandler" method="post" name="addNew" id="addNew">

              <p><%=props.getProperty("kw.add.new")%>
              <input name="readableName" type="text" id="readableName" size="40" maxlength="40"> 
              <br /><%=props.getProperty("kw.add.example")%></p>
				<input name="action" type="hidden" id="action" value="addNewWord" />
              <p><input type="submit" name="Submit" value="<%=props.getProperty("kw.add.submit")%>"></p>
            </form>
          </td>
        </tr>
      </table>
      <p>&nbsp;</p>
      <table width="720" border="1" cellpadding="3" bordercolor="#000000" bgcolor="#CCCCCC">
        <tr>
          <td>
            <p><strong><%=props.getProperty("kw.remove.title")%></strong></p>

            <form action="../KeywordHandler" method="post" name="removeWord" id="removeWord">
              <p><%=props.getProperty("kw.remove.keyword")%> <select name="keyword" id="keyword">

                <%
                  	
                  int totalKeywords = myShepherd.getNumKeywords();
                  Iterator<Keyword> keys = myShepherd.getAllKeywords();
                  if(keys!=null){
	                  while(keys.hasNext()){
	                    Keyword word = keys.next();
	                	%>
	
	                	<option value="<%=word.getIndexname()%>"><%=word.getReadableName()%>
	                	</option>
	                	<%
	               		 }
                
                  }
                %>

              </select> 
              <input name="action" type="hidden" id="action" value="removeWord"></p>
              <p><input name="Submit2" type="submit" id="Submit2" value="<%=props.getProperty("kw.remove.submit")%>"></p>
            </form>
          </td>
        </tr>
      </table>
      <p>&nbsp;</p>
      <table width="720" border="1" cellpadding="3" bordercolor="#000000" bgcolor="#CCCCCC">
        <tr>
          <td>
            <p><strong><%=props.getProperty("kw.rename.title")%></strong></p>

            <form action="../KeywordHandler" method="post" name="renameWord" id="remnameWord">
              <p><%=props.getProperty("kw.rename.keyword")%> <select name="keyword" id="keyword">

                <%
					
	           keys = myShepherd.getAllKeywords();
                if(keys!=null){
	                  while(keys.hasNext()) {
	                    Keyword word = keys.next();
	                %>
	
	                <option value="<%=word.getReadableName()%>"><%=word.getReadableName()%>
	                </option>
	                <%}
                
                  }
                %>

              </select>
              <p><%=props.getProperty("kw.rename.new")%> <input
                name="newName" type="text" id="newName" size="40" maxlength="40"></p>
              <input name="action" type="hidden" id="action" value="rename"></p>
              <p><input name="Submit2" type="submit" id="Submit2" value="<%=props.getProperty("kw.rename.submit")%>"></p>
            </form>
          </td>
        </tr>
      </table>

      <%

        myShepherd.rollbackDBTransaction();
        myShepherd.closeDBTransaction();

        myShepherd = null;
      %>
      
       </div>
     
      
      <jsp:include page="../footer.jsp" flush="true"/>
   



