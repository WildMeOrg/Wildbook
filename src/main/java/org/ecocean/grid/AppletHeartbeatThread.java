package org.ecocean.grid;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.IOException;
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLConnection;
import javax.net.ssl.HttpsURLConnection;

/**
 * COmment
 *
 * @author jholmber more
 */
public class AppletHeartbeatThread implements Runnable, ISharkGridThread {
    public Thread heartbeatThread;
    public boolean finished = false;
    private int numProcessors = 1;
    private String appletID = "";
    private String rootURL = "";
    private String version = "";

    /**
     * Constructor to create a new thread object
     */
    public AppletHeartbeatThread(String appletID, int numProcessors, String thisURLRoot,
        String version) {
        this.numProcessors = numProcessors;
        this.appletID = appletID;
        heartbeatThread = new Thread(this, ("sharkGridNodeHeartbeat_" + appletID));
        this.rootURL = thisURLRoot;
        heartbeatThread.start();
        this.version = version;
    }

    /**
     * main method of the heartbeat thread
     */
    public void run() {
        // boolean ok2run=true;
        while (!finished) {
            try {
                sendHeartbeat(appletID);
                Thread.sleep(60000);
            } catch (Exception e) {
                System.out.println(
                    "     Heartbeat thread registering an exception while trying to sleep!");
            }
        }
    }

    public boolean isFinished() {
        return finished;
    }

    public void setFinished(boolean finish) {
        this.finished = finish;
    }

    private void sendHeartbeat(String appletID) {
        HttpURLConnection finishConnection = null;
        InputStream inputStreamFromServlet = null;
        BufferedReader in = null;

        try {
            URL u = new URL(rootURL + "/GridHeartbeatReceiver?nodeIdentifier=" + appletID +
                "&numProcessors=" + numProcessors + "&version=" + version);

            System.out.println("...sending heartbeat...thump...thump...to: " + u.toString());
            if (rootURL.startsWith("https")) {
                finishConnection = (HttpsURLConnection)u.openConnection();
            } else {
                finishConnection = (HttpURLConnection)u.openConnection();
            }
            finishConnection.setConnectTimeout(15000);
            finishConnection.setReadTimeout(15000);
            inputStreamFromServlet = finishConnection.getInputStream();
            in = new BufferedReader(new InputStreamReader(inputStreamFromServlet));
            String line = in.readLine();
        } catch (Exception e) {
            System.out.println("Heartbeat error: " + e.getMessage());
        } finally {
            try { if (in != null) in.close(); } catch (Exception ignored) {}
            try { if (inputStreamFromServlet != null) inputStreamFromServlet.close(); } catch (Exception ignored) {}
            if (finishConnection != null) finishConnection.disconnect();
        }
    }
}
