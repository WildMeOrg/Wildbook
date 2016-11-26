package org.ecocean.servlet;

import java.io.IOException;
import java.io.PrintWriter;
import java.net.URISyntaxException;
import javax.servlet.ServletConfig;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.ecocean.*;
import org.ecocean.identity.IBEISIA;

import java.util.ArrayList;
import java.util.StringTokenizer;

import org.json.JSONObject;







//handles operations to sharks. possible operations include, create new, add encounter, remove encounter from
public class PrimeIBEISImageAnalysisForSpecies extends HttpServlet {


  public void init(ServletConfig config) throws ServletException {
      super.init(config);
    }




  public void doGet(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException{
    doPost(request, response);
  }

  public void doPost(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException{
    
   
    //set up for response
    response.setContentType("text/html");
    PrintWriter out = response.getWriter();
    
    String context=ServletUtilities.getContext(request);
    Shepherd myShepherd=new Shepherd(context);
    myShepherd.setAction("PrimeIBEISImageAnalysisForSpecies.class");
    String baseUrl="";
    myShepherd.beginDBTransaction();
    try{
      
      try {
        baseUrl = CommonConfiguration.getServerURL(request, request.getContextPath());
      } 
      catch (URISyntaxException ex) {
        System.out.println("ScanWorkItemCreationThread() failed to obtain baseUrl: " + ex.toString());
        ex.printStackTrace();
      }
      System.out.println("baseUrl --> " + baseUrl);
      
      //figure out which species to send annotations and media assets for
      StringTokenizer str=new StringTokenizer(request.getParameter("genusSpecies")," ");
      String genus=str.nextToken();
      String species=str.nextToken();
      
      
      ArrayList<Encounter> encs=myShepherd.getAllEncountersForSpecies(genus, species);
      
      JSONObject results=IBEISIA.primeImageAnalysisForSpecies(encs, myShepherd, Util.taxonomyString(genus, species), baseUrl, context);

      //now that we have a result from priming the system
      if(results.getBoolean("success")){
        out.println(ServletUtilities.getHeader(request));
        out.println("<strong>Success!</strong> I have successfully sent all "+genus+" "+species+" annotations and media assets to image analysis.");

        out.println("<p><a href=\""+request.getScheme()+"://" + CommonConfiguration.getURLLocation(request) + "/appadmin/scanTaskAdmin.jsp" + "\">Return to Grid Administration" + "</a></p>\n");
        out.println(ServletUtilities.getFooter(context));
      }
      else{
        out.println(ServletUtilities.getHeader(request));
        out.println("<strong>Failure:</strong> I was NOT successful sending all "+genus+" "+species+" annotations and media assets to image analysis.");

        out.println("<p><a href=\""+request.getScheme()+"://" + CommonConfiguration.getURLLocation(request) + "/appadmin/scanTaskAdmin.jsp" + "\">Return to Grid Administration" + "</a></p>\n");
        out.println(ServletUtilities.getFooter(context));
      }
      
    
    }
    catch(Exception e){
      e.printStackTrace();
      
    }
    finally{
      myShepherd.rollbackDBTransaction();
      myShepherd.closeDBTransaction();
    }
      
      myShepherd=null;
      out.flush();
      out.close();
    }

}

