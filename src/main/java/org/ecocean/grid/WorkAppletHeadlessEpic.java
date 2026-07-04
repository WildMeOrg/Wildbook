package org.ecocean.grid;

import java.io.*;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLConnection;

import javax.net.ssl.HttpsURLConnection;

import java.util.ArrayList;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.Random;
import java.util.Vector;
import java.util.zip.GZIPInputStream;
import java.util.zip.GZIPOutputStream;

/**
 * Headless grid client for distributed Modified Groth pattern matching.
 * Polls a Wildbook server for scan work items, executes comparisons locally,
 * and sends results back. Runs as a standalone Java process.
 *
 * Usage: java WorkAppletHeadlessEpic <server-url> [num-processors]
 *   server-url     - Full URL of the Wildbook server (e.g., https://www.whaleshark.org)
 *   num-processors - Number of threads to use (default: available processors)
 */
public class WorkAppletHeadlessEpic {
    private int numComparisons = 0;
    private int numMatches = 0;
    private static final String VERSION = "1.4";
    private static final long POLL_INTERVAL_MS = 10000;
    private static final long ERROR_BACKOFF_MS = 30000;
    private static final int MAX_CONSECUTIVE_ERRORS = 10;
    private static final int CONNECTION_TIMEOUT_MS = 30000;
    private static final int READ_TIMEOUT_MS = 60000;

    public static ArrayList<String> urlArray = new ArrayList<String>();
    private volatile boolean running = true;

    // polling heartbeat thread
    AppletHeartbeatThread hb;

    public WorkAppletHeadlessEpic() {}

