<%@ page contentType="text/html; charset=utf-8" language="java"
     import="org.ecocean.*,
java.util.List,
java.util.ArrayList,
java.util.Map,
java.util.HashMap,
org.ecocean.api.*,
org.ecocean.customfield.*,
org.json.JSONObject
              "
%>




<%

String id = "c2d35437-1981-4895-8161-4a3b48e5f132";

Map<String,Object> opts = new HashMap<String,Object>();
opts.put("debug", new Boolean(true));


Shepherd myShepherd = new Shepherd("context0");

//Organization org = new Organization("TEST");

TestObject tobj = ((TestObject) (myShepherd.getPM().getObjectById(myShepherd.getPM().newObjectIdInstance(TestObject.class, id), true)));


////////////out.println(tobj.toApiJSONObject(opts));


//out.println("<p>" +  tobj.getId() + "</p><p>" + tobj.getVersion() + "</p>");

/*
List<Organization> orgs = new ArrayList<Organization>();
orgs.add(org);

tobj.setOrganizations(orgs);
myShepherd.getPM().makePersistent(tobj);
*/

//out.println("<p>" + tobj.getOrganizations() + "</p>");

//CustomField stuff....
String cfdId1 = "4e57029b-59c2-42f8-9d46-1eb3dcd2425d";  // double (single)
CustomFieldDefinition cfd1 = ((CustomFieldDefinition) (myShepherd.getPM().getObjectById(myShepherd.getPM().newObjectIdInstance(CustomFieldDefinition.class, cfdId1), true)));
String cfdId2 = "84864a83-e998-410d-9610-2cbb0585c324";  // string (multiple)
CustomFieldDefinition cfd2 = ((CustomFieldDefinition) (myShepherd.getPM().getObjectById(myShepherd.getPM().newObjectIdInstance(CustomFieldDefinition.class, cfdId2), true)));

///// either of these will work the same
//out.println( tobj.getCustomFieldValues(cfd1) );
//out.println( tobj.getCustomFieldValues(cfdId1) );

//CustomFieldValue val = new CustomFieldValueDouble(cfd1, 7d + Math.random());
CustomFieldValue val = new CustomFieldValueString(cfd2, "yet another: " + Util.generateUUID().substring(0,4));
tobj.addCustomFieldValue(val);

//tobj.resetCustomFieldValues(cfd1);
out.println(tobj.getCustomFieldValuesMap());


%>



