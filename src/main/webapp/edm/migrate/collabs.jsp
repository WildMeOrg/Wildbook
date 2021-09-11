<%@ page contentType="text/html; charset=utf-8" language="java"
     import="org.ecocean.*,
java.util.Collection,
java.util.List,
java.util.Iterator,
java.nio.file.Path,
java.util.ArrayList,
java.io.File,
javax.jdo.*,
java.util.Arrays,
java.util.Map,
java.util.HashMap,
java.util.Set,
java.util.UUID,
java.util.HashSet,
org.json.JSONObject,
org.json.JSONArray,
java.sql.Connection,
java.sql.PreparedStatement,
java.sql.DriverManager,
java.lang.reflect.*,
org.ecocean.MigrationUtil,
org.ecocean.Util.MeasurementDesc,
org.ecocean.security.Collaboration,
org.ecocean.api.ApiCustomFields,
org.ecocean.customfield.*,
org.ecocean.servlet.ServletUtilities,

org.ecocean.media.*
              "
%><%!

private String fileWriteAndPreview(String name, String content) throws java.io.IOException {
    String fname = "collaborations_" + name;
    File loc = MigrationUtil.writeFile(fname, content);
    String rtn = content;
    if (rtn.length() > 3000) rtn = rtn.substring(0, 3000) + "\n\n   [... preview truncated ...]";
    return "<div>This file located at: <i class=\"code\">" + loc.toString() + "</i><br /><textarea class=\"preview\">" + rtn + "</textarea></div>";
}




%><html>
<head>
    <title>Codex Collaboration Migration</title>
    <link rel="stylesheet" href="m.css" />
</head>
<body>
<p>
<h1>This will help migrate <b>User Collaborations</b> to Codex.</h1>
</p>
<hr />

<p>
Words.
</p>

<%
MigrationUtil.setDir(request.getParameter("migrationDir"));
String checkDir = MigrationUtil.checkDir();
%>
<p>
migrationDir: <b><%=checkDir%></b>
</p>
<%


/*
{
    "created": "2021-09-10T21:39:24.679756+00:00",
    "updated": "2021-09-10T21:39:24.695355+00:00",
    "guid": "abcff2a6-18c0-4267-911b-d9b5608525de",
    "members": {
        "0b5ae7a3-f140-4e69-8ae2-b421c22d030c": {
            "email": "jon@wildme.org",
            "guid": "0b5ae7a3-f140-4e69-8ae2-b421c22d030c",
            "full_name": "jon testing",
            "viewState": "approved",
            "editState": "approved",
            "initiator": true
        },
        "e849c875-fa04-4eef-afb8-b91f77235f45": {
            "email": "mark@wildme.org",
            "guid": "e849c875-fa04-4eef-afb8-b91f77235f45",
            "full_name": "Mark",
            "viewState": "pending",
            "editState": "pending",
            "initiator": false
        }
    }
}


houston=# select * from collaboration;
          created           |          updated           |           viewed           |                 guid                 
----------------------------+----------------------------+----------------------------+--------------------------------------
 2021-09-10 21:39:24.679756 | 2021-09-10 21:39:24.695355 | 2021-09-10 21:39:24.679782 | abcff2a6-18c0-4267-911b-d9b5608525de
(1 row)

houston=# select * from collaboration_user_associations 
houston-# ;
          created           |          updated           |           viewed           |          collaboration_guid          |              user_guid 
              | initiator | read_approval_state | edit_approval_state 
----------------------------+----------------------------+----------------------------+--------------------------------------+------------------------
--------------+-----------+---------------------+---------------------
 2021-09-10 21:39:24.682074 | 2021-09-10 21:39:24.682088 | 2021-09-10 21:39:24.682093 | abcff2a6-18c0-4267-911b-d9b5608525de | 0b5ae7a3-f140-4e69-8ae2
-b421c22d030c | t         | approved            | approved
 2021-09-10 21:39:24.694019 | 2021-09-10 21:39:24.694034 | 2021-09-10 21:39:24.694039 | abcff2a6-18c0-4267-911b-d9b5608525de | e849c875-fa04-4eef-afb8
-b91f77235f45 | f         | pending             | pending

        'declined',
        'approved',
        'pending',
        'not_initiated',
        'revoked',
        'creator',

*/

