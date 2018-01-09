package org.ecocean.servlet;
import java.io.IOException;
import java.io.PrintWriter;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.ecocean.*;

public class SurveyCreate extends HttpServlet {

  /** @author Colin Kingen
   *  2017
   */
  private static final long serialVersionUID = 1L;
  
  @Override
  public void doGet(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
    try {
      doPost(request, response);      
    } catch (Exception e) {
      e.printStackTrace();
    }
  }
  
  @Override
  public void doPost(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
    String context = ServletUtilities.getContext(request);
    Shepherd myShepherd = new Shepherd(context);
    myShepherd.setAction("SurveyCreate.class");
    
    // Set up for routing to result info.
    response.setContentType("text/html");
    
    PrintWriter out = null;
    try {
      out = response.getWriter();
    } catch (Exception e) {
      e.printStackTrace();
    }
    
    String date = null;
    if (request.getParameter("date")!=null) {
      date = request.getParameter("date");
    }

    Survey sv = null;
    if (date!=null) {
      try {
        sv = new Survey(date);
      } catch (Exception e) {
        e.printStackTrace();
      }      
    }
    
    try {
      String project = null;
      if (request.getParameter("project")!=null) {
        project = request.getParameter("project");
        sv.setProjectName(project);
      }
      
      String organization = null;
      if (request.getParameter("organization")!=null) {
        organization = request.getParameter("organization");
        sv.setOrganization(organization);
      }
      
      String startTime = null;
      if (request.getParameter("startTime")!=null) {
        startTime = request.getParameter("startTime");
        System.out.println("Endtime : "+startTime);
      }
      
      String endTime = null;
      if (request.getParameter("endTime")!=null) {
        endTime = request.getParameter("endTime");
        System.out.println("Endtime : "+endTime);
      }
      
      String effort = null;
      if (request.getParameter("effort")!=null) {
        effort = request.getParameter("effort");
        Double effNum = Double.valueOf(effort);
        Measurement eff = new Measurement("","",effNum,"HHmm","Observed");
        sv.setEffort(eff);
      }
      
      String comments = null;
      if (request.getParameter("comments")!=null) {
        comments = request.getParameter("comments");
        sv.addComments(comments);
      }
      
      String type = null;
      if (request.getParameter("type")!=null) {
        type = request.getParameter("type");
        sv.setProjectType(type);
      }      
    } catch (Exception e) {
      e.printStackTrace();
    }
    
    
    
  }
}








