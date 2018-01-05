package org.ecocean.servlet;

import java.io.IOException;
import java.io.PrintWriter;

import javax.servlet.ServletConfig;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.ecocean.*;

public class SubmitSurvey extends HttpServlet {
  private static final long serialVersionUID = -3893945777300963422L;

  public void init(ServletConfig config) throws ServletException {
    super.init(config);
  }
  
  public void doGet(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
    doPost(request, response);
  }
  
  public void doPost(HttpServletRequest request, HttpServletResponse response) throws IOException {
    PrintWriter out = response.getWriter();
    
    String context = ServletUtilities.getContext(request);
    
    String project = request.getParameter("project");
    String organization = request.getParameter("organization");
    String surveyType = request.getParameter("surveyType");
    String dateString = request.getParameter("dateString");
    String startTime = request.getParameter("startTime");
    String endTime = request.getParameter("endTime");
    
    Survey sv;
    if (dateString == null) {
      sv = new Survey("No Date String");
    }
    sv = new Survey(dateString);
    
    try {
      sv.setProjectName(project);
      sv.setOrganization(organization);
      sv.setProjectType(surveyType);
      sv.setDate(dateString);
      sv.setStartTimeWithDate(startTime);
      sv.setEndTimeWithDate(endTime);   
      sv.generateID();
    } catch (Exception e) {
      e.printStackTrace();
      out.println("Choked on saving Survey Properties!");
    }
    
    Shepherd myShepherd = new Shepherd(context);
    myShepherd.beginDBTransaction();
    try {
      myShepherd.storeNewSurvey(sv);
      myShepherd.commitDBTransaction();     
      out.println("Success saving new survey!");
      out.println("Project = "+sv.getProjectName());
      out.println("Organization = "+sv.getOrganization());
      out.println("Type = "+sv.getProjectType());
      out.println("StartTime? = "+sv.getStartTimeMilli());
    } catch (Exception e) {
      e.printStackTrace();
      myShepherd.rollbackDBTransaction();
      out.println("Failed to persist new Survey.");
    }
    
  }
  
  
}




