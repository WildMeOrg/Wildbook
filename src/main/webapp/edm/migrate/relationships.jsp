<%@ page contentType="text/html; charset=utf-8" language="java"
     import="org.ecocean.*,
java.util.Collection,
java.util.List,
java.util.ArrayList,
java.util.Map,
java.util.HashMap,
java.util.Set,
java.util.HashSet,
javax.jdo.*,
java.util.Arrays,
org.json.JSONObject,
org.ecocean.MigrationUtil,
java.lang.reflect.*,
java.time.ZonedDateTime,
java.time.ZoneOffset,
java.io.BufferedReader,
java.io.FileReader,
java.io.File,

org.ecocean.api.ApiCustomFields,
org.ecocean.customfield.*,
org.ecocean.social.Relationship,

org.ecocean.media.*
              "
%><%!

public Map<String,Set<String>> typeRoleMap = new HashMap<String, Set<String>>();

private String filePreview(String name) throws java.io.IOException {
    File path = new File(MigrationUtil.getDir(), name);
    BufferedReader br = new BufferedReader(new FileReader(path));
    String content = "";
    String line;
    while ( ((line = br.readLine()) != null) && (content.length() < 3500) ) {
        content += line + "\n";
    }
    String rtn = content;
    if (rtn.length() > 3000) rtn = rtn.substring(0, 3000) + "\n\n   [... preview truncated ...]";
    return "<div>This file located at: <i class=\"code\">" + path.toString() + "</i><br /><textarea class=\"preview\">" + rtn + "</textarea></div>";
}

/*
-[ RECORD 1 ]--------------------------+-------------------------------------
RELATIONSHIP_ID                        | 19
BIDIRECTIONAL                          | 
ENDTIME                                | -1
MARKEDINDIVIDUAL1DIRECTIONALDESCRIPTOR | 
MARKEDINDIVIDUAL2DIRECTIONALDESCRIPTOR | 
MARKEDINDIVIDUALNAME1                  | bb9c2da0-6ef0-498a-8680-c3e8bd2afb7d
MARKEDINDIVIDUALNAME2                  | d015536e-6582-43cb-b84d-c696d1bdf745
MARKEDINDIVIDUALROLE1                  | member
MARKEDINDIVIDUALROLE2                  | member
RELATEDCOMMENTS                        | 
RELATEDSOCIALUNITNAME                  | Harem__Vaana
STARTTIME                              | 1293840000000
TYPE                                   | social grouping
INDIVIDUAL1_ID_OID                     | bb9c2da0-6ef0-498a-8680-c3e8bd2afb7d
INDIVIDUAL2_ID_OID                     | d015536e-6582-43cb-b84d-c696d1bdf745
-[ RECORD 2 ]--------------------------+-------------------------------------
RELATIONSHIP_ID                        | 1
BIDIRECTIONAL                          | 
ENDTIME                                | -1
MARKEDINDIVIDUAL1DIRECTIONALDESCRIPTOR | 
MARKEDINDIVIDUAL2DIRECTIONALDESCRIPTOR | 
MARKEDINDIVIDUALNAME1                  | e7e919bf-d240-4bb5-8207-1a0576fe8768
MARKEDINDIVIDUALNAME2                  | 3229eaeb-9400-47de-9bf8-877133f3e931
MARKEDINDIVIDUALROLE1                  | mother
MARKEDINDIVIDUALROLE2                  | infant
RELATEDCOMMENTS                        | 
RELATEDSOCIALUNITNAME                  | 
STARTTIME                              | -1
TYPE                                   | social grouping
INDIVIDUAL1_ID_OID                     | e7e919bf-d240-4bb5-8207-1a0576fe8768
INDIVIDUAL2_ID_OID                     | 3229eaeb-9400-47de-9bf8-877133f3e931
-[ RECORD 3 ]--------------------------+-------------------------------------
RELATIONSHIP_ID                        | 11
BIDIRECTIONAL                          | 
ENDTIME                                | -1
MARKEDINDIVIDUAL1DIRECTIONALDESCRIPTOR | 
MARKEDINDIVIDUAL2DIRECTIONALDESCRIPTOR | 
MARKEDINDIVIDUALNAME1                  | 8c19b877-dfde-4bce-95d3-681a0a03a531
MARKEDINDIVIDUALNAME2                  | 99fbf9ed-95fa-455e-847c-3a369e7f3593
MARKEDINDIVIDUALROLE1                  | mare
MARKEDINDIVIDUALROLE2                  | stallion
RELATEDCOMMENTS                        | 
RELATEDSOCIALUNITNAME                  | Harem__Knights_of_Ren
STARTTIME                              | 1582848000000
TYPE                                   | familial
INDIVIDUAL1_ID_OID                     | 8c19b877-dfde-4bce-95d3-681a0a03a531
INDIVIDUAL2_ID_OID                     | 99fbf9ed-95fa-455e-847c-3a369e7f3593
-[ RECORD 4 ]--------------------------+-------------------------------------


                        Table "public.relationship"
   Column   |            Type             | Collation | Nullable | Default 
------------+-----------------------------+-----------+----------+---------
 created    | timestamp without time zone |           | not null | 
 updated    | timestamp without time zone |           | not null | 
 guid       | uuid                        |           | not null | 
 start_date | timestamp without time zone |           |          | 
 end_date   | timestamp without time zone |           |          | 
 type       | character varying           |           |          | 

                  Table "public.relationship_individual_member"
      Column       |            Type             | Collation | Nullable | Default 
-------------------+-----------------------------+-----------+----------+---------
 created           | timestamp without time zone |           | not null | 
 updated           | timestamp without time zone |           | not null | 
 viewed            | timestamp without time zone |           | not null | 
 guid              | uuid                        |           | not null | 
 relationship_guid | uuid                        |           |          | 
 individual_guid   | uuid                        |           |          | 
 individual_role   | character varying           |           | not null | 

*/

