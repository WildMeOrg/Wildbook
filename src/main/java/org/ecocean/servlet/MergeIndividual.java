package org.ecocean.servlet;

import javax.servlet.*;
import javax.servlet.http.*;

import java.io.*;
import java.security.Principal;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Date;

import org.apache.commons.collections.CollectionUtils;
import org.apache.shiro.web.util.WebUtils;

import org.ecocean.*;
import org.ecocean.scheduled.WildbookScheduledIndividualMerge;


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

    boolean canMergeAutomatically = false;
    
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
        myShepherd.rollbackDBTransaction();
        myShepherd.closeDBTransaction();
        return;
      }

      String sex = request.getParameter("sex");
      String taxonomyStr = request.getParameter("taxonomy");
      String throwawayStr = request.getParameter("throwaway");
      boolean throwaway = Util.stringExists(throwawayStr) && !throwawayStr.toLowerCase().equals("false");

      
      //TEMPORARY PLACEMENT!!!! Just to see if it errz (it will )
      // should we check if this is already scheduled? does it matter if it just happens sooner? probably. 
      WildbookScheduledIndividualMerge merge = new WildbookScheduledIndividualMerge(mark1, mark2, twoWeeksFromNowLong(), currentUsername);
      myShepherd.storeNewWildbookScheduledTask(merge);
      myShepherd.beginDBTransaction();
      
      //check for eligibility.. must throw on timer if not able to do right away
      ArrayList<String> mark1Users = mark1.getAllAssignedUsers();
      ArrayList<String> mark2Users = mark2.getAllAssignedUsers();
      Principal userPrincipal = request.getUserPrincipal();
      String currentUsername = null;
      if (userPrincipal!=null) {
        currentUsername = userPrincipal.getName();
      }

      if (currentUsername!=null) {
        ArrayList<String> allUniqueUsers = new ArrayList<>(mark1Users);
        for (String user : mark2Users) {
          if (!allUniqueUsers.contains(user)&&!"".equals(user)) {
            allUniqueUsers.add(user);
            System.out.println("unique user == "+user);
          }
        }

        if (allUniqueUsers.size()==1&&allUniqueUsers.get(0).equals(currentUsername)) {
          canMergeAutomatically = true;
        } else {
          //TODO okay, we got people to notify

          //TODO check for user collaboration before setting timed task

          //TODO actually create merge task - correct location after testing
          // WildbookScheduledIndividualMerge merge = new WildbookScheduledIndividualMerge(mark1, mark2, twoWeeksFromNowLong(), currentUsername);
          // myShepherd.storeNewWildbookScheduledTask(merge);
          // myShepherd.beginDBTransaction();

          //TODO what does the deny action do- update state or DELETE the scheduled task?
        }
      }

      if (canMergeAutomatically) {
        mark1.mergeAndThrowawayIndividual(mark2, currentUsername, myShepherd);
        if (sex != null) mark1.setSex(sex);
        if (taxonomyStr !=null) mark1.setTaxonomyString(taxonomyStr);
        if (throwaway) myShepherd.getPM().deletePersistent(mark2);
        myShepherd.commitDBTransaction();
        myShepherd.closeDBTransaction();
      }

      //TODO figure out further messaging for timed merge initiated

    } catch (Exception le){
      le.printStackTrace();
      myShepherd.rollbackDBTransaction();
      myShepherd.closeDBTransaction();
      errorAndClose("An exception occurred. Please contact the admins.", response);
      return;
    }

    if(!locked&&canMergeAutomatically){
        
        out.println("<strong>Success!</strong> I have successfully merged individuals "+id1+" and "+id2+".</p>");
        out.close();
        response.setStatus(HttpServletResponse.SC_OK);

        // redirect to the confirm page
        try {
          WebUtils.redirectToSavedRequest(request, response, "/confirmSubmit.jsp?oldNameA="+oldName1+"&oldNameB="+oldName2+"&newId="+ id1);
        } catch (IOException ioe) {
          ioe.printStackTrace();
        }

      } else if (!locked) {
        out.println("<strong>Pending:</strong> Participating user have been notified of your request to merge individuals "+id1+" and "+id2+".</p>");
        out.close();
        response.setStatus(HttpServletResponse.SC_OK);
      } else {
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

  private long twoWeeksFromNowLong() {
    // i know. this was really the least stupid way.
    //final long twoWeeksInMillis = 1209600000;

    //TODO restore desired delay after testing OR, add to task as variable
    final long twoWeeksInMillis = 5000;
    return System.currentTimeMillis() + twoWeeksInMillis;
  }


}