    public static void main(String[] args) {
        System.out.println("Starting WorkAppletHeadlessEpic v" + VERSION);

        if (args.length < 1) {
            System.err.println("Usage: java WorkAppletHeadlessEpic <server-url> [num-processors]");
            System.err.println("  server-url     - Full URL (e.g., https://www.whaleshark.org)");
            System.err.println("  num-processors - Thread count (default: available cores)");
            System.exit(1);
        }

        urlArray.add(args[0]);
        System.out.println("Performing matching for server: " + args[0]);

        int numProcessors = Runtime.getRuntime().availableProcessors();
        if (args.length >= 2) {
            try {
                numProcessors = Integer.parseInt(args[1]);
            } catch (NumberFormatException e) {
                System.out.println("Invalid processor count '" + args[1] +
                    "', using default: " + numProcessors);
            }
        }

        WorkAppletHeadlessEpic worker = new WorkAppletHeadlessEpic();

        // Register shutdown hook for graceful cleanup
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            System.out.println("Shutdown signal received, stopping...");
            worker.running = false;
        }));

        worker.getGoing(numProcessors);
    }

    public void getGoing(int numProcessors) {
        String holdEncNumber = "";
        long startTime = System.currentTimeMillis();

        // set up the random identifier for this "node"
        String nodeID = "Grid_" + Math.abs(new Random().nextInt());
        int consecutiveErrors = 0;

        System.out.println();
        System.out.println("***Welcome to sharkGrid!***");
        System.out.println("...I have " + numProcessors + " processor(s) to work with...");
        System.out.println("...Node ID: " + nodeID);

        int groupSize = 10 * numProcessors;
        int serverIndex = 0;

        // start the heartbeat
        hb = new AppletHeartbeatThread(nodeID, numProcessors, urlArray.get(serverIndex), VERSION);

        while (running) {
            HttpURLConnection httpCon = null;
            try {
                long currentTime = System.currentTimeMillis();
                long runningMinutes = (currentTime - startTime) / 60000;

                ScanWorkItem swi = new ScanWorkItem();
                Vector workItems = new Vector();
                Vector workItemResults = new Vector();
                ObjectInputStream inputFromServlet = null;
                boolean successfulConnect = false;
                boolean hasWork = false;

                // ---- Phase 1: Get work items from server ----
                try {
                    System.out.println("\n\nLooking for work... (running " +
                        runningMinutes + " min, " + numComparisons + " comparisons done)");

                    String encNumParam = "&newEncounterNumber=" + holdEncNumber;
                    URL u = new URL(urlArray.get(serverIndex) +
                        "/scanAppletSupport?version=" + VERSION + "&nodeIdentifier=" + nodeID +
                        "&action=getWorkItemGroup" + encNumParam + "&groupSize=" +
                        groupSize + "&numProcessors=" + numProcessors);

                    httpCon = openConnection(u, urlArray.get(serverIndex));
                    httpCon.setConnectTimeout(CONNECTION_TIMEOUT_MS);
                    httpCon.setReadTimeout(READ_TIMEOUT_MS);
                    httpCon.setDoInput(true);
                    httpCon.setDoOutput(true);
                    httpCon.setUseCaches(false);
                    httpCon.setRequestProperty("Content-type", "application/octet-stream");

                    inputFromServlet = new ObjectInputStream(new BufferedInputStream(
                        new GZIPInputStream(httpCon.getInputStream())));

                    workItems = (Vector) inputFromServlet.readObject();
                    if (workItems != null && workItems.size() > 0) {
                        swi = (ScanWorkItem) workItems.get(0);
                        successfulConnect = true;
                        hasWork = swi.getTotalWorkItemsInTask() > 0;
                    }
                    consecutiveErrors = 0;
                } catch (EOFException e) {
                    // empty response, no work available
                    consecutiveErrors = 0;
                } catch (Exception ioe) {
                    consecutiveErrors++;
                    System.err.println("Connection error (" + consecutiveErrors + "/" +
                        MAX_CONSECUTIVE_ERRORS + "): " + ioe.getMessage());
                    if (consecutiveErrors >= MAX_CONSECUTIVE_ERRORS) {
                        System.err.println("Too many consecutive errors, backing off...");
                        consecutiveErrors = 0;
                        safeSleep(ERROR_BACKOFF_MS * 3);
                    } else {
                        safeSleep(ERROR_BACKOFF_MS);
                    }
                    continue;
                } finally {
                    closeQuietly(inputFromServlet);
                    disconnectQuietly(httpCon);
                    httpCon = null;
                }

                if (!successfulConnect || !hasWork) {
                    if (!hasWork && successfulConnect) {
                        // Server responded but no work available
                        serverIndex = (serverIndex + 1) % urlArray.size();
                    }
                    safeSleep(POLL_INTERVAL_MS);
                    continue;
                }

                // ---- Phase 2: Execute comparisons ----
                int vectorSize = workItems.size();
                System.out.println("...received " + vectorSize + " comparisons to make...");

                ThreadPoolExecutor threadHandler = new ThreadPoolExecutor(
                    numProcessors, numProcessors, 0, TimeUnit.SECONDS,
                    new ArrayBlockingQueue<>(vectorSize + 10));

                for (int q = 0; q < vectorSize; q++) {
                    ScanWorkItem tempSWI = (ScanWorkItem) workItems.get(q);
                    try {
                        threadHandler.submit(new AppletWorkItemThread(tempSWI, workItemResults));
                    } catch (Exception e) {
                        System.err.println("Failed to submit work item " + q + ": " + e.getMessage());
                    }
                }

                threadHandler.shutdown();
                // Use proper blocking wait instead of busy-wait spin loop
                boolean completed = threadHandler.awaitTermination(30, TimeUnit.MINUTES);
                if (!completed) {
                    System.err.println("WARNING: Thread pool timed out after 30 minutes, " +
                        threadHandler.getCompletedTaskCount() + "/" + vectorSize + " completed");
                    threadHandler.shutdownNow();
                }
                System.out.println("...all threads done! (" +
                    workItemResults.size() + " results)...");

                // ---- Phase 3: Send results back ----
                int resultsSize = workItemResults.size();
                if (resultsSize > 0) {
                    boolean sent = sendResults(urlArray.get(serverIndex), nodeID, workItemResults);
                    if (sent) {
                        numComparisons += resultsSize;
                        System.out.println("Total comparisons completed: " + numComparisons);
                    }
                }

            } catch (OutOfMemoryError oome) {
                System.err.println("OUT OF MEMORY! Clearing state and requesting GC...");
                oome.printStackTrace();
                System.gc();
                safeSleep(ERROR_BACKOFF_MS);
            } catch (Exception e) {
                System.err.println("Unexpected error in main loop: " + e.getMessage());
                e.printStackTrace();
                safeSleep(ERROR_BACKOFF_MS);
            }
        } // end while

        System.out.println("WorkAppletHeadlessEpic shutting down. Total comparisons: " +
            numComparisons);
        if (hb != null) {
            hb.setFinished(true);
        }
    }

    /**
     * Send completed work item results back to the server.
     */
    private boolean sendResults(String serverUrl, String nodeID, Vector results) {
        HttpURLConnection finishConnection = null;
        ObjectOutputStream outputStream = null;
        InputStream inputStream = null;

        try {
            URL finishUrl = new URL(serverUrl +
                "/ScanWorkItemResultsHandler2?group=true&nodeIdentifier=" + nodeID);
            System.out.println("Sending " + results.size() + " results to: " + finishUrl);

            finishConnection = openConnection(finishUrl, serverUrl);
            finishConnection.setDoInput(true);
            finishConnection.setDoOutput(true);
            finishConnection.setUseCaches(false);
            finishConnection.setConnectTimeout(CONNECTION_TIMEOUT_MS);
            finishConnection.setReadTimeout(READ_TIMEOUT_MS);
            finishConnection.setRequestProperty("Content-Type", "application/octet-stream");

            outputStream = new ObjectOutputStream(
                new BufferedOutputStream(new GZIPOutputStream(
                    finishConnection.getOutputStream())));
            outputStream.writeObject(results);
            outputStream.flush();
            outputStream.close();
            outputStream = null;

            inputStream = finishConnection.getInputStream();
            BufferedReader reader = new BufferedReader(new InputStreamReader(inputStream));
            String line = reader.readLine();
            reader.close();
            inputStream.close();
            inputStream = null;

            if ("success".equals(line)) {
                System.out.println("Results accepted by server.");
                return true;
            } else {
                System.err.println("Server rejected results: " + line);
                return false;
            }
        } catch (Exception e) {
            System.err.println("Failed to send results: " + e.getMessage());
            e.printStackTrace();
            return false;
        } finally {
            closeQuietly(outputStream);
            closeQuietly(inputStream);
            disconnectQuietly(finishConnection);
        }
    }

    /**
     * Open an HTTP(S) connection based on the server URL scheme.
     */
    private static HttpURLConnection openConnection(URL url, String serverUrl) throws IOException {
        if (serverUrl.startsWith("https")) {
            return (HttpsURLConnection) url.openConnection();
        } else {
            return (HttpURLConnection) url.openConnection();
        }
    }

    private static void safeSleep(long millis) {
        try {
            Thread.sleep(millis);
        } catch (InterruptedException ie) {
            Thread.currentThread().interrupt();
        }
    }

    private static void closeQuietly(Closeable c) {
        if (c != null) {
            try { c.close(); } catch (Exception ignored) {}
        }
    }

    private static void disconnectQuietly(HttpURLConnection con) {
        if (con != null) {
            try { con.disconnect(); } catch (Exception ignored) {}
        }
    }
}
