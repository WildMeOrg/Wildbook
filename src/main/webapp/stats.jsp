<%@ page contentType="text/plain; charset=utf-8" language="java"
     import="org.ecocean.*,
java.util.List,
java.util.Map,
org.json.JSONObject,
org.json.JSONArray
              "
%>

            <%
String context = "context0";
Shepherd myShepherd = new Shepherd(context);

JSONObject stats = new JSONObject();
stats.put("created", System.currentTimeMillis());

myShepherd.beginDBTransaction();
List<User> users = myShepherd.getAllUsers();
JSONArray jarr = new JSONArray();
for (User u : users) {
    JSONObject ju = new JSONObject();
    ju.put("fullName", u.getFullName());
    ju.put("affiliation", u.getAffiliation());
    ju.put("userStatement", u.getUserStatement());
    ju.put("uuid", u.getUUID());
    ju.put("username", u.getUsername());
    jarr.put(ju);
}
stats.put("users", jarr);

List<Encounter> recent = myShepherd.getMostRecentIdentifiedEncountersByDate(20);
jarr = new JSONArray();
for (Encounter enc : recent) {
    JSONObject je = new JSONObject();
    je.put("id", enc.getCatalogNumber());
    je.put("locationID", enc.getLocationID());
    je.put("date", enc.getDateInMilliseconds());
    je.put("thumbUrl", enc.getThumbnailUrl(context));
    je.put("individualID", enc.getIndividualID());
    je.put("taxonomy", enc.getTaxonomyString());
    jarr.put(je);
}
stats.put("recentEncounters", jarr);

long startTime = System.currentTimeMillis() - Long.valueOf(1000L*60L*60L*24L*30L);
stats.put("topSpottersStartTime", startTime);
Map<String,Integer> spotters = myShepherd.getTopUsersSubmittingEncountersSinceTimeInDescendingOrder(startTime);
jarr = new JSONArray();
for (String username : spotters.keySet()) {
    JSONObject s = new JSONObject();
    s.put("username", username);
    s.put("numEncs", spotters.get(username));
    jarr.put(s);
}
stats.put("topSpotters", jarr);

myShepherd.rollbackDBTransaction();

out.println(stats.toString());

%>
