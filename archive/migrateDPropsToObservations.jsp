<%@ page contentType="text/html; charset=utf-8" language="java"
     import="org.ecocean.*,
org.ecocean.Observation,     
java.util.ArrayList,
java.io.BufferedReader,
java.io.IOException,
java.io.InputStream,
java.io.InputStreamReader,
java.io.File,
org.json.JSONObject,
org.ecocean.servlet.ServletUtilities,

javax.jdo.Query,
javax.jdo.Extent,
java.util.*,


org.ecocean.media.*
              "
%>

<h3>Migrating Dynamic Properties to Observations...</h3>
<br>



<%
boolean commit = Boolean.valueOf(request.getParameter("commit"));

String context = ServletUtilities.getContext(request);
Shepherd myShepherd = new Shepherd(context);

    if (commit) {
        out.println("<p><strong>Commit is TRUE! Creating Observations.</strong></p><br>");
    } else {
        out.println("<p><strong>Commit is FALSE! Testing Observation Migration.</strong></p><br>");
    }

    int encNum = 0;
    int successful = 0;
    int failed = 0;
    int count = 0;
    ArrayList<String> propNames = new ArrayList<String>(); 
    Iterator<Encounter> encs = myShepherd.getAllEncountersNoQuery();
    out.println("<p><strong>Processing Encounters...</strong></p><br>");
    String sampleEncs  = "<p>Encs for verification: </p><br>";
    while (encs.hasNext()) {
        encNum++;
        Encounter enc = encs.next();
        String dPropsString = enc.getDynamicProperties();          
        if (dPropsString!=null&&dPropsString.contains("=")) {
            String[] dProps = dPropsString.split(";");
            if (count<4) {
                out.println("Test: "+dPropsString);
                sampleEncs += "<p><a href=\"//"+CommonConfiguration.getURLLocation(request)+"/encounters/encounter.jsp?number="+enc.getCatalogNumber()+"\">"+enc.getCatalogNumber()+"</a></p><br>";
                count++;
            }
            for (String prop : dProps) {
                String[] propParts = prop.split("=");
                String key = propParts[0];
                String value = propParts[1];
                if (!propNames.contains(key)) {
                    propNames.add(key);
                }
                if (key!=null&&key.length()>0&&value!=null&&value.length()>0) {
                    try {
                        successful++;
                        if (commit) {
                            Observation newOb = new Observation(key,value,enc,enc.getCatalogNumber()); 
                            myShepherd.beginDBTransaction();
                            myShepherd.getPM().makePersistent(newOb);
                            myShepherd.commitDBTransaction();
                            enc.addObservation(newOb);
                        }
                        //System.out.println("OBSERVATION -  Enc: "+enc.getCatalogNumber()+" Key: "+key+" Value: "+value);
                    } catch (Exception e) {
                        failed++;
                        e.printStackTrace();
                    }
                }
            }
            myShepherd.closeDBTransaction(); 
        }
    }
    out.println("<p><strong>Successful: "+successful+"</strong></p><br>");
    out.println("<p><strong>Failed: "+failed+"</strong></p><br>");
    out.println("<p><strong>Keys Converted: "+String.valueOf(propNames)+"</strong></p><br>");
    out.println("<p><strong>Sample Encs:</strong></p><br>");
    out.println(sampleEncs);











%>
