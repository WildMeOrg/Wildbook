<%@ page contentType="text/plain; charset=utf-8" language="java"
     import="org.ecocean.*,
org.ecocean.servlet.ServletUtilities,
java.util.List,
java.util.ArrayList,
java.util.Map,
org.ecocean.identity.IBEISIA,
org.ecocean.media.*
              "
%><%

String context = ServletUtilities.getContext(request);
Shepherd myShepherd = new Shepherd(context);
myShepherd.setAction("iaSpeciesDiff.jsp");
myShepherd.beginDBTransaction();

/*
Map<String,String> ourMap = IBEISIA.acmIdSpeciesMap(myShepherd);
out.println("ourMap.size = " + ourMap.size());

List<String> iaList = IBEISIA.iaGetSpecies(new ArrayList<String>(ourMap.keySet()), context);
out.println("iaList.size = " + iaList.size());

//out.println(String.join(" ", iaList));

*/

Map<String,String> diff = IBEISIA.iaSpeciesDiff(myShepherd, true);
myShepherd.rollbackDBTransaction();
myShepherd.closeDBTransaction();

List<String> uuids = new ArrayList<String>();
List<String> species = new ArrayList<String>();
for (Map.Entry<String,String> d : diff.entrySet()) {
    String aid = d.getKey();
    String spec = d.getValue();
    uuids.add(aid);
    species.add(spec);
    out.println(aid + " => " + spec);
}

System.out.println("iaSpeciesDiff.jsp: uuids=" + uuids);
System.out.println("iaSpeciesDiff.jsp: species=" + species);
if (uuids.size() > 0) {
    IBEISIA.iaUpdateSpecies(uuids, species, context);
    out.println("update sent.");
} else {
    out.println("nothing to update.");
}

%>
