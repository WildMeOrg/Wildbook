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
import org.ecocean.queue.*;
import org.ecocean.ia.IA;
import org.ecocean.ia.IAPluginManager;
import org.ecocean.grid.MatchGraphCreationThread;
//import org.ecocean.grid.ScanTaskCleanupThread;
import org.ecocean.grid.SharkGridThreadExecutorService;
import org.ecocean.media.LocalAssetStore;
import org.ecocean.servlet.ServletUtilities;
import org.ecocean.identity.IBEISIA;

import java.util.concurrent.ThreadPoolExecutor;


import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.Executors;
import java.lang.Runnable;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.ScheduledFuture;

import java.util.concurrent.ThreadPoolExecutor;



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
        Role newRole2=new Role("tomcat","researcher");
        newRole2.setContext("context0");
        myShepherd.getPM().makePersistent(newRole2);
        Role newRole3=new Role("tomcat", "machinelearning");
        newRole3.setContext("context0");
        myShepherd.getPM().makePersistent(newRole3);
        Role newRole4=new Role("tomcat","destroyer");
        newRole4.setContext("context0");
        myShepherd.getPM().makePersistent(newRole4);
        Role newRole5=new Role("tomcat","rest");
        newRole5.setContext("context0");
        myShepherd.getPM().makePersistent(newRole5);
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
        ServletContext sContext = sce.getServletContext();
        String context = "context0";  //TODO ??? how????
        System.out.println(new org.joda.time.DateTime() + " ### StartupWildbook initialized for: " + servletContextInfo(sContext));
        if (skipInit(sce, null)) {
            System.out.println("- SKIPPED initialization due to skipInit()");
            return;
        }

        //initialize the plugin (instances)
        IAPluginManager.initPlugins(context);
        //this should be handling all plugin startups
        IAPluginManager.startup(sce);

        //NOTE! this is whaleshark-specific (and maybe other spot-matchers?) ... should be off on any other trees
        if (CommonConfiguration.useSpotPatternRecognition(context)) {
            createMatchGraph();
        }

        //TODO genericize starting "all" consumers ... configurable? how?  etc.
        // actually, i think we want to move this to WildbookIAM.startup() ... probably!!!
        startIAQueues(context); //TODO this should get moved to plugins!!!!  FIXME
        TwitterBot.startServices(context);
    }


    private void startIAQueues(String context) {
        class IAMessageHandler extends QueueMessageHandler {
            public boolean handler(String msg) {
                try {
                    org.ecocean.servlet.IAGateway.processQueueMessage(msg);  //yeah we need to move this somewhere else...
                } catch (Exception ex) {
                    System.out.println("WARNING: IAMessageHandler processQueueMessage() threw " + ex.toString());
                    ex.printStackTrace();
                }
                return true;
            }
        }
        class IACallbackMessageHandler extends QueueMessageHandler {
            public boolean handler(String msg) {
                try {
                    org.ecocean.servlet.IAGateway.processCallbackQueueMessage(msg);  //yeah we need to move this somewhere else...
                } catch (Exception ex) {
                    System.out.println("WARNING: IACallbackMessageHandler processCallbackQueueMessage() threw " + ex.toString());
                    ex.printStackTrace();
                }
                return true;
            }
        }

        if (!IBEISIA.iaEnabled()) {
            System.out.println("+ INFO: IA not enabled; IA queue service not started");
            return;
        }

        Queue queue = null;
        try {
            queue = QueueUtil.getBest(context, "IA");
        } catch (java.io.IOException ex) {
            System.out.println("+ ERROR: IA queue startup exception: " + ex.toString());
        }
        Queue queueCallback = null;
        try {
            queueCallback = QueueUtil.getBest(context, "IACallback");
        } catch (java.io.IOException ex) {
            System.out.println("+ ERROR: IACallback queue startup exception: " + ex.toString());
        }
        if ((queue == null) || (queueCallback == null)) {
            System.out.println("+ WARNING: IA queue service(s) NOT started");
            return;
        }

        IAMessageHandler qh = new IAMessageHandler();
        try {
            queue.consume(qh);
            System.out.println("+ StartupWildbook.startIAQueues() queue.consume() started on " + queue.toString());
        } catch (java.io.IOException iox) {
            System.out.println("+ StartupWildbook.startIAQueues() queue.consume() FAILED on " + queue.toString() + ": " + iox.toString());
        }
        IACallbackMessageHandler qh2 = new IACallbackMessageHandler();
        try {
            queueCallback.consume(qh2);
            System.out.println("+ StartupWildbook.startIAQueues() queueCallback.consume() started on " + queueCallback.toString());
        } catch (java.io.IOException iox) {
            System.out.println("+ StartupWildbook.startIAQueues() queueCallback.consume() FAILED on " + queueCallback.toString() + ": " + iox.toString());
        }
    }


    public void contextDestroyed(ServletContextEvent sce) {
        ServletContext sContext = sce.getServletContext();
        System.out.println("* StartupWildbook destroyed called for: " + servletContextInfo(sContext));
        QueueUtil.cleanup();
        TwitterBot.cleanup();
    }


    public static void createMatchGraph(){
      System.out.println("Entering createMatchGraph StartupWildbook method.");
      ThreadPoolExecutor es=SharkGridThreadExecutorService.getExecutorService();
      es.execute(new MatchGraphCreationThread());
    }

    public static boolean skipInit(ServletContextEvent sce, String extra) {
        ServletContext sc = sce.getServletContext();
        if ("".equals(sc.getContextPath())) {
            System.out.println("++ StartupWildbook.skipInit() skipping ROOT (empty string context path)");
            return true;
        }
        String fname = "/tmp/WB_SKIP_INIT" + ((extra == null) ? "" : "_" + extra);
        boolean skip = new File(fname).exists();
        System.out.println("++ StartupWildbook.skipInit() test on " + extra + " [" + fname + "] --> " + skip);
        return skip;
    }

/*  NOTE: this is back-burnered for now.... maybe it will be useful later?  cant quite figure out *when* tomcat is "double startup" problem... 
    //this is very hacky but is meant to be a way for us to make sure we arent just deploying.... TODO do this right????
    private static boolean properStartupResource(ServletContextEvent sce) {
        if (sce == null) return false;
        ServletContext context = sce.getServletContext(); 
        if (context == null) return false;
        URL res = null;
        try {
            res = context.getResource("/");
        } catch (Exception ex) {
            System.out.println("  ERROR: StartupWildbook.properStartupResource() .getResource() threw exception: " + ex);
            return false;
        }
System.out.println("  StartupWildbook.properStartupResource() res = " + res);
        if (res == null) return false;
        return res.toString().equals("jndi:/localhost/uptest/");
    }
*/


    public static String servletContextInfo(ServletContext sc) {
        if (sc == null) return null;
        try {
            return sc.getServletContextName() + " [" + sc.getContextPath() + " via " + sc.getRealPath("/") + "]";
        } catch (Exception ex) {
            System.out.println("WARNING: StartupWildbook.servletContextInfo() threw " + ex.toString());
            return "<unknown>";
        }
    }

}

