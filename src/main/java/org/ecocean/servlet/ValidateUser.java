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
    System.out.println("Testing for username");
    System.out.println(request.getParameter("username"));
    
  }
  
  public static User registerUser(Shepherd myShepherd, String username, String email, String pw1, String pw2) throws java.io.IOException {
    if (!Util.stringExists(username)) throw new IOException("Username already exists");
    username = username.trim();
    if (!Util.isValidEmailAddress(email)) throw new IOException("Invalid email format");
    if (!Util.stringExists(pw1) || !Util.stringExists(pw2) || !pw1.equals(pw2)) throw new IOException("Password invalid or do not match");
    if (pw1.length() < 8) throw new IOException("Password is too short");
    User exists = myShepherd.getUser(username);
    if (exists == null) exists = myShepherd.getUserByEmailAddress(email);
    if ((exists != null) || username.equals("admin")) throw new IOException("Invalid username/email");
    String salt = Util.generateUUID();
    String hashPass = ServletUtilities.hashAndSaltPassword(pw1, salt);
    User user = new User(username, hashPass, salt);
    user.setEmailAddress(email);
    user.setNotes("<p data-time=\"" + System.currentTimeMillis() + "\">created via registration.</p>");
    Role role = new Role(username, "cat_walk_volunteer");
    role.setContext(myShepherd.getContext());
    myShepherd.getPM().makePersistent(role);
    return user;
  }

}
