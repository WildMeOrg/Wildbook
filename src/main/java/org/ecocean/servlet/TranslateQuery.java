package org.ecocean.servlet;

import org.ecocean.*;
import org.ecocean.media.*;

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
import java.util.Map;

//import JSONObject;
//import JSONArray;

// UGH -- we use two different JSON libraries!
import org.datanucleus.api.rest.orgjson.JSONObject;
import org.datanucleus.api.rest.orgjson.JSONArray;
import org.datanucleus.api.rest.orgjson.JSONException;

/**
 * Takes JS queries from the UI, and returns a JSONArray of REST-like database results.
 * JavaScript usage example:
 * <pre><code> // note that the tags to the left simply delimit the example
 * var args = {class: 'org.ecocean.media.MediaAsset', query: {}, range: 100};
 * // var args = {class: 'org.ecocean.Encounter', query: {sex: {$ne: "male"}}, range: 15};
 * // var args = {class: 'org.ecocean.Encounter', query: {}};
 * $.post( "TranslateQuery", args, function( data ) {
 *   $(".results").append( "Data Loaded: " + data );
 * });
 * </code></pre>
 * @requestParameter class a string naming a Wildbook class, e.g. org.ecocean.Encounter or org.ecocean.media.MediaAsset. Note this is the only required argument.
 * @requestParameter query a mongo-query-syntax JSON object defining the search on 'class'.
 * @requestParameter rangeMin the start index of the results. E.g. rangeMin=10 returns search
 * results starting with the 10th entry. Default 0. Note that sorting options are required (TODO)
 * for this to be as useful as we'd like, as results are currently returned in whatever order JDOQL needs.
 * @requestParameter range the end index of the results, similarly to rangeMin. Defaults to 100 because the server is slow on anything longer, and it's hard to imagine a UI call that would need so many objects.
 */
public class TranslateQuery extends HttpServlet {

  public void init(ServletConfig config) throws ServletException {
    super.init(config);
  }


  /**
   * From stackOverflow http://stackoverflow.com/a/7085652
   *
   **/
  public static JSONObject requestParamsToJSON(HttpServletRequest req) throws JSONException {
    JSONObject jsonObj = new JSONObject();
    Map<String,String[]> params = req.getParameterMap();
    for (Map.Entry<String,String[]> entry : params.entrySet()) {
      String v[] = entry.getValue();
      Object o = (v.length == 1) ? v[0] : v;
      jsonObj.put(entry.getKey(), o);
    }
    return jsonObj;
  }

    public void doOptions(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
        response.setHeader("Access-Control-Allow-Origin", "*");
        response.setHeader("Access-Control-Allow-Methods", "GET, POST");
        if (request.getHeader("Access-Control-Request-Headers") != null) response.setHeader("Access-Control-Allow-Headers", request.getHeader("Access-Control-Request-Headers"));
        //response.setContentType("text/plain");
    }

  public void doGet(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
    doPost(request, response);
  }

  public void doPost(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
    response.setHeader("Access-Control-Allow-Origin", "*");  //allow us stuff from localhost

    try {
    JSONObject res = new JSONObject("{\"success\": false, \"error\": \"unknown\"}");
    String getOut = "";

    String context="context0";
    context=ServletUtilities.getContext(request);
    Shepherd myShepherd = new Shepherd(context);

    // set up response type: should this be JSON?
    response.setContentType("text/plain");
    PrintWriter out = response.getWriter();
    //out.println("Servlet wrote this!");
    out.println("[");

    try {

      JSONObject json = requestParamsToJSON(request);

      if (json.optString("class").isEmpty()) {
        throw new IOException("TranslateQuery argument requires a \"class\" field, which could not be parsed from your input.");
      }
      if (json.optString("query")==null) {
        json.put("query", new JSONObject());
      }



      WBQuery wbq = new WBQuery(json);
      List<Object> queryResult = wbq.doQuery(myShepherd);
      int nResults = queryResult.size();
      String[] queryResultStrings = new String[nResults];


      String queryClass = wbq.getCandidateClass().getName();
      //out.println("</br>queryClass = "+queryClass);

      // Need to switch on queryClass, because we need to know the class of each object in queryResult in order to call .sanitizeJson


      // hackey debug mode
      if (request.getParameter("debug")!=null) {
        String translatedQuery = wbq.toJDOQL();
        out.println("{debug: {JDOQL: \""+translatedQuery+"\" }}, ");
      }
      switch (queryClass) {
        case "org.ecocean.Encounter":
          for (int i=0;i<nResults;i++) {
            if (i!=0) {out.println(",");}
            Encounter enc = (Encounter) queryResult.get(i);
            res = enc.sanitizeJson(request, new JSONObject());
            out.print(res.toString());
          }
          break;
        case "org.ecocean.media.MediaAsset":
          for (int i=0;i<nResults;i++) {
            if (i!=0) {out.println(",");}
            MediaAsset ma = (MediaAsset) queryResult.get(i);
            res = ma.sanitizeJson(request, new JSONObject());
            out.print(res.toString());
          }
          break;
        case "org.ecocean.media.MediaAssetSet":
          for (int i=0;i<nResults;i++) {
            if (i!=0) {out.println(",");}
            MediaAssetSet maSet = (MediaAssetSet) queryResult.get(i);
            res = maSet.sanitizeJson(request, new JSONObject());
            out.print(res.toString());
          }
          break;
        case "org.ecocean.MarkedIndividual":
        for (int i=0;i<nResults;i++) {
            if (i!=0) {out.println(",");}
            MarkedIndividual mi = (MarkedIndividual) queryResult.get(i);
            res = mi.sanitizeJson(request, new JSONObject());
            out.print(res.toString());
          }
          break;
      } // end switch(queryClass)

    }
    catch (Exception e) {
      StringWriter sw = new StringWriter();
      PrintWriter pw = new PrintWriter(sw);
      e.printStackTrace(pw);
      res.put("error", sw.toString());
      out.println(res.toString());
      myShepherd.rollbackDBTransaction();
      response.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
    }
    out.println("]");

    out.close();
    myShepherd.closeDBTransaction();

  }
  catch (JSONException e) {
    // hmmm how do we handle this
  }
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
