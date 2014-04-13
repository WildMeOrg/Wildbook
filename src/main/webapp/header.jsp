<%@ page contentType="text/html; charset=utf-8" language="java"
         import="java.util.ArrayList,org.ecocean.servlet.ServletUtilities,org.apache.commons.lang.WordUtils,org.ecocean.*, java.util.Properties" %>

<%--
  ~ The Shepherd Project - A Mark-Recapture Framework
  ~ Copyright (C) 2011-2013 Jason Holmberg
  ~
  ~ This program is free software; you can redistribute it and/or
  ~ modify it under the terms of the GNU General Public License
  ~ as published by the Free Software Foundation; either version 2
  ~ of the License, or (at your option) any later version.
  ~
  ~ This program is distributed in the hope that it will be useful,
  ~ but WITHOUT ANY WARRANTY; without even the implied warranty of
  ~ MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
  ~ GNU General Public License for more details.
  ~
  ~ You should have received a copy of the GNU General Public License
  ~ along with this program; if not, write to the Free Software
  ~ Foundation, Inc., 51 Franklin Street, Fifth Floor, Boston, MA  02110-1301, USA.
  --%>

<%

String context="context0";
context=ServletUtilities.getContext(request);

  //handle some cache-related security
  response.setHeader("Cache-Control", "no-cache"); //Forces caches to obtain a new copy of the page from the origin server
  response.setHeader("Cache-Control", "no-store"); //Directs caches not to store the page under any circumstance
  response.setDateHeader("Expires", 0); //Causes the proxy cache to see the page as "stale"
  response.setHeader("Pragma", "no-cache"); //HTTP 1.0 backward compatibility

//setup our Properties object to hold all properties
  Properties props = new Properties();
  String langCode = "en";

  if (session.getAttribute("langCode") != null) {
    langCode = (String) session.getAttribute("langCode");
  }

  //set up the file input stream
  //props.load(getClass().getResourceAsStream("/bundles/" + langCode + "/header.properties"));
  props = ShepherdProperties.getProperties("header.properties", langCode);


%>

<div id="header"><img name="masthead"
                      src="<%=CommonConfiguration.getURLToMastheadGraphic(context) %>" width="810"
                      height="150" border="0" usemap="#m_masthead" alt=""/></div>
