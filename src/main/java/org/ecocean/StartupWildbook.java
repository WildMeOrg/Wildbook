package org.ecocean;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;
import java.net.URL;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.ServletContext;
import javax.servlet.ServletContextEvent;
import javax.servlet.ServletContextListener;

import org.ecocean.grid.MatchGraphCreationThread;
import org.ecocean.grid.SharkGridThreadExecutorService;
import org.ecocean.ia.IAPluginManager;
import org.ecocean.identity.IBEISIA;
import org.ecocean.media.AssetStore;
import org.ecocean.media.AssetStoreConfig;
import org.ecocean.media.LocalAssetStore;

import org.ecocean.media.MediaAsset;

import org.ecocean.queue.*;
import org.ecocean.scheduled.WildbookScheduledTask;
import org.ecocean.servlet.IAGateway;
import org.ecocean.servlet.ServletUtilities;

import java.util.concurrent.ThreadPoolExecutor;

import org.ecocean.shepherd.core.Shepherd;
import org.ecocean.shepherd.core.ShepherdProperties;
import org.json.JSONObject;

import java.io.IOException;
import java.lang.Runnable;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

// This little collection of functions will be called on webapp start. static Its main purpose is to check that certain
// global variables are initialized, and do so if necessary.

public class StartupWildbook implements ServletContextListener {
    // this function is automatically run on webapp init
    // it is attached via web.xml's <listener></listener>
    public static void initializeWildbook(HttpServletRequest request, Shepherd myShepherd) {
        ensureTomcatUserExists(myShepherd);
        ensureAssetStoreExists(request, myShepherd);
        ensureServerInfo(myShepherd);
        ensureProfilePhotoKeywordExists(myShepherd);
    }

    /*
        right now this *only* uses SERVER_URL env variable TODO: make this work in the more general case where it isnt e.g.
           CommonConfiguration.checkServerInfo(myShepherd, request)
     */
    public static void ensureServerInfo(Shepherd myShepherd) {
        String urlString = System.getenv("SERVER_URL");

        if (urlString == null) return;
        URL url = null;
        try {
            url = new URL(urlString);
        } catch (java.net.MalformedURLException mal) {
            System.out.println("StartupWildbook.ensureServerInfo failed on " + urlString + ": " +
                mal.toString());
            return;
        }
        JSONObject info = new JSONObject();
        info.put("scheme", url.getProtocol());
        info.put("serverName", url.getHost());
        int port = url.getPort();
        if (port > 0) info.put("serverPort", port);
        info.put("contextPath", url.getFile());
        // if (!isValidServerName(req.getServerName())) return false;  //dont update if we got wonky name like "localhost"
        info.put("timestamp", System.currentTimeMillis());
        info.put("context", myShepherd.getContext());
        CommonConfiguration.setServerInfo(myShepherd, info);
        System.out.println("StartupWildbook.ensureServerInfo updated server info to: " +
            info.toString());
        updateAssetStore(myShepherd); // piggyback here, thus we ensure we have a *good* SERVER_URL
    }

    // note: this (currently) is ONLY for docker-based deployment (hence reliance on SERVER_URL)
    public static void updateAssetStore(Shepherd myShepherd) {
        String urlString = System.getenv("SERVER_URL");

        if (urlString == null) return;
        AssetStore as = AssetStore.getDefault(myShepherd); // should exist either (or both) cuz of ensureAssetStore and/or sql
        if (as == null) return;
        AssetStoreConfig newConfig = new AssetStoreConfig();
        newConfig.put("root", "/usr/local/tomcat/webapps/wildbook_data_dir"); // docker-specific
        newConfig.put("webroot", urlString + "/wildbook_data_dir");
        System.out.println("StartupWildbook.updateAssetStore() changing " + as + " config from [" +
            as.getConfig() + "] to [" + newConfig + "]");
        as.setConfig(newConfig);
    }

