package org.ecocean;

import java.io.File;
import java.util.List;
import java.util.Iterator;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.ServletContextListener;
import javax.servlet.ServletContextEvent;
import javax.servlet.ServletContext;
import java.net.URL;

import org.ecocean.*;
import org.ecocean.media.LocalAssetStore;
import org.ecocean.servlet.ServletUtilities;
import org.ecocean.identity.IBEISIA;

import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.Executors;
import java.lang.Runnable;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.ScheduledFuture;

// This little collection of functions will be called on webapp start. static Its main purpose is to check that certain
// global variables are initialized, and do so if necessary.

public class StartupWildbook implements ServletContextListener {

  // this function is automatically run on webapp init
  // it is attached via web.xml's <listener></listener>
  public static void initializeWildbook(HttpServletRequest request, Shepherd myShepherd) {

    ensureTomcatUserExists(myShepherd);
    ensureAssetStoreExists(request, myShepherd);
    ensureProfilePhotoKeywordExists(myShepherd);

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
    String urlLoc = request.getScheme()+"://" + CommonConfiguration.getURLLocation(request);
    String dataUrl = urlLoc + "/wildbook_data_dir";
    myShepherd.beginDBTransaction();
    LocalAssetStore as = new LocalAssetStore("Default Local AssetStore", new File(dataDir).toPath(), dataUrl, true);
    myShepherd.getPM().makePersistent(as);
    myShepherd.commitDBTransaction();

  }
  
  public static void ensureProfilePhotoKeywordExists(Shepherd myShepherd) {
    int numKeywords=myShepherd.getNumKeywords();
    if(numKeywords==0){
      String readableName = "ProfilePhoto";
      Keyword newword = new Keyword(readableName);
      myShepherd.storeNewKeyword(newword);

    }

  }

    //these get run with each tomcat startup/shutdown, if web.xml is configured accordingly.  see, e.g. https://stackoverflow.com/a/785802
    public void contextInitialized(ServletContextEvent sce) {
        System.out.println("* StartupWildbook initialized called");
        ServletContext context = sce.getServletContext(); 
        URL res = null;
        try {
            res = context.getResource("/");
        } catch (Exception ex) {}
System.out.println("  StartupWildbook.contextInitialized() res = " + res);
        //this is very hacky but lets it prime IA only during tomcat restart (not .war deploy)
        //if ((res == null) || !res.toString().equals("jndi:/localhost/")) return;
        if ((res != null) && res.toString().equals("jndi:/localhost/")) {
            IBEISIA.primeIA();
        }

        File qdir = ScheduledQueue.setQueueDir(context);
        if (qdir == null) {
            System.out.println("+ WARNING: queue service NOT started: could not determine queue directory");
        } else {
            System.out.println("+ queue service starting; dir = " + qdir.toString());
            final ScheduledExecutorService schedExec = Executors.newScheduledThreadPool(5);
            ScheduledFuture schedFuture = schedExec.scheduleWithFixedDelay(new Runnable() {
                int count = 0;
                public void run() {
                    ++count;
                    boolean cont = ScheduledQueue.checkQueue();
                    System.out.println("==== ScheduledQueue run [count " + count + "]; queueDir=" + ScheduledQueue.getQueueDir() + "; continue = " + cont + " ====");
                    if (!cont) {
                        System.out.println(":::: ScheduledQueue shutdown via discontinue signal ::::");
                        schedExec.shutdown();
                    }
                }
            },
            10,  //initial delay  ... TODO these could be configurable, obvs
            10,  //period delay *after* execution finishes
            TimeUnit.SECONDS);
            System.out.println("---- about to awaitTermination() ----");
            try {
                schedExec.awaitTermination(5000, TimeUnit.MILLISECONDS);
            } catch (java.lang.InterruptedException ex) {
                System.out.println("WARNING: queue interrupted! " + ex.toString());
            }
            System.out.println("==== schedExec.shutdown() called, apparently");
        }

    }

    public void contextDestroyed(ServletContextEvent sce) {
        System.out.println("* StartupWildbook destroyed called");
    }
}


