<%@ page contentType="text/html; charset=utf-8" language="java"
     import="org.ecocean.*,
java.util.List,
java.util.ArrayList,
java.util.Map,
java.util.HashMap,
org.ecocean.api.*,
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


out.println(tobj.toApiJSONObject(opts));

//out.println("<p>" +  tobj.getId() + "</p><p>" + tobj.getVersion() + "</p>");

/*
List<Organization> orgs = new ArrayList<Organization>();
orgs.add(org);

tobj.setOrganizations(orgs);
myShepherd.getPM().makePersistent(tobj);
*/

//out.println("<p>" + tobj.getOrganizations() + "</p>");



%>



