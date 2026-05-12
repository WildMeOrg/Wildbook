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

import org.ecocean.grid.GridManager;
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
    // ml-service migration v2 §commit #11: handle to the WBIA-registration
    // poller so contextDestroyed can shut it down cleanly. Without this the
    // executor leaks across redeploys and a new poll thread starts on top
    // of any zombie that survived undeploy.
    private static volatile ScheduledExecutorService wbiaRegisterExecutor;

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
            loadMatchGraphOrRebuild(sContext, context);
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
        // ml-service migration v2 §commit #11: DB-backed WBIA registration
        // polling. Replaces v1's plan to use a separate "wbiaRegister"
        // FileQueue with manual reconcile servlet. The polling thread reads
        // Annotation.wbiaRegistered/wbiaRegisterAttempts directly so state
        // survives JVM restarts without queue infrastructure.
        try {
            startWbiaRegistrationPollingThread(context);
        } catch (Exception e) {
            e.printStackTrace();
        }
        // ml-service migration v2 §commit #12: at-most-once delivery on the
        // FileQueue means a JVM crash mid-detection can leave a MediaAsset
        // in processing-mlservice forever. Once at startup, walk assets
        // stuck past a threshold and re-enqueue them.
        try {
            runStaleMlServiceReconciliation(context);
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

    /**
     * ml-service migration v2 §commit #11. Background polling thread that
     * registers ml-service-created annotations with WBIA so HotSpotter is
     * available on demand for them. State is on the Annotation row itself
     * ({@code wbiaRegistered} + {@code wbiaRegisterAttempts}); no separate
     * queue or reconcile servlet is needed.
     *
     * <p>Per cycle (~30s): query annotations with
     * {@code wbiaRegistered == false AND wbiaRegisterAttempts < MAX},
     * up to a small batch limit. For each, call
     * {@link org.ecocean.ia.plugin.WildbookIAM#sendAnnotationsForceId} in a
     * per-annotation Shepherd transaction (so one slow WBIA call blocks
     * only one slot, not the entire batch). On success: set
     * {@code wbiaRegistered = TRUE} (terminal). On failure: increment
     * {@code wbiaRegisterAttempts}; the next cycle retries until cutoff.</p>
     *
     * <p>Legacy annotations are excluded from the query because the DDL
     * migration in {@code archive/sql/ml_service_idempotency.sql} backfills
     * their {@code wbiaRegistered} to {@code TRUE} on deploy.</p>
     */
    private static final int WBIA_REGISTER_MAX_ATTEMPTS = 10;
    private static final int WBIA_REGISTER_BATCH_LIMIT = 50;
    private static final long WBIA_REGISTER_POLL_SECONDS = 30L;

    private static void startWbiaRegistrationPollingThread(final String context) {
        // Refuse to start a second poller if one is already running; this
        // also matters when contextInitialized fires more than once for
        // the same JVM (e.g., context reload).
        if (wbiaRegisterExecutor != null) {
            System.out.println(
                "WARN: startWbiaRegistrationPollingThread() called with existing executor; skipping");
            return;
        }
        System.out.println("STARTING: StartupWildbook.startWbiaRegistrationPollingThread()");
        ScheduledExecutorService ses = Executors.newSingleThreadScheduledExecutor(
            new java.util.concurrent.ThreadFactory() {
                @Override public Thread newThread(Runnable r) {
                    Thread t = new Thread(r, "WbiaRegistrationPoll");
                    t.setDaemon(true);
                    return t;
                }
            });
        ses.scheduleAtFixedRate(new Runnable() {
            @Override public void run() {
                try {
                    runWbiaRegistrationPoll(context);
                } catch (Throwable t) {
                    // Catch Throwable here: ScheduledExecutorService silently
                    // stops re-firing the task on any uncaught exception.
                    // We want the thread to keep ticking through transient
                    // failures.
                    System.out.println("WARN: WbiaRegistrationPoll uncaught: " + t);
                    t.printStackTrace();
                }
            }
        }, WBIA_REGISTER_POLL_SECONDS, WBIA_REGISTER_POLL_SECONDS, TimeUnit.SECONDS);
        wbiaRegisterExecutor = ses;
    }

    private static void runWbiaRegistrationPoll(String context) {
        // Phase 1: query the pending list (Shepherd open, no network). Capture
        // annotation IDs and release before any WBIA calls.
        java.util.List<String> pendingIds = new ArrayList<String>();
        Shepherd shep = new Shepherd(context);
        shep.setAction("StartupWildbook.WbiaRegistrationPoll.fetch");
        shep.beginDBTransaction();
        try {
            javax.jdo.Query q = shep.getPM().newQuery(
                org.ecocean.Annotation.class,
                "wbiaRegistered == false && wbiaRegisterAttempts < "
                + WBIA_REGISTER_MAX_ATTEMPTS);
            q.setOrdering("wbiaRegisterAttempts ascending");
            q.setRange(0, WBIA_REGISTER_BATCH_LIMIT);
            @SuppressWarnings("unchecked")
            java.util.List<org.ecocean.Annotation> pending =
                (java.util.List<org.ecocean.Annotation>) q.execute();
            if (pending != null) {
                for (org.ecocean.Annotation a : pending) pendingIds.add(a.getId());
            }
            q.closeAll();
            shep.commitDBTransaction();
        } catch (Exception ex) {
            System.out.println("WARN: WbiaRegistrationPoll fetch failed: " + ex);
            shep.rollbackDBTransaction();
        } finally {
            shep.closeDBTransaction();
        }

        if (pendingIds.isEmpty()) return;
        System.out.println("[INFO] WbiaRegistrationPoll: " + pendingIds.size() + " pending");

        // Phase 2: per-annotation registration. Each runs in its own short
        // Shepherd tx so a slow WBIA call blocks only that one slot.
        for (String annId : pendingIds) {
            registerOneAnnotationWithWbia(context, annId);
        }
    }

    /**
     * ml-service migration v2 §commit #12. Once-at-startup pass that
     * detects MediaAssets stuck in {@code processing-mlservice} past a
     * threshold (worker presumably died mid-detection due to the
     * at-most-once FileQueue semantics) and re-enqueues them through
     * the normal routing layer.
     *
     * <p>Safe under any active worker because:</p>
     * <ul>
     *   <li>The re-check inside reconcileOneStaleAsset uses the fresh
     *       Shepherd's current state; if another worker has already
     *       progressed the asset, the status will no longer be
     *       {@code processing-mlservice} and the reconciler skips.</li>
     *   <li>MlServiceProcessor's Phase 4 idempotency check (composite of
     *       mediaAsset + predictModelId + bboxKey + thetaKey) prevents
     *       duplicate annotation creation if the dead worker had already
     *       persisted some results.</li>
     *   <li>On re-enqueue, {@code MediaAsset.setDetectionStatus} bumps
     *       REVISION so this reconciler does not re-pick the same asset
     *       on a subsequent restart.</li>
     * </ul>
     *
     * <p>Threshold default: 1 hour. Longer than any healthy detection
     * job's worst-case duration; short enough that operators don't wait
     * days for recovery.</p>
     */
    private static final long STALE_MLSERVICE_THRESHOLD_MS = 60L * 60L * 1000L;

    private static void runStaleMlServiceReconciliation(String context) {
        System.out.println(
            "STARTING: StartupWildbook.runStaleMlServiceReconciliation()");
        long revisionCutoff = System.currentTimeMillis() - STALE_MLSERVICE_THRESHOLD_MS;
        java.util.List<String> staleIds = fetchStaleMlServiceAssetIds(context, revisionCutoff);
        if (staleIds.isEmpty()) {
            System.out.println(
                "[INFO] StaleMlServiceReconciliation: no stuck assets older than threshold");
            return;
        }
        System.out.println("[INFO] StaleMlServiceReconciliation: " + staleIds.size() +
            " stuck assets older than " + STALE_MLSERVICE_THRESHOLD_MS + "ms");
        for (String maId : staleIds) {
            reconcileOneStaleAsset(context, maId);
        }
    }

    private static java.util.List<String> fetchStaleMlServiceAssetIds(String context,
        long revisionCutoff) {
        java.util.List<String> ids = new ArrayList<String>();
        Shepherd shep = new Shepherd(context);
        shep.setAction("StartupWildbook.StaleMlServiceReconciliation.fetch");
        shep.beginDBTransaction();
        try {
            javax.jdo.Query q = shep.getPM().newQuery(
                org.ecocean.media.MediaAsset.class,
                "detectionStatus == 'processing-mlservice' && revision < "
                + revisionCutoff);
            @SuppressWarnings("unchecked")
            java.util.List<org.ecocean.media.MediaAsset> stale =
                (java.util.List<org.ecocean.media.MediaAsset>) q.execute();
            if (stale != null) {
                for (org.ecocean.media.MediaAsset ma : stale) {
                    ids.add(String.valueOf(ma.getId()));
                }
            }
            q.closeAll();
            shep.commitDBTransaction();
        } catch (Exception ex) {
            System.out.println(
                "WARN: StaleMlServiceReconciliation fetch failed: " + ex);
            shep.rollbackDBTransaction();
        } finally {
            shep.closeDBTransaction();
        }
        return ids;
    }

    private static void reconcileOneStaleAsset(String context, String maId) {
        Shepherd shep = new Shepherd(context);
        shep.setAction("StartupWildbook.StaleMlServiceReconciliation." + maId);
        shep.beginDBTransaction();
        try {
            org.ecocean.media.MediaAsset ma = shep.getMediaAsset(maId);
            if (ma == null) {
                shep.commitDBTransaction();
                return;
            }
            // Re-check: another worker may have progressed it since fetch.
            if (!"processing-mlservice".equals(ma.getDetectionStatus())) {
                shep.commitDBTransaction();
                return;
            }
            // Derive taxonomy.
            java.util.List<org.ecocean.Taxonomy> taxies = ma.getTaxonomies(shep);
            org.ecocean.Taxonomy taxy = null;
            if (taxies != null && !taxies.isEmpty()) taxy = taxies.get(0);

            org.ecocean.IAJsonProperties iac = org.ecocean.IAJsonProperties.iaConfig();
            boolean stillVectorRouted = iac != null && taxy != null
                && iac.getActiveMlServiceConfigs(taxy) != null;
            if (!stillVectorRouted) {
                // Species is no longer configured for ml-service (or no taxy).
                // Flip to error so the operator sees it; don't re-enqueue.
                ma.setDetectionStatus("error");
                System.out.println("[INFO] StaleMlServiceReconciliation: " + maId +
                    " no longer vector-routed; marking error");
                shep.commitDBTransaction();
                return;
            }
            // Clear the stuck status before re-routing. The routing layer
            // and MlServiceProcessor will set processing-mlservice again
            // on resume. (Also bumps REVISION via setDetectionStatus, so
            // a subsequent reconcile cycle won't re-pick this asset.)
            ma.setDetectionStatus(null);

            java.util.List<org.ecocean.media.MediaAsset> single =
                new ArrayList<org.ecocean.media.MediaAsset>();
            single.add(ma);
            org.ecocean.ia.IA.intakeMediaAssetsOneSpecies(shep, single, taxy, null);
            shep.commitDBTransaction();
            System.out.println("[INFO] StaleMlServiceReconciliation: re-enqueued " + maId);
        } catch (Exception ex) {
            System.out.println("WARN: StaleMlServiceReconciliation registerOne failed for " +
                maId + ": " + ex);
            shep.rollbackDBTransaction();
        } finally {
            shep.closeDBTransaction();
        }
    }

    /**
     * Phase A/B/C split per Codex c11 fix-review.
     * <ul>
     *   <li>Phase A: Shepherd open, re-check state, build DTO, close.
     *   <li>Phase B: no Shepherd held; WBIA HTTP via
     *       {@link org.ecocean.ia.plugin.WildbookIAM#registerOneByDto}.
     *   <li>Phase C: Shepherd open, re-load, persist outcome, close.
     * </ul>
     * Ineligible annotations (missing media asset, missing acmId, fails
     * {@code validForIdentification}) are parked at MAX_ATTEMPTS so they
     * fall out of the polling query.
     */
    private static void registerOneAnnotationWithWbia(String context, String annId) {
        // ---- Phase A: load DTO under a short transaction. ----
        org.ecocean.ia.plugin.WildbookIAM.WbiaRegisterRequest dto =
            loadWbiaRegisterDto(context, annId);
        if (dto == null) return;  // ineligible / already registered / parked

        // ---- Phase B: no Shepherd held; call WBIA. ----
        org.ecocean.ia.plugin.WildbookIAM iam =
            new org.ecocean.ia.plugin.WildbookIAM(context);
        org.ecocean.ia.plugin.WildbookIAM.WbiaRegisterOutcome outcome =
            iam.registerOneByDto(dto);

        // ---- Phase C: persist outcome under a short transaction. ----
        persistWbiaRegisterResult(context, annId, outcome);
    }

    /**
     * Phase A. Returns a detached DTO ready for Phase B, or null if the
     * annotation does not need (or cannot get) a Phase-B network call.
     * Null cases: missing annotation, already registered, parked at max
     * attempts, or ineligible (missing media asset / acmId / bbox / etc.).
     * Ineligible annotations are parked here so they stop being polled.
     */
    private static org.ecocean.ia.plugin.WildbookIAM.WbiaRegisterRequest
        loadWbiaRegisterDto(String context, String annId) {
        Shepherd shep = new Shepherd(context);
        shep.setAction("StartupWildbook.WbiaRegistrationPoll.loadDto." + annId);
        shep.beginDBTransaction();
        try {
            org.ecocean.Annotation ann = shep.getAnnotation(annId);
            if (ann == null) {
                shep.commitDBTransaction();
                return null;
            }
            if (Boolean.TRUE.equals(ann.getWbiaRegistered())) {
                shep.commitDBTransaction();
                return null;
            }
            if (ann.getWbiaRegisterAttempts() >= WBIA_REGISTER_MAX_ATTEMPTS) {
                shep.commitDBTransaction();
                return null;
            }
            // Eligibility checks. Any failure here is permanent for this
            // annotation under its current state, so park it.
            org.ecocean.media.MediaAsset ma = ann.getMediaAsset();
            String reason = null;
            if (ma == null) reason = "missing media asset";
            else if (!Util.stringExists(ma.getAcmId())) reason = "media asset has no acmId";
            else if (!Util.stringExists(ann.getId())) reason = "annotation has no id";
            else if (!org.ecocean.identity.IBEISIA.validForIdentification(ann))
                reason = "validForIdentification returned false (bbox/iaClass/etc.)";
            if (reason != null) {
                System.out.println("WARN: WbiaRegistrationPoll parking " + annId +
                    " (ineligible: " + reason + ")");
                ann.setWbiaRegisterAttempts(WBIA_REGISTER_MAX_ATTEMPTS);
                shep.commitDBTransaction();
                return null;
            }
            // Resolve the individual name now while the Shepherd is open;
            // Phase B has no DB access.
            String name = ann.findIndividualId(shep);
            // Copy bbox into a fresh array so the DTO is fully detached.
            int[] bb = ann.getBbox();
            int[] bbCopy = (bb == null) ? null : new int[] { bb[0], bb[1], bb[2], bb[3] };
            org.ecocean.ia.plugin.WildbookIAM.WbiaRegisterRequest dto =
                new org.ecocean.ia.plugin.WildbookIAM.WbiaRegisterRequest(
                    ann.getId(), ma.getAcmId(), bbCopy, ann.getTheta(),
                    ann.getIAClass(), name);
            shep.commitDBTransaction();
            return dto;
        } catch (Exception ex) {
            System.out.println("WARN: WbiaRegistrationPoll loadWbiaRegisterDto failed for " +
                annId + ": " + ex);
            shep.rollbackDBTransaction();
            return null;
        } finally {
            shep.closeDBTransaction();
        }
    }

    /**
     * Phase C. Re-loads the annotation and writes the outcome of the
     * Phase-B network call. On terminal-success outcomes the annotation
     * is marked registered; on retryable outcomes the attempts counter
     * is bumped and we WARN-log when we hit the abandonment threshold.
     */
    private static void persistWbiaRegisterResult(String context, String annId,
        org.ecocean.ia.plugin.WildbookIAM.WbiaRegisterOutcome outcome) {
        Shepherd shep = new Shepherd(context);
        shep.setAction("StartupWildbook.WbiaRegistrationPoll.persist." + annId);
        shep.beginDBTransaction();
        try {
            org.ecocean.Annotation ann = shep.getAnnotation(annId);
            if (ann == null) {
                shep.commitDBTransaction();
                return;
            }
            if (Boolean.TRUE.equals(ann.getWbiaRegistered())) {
                // Some other path flipped it while Phase B ran; respect that.
                shep.commitDBTransaction();
                return;
            }
            switch (outcome) {
                case REGISTERED_OK:
                case REGISTERED_ALREADY_PRESENT:
                    // Always honor a success outcome even if the row was
                    // parked by a racing poller: stuck-at-attempts==MAX
                    // would otherwise become permanent.
                    ann.setWbiaRegistered(Boolean.TRUE);
                    break;
                case NETWORK_FAIL:
                case RESPONSE_BAD:
                default:
                    if (ann.getWbiaRegisterAttempts() >= WBIA_REGISTER_MAX_ATTEMPTS) {
                        // Already parked by another path; do not increment past MAX.
                        break;
                    }
                    ann.incrementWbiaRegisterAttempts();
                    if (ann.getWbiaRegisterAttempts() >= WBIA_REGISTER_MAX_ATTEMPTS) {
                        System.out.println("WARN: WbiaRegistrationPoll abandoning " + annId +
                            " after " + WBIA_REGISTER_MAX_ATTEMPTS +
                            " attempts (last outcome=" + outcome + "); will not retry");
                    }
                    break;
            }
            shep.commitDBTransaction();
        } catch (Exception ex) {
            System.out.println("WARN: WbiaRegistrationPoll persistWbiaRegisterResult failed for " +
                annId + ": " + ex);
            shep.rollbackDBTransaction();
        } finally {
            shep.closeDBTransaction();
        }
    }

    public void contextDestroyed(ServletContextEvent sce) {
        ServletContext sContext = sce.getServletContext();
        String context = "context0";

        System.out.println("* StartupWildbook destroyed called for: " +
            servletContextInfo(sContext));

        if (CommonConfiguration.useSpotPatternRecognition(context)) {
            saveMatchGraph(sContext, context);
        }
        // Stop the WBIA poller first so it does not race teardown of
        // Shepherd / IndexingManager / QueueUtil while a poll cycle is in
        // flight. The shutdown is bounded (5s awaitTermination).
        shutdownWbiaRegisterExecutor();
        AnnotationLite.cleanup(sContext, context);
        QueueUtil.cleanup();
        MetricsBot.cleanup();
        AcmIdBot.cleanup();
        IndexingManagerFactory.getIndexingManager().shutdown();
    }

    // ml-service migration v2 §commit #11 fix-pass. The polling executor
    // was previously held only in a local variable, which meant redeploys
    // could leak a zombie thread that re-armed on next contextInitialized.
    private static void shutdownWbiaRegisterExecutor() {
        ScheduledExecutorService ses = wbiaRegisterExecutor;
        if (ses == null) return;
        wbiaRegisterExecutor = null;
        System.out.println("STOPPING: StartupWildbook.wbiaRegisterExecutor");
        ses.shutdown();
        try {
            if (!ses.awaitTermination(5L, TimeUnit.SECONDS)) ses.shutdownNow();
        } catch (InterruptedException ie) {
            ses.shutdownNow();
            Thread.currentThread().interrupt();
        }
    }

    public static void createMatchGraph() {
        System.out.println("Entering createMatchGraph StartupWildbook method.");
        ThreadPoolExecutor es = SharkGridThreadExecutorService.getExecutorService();
        es.execute(new MatchGraphCreationThread());
    }

    /**
     * Try loading the matchGraph from disk cache. If the cache exists and loads
     * successfully, use it directly. Otherwise fall back to the full DB rebuild
     * via MatchGraphCreationThread.
     */
    public static void loadMatchGraphOrRebuild(ServletContext sContext, String context) {
        try {
            String dataDir = CommonConfiguration.getDataDirectory(sContext, context).getAbsolutePath();
            String cacheFile = GridManager.getCacheFilePath(dataDir);
            if (new File(cacheFile).exists() && GridManager.cacheRead(cacheFile)) {
                System.out.println("INFO: matchGraph loaded from cache.");
                return;
            }
        } catch (Exception e) {
            System.out.println("WARNING: Could not load matchGraph cache, rebuilding from DB: " +
                e.getMessage());
        }
        createMatchGraph();
    }

    public static void saveMatchGraph(ServletContext sContext, String context) {
        try {
            String dataDir = CommonConfiguration.getDataDirectory(sContext, context).getAbsolutePath();
            String cacheFile = GridManager.getCacheFilePath(dataDir);
            GridManager.cacheWrite(cacheFile);
        } catch (Exception e) {
            System.out.println("WARNING: Could not save matchGraph cache: " + e.getMessage());
        }
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
