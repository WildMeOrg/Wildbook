<%@ page contentType="text/html; charset=utf-8" language="java"
     import="org.ecocean.*,
org.ecocean.servlet.ServletUtilities,
org.ecocean.media.*,
org.ecocean.identity.IBEISIA,
java.util.Map,
java.io.BufferedReader,
java.io.IOException,
java.io.InputStream,
java.io.InputStreamReader,
java.io.File,
java.util.Collection,
java.util.ArrayList,
org.json.JSONObject,
javax.jdo.Query,
javax.jdo.Extent,
java.util.HashMap,

org.ecocean.Annotation,
org.ecocean.media.*
              "
%>


<html><head>
<script src="https://ajax.googleapis.com/ajax/libs/jquery/2.2.2/jquery.min.js"></script>
<title>updateOccFromSmartXML</title>
</head><body>


<%

	String[] ids = new String[]{
"x2543ba0-0fd2-48db-9107-eb1946d64fec",
"xeb87dbe-2366-4f43-be0d-5082d82fc5ad"
};

Shepherd myShepherd=null;
myShepherd = new Shepherd("context0");
myShepherd.beginDBTransaction();

for (int i = 0 ; i < ids.length ; i++) {
	String setId = ids[i];
	Occurrence occ = myShepherd.getOccurrence(setId);
	if (occ == null) {
		out.println("<p><i>unknown occ id " + setId + "</i></p>");
		continue;
	}

	out.println("<p><b>" + occ + ":</b><br />");


        //lets grab stuff from the SmartXML file ... sure...
        int setIdInt = IBEISIA.iaImageSetIdFromUUID(setId);
        String smartXml = IBEISIA.iaSmartXmlFromSetId(setIdInt);
        int smartWaypointId = IBEISIA.iaSmartXmlWaypointIdFromSetId(setIdInt);
        HashMap smartMap = null;
        if ((smartXml == null) || (smartWaypointId < 0)) {
            out.println("WARNING: could not discover smartXml or smartXmlWaypointId; no xml metadata!</p>");
						continue;
        } else {
            smartMap = IBEISIA.parseSmartXml(Integer.toString(smartWaypointId), smartXml);
        }
        Double metaLatitude = IBEISIA.metaXmlDouble(smartMap, "decimalLatitude", null);
        Double metaLongitude = IBEISIA.metaXmlDouble(smartMap, "decimalLongitude", null);
				out.println(smartMap);


            occ.setDecimalLatitude(metaLatitude);
            occ.setDecimalLongitude(metaLongitude);
            occ.setHabitat(IBEISIA.metaXmlString(smartMap, "habitat", null));
            occ.setGroupSize(IBEISIA.metaXmlInteger(smartMap, "groupsize", 0));
            occ.setNumBachMales(IBEISIA.metaXmlInteger(smartMap, "noofbm", 0));
            occ.setNumTerMales(IBEISIA.metaXmlInteger(smartMap, "nooftm", 0));
            occ.setNumLactFemales(IBEISIA.metaXmlInteger(smartMap, "nooflf", 0));
            occ.setNumNonLactFemales(IBEISIA.metaXmlInteger(smartMap, "noofnlf", 0));
            occ.setDistance(IBEISIA.metaXmlDouble(smartMap, "distancem", null));
            occ.setBearing(IBEISIA.metaXmlDouble(smartMap, "bearing", null));
            //myShepherd.getPM().makePersistent(occ);


	out.println("</p>");
}

myShepherd.commitDBTransaction();
myShepherd.closeDBTransaction();



%>



<p>
done.
</body></html>
