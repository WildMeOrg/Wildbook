package org.ecocean.servlet;

import javax.servlet.*;
import javax.servlet.http.*;

import java.io.*;
import java.security.Principal;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Date;

import org.apache.commons.collections.CollectionUtils;
import org.apache.shiro.web.util.WebUtils;

import org.ecocean.*;
import org.ecocean.scheduled.ScheduledIndividualMerge;
import org.ecocean.security.Collaboration;


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


    response.setContentType("text/html");
    out = response.getWriter();

    String id1 = request.getParameter("id1");
    String id2 = request.getParameter("id2");
    if (id1==null || id2==null) {
      String msg = "<strong>Error:</strong> Missing two valid individualIDs for MergeIndividual. ";
      if (id1==null) msg+="<br>Bad id1: "+id1;
      if (id2==null) msg+="<br>Bad id2: "+id2;
      errorAndClose(msg, response);
      return;
    }

    String oldName1="";
    String oldName2="";

    boolean canMergeAutomatically = false;

    myShepherd=new Shepherd(request);
    myShepherd.setAction("MergeIndividual.class");

    try {

      myShepherd.beginDBTransaction();

      MarkedIndividual mark1 = myShepherd.getMarkedIndividualQuiet(id1);
      MarkedIndividual mark2 = myShepherd.getMarkedIndividualQuiet(id2);

      if (mark1==null || mark2==null) {
        String msg = "<strong>Error:</strong> Could not find both individuals in our database. ";
        System.out.println("MergeIndividual.java: "+msg);
        if (mark1==null) {msg+="<br>could not find individual "+id1;System.out.println("MergeIndividual.java couldn't find: "+id1);}
        if (mark2==null) {msg+="<br>could not find individual "+id2;System.out.println("MergeIndividual.java couldn't find: "+id2);}
        myShepherd.rollbackDBTransaction();
        myShepherd.closeDBTransaction();
        errorAndClose(msg, response);
        return;
      }
      
      oldName1 = mark1.getDisplayName() + "("+Util.prettyUUID(mark1.getIndividualID())+")";
      oldName2 = mark2.getDisplayName() + "("+Util.prettyUUID(mark2.getIndividualID())+")";


      String sex = request.getParameter("sex");
      String taxonomyStr = request.getParameter("taxonomy");
      List<String> desiredIncrementalIdArr = new ArrayList<String>();
      List<String> deprecatedIncrementIdsArr = new ArrayList<String>();
      List<String> projIdsArr = new ArrayList<String>();

      String desiredIncrementalIds = request.getParameter("desiredIncrementalIds");
      if(desiredIncrementalIds != null){
        desiredIncrementalIdArr = Arrays.asList(desiredIncrementalIds.split(";"));
      }
      String deprecatedIncrementIds = request.getParameter("deprecatedIncrementIds");
      if(deprecatedIncrementIds != null){
        deprecatedIncrementIdsArr = Arrays.asList(deprecatedIncrementIds.split(";"));
      }
      String projIds = request.getParameter("projIds");
      if(projIds != null){
        projIdsArr = Arrays.asList(projIds.split(";"));
      }
      String throwawayStr = request.getParameter("throwaway");
      boolean throwaway = Util.stringExists(throwawayStr) && !throwawayStr.toLowerCase().equals("false");

      //check for eligibility.. must throw on timer if not able to do right away
      //ArrayList<String> mark1Users = mark1.getAllAssignedUsers();
      //ArrayList<String> mark2Users = mark2.getAllAssignedUsers();
      Principal userPrincipal = request.getUserPrincipal();
      String currentUsername = null;
      if (userPrincipal!=null) {
        currentUsername = userPrincipal.getName();
      }
      
      //if we can't determine who is requeting this, no merge
      if (currentUsername!=null) {

        
        //WB-1017
        //1. if user is in role admin, they can force the automatic merge. we trust our admins. this also prevents unnecessary database calls.
        //2. if User has full edit access to every Encounter of both MarkedIndividuals, they are trusted to make this decision automatically
        //if (allUniqueUsers.size()==1&&allUniqueUsers.get(0).equals(currentUsername)) {
        if(request.isUserInRole("admin") || (Collaboration.canUserFullyEditMarkedIndividual(mark1, request) && Collaboration.canUserFullyEditMarkedIndividual(mark2, request))) {  
          canMergeAutomatically = true;
          System.out.println("Can merge automatically.");
        } 
        else {
          System.out.println("Scheduling a merge between: "+mark1.getIndividualID()+" and "+mark2.getIndividualID());
          ScheduledIndividualMerge merge = new ScheduledIndividualMerge(mark1, mark2, twoWeeksFromNowLong(), currentUsername);
          myShepherd.storeNewScheduledIndividualMerge(merge);
          myShepherd.updateDBTransaction();
        }
      }

      if (canMergeAutomatically) {
        System.out.println("Merging automatically.");
        mark1.mergeAndThrowawayIndividual(mark2, currentUsername, myShepherd);
        if (sex != null) mark1.setSex(sex);
        if (taxonomyStr !=null) mark1.setTaxonomyString(taxonomyStr);
        boolean incrementalIdAndProjectIdListsAreTheSameSize = deprecatedIncrementIdsArr.size()==desiredIncrementalIdArr.size() && deprecatedIncrementIdsArr.size()==projIdsArr.size(); //assumes parallel syntax structure between these lists as well, although that is not strictly checked or enforced here (but hopefully ensured elsewhere)
        if(desiredIncrementalIdArr.size()>0 && incrementalIdAndProjectIdListsAreTheSameSize){
            for (int i=0; i<desiredIncrementalIdArr.size(); i++){
              if(!deprecatedIncrementIdsArr.get(i).equals("_")){
                //there is a deprecated incremental ID that we need to rename and add to both individuals
                mark1.addName("Merged " + projIdsArr.get(i),deprecatedIncrementIdsArr.get(i));
              }
              if(desiredIncrementalIdArr.get(i).equals("_")){
                //Do nothing currently, I think
              }else{
                // remove old name
                mark1.getNames().removeKey(projIdsArr.get(i));
                mark1.addName(projIdsArr.get(i),desiredIncrementalIdArr.get(i));
              }
            }
        }
        if (throwaway) myShepherd.getPM().deletePersistent(mark2);
        myShepherd.commitDBTransaction();
  
      }
      else {
        myShepherd.rollbackDBTransaction();

      }

    }
    catch (Exception le){
      locked=true;
      le.printStackTrace();
      myShepherd.rollbackDBTransaction();
      errorAndClose("An exception occurred. Please contact the admins.", response);
      
    }
    finally {
      myShepherd.closeDBTransaction();
    }

    if(!locked&&canMergeAutomatically){

        out.println("<strong>Success!</strong> I have successfully merged individuals "+id1+" and "+id2+".</p>");
        out.close();
        response.setStatus(HttpServletResponse.SC_OK);

        // redirect to the confirm page
        try {
          WebUtils.redirectToSavedRequest(request, response, "/confirmSubmit.jsp?oldNameA="+oldName1+"&oldNameB="+oldName2+"&newId="+ id1);
        }
        catch (IOException ioe) {
          ioe.printStackTrace();
        }

      } 
    else if (!locked) {
        response.setStatus(HttpServletResponse.SC_OK);
        out.println("<strong>Pending:</strong> Participating user have been notified of your request to merge individuals "+id1+" and "+id2+".</p>");
        out.close();
        
      } 
     else {
        errorAndClose("<strong>Failure!</strong> This encounter is currently being modified by another user, or an exception occurred. Please wait a few seconds before trying to modify this encounter again.", response);
      }
  }


  private void errorAndClose(String msg, HttpServletResponse response) {
    response.setStatus(HttpServletResponse.SC_BAD_REQUEST);
    out.println(msg);
    out.close();
  }

  private long twoWeeksFromNowLong() {
    // i know. this was really the least stupid way.
    final long twoWeeksInMillis = 1209600000;

    //TODO restore desired delay after testing OR, add to task as variable
    //final long twoWeeksInMillis = 60000;
    return System.currentTimeMillis() + twoWeeksInMillis;
  }


}
