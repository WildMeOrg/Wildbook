<%@ page contentType="text/html; charset=utf-8" language="java"
         import="org.ecocean.CommonConfiguration,org.ecocean.ContextConfiguration,org.ecocean.servlet.ServletUtilities, java.util.Properties" %>

<%--
  ~ The Shepherd Project - A Mark-Recapture Framework
  ~ Copyright (C) 2011 Jason Holmberg
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

  //setup our Properties object to hold all properties
  Properties props = new Properties();
  //String langCode = "en";
  String langCode=ServletUtilities.getLanguageCode(request);
  
  String context="context0";
  context=ServletUtilities.getContext(request);

%>
<div id="footer">
 

  </div>
       <p align="left"><a href="http://<%=CommonConfiguration.getURLLocation(request)%>/<%=ContextConfiguration.getDataDirForContext(context) %>/rss.xml"><img src="http://<%=CommonConfiguration.getURLLocation(request)%>/images/rssfeed.gif"
                                                 width="80" height="15" border="0"
                                                 alt="RSS News Feed"/></a> &nbsp;<a href="http://<%=CommonConfiguration.getURLLocation(request)%>/<%=ContextConfiguration.getDataDirForContext(context) %>/atom.xml"><img
            src="http://<%=CommonConfiguration.getURLLocation(request)%>/images/atom-feed-icon.gif" border="0" alt="ATOM News Feed"/></a></p>
  
  <p class="credit">All Wildbook for Whale Sharks photo-identification library contents copyright Wild Me and respective individual contributors. Unauthorized usage of any material for any purpose is strictly prohibited. </p>
      <p class="credit">For more information about intellectual property protection and our terms of usage, please read our <a href="http://www.whaleshark.org/wiki/doku.php?id=visitor_agreement" target="_blank">Visitor Agreement</a>. To acknowledge Wildbook for Whale Sharks data usage in a paper, please visit our <a href="publications.jsp#acknowl" target="_blank">Publications page</a>. </p>
		

     

  <p class="credit">This software is distributed under the <a
    href="http://www.gnu.org/licenses/gpl-3.0.html" target="_blank">GPL v3 license</a> and is
    intended to support mark-recapture field studies. Open source and commercially licensed products
    used in this framework are listed <a
      href="http://<%=CommonConfiguration.getURLLocation(request)%>/thirdparty.jsp">here</a>. This is version <%=CommonConfiguration.getVersion("context0")%>.</p>


  <p>
  	<a href="http://www.wildme.org/shepherd" target="_blank">
    		<img border="0" src="http://<%=CommonConfiguration.getURLLocation(request)%>/images/lib_bottom.gif" alt="Powered by Wildbook"/>
    	</a>
  </p>


</div>
<!-- end footer -->

<!-- start google analytics -->
<script type="text/javascript">

  var _gaq = _gaq || [];
  _gaq.push(['_setAccount', 'UA-30944767-1']);
  _gaq.push(['_trackPageview']);

  (function() {
    var ga = document.createElement('script'); ga.type = 'text/javascript'; ga.async = true;
    ga.src = ('https:' == document.location.protocol ? 'https://ssl' : 'http://www') + '.google-analytics.com/ga.js';
    var s = document.getElementsByTagName('script')[0]; s.parentNode.insertBefore(ga, s);
  })();

</script>
