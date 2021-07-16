//Validating user name at the first page before we got on to the demographic survey so that user 



package org.ecocean.servlet;

import java.io.IOException;

import javax.servlet.ServletConfig;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.ecocean.Role;
import org.ecocean.Shepherd;
import org.ecocean.User;
import org.ecocean.Util;

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
    String username = request.getParameter("username");
    System.out.println("Validate User name" + username);
    
    String context = ServletUtilities.getContext(request);
    Shepherd myShepherd = new Shepherd(context);
    
    username = username.trim();
    if (!Util.stringExists(username) || username.equals("admin")) throw new IOException("Invalid username");
    
    User exists = myShepherd.getUser(username);
    if (exists != null) throw new IOException("Username is already exists");
   
    
  }
  
 

}
