<%@ page contentType="text/html; charset=utf-8" language="java"
     import="org.ecocean.*,
java.util.Collection,
java.io.IOException,
java.util.ArrayList,
java.util.Arrays,
javax.jdo.Query,
java.util.List,
java.util.Iterator,
java.util.Map,
java.util.HashMap,
java.lang.reflect.Method,
java.lang.reflect.Field,
org.json.JSONArray,
org.json.JSONObject,

org.ecocean.servlet.ServletUtilities,
org.ecocean.media.*,
org.ecocean.servlet.ServletUtilities,
org.ecocean.identity.*
              "
%>


<%

Shepherd myShepherd=null;
String context=ServletUtilities.getContext(request);
myShepherd = new Shepherd(context);
myShepherd.beginDBTransaction();


String output = "";

try {
    //String alg = IA.getProperty(context, "labelerAlgo");
    //String tag = IA.getProperty(context, "modelTag");
    //if (alg==null||tag==null) throw new IOException("Cant get viewpoints.. alg = "+alg+" tag = "+tag);
    //JSONObject ob = new JSONObject();
    //ob.put("algo", algo);
    //ob.put("model_tag", tag);
    String uuidList = "";
    Iterator<Annotation> annIt = myShepherd.getAllAnnotationsNoQuery();
    List<Annotation> anns = new ArrayList();
    while (annIt.hasNext()) {
        Annotation ann = annIt.next();
        if (ann.getAcmId()!=null&&ann.getAcmId()!="") {
            anns.add(ann);
            uuidList += IBEISIA.toFancyUUID(ann.getAcmId()).toString();
            //uuidList += ann.getAcmId().toString();
            System.out.println("Ann AcmId: "+ann.getAcmId());
            if (annIt.hasNext()) {
                uuidList+= ",";
            }
        } 
    }

    System.out.println("Num anns we got? --> "+anns.size());

    uuidList = "[" + uuidList + "]";
    JSONObject aidRtn = RestClient.get(IBEISIA.iaURL(context, "/api/annot/rowid/uuid/?uuid_list="+uuidList));
    JSONArray aidArr = aidRtn.optJSONArray("response");
    //System.out.println("List: "+list);
    JSONObject rtn = RestClient.get(IBEISIA.iaURL(context, "/api/annot/viewpoint/?aid_list="+aidArr.toString()));
    if (rtn==null|| rtn.optJSONArray("response") == null) {
        throw new RuntimeException("Invalid response from IA when retrieving viewpoints!");
    } 
    JSONArray vpArr = rtn.optJSONArray("response");

    System.out.println("VPARR LENGTH: "+vpArr.length());

    if (vpArr.length()==anns.size()) {
        System.out.println(">>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>");
        for (int i=0; i<vpArr.length();i++ ) {
            String vpString = (String) vpArr.get(i);
            Annotation ann = anns.get(i);
            System.out.println("["+i+"]ANN ID: "+ann.getId()+"WB VP: "+ann.getViewpoint()+" IA NEW VP: "+vpString);
            
            if (vpString!=ann.getViewpoint()) {
                ann.setViewpoint(vpString);
                System.out.println("Changed!");
                myShepherd.commitDBTransaction();
                myShepherd.beginDBTransaction();
            }
            
        }
        System.out.println(">>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>");
    } else {
        output += "[CRITICAL]: Viewpoint list is not the same length as annotation list!";
    }
    output += rtn.toString();

} catch (Exception e) {
    e.printStackTrace();
    myShepherd.rollbackDBTransaction();
}
myShepherd.closeDBTransaction();

%>


<p> <%=output%> </p>

