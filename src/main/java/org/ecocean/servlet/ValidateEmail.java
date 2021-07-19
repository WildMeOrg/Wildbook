//Validating user name at the first page before we got on to the demographic survey so that user 



package org.ecocean.servlet;

import java.io.IOException;
import java.io.PrintWriter;

import javax.servlet.ServletConfig;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.ecocean.Role;
import org.ecocean.Shepherd;
import org.ecocean.User;
import org.ecocean.Util;
import org.json.JSONObject;

public class ValidateEmail extends HttpServlet {
  
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
    PrintWriter out = response.getWriter();
    String email = request.getParameter("email");
    System.out.println("Validate Email " + email);
    
    String context = ServletUtilities.getContext(request);
    Shepherd myShepherd = new Shepherd(context);
    
    email = email.trim();
    
    JSONObject rtn = new JSONObject();
    boolean isValidUser = (!Util.isValidEmailAddress(email));
    if (isValidUser) {
      rtn.put("error", "Invalid email");
    }
    
    if (!isValidUser) {
      User exists = myShepherd.getUserByEmailAddress(email);
      if(exists != null)
        rtn.put("error", "Email already exists");
    }
    
    if(rtn.has("error")) {
      rtn.put("success", false);
    } else {
      rtn.put("success", true);
    }
    out.println(rtn.toString());
    
  }
  
 

}
