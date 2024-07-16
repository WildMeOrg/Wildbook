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


private static void migrateUsers(JspWriter out, Shepherd myShepherd, Connection conn) throws SQLException, IOException {
    out.println("<ol>");
    Statement st = conn.createStatement();
    ResultSet res = st.executeQuery("SELECT * FROM \"user\"");
    while (res.next()) {
        String guid = res.getString("guid");
        out.println("<li>" + guid + ": ");
        User user = myShepherd.getUserByUUID(guid);
        if (user != null) {
            out.println("<i>user exists; skipping</i>");
        } else {
            user = new User(guid);
/*
 created                          | timestamp without time zone |           | not null | 
 updated                          | timestamp without time zone |           | not null | 
 viewed                           | timestamp without time zone |           | not null | 
 guid                             | uuid                        |           | not null | 
 version                          | bigint                      |           |          | 
 email                            | character varying(120)      |           | not null | 
 password                         | bytea                       |           | not null | 
 full_name                        | character varying(120)      |           | not null | 
 website                          | character varying(120)      |           |          | 
 location                         | character varying(120)      |           |          | 
 affiliation                      | character varying(120)      |           |          | 
 forum_id                         | character varying(120)      |           |          | 
 locale                           | character varying(20)       |           |          | 
 accepted_user_agreement          | boolean                     |           | not null | 
 use_usa_date_format              | boolean                     |           | not null | 
 show_email_in_profile            | boolean                     |           | not null | 
 receive_notification_emails      | boolean                     |           | not null | 
 receive_newsletter_emails        | boolean                     |           | not null | 
 shares_data                      | boolean                     |           | not null | 
 default_identification_catalogue | uuid                        |           |          | 
 profile_fileupload_guid          | uuid                        |           |          | 
 static_roles                     | integer                     |           | not null | 
 indexed                          | timestamp without time zone |           | not null | CURRENT_TIMESTAMP
 linked_accounts                  | json                        |           |          | 
 twitter_username                 | character varying           |           |          | 
*/
            user.setUsername(res.getString("email"));
            user.setEmailAddress(res.getString("email"));
            user.setFullName(res.getString("full_name"));
            myShepherd.getPM().makePersistent(user);
            out.println("<b>created user " + user + "</b>");
            System.out.println("created user " + user);
        }
        out.println("</li>");
    }
    out.println("</ol>");
}


%>


<%


String context = ServletUtilities.getContext(request);
Shepherd myShepherd = new Shepherd(context);
myShepherd.beginDBTransaction();

Properties props = ShepherdProperties.getProperties("codexMigration.properties", "", context);
String dbUrl = props.getProperty("codexDbUrl", context);
String dbUsername = props.getProperty("codexDbUsername", context);
String dbPassword = props.getProperty("codexDbPassword", context);


Connection conn = DriverManager.getConnection(dbUrl, dbUsername, dbPassword);

migrateUsers(out, myShepherd, conn);

myShepherd.commitDBTransaction();
myShepherd.closeDBTransaction();

%>



