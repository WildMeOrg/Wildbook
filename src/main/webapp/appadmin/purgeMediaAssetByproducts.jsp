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
  System.out.println("--------------------- TRYING TO MURDER ALL FEATURES AND ANNOTATIONS FOR THIS MEDIA ASSET ---------------------");
  String assetId = request.getParameter("id");
  String commitString = request.getParameter("commit");

  boolean commit = "true".equals(commitString);
  System.out.println("=========================> COMMITING IS: "+commit);
  System.out.println("=========================> ASSET ID: "+assetId);
  try (Shepherd myShepherd = new Shepherd(context)) {
      if (assetId!=null) {
        
        MediaAsset ma = myShepherd.getMediaAsset(assetId);
        List<Feature> fts = ma.getFeatures();
        for (Feature ft : fts) {
            System.out.println("Feature to remove: id="+ft.getId());
            if (commit&&ft!=null) {
                Annotation ann = ft.getAnnotation();
                Encounter enc = Encounter.findByAnnotation(ann,myShepherd);
                enc.removeAnnotation(ann);
                ma.removeFeature(ft);
                if (ann!=null) {
                    myShepherd.throwAwayAnnotation(ann);
                } else {System.out.println("Annotation was null for this feature!");}
                myShepherd.throwAwayFeature(ft);
            }
            myShepherd.commitDBTransaction();
            myShepherd.beginDBTransaction();
        }

        if (commit) {
            ma.setDetectionStatus(null);
            System.out.println("Set Detection status to null!");
            ma.setAcmId(null);
            System.out.println("Set acmId to null!");
            myShepherd.commitDBTransaction();
        }

        System.out.println("%n %n -< FIN >-");

      } else {
          System.out.println("No MediaAsset Id was specified.");
      }

    myShepherd.closeDBTransaction();
    
  } catch (NullPointerException npe) {
    System.out.println("Could not retrieve Media Asset id="+assetId);
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
