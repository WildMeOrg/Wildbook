package org.ecocean.servlet;

import org.ecocean.*;
import org.ecocean.media.MediaAsset;

import javax.servlet.ServletConfig;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

/*import java.io.IOException;
import java.io.PrintWriter;
import java.io.InputStream;
import java.io.Writer;*/
import java.io.*;
import java.util.StringTokenizer;
import java.util.List;

//import JSONObject;
//import JSONArray;

// UGH -- we use two different JSON libraries!
import org.datanucleus.api.rest.orgjson.JSONObject;
import org.datanucleus.api.rest.orgjson.JSONArray;
import org.datanucleus.api.rest.orgjson.JSONException;


public class TranslateQuery extends HttpServlet {

  public void init(ServletConfig config) throws ServletException {
    super.init(config);
  }

  public void doGet(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
    doPost(request, response);
  }

  public void doPost(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {

    String context="context0";
    context=ServletUtilities.getContext(request);
    Shepherd myShepherd = new Shepherd(context);

    // set up response type: should this be JSON?
    response.setContentType("text/html");
    PrintWriter out = response.getWriter();

    // test request format:
    if (!(request.getParameter("class")==null) || !(request.getParameter("query")==null)) {
      throw new IOException("TranslateQuery argument requires a \"class\" and \"query\" field.");
    }

    try {

      String jsonString = convertStreamToString(request.getInputStream());
      JSONObject json = new JSONObject(jsonString);
      WBQuery wbq = new WBQuery(json);
      List<Object> queryResult = wbq.doQuery(myShepherd);



      String queryClass = wbq.getCandidateClass().toString();

      // Need to switch on queryClass, because we need to know the class of each object in queryResult in order to call .sanitizeJson
      switch (queryClass) {
        case "org.ecocean.Encounter":
          for (Object obj : queryResult) {
            Encounter enc = (Encounter) obj;
            JSONObject encJSON = enc.sanitizeJson(request, new JSONObject());
            out.println(encJSON.toString());
          }
          break;
        case "org.ecocean.MediaAsset":
          for (Object obj: queryResult) {
            MediaAsset ma = (MediaAsset) obj;
            JSONObject maJSON = ma.sanitizeJson(request, new JSONObject());
            out.println(maJSON.toString());
          }
          break;
        case "org.ecocean.MarkedIndividual":
          for (Object obj: queryResult) {
            MarkedIndividual mi = (MarkedIndividual) obj;
            JSONObject miJSON = mi.sanitizeJson(request, new JSONObject());
            out.println(miJSON.toString());
          }
          break;
      } // end switch(queryClass)


    }
    catch (Exception e) {
      e.printStackTrace();
      myShepherd.rollbackDBTransaction();
    }

    out.close();
    myShepherd.closeDBTransaction();


  } // end doPost

  public String convertStreamToString(InputStream is) throws IOException {
    // Handy method taken from https://kodejava.org/how-do-i-convert-inputstream-to-string/
    // To convert the InputStream to String we use the
    // Reader.read(char[] buffer) method. We iterate until the
    // Reader return -1 which means there's no more data to
    // read. We use the StringWriter class to produce the string.
    if (is != null) {
        Writer writer = new StringWriter();

        char[] buffer = new char[1024];
        try {
            Reader reader = new BufferedReader(
                    new InputStreamReader(is, "UTF-8"));
            int n;
            while ((n = reader.read(buffer)) != -1) {
                writer.write(buffer, 0, n);
            }
        } finally {
            is.close();
        }
        return writer.toString();
    }
    return "";
  }
}

/*
  public static JSONObject datanucleusJSONtoApacheJSON(org.datanucleus.api.rest.orgjson.JSONObjectdnJSON) {
    JSONObject outJSON = new JSONObject();



  }
*/
