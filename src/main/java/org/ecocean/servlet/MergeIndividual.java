package org.ecocean.servlet;
import javax.servlet.*;
import javax.servlet.http.*;

import java.io.*;
import org.apache.shiro.web.util.WebUtils;

import org.ecocean.*;


public class MergeIndividual extends HttpServlet {

  Shepherd myShepherd;
  PrintWriter out;
  boolean locked=false;

  public void init(ServletConfig config) throws ServletException {
    super.init(config);
  }


  public void doGet(HttpServletRequest request, HttpServletResponse response) throws ServletException,IOException {
    doPost(request, response);
  }


  public void doPost(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
    myShepherd=new Shepherd(request);
    myShepherd.setAction("MergeIndividual.class");
    response.setContentType("text/html");
    out = response.getWriter();

    String id1 = request.getParameter("id1");
    String id2 = request.getParameter("id2");
    if (id1==null || id2==null) {
      String msg = "<strong>Error:</strong> Missing two valid individualIDs for MergeIndividual. ";
      if (id1==null) msg+="<br>Bad id1: "+id1;
      if (id2==null) msg+="<br>Bad id2: "+id2;
      errorAndClose("msg", response);
      return;
    }

    String oldName1;
    String oldName2;
    try {

      myShepherd.beginDBTransaction();

      MarkedIndividual mark1 = myShepherd.getMarkedIndividualQuiet(id1);
      MarkedIndividual mark2 = myShepherd.getMarkedIndividualQuiet(id2);
      oldName1 = mark1.getDisplayName() + "("+Util.prettyUUID(mark1.getIndividualID())+")";
      oldName2 = mark2.getDisplayName() + "("+Util.prettyUUID(mark2.getIndividualID())+")";


      if (mark1==null || mark2==null) {
        String msg = "<strong>Error:</strong> Could not find both individuals in our database. ";
        if (mark1==null) msg+="<br>could not find individual "+mark1;
        if (mark2==null) msg+="<br>could not find individual "+mark2;
        errorAndClose("msg", response);
        return;
      }

      String sex = request.getParameter("sex");
      String taxonomyStr = request.getParameter("taxonomy");
      String throwawayStr = request.getParameter("throwaway");
      boolean throwaway = Util.stringExists(throwawayStr) && !throwawayStr.toLowerCase().equals("false");

      // here's where the magic happens
      mark1.mergeIndividual(mark2, request, myShepherd);
      if (sex != null) mark1.setSex(sex);
      if (taxonomyStr !=null) mark1.setTaxonomyString(taxonomyStr);
      if (throwaway) myShepherd.getPM().deletePersistent(mark2);
      myShepherd.commitDBTransaction();
      myShepherd.closeDBTransaction();

    }
    catch(Exception le){
      le.printStackTrace();
      myShepherd.rollbackDBTransaction();
      myShepherd.closeDBTransaction();
      errorAndClose("An exception occurred. Please contact the admins.", response);
      return;
    }

    if(!locked){
        
        out.println("<strong>Success!</strong> I have successfully merged individuals "+id1+" and "+id2+".</p>");
        out.close();
        response.setStatus(HttpServletResponse.SC_OK);

        // redirect to the confirm page
        WebUtils.redirectToSavedRequest(request, response, "/confirmSubmit.jsp?oldNameA="+oldName1+"&oldNameB="+oldName2+"&newId="+ id1);


      }
      else {
        errorAndClose("<strong>Failure!</strong> This encounter is currently being modified by another user, or an exception occurred. Please wait a few seconds before trying to modify this encounter again.", response);
      }
  }


  private void errorAndClose(String msg, HttpServletResponse response) {
    //out.println(ServletUtilities.getHeader(request));
    out.println(msg);
        //out.println("<p><a href=\"http://"+CommonConfiguration.getURLLocation(request)+"/encounters/encounter.jsp?number="+encNum+"\">Return to encounter "+encNum+"</a></p>\n");
        //out.println(ServletUtilities.getFooter(context));
    out.close();
    response.setStatus(HttpServletResponse.SC_BAD_REQUEST);
    myShepherd.rollbackDBTransaction();
    myShepherd.closeDBTransaction();
  }



}


