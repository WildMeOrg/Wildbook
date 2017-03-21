package org.ecocean.servlet;

import java.io.IOException;
import java.io.PrintWriter;
import java.util.List;

import javax.jdo.Extent;
import javax.jdo.Query;
import javax.servlet.ServletConfig;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.ecocean.CommonConfiguration;
import org.ecocean.User;
import org.ecocean.Shepherd;

public class UserResetAcceptedUserAgreement extends HttpServlet {

  public void init(ServletConfig config) throws ServletException {
    super.init(config);
  }


  public void doGet(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
    doPost(request, response);
  }


  public void doPost(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
    
    String context="context0";
    //context=ServletUtilities.getContext(request);
    Shepherd myShepherd = new Shepherd(context);
    myShepherd.setAction("UserResetAcceptedUserAgreement.class");

    //set up for response
    response.setContentType("text/html");
    PrintWriter out = response.getWriter();
    boolean locked = false;
    boolean madeChanges = false;


    Extent userClass = myShepherd.getPM().getExtent(User.class, true);
    Query query = myShepherd.getPM().newQuery(userClass);

    myShepherd.beginDBTransaction();
      try {
        List<User> it=myShepherd.getAllUsers();
        int numUsers=it.size();
        for(int i=0;i<numUsers;i++){
          User tempUser= it.get(i);
          tempUser.setAcceptedUserAgreement(false);
          madeChanges=true;
        } //end for
      
      } 
      catch (Exception le) {
        locked = true;
        myShepherd.rollbackDBTransaction();
        myShepherd.closeDBTransaction();
      }
      if (!madeChanges) {
        myShepherd.rollbackDBTransaction();
        myShepherd.closeDBTransaction();
      }
      //success!!!!!!!!

      else if (!locked) {
        myShepherd.commitDBTransaction();
        myShepherd.closeDBTransaction();
        out.println(ServletUtilities.getHeader(request));
        out.println(("<strong>Success!</strong> I have successfully reset the User Agreement status for all users. Each will now need to accept the User Agreement again after login."));
        out.println("<p><a href=\""+request.getScheme()+"://" + CommonConfiguration.getURLLocation(request) + "/appadmin/users.jsp?context=context0\">Return to the User Administration page.</a></p>\n");
        out.println(ServletUtilities.getFooter(ServletUtilities.getContext(request)));
      }
      //failure due to exception
      else {
        out.println(ServletUtilities.getHeader(request));
        out.println("<strong>Failure!</strong> I could not reset User Agreement status. Please check the server log for the relevant exception.");
        out.println(ServletUtilities.getFooter(context));
      }
      query.closeAll();

    out.close();
  }
  
}