<div id="header_menu">
  <ul id="pmenu">
    <li style="background: #000066;"><a
      href="http://<%=CommonConfiguration.getURLLocation(request) %>"
      style="margin: 0px 0 0px 0px; position: relative; width: 95px; height: 25px; z-index: 100;"><strong><%=props.getProperty("home")%>
    </strong></a></li>
    <li class="drop"><a
      href="http://<%=CommonConfiguration.getURLLocation(request) %>/index.jsp"
      style="margin: 0px 0 0px 0px; position: relative; width: 75px; height: 25px; z-index: 100;"><strong><%=props.getProperty("learn")%>
    </strong></a>
      <!--[if lte IE 6]>
      <table>
        <tr>
          <td><![endif]-->
      <ul>
        <li><a
          href="http://www.wildme.org/wildbook" class="enclose" target="_blank"
          style="margin: 0px 0 0px 0px; position: relative; width: 210px; height: 25px; z-index:
          100;"><strong><%=props.getProperty("learnAboutShepherd")%></strong></a>
        </li>

      </ul>
      <!--[if lte IE 6]></td></tr></table></a><![endif]--></li>
    <li class="drop"><a
      href="http://<%=CommonConfiguration.getURLLocation(request) %>/submit.jsp"
      style="margin: 0px 0 0px 0px; position: relative; width: 90px; height: 25px; z-index: 100;"><strong><%=props.getProperty("participate")%>
    </strong></a>
      <!--[if lte IE 6]>
      <table>
        <tr>
          <td><![endif]-->
      <ul>

        <li><a
          href="http://<%=CommonConfiguration.getURLLocation(request) %>/submit.jsp"
          class="enclose"
          style="margin: 0px 0 0px 0px; position: relative; width: 160px; height: 25px; z-index: 100;"><%=props.getProperty("report")%>
        </a></li>

      </ul>
      <!--[if lte IE 6]></td></tr></table></a><![endif]--></li>
    <li class="drop">
      <a href="http://<%=CommonConfiguration.getURLLocation(request) %>/individualSearchResults.jsp"
         style="margin: 0px 0 0px 0px; position: relative; width: 100px; height: 25px; z-index: 100;">
        <strong><%=props.getProperty("individuals")%>
        </strong></a><!--[if lte IE 6]>
      <table>
        <tr>
          <td><![endif]-->
      <ul>
        <li><a href="http://<%=CommonConfiguration.getURLLocation(request) %>/individualSearchResults.jsp"
               class="enclose"
               style="margin: 0px 0 0px 0px; position: relative; width: 130px; height: 25px;"><%=props.getProperty("viewAll")%>
        </a></li>

      </ul>
      <!--[if lte IE 6]></td></tr></table></a><![endif]--></li>
    <li class="drop"><a
      
      style="margin: 0px 0 0px 0px; position: relative; width: 100px; height: 25px; z-index: 100;"><strong><%=props.getProperty("encounters")%>
    </strong><!--[if IE 7]><!--></a><!--<![endif]-->
      <!--[if lte IE 6]>
      <table>
        <tr>
          <td><![endif]-->
      <ul>
      
      	<!-- list encounters by state -->
      						<%
      						boolean moreStates=true;
      						int cNum=0;
							while(moreStates){
	  								String currentLifeState = "encounterState"+cNum;
	  								if(CommonConfiguration.getProperty(currentLifeState,context)!=null){
	  									%>
										<li>
        									<a href="http://<%=CommonConfiguration.getURLLocation(request) %>/encounters/searchResults.jsp?state=<%=CommonConfiguration.getProperty(currentLifeState,context) %>" class="enclose" style="margin: 0px 0 0px 0px; position: relative; width: 210px; height: 25px;z-index: 100;">
        										<%=props.getProperty("viewEncounters").trim().replaceAll(" ",(" "+WordUtils.capitalize(CommonConfiguration.getProperty(currentLifeState,context))+" "))%>
        									</a>
        								</li>
										<%
										cNum++;
  									}
  									else{
     									moreStates=false;
  									}
  
							} //end while
      						%>
        

        <li><a
          href="http://<%=CommonConfiguration.getURLLocation(request) %>/encounters/thumbnailSearchResults.jsp?noQuery=true"
          class="enclose"
          style="margin: 0px 0 0px 0px; position: relative; width: 210px; height: 25px;"><%=props.getProperty("viewImages")%>
        </a></li>

        <li><a
          href="http://<%=CommonConfiguration.getURLLocation(request) %>/xcalendar/calendar.jsp"
          class="enclose"
          style="margin: 0px 0 0px 0px; position: relative; width: 210px; height: 25px;"><%=props.getProperty("encounterCalendar")%>
        </a></li>



      <%
      if(request.getUserPrincipal()!=null){
      %>
        <li>
        	<a href="http://<%=CommonConfiguration.getURLLocation(request) %>/encounters/searchResults.jsp?username=<%=request.getRemoteUser()%>" class="enclose" style="margin: 0px 0 0px 0px; position: relative; width: 210px; height: 25px;">
        		<%=props.getProperty("viewMySubmissions")%>
        	</a>
        </li>
        <%
        }
        %>
     


      </ul>
      <!--[if lte IE 6]></td></tr></table></a><![endif]--></li>
    <li class="drop">
      <a href="http://<%=CommonConfiguration.getURLLocation(request) %>/welcome.jsp?reflect=http://<%=CommonConfiguration.getURLLocation(request) %>/encounters/encounterSearch.jsp"
        style="margin: 0px 0 0px 0px; position: relative; width: 85px; height: 25px; z-index: 100;"><strong><%=props.getProperty("search")%>
      </strong></a>

        <!--[if lte IE 6]>
      <table>
        <tr>
          <td><![endif]-->
      <ul>


        <li>
          <a href="http://<%=CommonConfiguration.getURLLocation(request) %>/encounters/encounterSearch.jsp"
             class="enclose"
             style="margin: 0px 0 0px 0px; position: relative; width: 150px; height: 25px;">
            <%=props.getProperty("encounterSearch")%>
          </a></li>
        <li><a
          href="http://<%=CommonConfiguration.getURLLocation(request) %>/individualSearch.jsp"
          class="enclose"
          style="margin: 0px 0 0px 0px; position: relative; width: 150px; height: 25px;"><%=props.getProperty("individualSearch")%>
        </a></li>
        
        <li>
	          <a href="http://<%=CommonConfiguration.getURLLocation(request) %>/encounters/searchComparison.jsp"
	             class="enclose"
	             style="margin: 0px 0 0px 0px; position: relative; width: 150px; height: 25px;">
	            <%=props.getProperty("locationSearch")%>
        </a></li>
        
        <li><a
          href="http://<%=CommonConfiguration.getURLLocation(request) %>/googleSearch.jsp"
          class="enclose"
          style="margin: 0px 0 0px 0px; position: relative; width: 150px; height: 25px;"><%=props.getProperty("googleSearch")%>
        </a></li>

      </ul>
      <!--[if lte IE 6]></td></tr></table></a><![endif]--></li>


    <li class="drop"><a id="general_admin"
      href="http://<%=CommonConfiguration.getURLLocation(request) %>/welcome.jsp?reflect=http://<%=CommonConfiguration.getURLLocation(request) %>/appadmin/admin.jsp"
      style="margin: 0px 0 0px 0px; position: relative; width: 90px; height: 25px; z-index: 100;"><strong><%=props.getProperty("administer")%>
    </strong></a>
      <!--[if lte IE 6]>
      <table>
        <tr>
          <td><![endif]-->
      <ul>
        <%
          if (CommonConfiguration.getWikiLocation(context)!=null) {
        %>
        <li><a
          href="<%=CommonConfiguration.getWikiLocation(context) %>library_access_policy"
          target="_blank" class="enclose"
          style="margin: 0px 0 0px 0px; position: relative; width: 190px; height: 25px;"><%=props.getProperty("accessPolicy")%>
        </a></li>
        <li><a
          href="<%=CommonConfiguration.getWikiLocation(context) %>"
          target="_blank" class="enclose"
          style="margin: 0px 0 0px 0px; position: relative; width: 190px; height: 25px;"><%=props.getProperty("userWiki")%>
        </a></li>
        <% } %>

        <li><a
          href="http://<%=CommonConfiguration.getURLLocation(request) %>/appadmin/admin.jsp"
          class="enclose"
          style="margin: 0px 0 0px 0px; position: relative; width: 190px; height: 25px;"><%=props.getProperty("general")%>
        </a></li>
        
        <li><a
	          href="http://<%=CommonConfiguration.getURLLocation(request) %>/appadmin/logs.jsp"
	          class="enclose"
	          style="margin: 0px 0 0px 0px; position: relative; width: 190px; height: 25px;"><%=props.getProperty("logs")%>
        </a></li>
                
        <%
        if(CommonConfiguration.useSpotPatternRecognition(context)){
        %>
         <li><a
	          href="http://<%=CommonConfiguration.getURLLocation(request) %>/software/software.jsp"
	          class="enclose"
	          style="margin: 0px 0 0px 0px; position: relative; width: 190px; height: 25px;"><%=props.getProperty("gridSoftware")%>
        </a></li>

        	<li><a href="http://<%=CommonConfiguration.getURLLocation(request) %>/appadmin/scanTaskAdmin.jsp?langCode=<%=langCode%>" class="enclose" style="margin:0px 0 0px 0px; position:relative; width:190px; height:25px;z-index:99;">Grid Administration</a></li>
		<%
          }
		%>
		
	<li><a
	          href="http://<%=CommonConfiguration.getURLLocation(request) %>/appadmin/users.jsp?context=context0"
	          class="enclose"
	          style="margin: 0px 0 0px 0px; position: relative; width: 190px; height: 25px;"><%=props.getProperty("userManagement")%>
        </a></li>	
		
		
        
        <%
          if (CommonConfiguration.getTapirLinkURL(context) != null) {
        %>
        <li><a
          href="<%=CommonConfiguration.getTapirLinkURL(context) %>"
          class="enclose"
          style="margin: 0px 0 0px 0px; position: relative; width: 190px; height: 25px;"><%=props.getProperty("tapirLink")%>
        </a></li>
        <% } %>
        
        
                <%
	          if (CommonConfiguration.getIPTURL(context) != null) {
	        %>
	        <li><a
	          href="<%=CommonConfiguration.getIPTURL(context) %>"
	          class="enclose"
	          style="margin: 0px 0 0px 0px; position: relative; width: 190px; height: 25px;"><%=props.getProperty("iptLink")%>
	        </a></li>
        <% } %>
        
        
    
        <li><a href="http://<%=CommonConfiguration.getURLLocation(request) %>/appadmin/kwAdmin.jsp"
               class="enclose"
               style="margin: 0px 0 0px 0px; position: relative; width: 190px; height: 25px;"><%=props.getProperty("photoKeywords")%>
        </a>
        </li>
        <%
          

          if (CommonConfiguration.allowAdoptions(context)) {
        %>
        <li class="drop"><a
          href="http://<%=CommonConfiguration.getURLLocation(request) %>/adoptions/adoption.jsp"
          style="margin: 0px 0 0px 0px; position: relative; width: 190px; height: 25px; z-index: 100;"><strong><%=props.getProperty("adoptions")%>
        </strong>
          <img
            src="http://<%=CommonConfiguration.getURLLocation(request) %>/images/white_triangle.gif"
            border="0" align="absmiddle"></a>
          <!--[if lte IE 6]>
          <table>
            <tr>
              <td><![endif]-->
          <ul>
            <li><a
              href="http://<%=CommonConfiguration.getURLLocation(request) %>/adoptions/adoption.jsp"
              class="enclose"
              style="margin: 0px 0 0px 80px; position: relative; width: 190px; height: 25px;"><%=props.getProperty("createEditAdoption")%>
            </a></li>
            <li
              style="margin: 0px 0 0px 80px; position: relative; width: 191px; height: 26px;"><a
              href="http://<%=CommonConfiguration.getURLLocation(request) %>/adoptions/allAdoptions.jsp"
              class="enclose"
              style="margin: 0px 0 0px 0px; position: relative; width: 190px; height: 25px;"><%=props.getProperty("viewAllAdoptions")%>
            </a></li>

          </ul>
        </li>

        <%
          }
        %>

	        <li><a
          href="http://www.wildme.org/wildbook" class="enclose" target="_blank"
          style="margin: 0px 0 0px 0px; position: relative; width: 190px; height: 25px; z-index:
          100;"><strong><%=props.getProperty("shepherdDoc")%></strong></a>
        </li>
        
