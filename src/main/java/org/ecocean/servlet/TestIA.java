package org.ecocean.servlet;

import java.io.IOException;

import javax.servlet.ServletConfig;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.ecocean.servlet.ServletUtilities;

import java.util.ArrayList;
import org.ecocean.Annotation;
import org.json.JSONObject;
import org.ecocean.identity.IBEISIA;
import org.ecocean.*; 
import java.io.File;
import java.util.Iterator;

public class TestIA extends HttpServlet { 
  
  public void init(ServletConfig config) throws ServletException {
    super.init(config);
  }

  public void doGet(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
    doPost(request, response);
  }  
  
  
  public void doPost(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
    String context="context0";
    context=ServletUtilities.getContext(request);
    
//handle some cache-related security
    response.setHeader("Cache-Control", "no-cache"); //Forces caches to obtain a new copy of the page from the origin server
    response.setDateHeader("Expires", 0); //Causes the proxy cache to see the page as "stale"
    response.setHeader("Pragma", "no-cache"); //HTTP 1.0 backward compatibility
    
    //let's set up references to our file system components
    String rootDir = getServletContext().getRealPath("/");
    File webappsDir = new File(rootDir).getParentFile();
    File dataDir = new File(webappsDir, CommonConfiguration.getDataDirectoryName(context));
    File encountersDir=new File(dataDir.getAbsolutePath()+"/encounters");
    
    Shepherd myShepherd = new Shepherd(context);
    myShepherd.setAction("TestIA.class"); 
    
    ArrayList<Annotation> anns = new ArrayList<Annotation>();
    Iterator<Annotation> annIt = myShepherd.getAllAnnotationsNoQuery();
    Annotation thisAnn = null;
    while (annIt.hasNext()) {
      thisAnn = annIt.next();
      anns.add(thisAnn);
    }
    
    JSONObject result = new JSONObject();
    
    try {
      result = IBEISIA.sendAnnotations(anns);      
    } catch (Exception e) {
      System.out.println("GETTING RESULT FAILED!");
      e.printStackTrace();
    }
    
    boolean stop = false;
    int seconds = 1;
    while (result == null && stop == false) {
      try {
        Thread.sleep(1000);        
      } catch (Exception e) {
        System.out.println("SLEEP FAILED AHHHHHHHHHH");
        e.printStackTrace();
      }
      System.out.println("Waiting ... "+seconds+" seconds elapsed...");
      seconds+=1;
      if (seconds == 1200) {
        stop = true;
      }
    } 
    
    System.out.println(result.toString());
    
  }

} 