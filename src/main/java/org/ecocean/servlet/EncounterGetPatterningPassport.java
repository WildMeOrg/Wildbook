package org.ecocean.servlet;

import javax.servlet.ServletConfig;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.ecocean.PatterningPassport;
import org.ecocean.Shepherd;
import org.ecocean.MarkedIndividual;
import org.ecocean.Encounter;
import org.ecocean.SinglePhotoVideo;
import org.ecocean.servlet.ServletUtilities;

import com.oreilly.servlet.MultipartRequest;

import javax.jdo.Extent;
import javax.jdo.FetchPlan;
import javax.jdo.Query;

import java.io.File;
import java.io.IOException;
import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.Iterator;
import java.util.Properties;

public class EncounterGetPatterningPassport extends HttpServlet {
  Shepherd myShepherd = new Shepherd();

  public void init(ServletConfig config) throws ServletException {
    super.init(config);
  }

  public void doGet(HttpServletRequest request, HttpServletResponse response)
      throws ServletException, IOException {
    doPost(request, response); // Just forwards to the POST
  }

  public void doPost(HttpServletRequest request, HttpServletResponse response)
      throws ServletException, IOException {
    // set up for response
    response.setContentType("text/html");
    PrintWriter out = response.getWriter();
    String responseMsg = "";

    responseMsg += "GetPatterningPassport<p/>";
    ArrayList<PatterningPassport> ppArr = getAll();
    responseMsg += "getAll() " + ppArr.size() + "<p/>";
    
    Iterator ppIt = ppArr.iterator(); 
    while (ppIt.hasNext()) {
      File ppFile = (File)ppIt.next();
      if (ppFile != null && ppFile.isFile())
      {
        responseMsg += "::: " + ppFile.getName() + "<br/>";
      } else {
        responseMsg += ">>> LOOKS LIKE IT ISN'T A FILE! " + ppFile + "<br/>";
      }
    }

    // response
    out.println(ServletUtilities.getHeader(request));
    out.println(responseMsg);
    out.println(ServletUtilities.getFooter());
    out.close();

    return;
  }

  public ArrayList<PatterningPassport> getAll() {
    ArrayList<PatterningPassport> ppList;
    ppList = myShepherd.getPatterningPassports();
    
    return ppList;
  }

}
