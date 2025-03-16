package org.ecocean;

import java.lang.Runnable;
import java.util.ArrayList;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.Collection;
import java.util.List;
import javax.jdo.Query;
import org.ecocean.identity.IBEISIA;
import org.ecocean.media.Feature;
import org.ecocean.media.MediaAsset;

/*
 *TBD
 *
 */
public class OpenSearchIndexingBot {
    static String context = "context0";


    // background workers
    public static boolean startServices(String context) {
        startCollector(context);
        return true;
    }

    // basically our "listener" daemon; but is more pull (poll?) than push so to speak.
    private static void startCollector(final String context) { 
        long interval = 1; // number seconds between runs
        long initialDelay = 1; // number seconds before first execution occurs

        System.out.println("+ OpenSearchIndexingBot.startCollector(" + context + ") starting.");
        final ScheduledExecutorService schedExec = Executors.newScheduledThreadPool(5);
        List<String> indexThese=IndexingManager.getIndexingQueue();
        for(String indexMe:indexThese) {
	        final ScheduledFuture schedFuture = schedExec.scheduleWithFixedDelay(
	        		new Runnable() {
	        			// DO WORK HERE
	        			public void run() {
	
	            	
	        			}
	        		}, 
	        		initialDelay, // initial delay
	        		interval, // period delay *after* execution finishes
	        		TimeUnit.SECONDS
	        ); // unit of delays above
        }
        System.out.println("Let's get AcmIdBot's time running.");
        try {
            schedExec.awaitTermination(5000, TimeUnit.MILLISECONDS);
        } catch (java.lang.InterruptedException ex) {
            System.out.println("WARNING: AcmIdBot.startCollector(" + context + ") interrupted: " +
                ex.toString());
        }
        System.out.println("+ OpenSearchIndexingBot.startCollector(" + context + ") backgrounded");
    }

    // mostly for ContextDestroyed in StartupWildbook.
    public static void cleanup() {
        System.out.println(
            "================ = = = = = = ===================== OpenSearchIndexingBot.cleanup() finished.");
    }


}
