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

/**
 * request looks like this
 *
 *
 */
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
    response.setContentType("text/plain");
    PrintWriter out = response.getWriter();
    //out.println("Servlet wrote this!");

    try {

      if (request.getParameter("stringifiedJSONQuery")==null) {
        out.println("NO stringifiedJSONQuery ERROR");
        throw new IOException("NO stringifiedJSONQuery ERROR");
      }

      String jsonString = request.getParameter("stringifiedJSONQuery");

      //out.println("inputStreamToString = "+jsonString);

      JSONObject json = new JSONObject(jsonString);

      // test request format:
      if (json.optString("class")==null || json.optJSONObject("query")==null) {
        throw new IOException("TranslateQuery argument requires a \"class\" and \"query\" field.");
      }



      WBQuery wbq = new WBQuery(json);
      List<Object> queryResult = wbq.doQuery(myShepherd);
      int nResults = queryResult.size();
      String[] queryResultStrings = new String[nResults];


      String queryClass = wbq.getCandidateClass().getName();
      //out.println("</br>queryClass = "+queryClass);

      // Need to switch on queryClass, because we need to know the class of each object in queryResult in order to call .sanitizeJson

      out.println("[");

      // hackey debug mode
      if (request.getParameter("debug")!=null) {
        String translatedQuery = wbq.toJDOQL();
        out.println("{metadata: {JDOQL: "+translatedQuery+"}}, ");
      }
      switch (queryClass) {
        case "org.ecocean.Encounter":
          for (int i=0;i<nResults;i++) {
            if (i!=0) {out.println(",");}
            Encounter enc = (Encounter) queryResult.get(i);
            JSONObject encJSON = enc.sanitizeJson(request, new JSONObject());
            out.print(encJSON.toString());
          }
          break;
        case "org.ecocean.Media.MediaAsset":
        for (int i=0;i<nResults;i++) {
            if (i!=0) {out.println(",");}
            MediaAsset ma = (MediaAsset) queryResult.get(i);
            JSONObject maJSON = ma.sanitizeJson(request, new JSONObject());
            out.print(maJSON.toString());
          }
          break;
        case "org.ecocean.MarkedIndividual":
        for (int i=0;i<nResults;i++) {
            if (i!=0) {out.println(",");}
            MarkedIndividual mi = (MarkedIndividual) queryResult.get(i);
            JSONObject miJSON = mi.sanitizeJson(request, new JSONObject());
            out.print(miJSON.toString());
          }
          break;
      } // end switch(queryClass)
      out.println("]");

    }
    catch (Exception e) {
      out.println("There was an exception!");
      e.printStackTrace(out);
      myShepherd.rollbackDBTransaction();
    }

    out.close();
    myShepherd.closeDBTransaction();


  } // end doPost

  public String convertStreamToString(InputStream is, PrintWriter out) throws IOException {
    out.println("Beginning conversion</br>");
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
                out.println("look: "+n);
            }
        } finally {
            is.close();
        }
        return writer.toString();
    }
    out.println("returning");
    return "";
  }
}

/*
  public static JSONObject datanucleusJSONtoApacheJSON(org.datanucleus.api.rest.orgjson.JSONObjectdnJSON) {
    JSONObject outJSON = new JSONObject();



  }
*/
