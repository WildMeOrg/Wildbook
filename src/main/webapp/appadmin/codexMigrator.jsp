<%@ page contentType="text/html; charset=utf-8" language="java"
import="org.ecocean.*,
java.sql.*,
    java.util.ArrayList,
    java.util.List,
    javax.jdo.Query,
    org.ecocean.media.*
    "
%>

<%!


private static void migrateUsers(Connection conn) throws SQLException {
    Statement st = conn.createStatement();
    ResultSet res = st.executeQuery("SELECT * FROM \"user\"");
    while (res.next()) {
        String guid = res.getString("guid");
        System.out.println(">>> " + guid);
    }
}


%>


<%

String jdbcUrl = "jdbc:postgresql://localhost:5432/seal_codex";
String username = "example";
String password = "example";


/*
String context = ServletUtilities.getContext(request);
Shepherd myShepherd = new Shepherd(context);
myShepherd.beginDBTransaction();
*/


Connection conn = DriverManager.getConnection(jdbcUrl, username, password);

migrateUsers(conn);

%>



