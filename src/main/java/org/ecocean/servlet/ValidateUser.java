package org.ecocean.servlet;

import java.io.IOException;

import javax.servlet.ServletConfig;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

public class ValidateUser extends HttpServlet {
  
  @Override
  public void init(final ServletConfig config) throws ServletException {
      super.init(config);
  }


  @Override
  public void doGet(final HttpServletRequest request, final HttpServletResponse response) throws ServletException, IOException {
      doPost(request, response);
  } 
  
  @Override
  public void doPost(final HttpServletRequest request, final HttpServletResponse response) throws ServletException, IOException {
    System.out.print("Testing for username");
    System.out.print(request.getParameter("username"));
    
  }
    

}
