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

<!DOCTYPE html PUBLIC "-//W3C//DTD XHTML 1.0 Transitional//EN" "http://www.w3.org/TR/xhtml1/DTD/xhtml1-transitional.dtd">
<%@ page contentType="text/html; charset=utf-8" language="java"
         import="org.ecocean.servlet.ServletUtilities,org.ecocean.*, java.util.Properties" %>
<%

String context="context0";
context=ServletUtilities.getContext(request);
  //setup our Properties object to hold all properties
  Properties props = new Properties();
  //String langCode = "en";

  String langCode=ServletUtilities.getLanguageCode(request);
  

%>

<jsp:include page="../header.jsp" flush="true"/>
<div class="container maincontent">
          <h1>Client Software</h1>
     
        <p>Click on a link below to download the appropriate software client for use with Wildbook.</p>



        <p><a href="GridClient.zip"><strong>Grid Client</strong></a><br/>
          <em>Purpose</em>: Pattern comparison in the Wildbook Framework<br/>
          <em>Requirements</em>: <a href="http://www.java.com/en/download/index.jsp">Java 7</a>
        </p>


      </div>
    
    <jsp:include page="../footer.jsp" flush="true"/>
  