private String relationshipSql(Relationship rel) {
    if (rel == null) return "";
    MarkedIndividual indiv1 = rel.getMarkedIndividual1();
    MarkedIndividual indiv2 = rel.getMarkedIndividual2();
    if ((indiv1 == null) || (indiv2 == null)) return "-- unable to get individual(s) on " + rel.toString() + "\n\n";

    String sqlIns = "INSERT INTO relationship (created, updated, viewed, guid, start_date, end_date, type) VALUES (now(), now(), now(), ?, ?, ?, ?);";
    String memSqlIns = "INSERT INTO relationship_individual_member (created, updated, viewed, guid, relationship_guid, individual_guid, individual_role) VALUES (now(), now(), now(), ?, ?, ?, ?);";

    String guid = Util.generateUUID();
    sqlIns = MigrationUtil.sqlSub(sqlIns, guid);
    Long st = rel.getStartTime();
    Long et = rel.getEndTime();
    if (st < 1L) st = null;
    if (et < 1L) et = null;
    sqlIns = MigrationUtil.sqlSub(sqlIns, Util.millisToIso8601StringNoTimezone(st));
    sqlIns = MigrationUtil.sqlSub(sqlIns, Util.millisToIso8601StringNoTimezone(et));
    sqlIns = MigrationUtil.sqlSub(sqlIns, rel.getType());

    if (!typeRoleMap.containsKey(rel.getType())) typeRoleMap.put(rel.getType(), new HashSet<String>());

    String m1 = memSqlIns;
    m1 = MigrationUtil.sqlSub(m1, Util.generateUUID());
    m1 = MigrationUtil.sqlSub(m1, guid);
    m1 = MigrationUtil.sqlSub(m1, indiv1.getId());
    String r1 = rel.getMarkedIndividualRole1();
    if (r1 == null) r1 = "member";
    m1 = MigrationUtil.sqlSub(m1, r1);
    typeRoleMap.get(rel.getType()).add(r1);

    String m2 = memSqlIns;
    m2 = MigrationUtil.sqlSub(m2, Util.generateUUID());
    m2 = MigrationUtil.sqlSub(m2, guid);
    m2 = MigrationUtil.sqlSub(m2, indiv2.getId());
    String r2 = rel.getMarkedIndividualRole2();
    if (r2 == null) r2 = "member";
    m2 = MigrationUtil.sqlSub(m2, r2);
    typeRoleMap.get(rel.getType()).add(r2);

    return sqlIns + "\n" + m1 + "\n" + m2 + "\n\n";
}


private String siteSettingSql(String key, JSONObject data) {
    if ((data == null) || (key == null)) return "";
    String sqlIns = "INSERT INTO site_setting (created, updated, key, public, data) VALUES (now(), now(), ?, 't', ?);";
    sqlIns = MigrationUtil.sqlSub(sqlIns, key);
    sqlIns = MigrationUtil.sqlSub(sqlIns, data.toString());
    return "DELETE FROM site_setting WHERE key='" + key + "';\n" + sqlIns;
}

%><html>
<head>
    <title>Codex Relationship Migration Helper</title>
    <link rel="stylesheet" href="m.css" />
</head>
<body>
<p>
This will help migrate to Codex ComplexDateTime values.
</p>




<%

MigrationUtil.checkDir();

Shepherd myShepherd = new Shepherd("context0");
myShepherd.beginDBTransaction();
Query q = myShepherd.getPM().newQuery(Relationship.class);
Collection c = (Collection)q.execute();
List<Relationship> all = new ArrayList<Relationship>(c);
q.closeAll();
myShepherd.rollbackDBTransaction();

if (all.size() < 1) {
    out.println("<h1>no Relationships to migrate</h1>");
    return;
}

String fname = "houston_09_indiv_relationships.sql";
MigrationUtil.writeFile(fname, "");

String content = "BEGIN;\n";
for (Relationship rel : all) {
    content += relationshipSql(rel);
}
content += "\n\nEND;\n";
MigrationUtil.appendFile(fname, content);

JSONObject rtrJson = new JSONObject(typeRoleMap);
content = "BEGIN;\n\n" + siteSettingSql("relationship_type_roles", rtrJson) + "\n\nEND;\n";
MigrationUtil.appendFile(fname, content);
out.println(filePreview(fname));


System.out.println("migration/relationships.jsp DONE");
%>


<p>done.</p>
</body></html>
