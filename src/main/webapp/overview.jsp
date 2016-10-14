<%@ page contentType="text/html; charset=utf-8" language="java" %>
<%@ page import="java.util.Properties" %>
<%@ page import="org.ecocean.ShepherdProperties" %>
<%@ page import="org.ecocean.servlet.ServletUtilities" %>
<%
  String context="context0";
  context=ServletUtilities.getContext(request);
  String langCode=ServletUtilities.getLanguageCode(request);
  Properties props = ShepherdProperties.getProperties("overview.properties", langCode, context);
%>
<jsp:include page="header.jsp" flush="true"/>

<div class="container maincontent">

        <h2><%=props.getProperty("section1_title")%></h2>
            
          <p><%=props.getProperty("section1_text1")%></p>
          <p><%=props.getProperty("section1_text2")%></p>
          <p><%=props.getProperty("section1_text3")%></p>
          <p><%=props.getProperty("section1_text4")%></p>

        <h2><%=props.getProperty("section2_title")%></h2>
          <p><%=props.getProperty("section2_text1")%></p>
          <p><%=props.getProperty("section2_text2")%></p>

          <a name="species"><h3><%=props.getProperty("section2a_title")%></h3></a>
          <p><%=props.getProperty("section2a_text1")%></p>
        
        <table>
            <tr>
            <td>
            <img src="images/differences_between_species.jpg" />
            </td>
            </tr>
            <tr>
            <td align="center">
            <strong><%=props.getProperty("section2a_fig1")%></strong>
            </td>
            </tr>
        </table>

        <p><%=props.getProperty("section2a_text2")%></p>

        <ul>
          <li><%=props.getProperty("section2a_list1_item1")%></li>
          <li><%=props.getProperty("section2a_list1_item2")%></li>
          <li><%=props.getProperty("section2a_list1_item3")%></li>
          <li><%=props.getProperty("section2a_list1_item4")%></li>
        </ul>

        <p>
        <table>
            <tr>
            <td>
            <img src="images/dorsal_malfredi.jpg" />
            </td>
            </tr>
            <tr>
            <td align="center">
            <strong><%=props.getProperty("section2a_fig2")%></strong>
            </td>
            </tr>
        </table>
        </p>
        
        <p>
        <table>
            <tr>
            <td>
            <img src="images/ventral_alfredi.jpg" />
            </td>
            </tr>
            <tr>
            <td align="center">
            <strong><%=props.getProperty("section2a_fig3")%></strong>
            </td>
            </tr>
        </table>
        </p>


        <p><%=props.getProperty("section2a_text3")%></p>
        
        <ul>
          <li><%=props.getProperty("section2a_list2_item1")%></li>
          <li><%=props.getProperty("section2a_list2_item2")%></li>
          <li><%=props.getProperty("section2a_list2_item3")%></li>
          <li><%=props.getProperty("section2a_list2_item4")%></li>
        </ul>

            <p>
                <table>
                    <tr>
                    <td>
                    <img src="images/dorsal_giant.jpg" />
                    </td>
                    </tr>
                    <tr>
                    <td align="center">
                    <strong><%=props.getProperty("section2a_fig4")%></strong>
                    </td>
                    </tr>
                </table>
                </p>
                
                <p>
                <table>
                    <tr>
                    <td>
                    <img src="images/ventral_giants.jpg" />
                    </td>
                    </tr>
                    <tr>
                    <td align="center">
                    <strong><%=props.getProperty("section2a_fig5")%></strong>
                    </td>
                    </tr>
                </table>
        </p>
        <p>
        <table>
            <tr>
            <td>
            <img width="810px" height="*" src="images/spine_prescence.jpg" />
            </td>
            </tr>
            <tr>
            <td align="center">
            <strong><%=props.getProperty("section2a_fig6")%></strong>
            </td>
            </tr>
        </table>
        </p>
        <a name="patterningCode"><h3><%=props.getProperty("section2b_title")%></h3></a>

        <p><%=props.getProperty("section2b_text1")%></p>
        <p><%=props.getProperty("section2b_text2")%></p>
        <p><%=props.getProperty("section2b_text3")%></p>
        <p><%=props.getProperty("section2b_text4")%></p>

        <table>
            <tr>
            <td>
            <img width="810px" height="*" src="images/color_morphs.jpg" />
            </td>
            </tr>
            <tr>
            <td align="center">
            <strong><%=props.getProperty("section2b_fig7")%></strong>
            </td>
            </tr>
        </table>


        <a name="sex"><h3><%=props.getProperty("section2c_title")%></h3></a>

          <p><%=props.getProperty("section2c_text1")%></p>
		
		
		<table>
		<tr>
		<td>
		<img width="810px" height="*" src="images/determining_sex.jpg" />
		</td>
		</tr>
		<tr>
		<td align="center">
		<strong><%=props.getProperty("section2c_fig8")%></strong>
		</td>
		</tr>
		</table>


        <p><%=props.getProperty("section2c_text2")%></p>
		
				<table>
				<tr>
				<td>
				<img width="810px" height="*" src="images/female_maturity.jpg" />
				</td>
				</tr>
				<tr>
				<td align="center">
				<strong><%=props.getProperty("section2c_fig9")%></strong>
				</td>
				</tr>
		</table>
    <p><%=props.getProperty("section2c_text3")%></p>
</div>

<jsp:include page="footer.jsp" flush="true"/>