String context = "context0";
Shepherd myShepherd = new Shepherd(context);
myShepherd.beginDBTransaction();

List collabs = myShepherd.getAllCollaborations();
if (Util.collectionIsEmptyOrNull(collabs)) {
    myShepherd.rollbackDBTransaction();
    out.println("<p>No collaborations to migrate.</p>");
    return;
}

String content = "BEGIN;\n\n";
for (Object c : collabs) {
    Collaboration collab = (Collaboration)c;
    User u1 = myShepherd.getUser(collab.getUsername1());
    User u2 = myShepherd.getUser(collab.getUsername2());
    if ((u1 == null) || (u2 == null)) {
        content += "-- failed to find one/both users for " + collab.toString() + "\n";
        continue;
    }
    String id = Util.generateUUID();
    String sqlIns = "\nINSERT INTO collaboration (created, updated, viewed, guid) VALUES (?, ?, ?, ?);\n";
    sqlIns = MigrationUtil.sqlSub(sqlIns, Util.millisToIso8601StringNoTimezone(collab.getDateTimeCreated()));
    sqlIns = MigrationUtil.sqlSub(sqlIns, Util.millisToIso8601StringNoTimezone(System.currentTimeMillis()));
    sqlIns = MigrationUtil.sqlSub(sqlIns, Util.millisToIso8601StringNoTimezone(System.currentTimeMillis()));
    sqlIns = MigrationUtil.sqlSub(sqlIns, id);
    content += sqlIns;

    sqlIns = "\nINSERT INTO collaboration_user_associations (created, updated, viewed, collaboration_guid, user_guid, initiator, read_approval_state, edit_approval_state) VALUES (?, ?, ?, ?, ?, ?, ?, ?);\n";
    sqlIns = MigrationUtil.sqlSub(sqlIns, Util.millisToIso8601StringNoTimezone(collab.getDateTimeCreated()));
    sqlIns = MigrationUtil.sqlSub(sqlIns, Util.millisToIso8601StringNoTimezone(System.currentTimeMillis()));
    sqlIns = MigrationUtil.sqlSub(sqlIns, Util.millisToIso8601StringNoTimezone(System.currentTimeMillis()));
    sqlIns = MigrationUtil.sqlSub(sqlIns, id);

    String sql1 = sqlIns;
    String sql2 = sqlIns;
    //Collaboration.java says this, so i am going with it:  "NOTE the first user, by convention, is the initiator"
    sql1 = MigrationUtil.sqlSub(sql1, true);
    sql2 = MigrationUtil.sqlSub(sql2, false);

    //initiator is always approved   TODO does this _always_ include edit as well?
    sql1 = MigrationUtil.sqlSub(sql1, "approved");  //read
    sql1 = MigrationUtil.sqlSub(sql1, "approved");  //edit

    if (Collaboration.STATE_INITIALIZED.equals(collab.getState())) {
        sql2 = MigrationUtil.sqlSub(sql2, "pending");
        sql2 = MigrationUtil.sqlSub(sql2, "pending");
    } else if (Collaboration.STATE_REJECTED.equals(collab.getState())) {
        sql2 = MigrationUtil.sqlSub(sql2, "declined");
        sql2 = MigrationUtil.sqlSub(sql2, "declined");
    } else if (Collaboration.STATE_APPROVED.equals(collab.getState())) {  //"approved" means read-approved
        sql2 = MigrationUtil.sqlSub(sql2, "approved");
        sql2 = MigrationUtil.sqlSub(sql2, "pending");
    } else if (Collaboration.STATE_EDIT_PRIV.equals(collab.getState())) {  //"edit" means edit-(and-read)-approved
        sql2 = MigrationUtil.sqlSub(sql2, "approved");
        sql2 = MigrationUtil.sqlSub(sql2, "approved");
    } else {
        sql1 = "-- skipping: unknown STATE for " + collab.toString() + "\n";
        sql2 = "";
    }
    content += sql1;
    content += sql2;
}

content += "\n\nEND;\n";

out.println(fileWriteAndPreview("create.sql", content));
System.out.println("migration/collabs.jsp finished sql");


myShepherd.rollbackDBTransaction();

%>


</body></html>
