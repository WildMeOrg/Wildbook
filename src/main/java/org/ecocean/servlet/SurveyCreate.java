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
    
    String project = null;
    if (request.getParameter("project")!=null) {
      project = request.getParameter("project");
    }
    
    String organization = null;
    if (request.getParameter("organization")!=null) {
      project = request.getParameter("organization");
    }
    
    String startTime = null;
    if (request.getParameter("startTime")!=null) {
      project = request.getParameter("startTime");
    }
    
    String endTime = null;
    if (request.getParameter("endTime")!=null) {
      project = request.getParameter("endTime");
    }
    
    String effort = null;
    if (request.getParameter("effort")!=null) {
      project = request.getParameter("effort");
    }
    
    String comments = null;
    if (request.getParameter("comments")!=null) {
      project = request.getParameter("comments");
    }
    
    String type = null;
    if (request.getParameter("type")!=null) {
      project = request.getParameter("type");
    }
    
    System.out.println("Ping!");
  }
}








