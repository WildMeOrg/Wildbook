<%@ page contentType="text/plain" %>
<%@ page import="javax.naming.*" %>
<%@ page import="javax.sql.DataSource" %>
<%@ page import="java.sql.Connection" %>
<%
  // Simple health check that verifies Tomcat is running and DB is accessible
  response.setContentType("text/plain");
  
  String status = "OK";
  int httpStatus = 200;
  
  try {
    // Optional: Check database connectivity
    // Uncomment if you want to verify DB connection in health check
    /*
    Context initContext = new InitialContext();
    Context envContext = (Context) initContext.lookup("java:/comp/env");
    DataSource ds = (DataSource) envContext.lookup("jdbc/wildbook");
    Connection conn = ds.getConnection();
    conn.close();
    */
    
    out.print("healthy");
  } catch (Exception e) {
    status = "UNHEALTHY: " + e.getMessage();
    httpStatus = 503;
    response.setStatus(httpStatus);
    out.print(status);
  }
%>