    public static void ensureTomcatUserExists(Shepherd myShepherd) {
        List<User> users = myShepherd.getAllUsers();

        if (users.size() == 0) {
            System.out.println("");
            String salt = ServletUtilities.getSalt().toHex();
            String hashedPassword = ServletUtilities.hashAndSaltPassword("tomcat123", salt);
            User newUser = new User("tomcat", hashedPassword, salt);
            myShepherd.getPM().makePersistent(newUser);
            System.out.println(
                "StartupWildbook: No users found on Wildbook. Creating tomcat user account...");
            myShepherd.commitDBTransaction();
            List<Role> roles = myShepherd.getAllRoles();
            if (roles.size() == 0) {
                myShepherd.beginDBTransaction();
                System.out.println("Creating tomcat roles...");

                Role newRole1 = new Role("tomcat", "admin");
                newRole1.setContext("context0");
                myShepherd.getPM().makePersistent(newRole1);
                Role newRole1a = new Role("tomcat", "orgAdmin");
                newRole1a.setContext("context0");
                myShepherd.getPM().makePersistent(newRole1a);
                Role newRole2 = new Role("tomcat", "researcher");
                newRole2.setContext("context0");
                myShepherd.getPM().makePersistent(newRole2);
                Role newRole3 = new Role("tomcat", "machinelearning");
                newRole3.setContext("context0");
                myShepherd.getPM().makePersistent(newRole3);
                Role newRole5 = new Role("tomcat", "rest");
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
        String urlLoc = request.getScheme() + "://" + CommonConfiguration.getURLLocation(request);
        String dataUrl = urlLoc + "/wildbook_data_dir";

        myShepherd.beginDBTransaction();
        LocalAssetStore as = new LocalAssetStore("Default Local AssetStore",
            new File(dataDir).toPath(), dataUrl, true);
        myShepherd.getPM().makePersistent(as);
        myShepherd.commitDBTransaction();
    }

    public static void ensureProfilePhotoKeywordExists(Shepherd myShepherd) {
        int numKeywords = myShepherd.getNumKeywords();

        if (numKeywords == 0) {
            String readableName = "ProfilePhoto";
            Keyword newword = new Keyword(readableName);
            myShepherd.storeNewKeyword(newword);
        }
    }

    // these get run with each tomcat startup/shutdown, if web.xml is configured accordingly.  see, e.g. https://stackoverflow.com/a/785802
    public void contextInitialized(ServletContextEvent sce) {
        ServletContext sContext = sce.getServletContext();
        String context = "context0";

        System.out.println(new org.joda.time.DateTime() + " ### StartupWildbook initialized for: " +
            servletContextInfo(sContext));
        if (skipInit(sce, null)) {
            System.out.println("- SKIPPED initialization due to skipInit()");
            return;
        }
        Setting.initialize(context);
        // initialize the plugin (instances)
        IAPluginManager.initPlugins(context);
        // this should be handling all plugin startups
        IAPluginManager.startup(sce);
        // NOTE! this is whaleshark-specific (and maybe other spot-matchers?) ... should be off on any other trees
        if (CommonConfiguration.useSpotPatternRecognition(context)) {
            createMatchGraph();
        }
        // TODO: set strategy for the following (genericize starting "all" consumers, make configurable, move to WildbookIAM.startup, move to plugins, or other)
        startIAQueues(context);
        MetricsBot.startServices(context);
        AcmIdBot.startServices(context);
        AnnotationLite.startup(sContext, context);
        OpenSearch.unsetActiveIndexingBackground(); // since tomcat is just starting, these reset to false
        OpenSearch.unsetActiveIndexingForeground();
        OpenSearch.backgroundStartup(context);

        try {
            startWildbookScheduledTaskThread(context);
        } catch (Exception e) {
            e.printStackTrace();
        }
        // initialize the MarkedIndividual names cache
        // moved initNamesCache here
        Shepherd myShepherd = new Shepherd(context);
        myShepherd.setAction("MarkedIndividual.initNamesCache");
        myShepherd.beginDBTransaction();
        try {
            boolean cached = org.ecocean.MarkedIndividual.initNamesCache(myShepherd);
        } catch (Exception f) {
            f.printStackTrace();
        } finally { myShepherd.rollbackAndClose(); }
    }

    private void startIAQueues(String context) {
        class IAMessageHandler extends QueueMessageHandler {
            public boolean handler(String msg) {
                try {
                    org.ecocean.servlet.IAGateway.processQueueMessage(msg); // yeah we need to move this somewhere else...
                } catch (Exception ex) {
                    System.out.println("WARNING: IAMessageHandler processQueueMessage() threw " +
                        ex.toString());
                    ex.printStackTrace();
                }
                return true;
            }
        }
        
        //instructions on what to do if a message is published to the acmid queue
        //which handles ACM ID registration for MediaAssets
        class AcmIdMessageHandler extends QueueMessageHandler {
            public boolean handler(String mediaAssetID) {
            	Shepherd myShepherd = new Shepherd(context);
            	myShepherd.setAction("AcmIdMessageHandler.handler."+mediaAssetID);
            	myShepherd.beginDBTransaction();
            	try {
            		MediaAsset asset=myShepherd.getMediaAsset(mediaAssetID);
            		if(asset!=null) {
		                ArrayList<MediaAsset> fixMe = new ArrayList<MediaAsset>();
	            		fixMe.add(asset);
		                IBEISIA.sendMediaAssetsNew(fixMe, context);
		                myShepherd.updateDBTransaction();
            		}
            	}
            	//RuntimeExceptions include an array of timeout and connectivitivity issues
            	//indicating WBIA may be overloaded or restarting
            	//therefore this exception includes a simple sleep function to pause ACM ID registration
            	//to give WBIA time to restart or be less busy.
            	//This implementation is temporary until ACM ID registration is removed entirely
                catch (java.lang.RuntimeException ex) {
                    System.out.println("\r\n\r\nWARNING: AcmIdMessageHandler processQueueMessage() threw " +
                        ex.toString()+"\r\n\r\n");
                    ex.printStackTrace();
                    
                    long timeoutMilliseconds=60000;
                    Properties props = ShepherdProperties.getProperties("queue.properties", "", context);
                    if(props!=null && props.getProperty("timeoutMilliseconds")!=null) {
                    	String millis = props.getProperty("timeoutMilliseconds");
                    	Long millisAsLong = Long.getLong(millis);
                    	if(millisAsLong!=null)timeoutMilliseconds=millisAsLong.longValue();
                    }
                    
                    try {
                    	Thread.sleep(timeoutMilliseconds);
                    	Queue acmIdQueue=IAGateway.getAcmIdQueue(context);
                    	acmIdQueue.publish(mediaAssetID);
                    }
                    catch(Exception ioe) {
                    	ioe.printStackTrace();
                    }
                    return false;
                }
                catch (Exception ex) {
                    System.out.println("\r\n\r\nWARNING: AcmIdMessageHandler processQueueMessage() threw " +
                        ex.toString()+"\r\n\r\n");
                    ex.printStackTrace();
                    
                    try {
                    	Queue acmIdQueue=IAGateway.getAcmIdQueue(context);
                    	acmIdQueue.publish(mediaAssetID);
                    }
                    catch(Exception ioe) {
                    	ioe.printStackTrace();
                    }
                    return false;
                }
            	finally {
            		myShepherd.rollbackAndClose();
            	}
                return true;
            }
        }

        class IACallbackMessageHandler extends QueueMessageHandler {
            public boolean handler(String msg) {
                try {
                    org.ecocean.servlet.IAGateway.processCallbackQueueMessage(msg); // yeah we need to move this somewhere else...
                } catch (Exception ex) {
                    System.out.println(
                        "WARNING: IACallbackMessageHandler processCallbackQueueMessage() threw " +
                        ex.toString());
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
        } catch (IOException ex) {
            System.out.println("+ ERROR: IA queue startup exception: " + ex.toString());
        }
        Queue queueCallback = null;
        try {
            queueCallback = QueueUtil.getBest(context, "IACallback");
        } catch (IOException ex) {
            System.out.println("+ ERROR: IACallback queue startup exception: " + ex.toString());
        }
        Queue detectionQ = null;
        try {
            detectionQ = QueueUtil.getBest(context, "detection");
        } catch (IOException ex) {
            System.out.println("+ ERROR: detection queue startup exception: " + ex.toString());
        }
        //MediaAsset ACM ID registration queue
        Queue acmidQ = null;
        try {
            acmidQ = QueueUtil.getBest(context, "acmid");
        } catch (IOException ex) {
            System.out.println("+ ERROR: acmid queue startup exception: " + ex.toString());
        }
        if ((queue == null) || (queueCallback == null) || (detectionQ == null) || (acmidQ == null)) {
            System.out.println("+ WARNING: IA queue service(s) NOT started");
            return;
        }
        IAMessageHandler qh = new IAMessageHandler();
        try {
            queue.consume(qh);
            System.out.println("+ StartupWildbook.startIAQueues() queue.consume() started on " +
                queue.toString());
        } catch (IOException iox) {
            System.out.println("+ StartupWildbook.startIAQueues() queue.consume() FAILED on " +
                queue.toString() + ": " + iox.toString());
        }
        IACallbackMessageHandler qh2 = new IACallbackMessageHandler();
        try {
            queueCallback.consume(qh2);
            System.out.println(
                "+ StartupWildbook.startIAQueues() queueCallback.consume() started on " +
                queueCallback.toString());
        } catch (IOException iox) {
            System.out.println(
                "+ StartupWildbook.startIAQueues() queueCallback.consume() FAILED on " +
                queueCallback.toString() + ": " + iox.toString());
        }
        IAMessageHandler qh3 = new IAMessageHandler();
        try {
            detectionQ.consume(qh3);
            System.out.println(
                "+ StartupWildbook.startIAQueues() detectionQ.consume() started on " +
                detectionQ.toString());
        } catch (IOException iox) {
            System.out.println("+ StartupWildbook.startIAQueues() detectionQ.consume() FAILED on " +
                detectionQ.toString() + ": " + iox.toString());
        }
        //ACM ID queue handler
        AcmIdMessageHandler qh4 = new AcmIdMessageHandler();
        try {
            acmidQ.consume(qh4);
            System.out.println(
                "+ StartupWildbook.startIAQueues() acmidQ.consume() started on " +
                acmidQ.toString());
        } catch (IOException iox) {
            System.out.println("+ StartupWildbook.startIAQueues() acmidQ.consume() FAILED on " +
                acmidQ.toString() + ": " + iox.toString());
        }
    }

    private static void startWildbookScheduledTaskThread(String context) {
        System.out.println("STARTING: StartupWildbook.startWildbookScheduledTaskThread()");
        ScheduledExecutorService ses = Executors.newSingleThreadScheduledExecutor();
        ses.scheduleAtFixedRate(new Runnable() {
            @Override public void run() {
                System.out.println("[INFO]: checking for scheduled tasks to execute...");
                Shepherd myShepherd = new Shepherd(context);
                myShepherd.setAction("WildbookScheduledTaskThread");
                try {
                    ArrayList<WildbookScheduledTask> scheduledTasks =
                    myShepherd.getAllIncompleteWildbookScheduledTasks();
                    for (WildbookScheduledTask scheduledTask : scheduledTasks) {
                        if (scheduledTask.isTaskEligibleForExecution()) {
                            scheduledTask.execute(myShepherd);
                        }
                    }
                } catch (Exception e) {
                    myShepherd.rollbackAndClose();
                    e.printStackTrace();
                }
                myShepherd.closeDBTransaction();
            }
        }, 0, 1, TimeUnit.HOURS);
    }

    public void contextDestroyed(ServletContextEvent sce) {
        ServletContext sContext = sce.getServletContext();
        String context = "context0";

        System.out.println("* StartupWildbook destroyed called for: " +
            servletContextInfo(sContext));

        AnnotationLite.cleanup(sContext, context);
        QueueUtil.cleanup();
        MetricsBot.cleanup();
        AcmIdBot.cleanup();
        IndexingManagerFactory.getIndexingManager().shutdown();
    }

    public static void createMatchGraph() {
        System.out.println("Entering createMatchGraph StartupWildbook method.");
        ThreadPoolExecutor es = SharkGridThreadExecutorService.getExecutorService();
        es.execute(new MatchGraphCreationThread());
    }

    public static boolean skipInit(ServletContextEvent sce, String extra) {
        ServletContext sc = sce.getServletContext();

/*   WARNING!  this bad hackery to try to work around "double deployment" ... yuck!
     see:  https://octopus.com/blog/defining-tomcat-context-paths
 */
        String fname = "/tmp/WB_SKIP_INIT" + ((extra == null) ? "" : "_" + extra);
        boolean skip = new File(fname).exists();

        System.out.println("++ StartupWildbook.skipInit() test on " + extra + " [" + fname +
            "] --> " + skip);
        return skip;
    }

    public static String servletContextInfo(ServletContext sc) {
        if (sc == null) return null;
        try {
            return sc.getServletContextName() + " [" + sc.getContextPath() + " via " +
                       sc.getRealPath("/") + "]";
        } catch (Exception ex) {
            System.out.println("WARNING: StartupWildbook.servletContextInfo() threw " +
                ex.toString());
            return "<unknown>";
        }
    }
    
    
    
}
