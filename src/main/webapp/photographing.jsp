<%@ page contentType="text/html; charset=utf-8" language="java" %>
<%@ page import="java.util.Properties" %>
<%@ page import="org.ecocean.ShepherdProperties" %>
<%@ page import="org.ecocean.servlet.ServletUtilities" %>
<%
	String context="context0";
	context=ServletUtilities.getContext(request);
	String langCode=ServletUtilities.getLanguageCode(request);
	Properties props = ShepherdProperties.getProperties("photographing.properties", langCode, context);
%>
<jsp:include page="header.jsp" flush="true"/>

<div class="container maincontent">

		<h2><%=props.getProperty("section1_title")%></h2>

		<p><%=props.getProperty("section1_text1")%></p>

		<h3><%=props.getProperty("section1a_title")%></h3>

		<p><%=props.getProperty("section1a_text1")%></p>
		<p><%=props.getProperty("section1a_text2")%></p>
		<p><%=props.getProperty("section1a_text3")%></p>

		
		<table>
		
		<tr><td>
		<table>
		
		<tr>
		<td><img src="images/alfredi_standardized_id_area.jpg"/></td>
		</tr>
		<tr>
		<td align="center"><strong><%=props.getProperty("section1a_fig1")%></strong></td>
		
		</tr>
		
		</table>
		</td></tr>
		
		
		<tr><td>
		<table>
		<tr>
		<td><img src="images/birostris_standardized_id_area.jpg"/></td>
		</tr>
		<tr>
		<td align="center"><strong><%=props.getProperty("section1a_fig2")%></strong></td>
		</tr>
		
		</table>
		
		</td></tr>
		</table>

		<p><%=props.getProperty("section1a_text4")%></p>
		
				<table>
				<tr>
				<td>
				<img width="810px" height="*" src="images/good_vs_bad_id_shot.jpg" />
				</td>
				</tr>
				<tr>
				<td align="center">
				<strong><%=props.getProperty("section1a_fig3")%></strong>
				</td>
				</tr>
		</table>
		
		<a name="sex"><h3><%=props.getProperty("section1b_title")%></h3></a>

		<p><%=props.getProperty("section1b_text1")%></p>
		
		<table>
						<tr>
						<td>
						<img width="810px" height="*" src="images/edit_quality.jpg" />
						</td>
						</tr>
						<tr>
						<td align="center">
						<strong><%=props.getProperty("section1b_fig4")%></strong>
						</td>
						</tr>
		</table>
		<p>&nbsp;</p>

		<p><%=props.getProperty("section1b_text2")%></p>
		<p><%=props.getProperty("section1b_text3")%></p>
		<p><%=props.getProperty("section1b_text4")%></p>
		
		
		
			<p>&nbsp;</p>
	</div>
	

<jsp:include page="footer.jsp" flush="true" />

