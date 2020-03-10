<%@ page contentType="text/html; charset=utf-8" language="java"
     import="org.ecocean.*,
java.util.List,
java.util.ArrayList,
org.ecocean.api.*,
org.json.JSONObject
              "
%>




<%

String id = "c2d35437-1981-4895-8161-4a3b48e5f132";



Shepherd myShepherd = new Shepherd("context0");
JSONObject params = new JSONObject();

//Organization org = new Organization("TEST");

TestObject tobj = ((TestObject) (myShepherd.getPM().getObjectById(myShepherd.getPM().newObjectIdInstance(TestObject.class, id), true)));

/*
TestObject tobj = new TestObject();
myShepherd.getPM().makePersistent(tobj);
*/

out.println("<p>" +  tobj.getId() + "</p><p>" + tobj.getVersion() + "</p>");

/*
List<Organization> orgs = new ArrayList<Organization>();
orgs.add(org);

tobj.setOrganizations(orgs);
myShepherd.getPM().makePersistent(tobj);
*/

out.println("<p>" + tobj.getOrganizations() + "</p>");



%>



