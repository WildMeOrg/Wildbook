package org.ecocean;

import java.io.File;
import java.util.List;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.ServletContextListener;
import javax.servlet.ServletContext;

import org.ecocean.*;
import org.ecocean.media.LocalAssetStore;
import org.ecocean.servlet.ServletUtilities;


// This little collection of functions will be called on webapp start. static Its main purpose is to check that certain
// global variables are initialized, and do so if necessary.

public class StartupWildbook {

  // this function is automatically run on webapp init
  // it is attached via web.xml's <listener></listener>
  public static void initializeWildbook(HttpServletRequest request, Shepherd myShepherd) {

    ensureTomcatUserExists(myShepherd);
    ensureAssetStoreExists(request, myShepherd);

  }

  public static void ensureTomcatUserExists(Shepherd myShepherd) {
    List<User> users = myShepherd.getAllUsers();
    if(users.size()==0){
      System.out.println("");
      String salt=ServletUtilities.getSalt().toHex();
      String hashedPassword=ServletUtilities.hashAndSaltPassword("tomcat123", salt);

      User newUser=new User("tomcat",hashedPassword,salt);
      myShepherd.getPM().makePersistent(newUser);
      System.out.println("StartupWildbook: No users found on Wildbook. Creating tomcat user account...");
      myShepherd.commitDBTransaction();
      List<Role> roles=myShepherd.getAllRoles();
      if(roles.size()==0){

        myShepherd.beginDBTransaction();
        System.out.println("Creating tomcat roles...");

        Role newRole1=new Role("tomcat","admin");
        newRole1.setContext("context0");
        myShepherd.getPM().makePersistent(newRole1);
        Role newRole4=new Role("tomcat","destroyer");
        newRole4.setContext("context0");
        myShepherd.getPM().makePersistent(newRole4);

        Role newRole7=new Role("tomcat","rest");
        newRole7.setContext("context0");
        myShepherd.getPM().makePersistent(newRole7);
        myShepherd.commitDBTransaction();
        System.out.println("Creating tomcat user account...");
        }
    }

  }

  public static void ensureAssetStoreExists(HttpServletRequest request, Shepherd myShepherd) {

    String rootDir = request.getSession().getServletContext().getRealPath("/");
    String dataDir = ServletUtilities.dataDir("context0", rootDir);
    String urlLoc = "http://" + CommonConfiguration.getURLLocation(request);
    String dataUrl = urlLoc + "/wildbook_data_dir";

    LocalAssetStore as = new LocalAssetStore("Default Local AssetStore", new File(dataDir).toPath(), dataUrl, true);
    myShepherd.getPM().makePersistent(as);

  }


}
