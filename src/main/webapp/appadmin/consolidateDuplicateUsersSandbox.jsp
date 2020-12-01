<!DOCTYPE html PUBLIC "-//W3C//DTD XHTML 1.0 Transitional//EN"
        "http://www.w3.org/TR/xhtml1/DTD/xhtml1-transitional.dtd">
<%@ page contentType="text/html; charset=utf-8" language="java" import="org.joda.time.LocalDateTime,
org.joda.time.format.DateTimeFormatter,
org.joda.time.format.ISODateTimeFormat,java.net.*,
org.ecocean.grid.*,
org.ecocean.servlet.ServletUtilities,
java.io.*,java.util.*,
java.io.FileInputStream,
java.io.File,
java.io.FileNotFoundException,
org.ecocean.*,
org.ecocean.servlet.*,
javax.jdo.*,
java.lang.StringBuffer,
java.util.Vector,
java.util.Iterator,
org.ecocean.servlet.importer.*,
java.lang.NumberFormatException"%>

<%
String context="context0";
context=ServletUtilities.getContext(request);
Shepherd myShepherd=new Shepherd(context);
int numFixes=0;
%>

<jsp:include page="../header.jsp" flush="true"/>
    <script>
      let txt = getText("myUsers.properties");
    </script>
    <%
    myShepherd.beginDBTransaction();
    try{
        // User mfisher1 = myShepherd.getUserByUUID("411704e5-045e-45c7-a14c-53d8ada46bc7");
        // if(mfisher1!=null){
        //   System.out.println("mfisher1 is: " + mfisher1.toString());
        // }
        // User mfisher = myShepherd.getUserByUUID("b9cf74d7-f630-46fd-92d4-5209c247e20f");
        // String filter="SELECT FROM org.ecocean.Encounter where photographers.contains(user) && user.uuid==\""+mfisher1.getUUID()+"\" VARIABLES org.ecocean.User user";
        // System.out.println("query in getPhotographerEncountersForUser is: " + filter);
      	// List<Encounter> encs=new ArrayList<Encounter>();
        // Query query= myShepherd.getPM().newQuery(filter);
        // Collection c = (Collection) (query.execute());
        // if(c!=null){
        //   System.out.println("collection in getPhotographerEncountersForUser not null");
        //   encs=new ArrayList<Encounter>(c);
        //   System.out.println("encs are: " + encs.toString());
        // }
        // query.closeAll();

        // System.out.println("got here x");
        User AMMTofo = myShepherd.getUserByUUID("702df060-0151-49f3-834a-4c3cd383c961");
        User aFlam = myShepherd.getUserByUUID("209a2d33-90ef-4ee4-9aa3-e319574ce33c");
        User userToRetain = AMMTofo;
        User userToBeConsolidated = aFlam;

        List<Organization> originalOrganizationsOfUserToRetain = userToRetain.getOrganizations();
        System.out.println("mark before consoldiating orgs: " + originalOrganizationsOfUserToRetain.toString());
        UserConsolidate.consolidateOrganizations(myShepherd, userToRetain, userToBeConsolidated);
        List<Organization> finalOrganizationsOfUserToRetain = userToRetain.getOrganizations();
        System.out.println("mark after consolidating orgs finalOrganizationsOfUserToRetain is: " + finalOrganizationsOfUserToRetain.toString());
       //  System.out.println("got here 1 dedupe consolidating organizations from user: " + userToBeConsolidated.toString() + " into user: " + userToRetain.toString());
       //  if(userToBeConsolidated==null){
       //    System.out.println("userToBeConsolidated is null!");
       //  }
       //  //TODO organizations have members, but Users also have lists of organizations. Removing members from an org ultimately removes organizations from the user
       //  if(userToBeConsolidated!=null){
       //    // System.out.println("got here 2");
       //    List<Organization> organizationsOfUserToBeConsolidated = userToBeConsolidated.getOrganizations();
       //    List<Organization> organizationsOfUserToRetain = userToRetain.getOrganizations();
       //    // System.out.println("got here 3");
       //    if(organizationsOfUserToBeConsolidated!=null && organizationsOfUserToBeConsolidated.size()==0){
       //      System.out.println("organizationsOfUserToBeConsolidated is empty!");
       //    }
       //    if(organizationsOfUserToBeConsolidated!=null && organizationsOfUserToBeConsolidated.size()>0){
       //      // System.out.println("got here 4");
       //      for(int i=0; i<organizationsOfUserToBeConsolidated.size(); i++){
       //        System.out.println("got here 5. Index is: " + i);
       //        Organization currentOrganization = organizationsOfUserToBeConsolidated.get(i);
       //        // System.out.println("got here 6");
       //        if(currentOrganization!=null){
       //          if(!organizationsOfUserToRetain.contains(currentOrganization)){
       //            System.out.println("got here 7");
       //            currentOrganization.updateModified();
       //            System.out.println("got here 7.1");
       //            myShepherd.commitDBTransaction();
       //            myShepherd.beginDBTransaction();
       //            System.out.println("got here 7.2");
       //            System.out.println("organizationsOfUserToRetain before adding a member is: " + organizationsOfUserToRetain.toString());
       //            organizationsOfUserToRetain.add(currentOrganization);
       //            System.out.println("organizationsOfUserToRetain after adding a member is: " + organizationsOfUserToRetain.toString());
       //            System.out.println("got here 7.3");
       //            myShepherd.commitDBTransaction();
       //            myShepherd.beginDBTransaction();
       //            System.out.println("got here 8");
       //            // currentOrganization.addMember(userToRetain);
       //            userToRetain.setOrganizations(organizationsOfUserToRetain);
       //            List<Organization> newOrganizationsOfUserToRetain = userToRetain.getOrganizations();
       //            System.out.println("got here 8.1");
       //            System.out.println("newOrganizationsOfUserToRetain is: " + newOrganizationsOfUserToRetain.toString());
       //            myShepherd.commitDBTransaction();
       //            myShepherd.beginDBTransaction();
       //            System.out.println("got here 8.2");
       //            System.out.println("newOrganizationsOfUserToRetain is: " + newOrganizationsOfUserToRetain.toString());
       //          }
       //          // userToBeConsolidated.getOrganizations
       //          //TODO User.setOrganizations
       //          List<User> currentMembers = currentOrganization.getMembers();
       //          System.out.println("got here 9");
       //        }//end if currentOrganization!=null
       //        System.out.println("got here 10");
       //      } //end for loop of organizationsOfUserToBeConsolidated
       //      System.out.println("got here 11");
       //      System.out.println("organizationsOfUserToRetain about to be set is: " + organizationsOfUserToRetain.toString());
       //      userToRetain.setOrganizations(organizationsOfUserToRetain);
       //      System.out.println("got here 12");
       //      userToBeConsolidated.setOrganizations(new ArrayList<Organization>());
       //      myShepherd.commitDBTransaction();
       //      myShepherd.beginDBTransaction();
       //      System.out.println("got here 13");
       //      // System.out.println("got here 14");
       //    } //end if consolidatedUserProjectsInWhichUserIsListedInUsers exists and has >0 elements
       //    // System.out.println("got here 15");
       // }//end if userToBeConsolidated null check
       // System.out.println("got here 16");
       // List<Organization> finalOrganizationsOfUserToRetain = userToRetain.getOrganizations();
       // System.out.println("finalOrganizationsOfUserToRetain is: " + finalOrganizationsOfUserToRetain.toString());


       //  System.out.println("got here y");
       //  if(userToRetain!=null && userToBeConsolidated!=null){
       //    List<Organization> organizationsOfUserToBeConsolidated = userToBeConsolidated.getOrganizations();
       //    List<Organization> organizationsOfUserToRetain = userToRetain.getOrganizations();
       //    System.out.println("got here 3");
       //    if(organizationsOfUserToBeConsolidated!=null && organizationsOfUserToBeConsolidated.size()>0){
       //      System.out.println("got here 4");
       //      System.out.println("size of organizationsOfUserToBeConsolidated is: " + organizationsOfUserToBeConsolidated.size());
       //      System.out.println("size of organizationsOfUserToRetain is: " + organizationsOfUserToRetain.size());
       //      for(int i=0; i<organizationsOfUserToBeConsolidated.size(); i++){
       //        System.out.println("got here 5");
       //        Organization currentOrganization = organizationsOfUserToBeConsolidated.get(i);
       //        System.out.println("got here 6");
       //        if(currentOrganization!=null){
       //          if(!organizationsOfUserToRetain.contains(currentOrganization)){
       //            System.out.println("got here 7");
       //            currentOrganization.updateModified();
       //            myShepherd.commitDBTransaction();
       //            myShepherd.beginDBTransaction();
       //            organizationsOfUserToRetain.add(currentOrganization);
       //            myShepherd.commitDBTransaction();
       //            myShepherd.beginDBTransaction();
       //            System.out.println("got here 8");
       //          }
       //          // userToBeConsolidated.getOrganizations
       //          //TODO User.setOrganizations
       //          List<User> currentMembers = currentOrganization.getMembers();
       //          System.out.println("got here 9");
       //        }//end if currentOrganization!=null
       //        System.out.println("got here 10");
       //      } //end for loop of organizationsOfUserToBeConsolidated
       //      System.out.println("got here 11");
       //      userToRetain.setOrganizations(organizationsOfUserToRetain);
       //      System.out.println("got here 12");
       //      userToBeConsolidated.setOrganizations(new ArrayList<Organization>());
       //      System.out.println("got here 13");
       //
       //      System.out.println("got here 14");
       //    } //end if consolidatedUserProjectsInWhichUserIsListedInUsers exists and has >0 elements
       //    System.out.println("got here 15");
       // }//end if userToBeConsolidated and userToRetain null check
       // System.out.println("got here 16");
          // System.out.println("got here and organizationsOfUserToRetain size is: " + organizationsOfUserToRetain.size());
          // Organization shamOrg = new Organization("sham");
          // System.out.println("create new organization sham");
          // organizationsOfUserToRetain.add(shamOrg);
          // System.out.println("added sham and new size of organizationsOfUserToRetain is: " + organizationsOfUserToRetain.size());
        // }else{
        //   System.out.println("oh she null");
        // }

        // Encounter targetEncounter = myShepherd.getEncounter("967cd20d-6170-4334-a51e-6b409f30d130");
        // if(targetEncounter!=null){
        //   System.out.println("targetEncounter is: " + targetEncounter.toString());
        //   List<User> photographers = targetEncounter.getPhotographers();
        //   if(photographers!=null){
        //     System.out.println("photographers are: " + photographers.toString());
        //   }
        // }
        // Encounter targetEncounter2 = myShepherd.getEncounter("833c7343-eda9-4bb8-917e-27c4bf7d1059");
        // if(targetEncounter2!=null){
        //   System.out.println("targetEncounter2 is: " + targetEncounter2.toString());
        //   List<User> photographers = targetEncounter2.getPhotographers();
        //   if(photographers!=null){
        //     System.out.println("photographers are: " + photographers.toString());
        //   }
        // }

        // // User mfisher1 = myShepherd.getUserByUUID("411704e5-045e-45c7-a14c-53d8ada46bc7");
        // User mfisher1_only_collaborator = myShepherd.getUserByUUID("07271b48-2d8d-4c93-a195-678204eb33b5");
        // if(targetEncounter!=null && mfisher1_only_collaborator!=null){
        //   System.out.println("targetEncounter is: " + targetEncounter.toString());
        //   System.out.println("mfisher1_only_collaborator is: " + mfisher1_only_collaborator.toString());
        //   targetEncounter.addSubmitter(mfisher1_only_collaborator);
        //   System.out.println("added submitter");
        // }

        // Occurrence targetOccurrence = myShepherd.getOccurrence("a3fa9ea3-dfd4-4553-bcb9-6bf7fe018a88");
        // System.out.println("targetOccurrence is: " + targetOccurrence.toString());
        // User mfisher1_only_collaborator = myShepherd.getUserByUUID("07271b48-2d8d-4c93-a195-678204eb33b5");
        // List<User> newUsersToAdd = new ArrayList<User>();
        // if(targetOccurrence!=null && mfisher1_only_collaborator!=null){
        //   newUsersToAdd.add(mfisher1_only_collaborator);
        //   System.out.println("newUsersToAdd is: " + newUsersToAdd.toString());
        //   targetOccurrence.setSubmitters(newUsersToAdd);
        //   targetOccurrence.setInformOthers(newUsersToAdd);
        // }
        myShepherd.commitDBTransaction();
      	myShepherd.beginDBTransaction();


        // User userToRetain = myShepherd.getUserByUUID("702df060-0151-49f3-834a-4c3cd383c961");//"702df060-0151-49f3-834a-4c3cd383c961");
        // User userToBeConsolidated = myShepherd.getUserByUUID("d9ff86dd-88b5-4de8-aeaf-ea161b9e41e2");//"0fa7dc0b-107e-43e0-942d-cc2326f09036");  //d9ff86dd-88b5-4de8-aeaf-ea161b9e41e2 = craig o'neil
        //
        // List<User> similarUsers = UserConsolidate.getSimilarUsers(userToBeConsolidated, myShepherd.getPM());
        // System.out.println("similarUsers are: " + similarUsers.toString());


      }finally{
        myShepherd.rollbackDBTransaction();
      	myShepherd.closeDBTransaction();
      }

    %>
<jsp:include page="../footer.jsp" flush="true"/>