<li><a href="http://<%=CommonConfiguration.getURLLocation(request) %>/javadoc/index.html" class="enclose" style="margin:0px 0 0px 0px; position:relative; width:190px; height:25px;z-index:99;">Javadoc</a></li>
<%
if(CommonConfiguration.isCatalogEditable(context)){
%>						
<li><a href="http://<%=CommonConfiguration.getURLLocation(request) %>/appadmin/import.jsp" class="enclose" style="margin:0px 0 0px 0px; position:relative; width:190px; height:25px;z-index:99;">Data Import</a></li>
<%
}
%>					

        <!--[if lte IE 6]></td></tr></table></a><![endif]-->

      </ul>
      <!--[if lte IE 6]></td></tr></table></a><![endif]--></li>

    <li><a href="http://<%=CommonConfiguration.getURLLocation(request) %>/contactus.jsp"
           style="margin:0px 0 0px 0px; position:relative; width:90px; height:25px; z-index:100;"><strong><%=props.getProperty("contactUs")%>
    </strong></a></li>
    <%if (request.getRemoteUser() == null) {%>
    <li><a
      href="http://<%=CommonConfiguration.getURLLocation(request) %>/login.jsp"
      style="margin: 0px 0 0px 0px; position: relative; width: 76px; height: 25px; z-index: 100;"><strong><%=props.getProperty("login")%>
    </strong></a></li>
    <%} else {%>
    <li><a
      href="http://<%=CommonConfiguration.getURLLocation(request) %>/LogoutUser"
      style="margin: 0px 0 0px 0px; position: relative; width: 76px; height: 25px; z-index: 100;"><strong><%=props.getProperty("logout")%>
    </strong></a></li>
    <%}%>

  </ul>
