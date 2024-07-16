<%@ page contentType="text/html; charset=utf-8" language="java"
import="org.ecocean.*,
org.ecocean.servlet.ServletUtilities,
java.io.IOException,
javax.servlet.jsp.JspWriter,
java.sql.*,
java.util.ArrayList,
java.util.List,
java.util.Properties,
javax.jdo.Query,
org.ecocean.media.*
    "
%>

<%!


private static void migrateUsers(JspWriter out, Connection conn) throws SQLException, IOException {
    Statement st = conn.createStatement();
    ResultSet res = st.executeQuery("SELECT * FROM \"user\"");
    while (res.next()) {
        String guid = res.getString("guid");
        out.println("<p>" + guid + "</p>");
    }
}


%>


<%


String context = ServletUtilities.getContext(request);
Shepherd myShepherd = new Shepherd(context);
myShepherd.beginDBTransaction();

Properties props = ShepherdProperties.getProperties("codexMigration.properties", "", context);
"
String dbUrl = props.getProperty("codexDbUrl", context);
String dbUsername = props.getProperty("codexDbUsername", context);
String dbPassword = props.getProperty("codexDbPassword", context);


Connection conn = DriverManager.getConnection(dbUrl, dbUsername, dbPassword);

migrateUsers(out, conn);

myShepherd.commitDBTransaction();
myShepherd.closeDBTransaction();

%>



