package org.ecocean.servlet;

import org.ecocean.*;
import org.ecocean.media.*;

import javax.servlet.ServletConfig;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import java.io.*;
import java.util.List;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.Arrays;
import java.util.Map;
import java.util.Vector;
import java.util.concurrent.ThreadPoolExecutor;

/**
 * Given a MediaAssetSet id, create the appropriate occurrences, and return their IDs.
 *
 */


public class OccurrenceClusterMASet extends HttpServlet {

  public void init(ServletConfig config) throws ServletException {
    super.init(config);
  }


  public void doGet(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
    doPost(request, response);
  }

  public void doPost(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
    String context="context0";
    context=ServletUtilities.getContext(request);
    String langCode = ServletUtilities.getLanguageCode(request);
    Shepherd myShepherd = new Shepherd(context);
    //set up for response
    response.setContentType("text/html");
    PrintWriter out = response.getWriter();
    boolean locked = false;

    // ServletUtilities.informInterestedParties(request, request.getParameter("number"), message,context);
    myShepherd.beginDBTransaction();

    String id="";

    try {

      id = request.getParameter("id");
      MediaAssetSet set = myShepherd.getMediaAssetSet(id);

      if (set!=null) {
        int n = 1;
        //String[] occIDs = Cluster.makeNOccurrences(1, set.getMediaAssets(), myShepherd);
        List<Occurrence> occurrences = Cluster.defaultCluster(set.getMediaAssets(), myShepherd);
        String[] occIDs = new String[occurrences.size()];
        for (int i=0;i<occurrences.size();i++) {
          if (occurrences.get(i)!=null) {
            occIDs[i] = occurrences.get(i).getOccurrenceID();
            n++;
          }
        }

        out.println("Made "+n+" occurrences. IDs: "+Arrays.toString(occIDs));
      }

    } catch (Exception edel) {
      locked = true;
      out.println("Exception caught on MediaAssetSet id="+id);
      edel.printStackTrace(out);
      myShepherd.rollbackDBTransaction();

    }


    if (!locked) {
      myShepherd.commitDBTransaction();
      out.println("{status: success}");
    }

    out.close();
    myShepherd.closeDBTransaction();
  }
}
