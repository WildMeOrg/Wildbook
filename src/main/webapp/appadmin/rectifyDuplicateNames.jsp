<!DOCTYPE html PUBLIC "-//W3C//DTD XHTML 1.0 Transitional//EN"
        "http://www.w3.org/TR/xhtml1/DTD/xhtml1-transitional.dtd">
<%@ page contentType="text/html; charset=utf-8" language="java" import="org.joda.time.LocalDateTime,
org.joda.time.format.DateTimeFormatter,
org.joda.time.format.ISODateTimeFormat,java.net.*,
org.ecocean.grid.*,org.ecocean.media.*,
org.ecocean.identity.*,
org.json.JSONObject,
java.io.*,java.util.*, java.io.FileInputStream, java.io.File, java.io.FileNotFoundException, org.ecocean.*,org.ecocean.servlet.*,javax.jdo.*, java.lang.StringBuffer, java.util.Vector, java.util.Iterator, java.lang.NumberFormatException"%>
<%
String context="context0";
context=ServletUtilities.getContext(request);
Shepherd myShepherd=new Shepherd(context);
String commit = request.getParameter("commit");
%>
<html>
<head>
<title>WTF??</title>
</head>

<body>
  <h1>Explore anns with duplicate names.</h1>
<ul>

<%

//@register_api(‘/api/annot/species/json/’, methods=[‘PUT’], __api_plural_check__=False)
//def set_annot_species_json(ibs, annot_uuid_list, species_text_list, **kwargs):
//   aid_list = ibs.get_annot_aids_from_uuid(annot_uuid_list)
//   return ibs.set_annot_species(aid_list, species_text_list)


try {

    // Should be in form ACMID ---> ANNOTATION ID's
    HashMap<String,ArrayList<Annotation>> dupMap = new HashMap<String, ArrayList<Annotation>>();

    //Any ACM id's that we get. Check for duplicates.
    //ArrayList<String> potentials = new ArrayList<String>();
    ArrayList<String> keys = new ArrayList<String>();


    myShepherd.beginDBTransaction();

    Iterator<Annotation> annsIt = myShepherd.getAllAnnotationsNoQuery();
    //ArrayList<String> acmIds = new ArrayList<>();
    int count = 0;
    while (annsIt.hasNext()) {
        Annotation ann = annsIt.next();
        String thisACMId = ann.getAcmId();
        //acmIds.add(thisACMId);
        ArrayList<Annotation> val = new ArrayList<Annotation>();
        if (dupMap.get(thisACMId)!=null) {
            val = dupMap.get(thisACMId);
        } else {
            keys.add(thisACMId);
        } 
        val.add(ann;
        dupMap.put(thisACMId, val);
    }

    for (String key : keys) {
        if (dupMap.get(key)!=null&&dupMap.get(key).size()>1) {
            count++;
            System.out.println("-----------------------------------------------------------------------------------------------------------------------");
            System.out.println("FOUND A DUPLICATE! ===================> ACMID = "+key);
            System.out.println("Annotations that share this ID:");
            for (Annotation ann : dupMap.get(key)) {
                System.out.println(ann.getId()+"  Name: "+ann.findIndividualId(myShepherd)+"  iaClass: "+ann.getIAClass());
            }
        }
    }



    System.out.println("Total Dups: "+count);
} catch (Exception e) {
	System.out.println("!!! ----------- > An error occurred resending ann and media assets: ");
	e.printStackTrace();
	myShepherd.rollbackDBTransaction();

} finally {

	myShepherd.closeDBTransaction();
	myShepherd=null;
}

%>

</ul>
</body>
</html>
