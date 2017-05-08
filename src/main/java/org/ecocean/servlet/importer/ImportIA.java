package org.ecocean.servlet.importer;

import org.ecocean.*;
import org.ecocean.servlet.*;
import org.ecocean.identity.*;
import org.ecocean.media.*;

import javax.servlet.ServletConfig;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.security.NoSuchAlgorithmException;
import java.security.InvalidKeyException;

import java.net.URL;
import java.net.MalformedURLException;
import java.util.List;
import java.util.ArrayList;
import java.util.Set;
import java.util.HashSet;
import java.util.HashMap;


import org.json.JSONObject;
import org.json.JSONArray;

import java.io.*;

public class ImportIA extends HttpServlet {

  public void init(ServletConfig config) throws ServletException {
    super.init(config);
  }

  public void doGet(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
    doPost(request, response);
  }


  public void doPost(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {

    boolean firstTime = false;
    if (request.getParameter("doInit") != null) firstTime = true;  //TODO FIXME this is very much hardcoded to one installation!

    String context="context0";
    // a "context=context1" in the URL should be enough
    context=ServletUtilities.getContext(request);

    Shepherd myShepherd = new Shepherd(context);
    FeatureType.initAll(myShepherd);
    PrintWriter out = response.getWriter();

    out.println("Context grabbed = "+context);



    if (firstTime) {
      initFeatureTypeAndAssetStore(myShepherd, out);
      out.println("it is the firstTime");
    } else {
      out.println("it is not the firstTime");
    }

    int offset = 0;
    if (request.getParameter("offset")!=null) {
      offset = Integer.parseInt(request.getParameter("offset"));
    }

    out.println("\n\nStarting ImportIA servlet...");
    myShepherd.beginDBTransaction();

    System.out.println("IA-IMPORT: started.....");
    String urlSuffix = "/api/imageset/json/?is_special=False";
    System.out.println("    urlSuffix = "+urlSuffix);
    System.out.println("    context = "+context);
    JSONObject imageSetRes = getFromIA(urlSuffix, context, out);
    JSONArray fancyImageSetUUIDS = imageSetRes.optJSONArray("response");
    System.out.println("IA-IMPORT: got "+fancyImageSetUUIDS.length()+" UUIDs back from IA");

    if (imageSetRes==null && request.getParameter("doOnly") == null) {
      System.out.println("Error! getFromIA(\""+urlSuffix+"\", context, out) returned null!");
      return;
    } else if (fancyImageSetUUIDS==null) {
      System.out.println("Got a result from IA but failed to parse fancyImageSetUUIDS. imageSetRes = "+imageSetRes);
      return;
    }

    System.out.println("IA-IMPORT: received from IA imageSetRes="+imageSetRes);


    int testingLimit = -1;
    if (request.getParameter("testingLimit") != null) {
        try {
            testingLimit = Integer.parseInt(request.getParameter("testingLimit"));
        } catch (Exception ex) {}
        if (testingLimit > 0) System.out.println("IA-IMPORT: testingLimit=" + testingLimit);
    }

       if (request.getParameter("doOnly") != null) {
               String onlyOcc = request.getParameter("doOnly");
               System.out.println("IA-IMPORT: doing only Occurrence " + onlyOcc);
               fancyImageSetUUIDS = new JSONArray();
               fancyImageSetUUIDS.put(IBEISIA.toFancyUUID(onlyOcc));
       }



    for (int i = 0; i < fancyImageSetUUIDS.length(); i++) {
        if ((testingLimit > 0) && (i >= testingLimit)) continue;
        JSONObject fancyID = fancyImageSetUUIDS.getJSONObject(i);
        Occurrence occ = null;
        String occID = IBEISIA.fromFancyUUID(fancyID);

        //System.out.println("IA-IMPORT: ImageSet " + occID);
      JSONObject annotRes = getFromIA("/api/imageset/annot/uuid/json/?imageset_uuid_list=[" + fancyID + "]", context, out);
/////System.out.println("annotRes -----> " + annotRes);
      // it's a singleton list, hence [0]
      JSONArray annotFancyUUIDs = annotRes.getJSONArray("response").getJSONArray(0);

/*
if (annotFancyUUIDs.length() > 10) {
    JSONArray x = new JSONArray();
    for (int j = 0 ; j < 10 ; j++) {
        x.put(annotFancyUUIDs.getJSONObject(j));
    }
    annotFancyUUIDs = x;
System.out.println("truncated to\n" + x);
}
*/
      List<String> annotUUIDs = fromFancyUUIDList(annotFancyUUIDs);
      //out.println("occID: " + occID + " has annotUUIDs " + annotUUIDs);
      System.out.println("occID: " + occID + " has " + annotUUIDs.size() + " annotUUIDs");


        //now we have to break this up a little since there are some pretty gigantic sets of annotations, it turns out.  :(
        // but ultimately we want to fill iaNamesArray and annots
        JSONArray iaNamesArray = new JSONArray();
        List<Annotation> annots = new ArrayList<Annotation>();
        int annotBatchSize = 100;

        int acount = 0;
        while (acount < annotUUIDs.size()) {

            //we also only *import* NEW Annotations, cuz sorry this is importing
            List<String> thisBatch = new ArrayList<String>();
            JSONArray thisFancy = new JSONArray();
            while ((thisBatch.size() < annotBatchSize) && (acount < annotUUIDs.size())) {
/*
                Annotation exist = null;
                try {
                    exist = ((Annotation) (myShepherd.getPM().getObjectById(myShepherd.getPM().newObjectIdInstance(Annotation.class, annotUUIDs.get(acount)), true)));
                } catch (Exception ex) {}
                if (exist != null) {
System.out.println(" - - - skipping existing " + exist);
                    acount++;
                    continue;
                }
*/
                thisBatch.add(annotUUIDs.get(acount));
                thisFancy.put(IBEISIA.toFancyUUID(annotUUIDs.get(acount)));
                acount++;
            }
//System.out.println(acount + " of " + annotUUIDs.size() + " ================================================ now have a batch to fetch: " + thisBatch);
            if (thisBatch.size() > 0) {
                myShepherd.beginDBTransaction();
                List<Annotation> these = IBEISIA.grabAnnotations(thisBatch, myShepherd);
                myShepherd.commitDBTransaction();
                annots.addAll(these);

                try {
                    JSONArray thisNames = IBEISIA.iaNamesFromAnnotUUIDs(thisFancy, context);
//System.out.println(" >>> thisNames length = " + thisNames.length());
                    for (int ti = 0 ; ti < thisNames.length() ; ti++) {
                        iaNamesArray.put(thisNames.get(ti));
                    }
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }
        }

        //at this point we should have annots and iaNamesArray filled
//System.out.println("iaNamesArray ----> " + iaNamesArray);

      List<String> uniqueNames = new ArrayList<String>();
      HashMap<String,ArrayList<Annotation>> annotGroups = new HashMap<String,ArrayList<Annotation>>();
      for (int j=0; j < iaNamesArray.length(); j++) {
        String thisName = iaNamesArray.getString(j);
        if (uniqueNames.contains(thisName)) continue;
        uniqueNames.add(thisName);
        annotGroups.put(thisName, new ArrayList<Annotation>());
      }
        //System.out.println("IA-IMPORT: unique names " + uniqueNames);

      for (int j=0; j < annots.size(); j++) {
        annotGroups.get(iaNamesArray.getString(j)).add(annots.get(j));
      }

      for (String uName : uniqueNames) {
        //System.out.println("Number Annotations with "+uName+": "+annotGroups.get(uName).size());
      }

      for (String name : uniqueNames) {
        if (IBEISIA.unknownName(name)) {   // we need one encounter per annot for unknown!
            for (Annotation ann : annotGroups.get(name)) {
                Encounter enc = new Encounter(ann);
                enc.setMatchedBy("IBEIS IA");
                enc.setState("approved");
                myShepherd.beginDBTransaction();
                myShepherd.storeNewEncounter(enc, Util.generateUUID());
                myShepherd.storeNewAnnotation(ann);
                myShepherd.commitDBTransaction();
                myShepherd.beginDBTransaction();
                //System.out.println("IA-IMPORT: " + enc);
                if (occ == null) {
                    occ = myShepherd.getOccurrence(occID);
                    if (occ == null) occ = new Occurrence(occID, enc);
//System.out.println("NEW OCC created (via unnamed) " + occ);
                } else {
                    occ.addEncounter(enc);
//System.out.println("using old OCC (via unnamed) " + occ);
                }
                myShepherd.getPM().makePersistent(occ);
                //System.out.println("IA-IMPORT: " + occ);
                myShepherd.commitDBTransaction();
            }

        } else {
            Encounter enc = new Encounter(annotGroups.get(name));
            enc.setMatchedBy("IBEIS IA");
            enc.setState("approved");

            // here we have to check if this encounter has been added already



        /*
            note: this constructor will set the date/time on the Encounter "based upon the Annotations"
            (which currently means the .getDateTime() of the first MediaAsset -- but this algorithm may change).
            not sure if this is the desirable end result here, since we can also pull the Annotation times from IA as well.
            (see IBEISIA.iaDateTimeFromAnnotUUID() )  -- we might want to do that or fall back to that if the constructor
            fails to set something.  tho... i think jasonp said annot times just come from images, so.

            Similarly, it should also get lat/lon and species based upon IA values (if they have some).  note that IA also has sex, however,
            it is stored on the "names" (i.e. individuals) so is not sighting-based ... so only useful to us if it is a new individual.

            Age, however, we dont store anywhere "lower", so we need to get that explicitly ... however IA has that on each Annotation,
            so we just ... um.. i guess take the first one we can find?

            note also that adding encounters to individuals should(!) gracefully adjust the individual accordingly (set sex/taxonomy *if unset*)
        */
            String sex = null;
            try {
            sex = IBEISIA.iaSexFromAnnotUUID(annotGroups.get(name).get(0).getId(), context);
//System.out.println("--- sex=" + sex);
            } catch (Exception ex) {}
            Double age = null;
            try {
                //guess this assumes we have at least one annot and it has age; could walk thru if not?
                age = IBEISIA.iaAgeFromAnnotUUID(annotGroups.get(name).get(0).getId(), context);
            } catch (Exception ex) {}
            if (age != null) enc.setAge(age);
            myShepherd.beginDBTransaction();
            myShepherd.storeNewEncounter(enc, Util.generateUUID());
            myShepherd.commitDBTransaction();
            myShepherd.beginDBTransaction();
            //System.out.println("IA-IMPORT: " + enc);

            enc.setIndividualID(name);
            if (myShepherd.isMarkedIndividual(name)) {
                MarkedIndividual ind = myShepherd.getMarkedIndividual(name);
                if ((ind.getSex() == null) && (sex != null)) ind.setSex(sex); //only if not set already
                ind.addEncounter(enc, context);
            } else {
                MarkedIndividual ind = new MarkedIndividual(name, enc);
                if (sex != null) ind.setSex(sex);
                myShepherd.storeNewMarkedIndividual(ind);
                System.out.println("IA-IMPORT: new indiv " + ind);
            }

            for (Annotation ann: annotGroups.get(name)) {
                myShepherd.storeNewAnnotation(ann);
            }
            myShepherd.commitDBTransaction();
            myShepherd.beginDBTransaction();
            if (occ == null) {
                occ = myShepherd.getOccurrence(occID);
                if (occ == null) occ = new Occurrence(occID, enc);
//System.out.println("NEW OCC created " + occ);
            } else {
                occ.addEncounter(enc);
//System.out.println("using old OCC " + occ);
            }
            myShepherd.getPM().makePersistent(occ);
            //System.out.println("IA-IMPORT: " + occ);
            myShepherd.commitDBTransaction();
        }

      }

//System.out.println("zzzzzzzzzzzzzzzzzz " + occ);
        myShepherd.getPM().makePersistent(occ);
        myShepherd.commitDBTransaction();
    }

    //myShepherd.closeDBTransaction();

System.out.println(". . . . . . IMPORT COMPLETE . . . . . .");
out.println("complete");

  }

  private void initFeatureTypeAndAssetStore(Shepherd myShepherd, PrintWriter out) {
    FeatureType.initAll(myShepherd);
    String rootDir = getServletContext().getRealPath("/");
    String context = myShepherd.getContext();
    String baseDir = ServletUtilities.dataDir(context, rootDir);
    String dataDirName = CommonConfiguration.getDataDirectoryName(context);
    String assetStorePath = "/data/" +dataDirName+ "/encounters";

    String rootURL = "http://128.112.89.89:8080/";
    String assetStoreURL = rootURL +dataDirName +"/encounters";
    out.println("initFeatureTypeAndAssetStore variable log:");
    out.println("     rootDir = "+rootDir);
    out.println("     baseDir = "+baseDir);
    out.println("     assetStorePath = "+assetStorePath);
    out.println("     assetStoreURL = "+assetStoreURL);

    //////////////// begin local //////////////
    LocalAssetStore as = new LocalAssetStore("Princeton-Asset-Store vII", new File(assetStorePath).toPath(), assetStoreURL, true);
    out.println("  made new asset store = "+as.toString());
    myShepherd.getPM().makePersistent(as);
    out.println("and persisted it.");
  }

  // I always swallow errors in the interest of clean code!
  private JSONObject getFromIA(String urlSuffix, String context, PrintWriter outForErrors) throws IOException {
    JSONObject res = new JSONObject();
    URL restGetString = IBEISIA.iaURL(context, urlSuffix);
    System.out.println("getFromIA about to call "+restGetString);
    try {
      res = RestClient.get(restGetString);
    }
    catch (MalformedURLException e) {
      outForErrors.println("MalformedURLException on getFromIA()"+urlSuffix+"), which tried to GET "+restGetString);
      e.printStackTrace(outForErrors);
    }
    catch (NoSuchAlgorithmException e) {
      outForErrors.println("NoSuchAlgorithmException on getFromIA()"+urlSuffix+"), which tried to GET "+restGetString);
      e.printStackTrace(outForErrors);
    }
    catch (InvalidKeyException e) {
      outForErrors.println("InvalidKeyException on getFromIA()"+urlSuffix+"), which tried to GET "+restGetString);
      e.printStackTrace(outForErrors);
    }
    catch (IOException e) {
      outForErrors.println("IOException on getFromIA()"+urlSuffix+"), which tried to GET "+restGetString);
      e.printStackTrace(outForErrors);
    }
    if (res==null) throw new IOException("Could not get "+urlSuffix+"from server");
    return res;
  }

  private List<String> fromFancyUUIDList(JSONArray fancyUUIDs) {
    List<String> ids = new ArrayList<String>();
    for (int j = 0; j < fancyUUIDs.length(); j++) {
      ids.add(IBEISIA.fromFancyUUID(fancyUUIDs.getJSONObject(j)));
    }
    return ids;
  }

}
