<%@page import="java.io.*,javax.naming.*,  
 java.util.Date,  
 java.util.Enumeration" %>  
<html>  
  
<body bgcolor="white">  
<h1> Session Info</h1>  
SessionID: <%= session.getId() %><br>  
CreationTime: <%= new Date(session.getCreationTime()) %><br>  
LastAccessedTime: <%= new Date(session.getLastAccessedTime()) %><br>  
  
<h1> Request Information </h1>  
<font size="4">  
JSP Request Method: <%= request.getMethod() %>  
<br>  
Request URL: <%= request.getRequestURL() %>  
<br>  
Request URI: <%= request.getRequestURI() %>  
<br>  
Request Protocol: <%= request.getProtocol() %>  
<br>  
Servlet path: <%= request.getServletPath() %>  
<br>  
Path info: <%= request.getPathInfo() %>  
<br>  
Path translated: <%= request.getPathTranslated() %>  
<br>  
Query string: <%= request.getQueryString() %>  
<br>  
Content length: <%= request.getContentLength() %>  
<br>  
Content type: <%= request.getContentType() %>  
<br>  
Server name: <%= request.getServerName() %>  
<br>  
Server port: <%= request.getServerPort() %>  
<br>  
UserPrincipal: <%= request.getUserPrincipal() %>  
<br>  
Remote user: <%= request.getRemoteUser() %>  
<br>  
Remote address: <%= request.getRemoteAddr() %>  
<br>  
Remote host: <%= request.getRemoteHost() %>  
<br>  
Authorization scheme: <%= request.getAuthType() %>  
<br>  
Is secure: <%= request.isSecure() %>  
<br>  
Locale: <%= request.getLocale() %>  
<hr>  
The browser you are using is <%= request.getHeader("User-Agent") %>  
<hr>  
</font>  
</body>  
</html>  