</div>
<div id="header_menu" style="background-color: #D7E0ED">
<table width="810px">
	<tr>
		<td class="caption" class="caption" style="text-align: left;" align="left">
		<table><tr><td>Find record:</td><td><form name="form2" method="get" action="http://<%=CommonConfiguration.getURLLocation(request) %>/individuals.jsp">
            <input name="number" type="text" id="shark" size="25"/>
            <input type="hidden" name="langCode" value="<%=langCode%>"/>
            <input name="Go" type="submit" id="Go2" value="Go"/>
          </form></td></table>
		 
		          
		</td>
		<%
		ArrayList<String> contextNames=ContextConfiguration.getContextNames();
		int numContexts=contextNames.size();
		if(numContexts>1){
		%>
		
		<td  class="caption" style="text-align: right;" align="right">
		<table align="right"><tr><td>Switch context:</td>
		<td><form>
				<select id="context" name="context">
					<%
					for(int h=0;h<numContexts;h++){
						String selected="";
						if(ServletUtilities.getContext(request).equals(("context"+h))){selected="selected=\"selected\"";}
					%>
					
						<option value="context<%=h%>" <%=selected %>><%=contextNames.get(h) %></option>
					<%
					}
					%>
				</select>
			</form>
			
			</td></tr></table>
		 
		</td>
		<script type="text/javascript" src="http://ajax.googleapis.com/ajax/libs/jquery/1.4.1/jquery.min.js"></script>
	<script type="text/javascript" src="http://<%=CommonConfiguration.getURLLocation(request) %>/javascript/jquery.cookie.js"></script>
	<script type="text/javascript">
		$( "#context" ).change(function() {
			
  			//alert( "Handler for .change() called with new value: "+$( "#context option:selected" ).text() +" with value "+ $( "#context option:selected").val());
  			$.cookie("wildbookContext", $( "#context option:selected").val(), {
  			   path    : '/',          //The value of the path attribute of the cookie 
  			                           //(default: path of page that created the cookie).
			   
  			   secure  : false          //If set to true the secure attribute of the cookie
  			                           //will be set and the cookie transmission will
  			                           //require a secure protocol (defaults to false).
  			});
  			
  			//alert("I have set the wildbookContext cookie to value: "+$.cookie("wildbookContext"));
  			location.reload(true);
  			
		});
	</script>

		<%
		}
		%>
	</tr>
</table>

</div>
