package org.ecocean.servlet;

import javax.servlet.ServletConfig;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.ecocean.CommonConfiguration;
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
import java.util.List;

public class EncounterGetPatterningPassport extends HttpServlet {
  

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

    String context="context0";
    context=ServletUtilities.getContext(request);
    
    String rootWebappPath = getServletContext().getRealPath("/");
    File webappsDir = new File(rootWebappPath).getParentFile();
    File shepherdDataDir = new File(webappsDir, CommonConfiguration.getDataDirectoryName(context));
    //if(!shepherdDataDir.exists()){shepherdDataDir.mkdirs();}
    File encountersDir = new File(shepherdDataDir.getAbsolutePath() + "/encounters");
    
    Shepherd myShepherd = new Shepherd(context);
    myShepherd.setAction("EncounterGetPatterningPassport.class");
    myShepherd.beginDBTransaction();

    responseMsg += "GetPatterningPassport<p/>";
    List<PatterningPassport> ppArr = getAll(myShepherd);
    responseMsg += "getAll() " + ppArr.size() + "<p/>";
    
    Iterator ppIt = ppArr.iterator(); 
    while (ppIt.hasNext()) {
      PatterningPassport ppFile = (PatterningPassport)ppIt.next();
      if ((ppFile != null) && (ppFile.getEncounterId()!=null))
      {
        ///File thisEncounterDir = new File(encountersDir, ppFile.getEncounterId());
				File thisEncounterDir = new File(Encounter.dir(shepherdDataDir, ppFile.getEncounterId()));
        File thisPPFileObject = new File(thisEncounterDir, (ppFile.getMediaId()+"_pp.xml"));
        if(thisPPFileObject.exists()){responseMsg += "::: The PP file also exists!<br/>";}
        
      } else {
        responseMsg += ">>> LOOKS LIKE IT ISN'T A FILE! " + ppFile + "<br/>";
      }
    }
    
    myShepherd.rollbackDBTransaction();
    myShepherd.closeDBTransaction();

    // response
    out.println(ServletUtilities.getHeader(request));
    out.println(responseMsg);
    out.println(ServletUtilities.getFooter(context));
    out.close();

    //return;
  }

  public List<PatterningPassport> getAll(Shepherd myShepherd) {
    List<PatterningPassport> ppList;
    ppList = myShepherd.getPatterningPassports();
    
    return ppList;
  }

}
