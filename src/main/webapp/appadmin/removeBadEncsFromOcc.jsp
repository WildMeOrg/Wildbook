<%@ page contentType="text/html; charset=utf-8" language="java"
  import="
    org.ecocean.servlet.ServletUtilities,
    org.ecocean.*,
    org.ecocean.media.*,
    org.ecocean.datacollection.*,
    javax.jdo.Extent,
    javax.jdo.Query,
    java.io.File,
    java.util.List,
    java.util.ArrayList,
    java.util.Properties,
    java.util.Enumeration,
    java.util.Arrays,
    java.lang.reflect.Method,
    java.lang.NullPointerException" 
%>

<html>
<head>
<title>P U R G E</title>
</head>

<body>
  <h1>Purify this asset!.</h1>

<%

  String context=ServletUtilities.getContext(request);
  //handle some cache-related security
  //response.setHeader("Cache-Control", "no-cache"); //Forces caches to obtain a new copy of the page from the origin server
  //response.setHeader("Cache-Control", "no-store"); //Directs caches not to store the page under any circumstance
  //response.setDateHeader("Expires", 0); //Causes the proxy cache to see the page as "stale"
  //response.setHeader("Pragma", "no-cache"); //HTTP 1.0 backward compatibility
  System.out.println("--------------------- MURDER ALL BAD ENCS ON OCCURRENCE ---------------------");
  String assetId = request.getParameter("id");
  String commitString = request.getParameter("commit");
  String exclude = request.getParameter("excludeEncounter");

  boolean commit = "true".equals(commitString);
  System.out.println("=========================> COMMITING IS: "+commit);
  System.out.println("=========================> ASSET ID: "+assetId);
   System.out.println("=========================> EXCLUDE ID: "+exclude);
  try (Shepherd myShepherd = new Shepherd(context)) {
      if (assetId!=null) {
        
        Occurrence occ = myShepherd.getOccurrence(assetId);

        ArrayList<Encounter> encs = occ.getEncounters();
        List<Encounter> killList = new ArrayList<>();
        int count = 0;
        for (Encounter enc : encs) {
            if (enc.getID().equals(exclude)) {
                System.out.println("Skipping Encounter: "+enc.getID());
                continue;
            }
            killList.add(enc);
            count++;
            System.out.println(count+") Added: "+enc.getID());
        }
        if (commit) {
            System.out.println("COMMITING!");
            for (Encounter enc : killList) {
                occ.removeEncounter(enc);
                myShepherd.throwAwayEncounter(enc);
                myShepherd.commitDBTransaction();
                myShepherd.beginDBTransaction();
            }
        }

        System.out.println("%n %n -< FIN >-");

      } else {
          System.out.println("No Occurrence Id was specified.");
      }

    myShepherd.closeDBTransaction();
    
  } catch (NullPointerException npe) {
    System.out.println("Could not retrieve Occurrence id="+assetId);
    npe.printStackTrace();
  } catch (Exception e) {
    System.out.println("General Exception!");
    e.printStackTrace();
  }

%>

<div class="container maincontent">

</div>

</body>

</html>
