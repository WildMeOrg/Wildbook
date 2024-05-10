package org.ecocean.servlet;

import org.ecocean.CommonConfiguration;
import org.ecocean.grid.GridManager;
import org.ecocean.grid.GridManagerFactory;
import org.ecocean.grid.GridNode;
import org.ecocean.grid.ScanTask;
import org.ecocean.grid.ScanWorkItemResult;
import org.ecocean.Shepherd;

import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.ServletConfig;
import javax.servlet.ServletException;

import java.io.BufferedInputStream;
import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.PrintWriter;
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.URL;

import javax.net.ssl.HttpsURLConnection;

import java.io.DataOutputStream;
import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.Vector;
import java.util.zip.GZIPInputStream;

public class ScanWorkItemResultsHandler extends HttpServlet {
    public void init(ServletConfig config)
    throws ServletException {
        super.init(config);
    }

    private static Object receiveObject(ObjectInputStream con)
    throws Exception {
        // System.out.println("scanresultsServlet: I am about to read in the byte array!");
        Object obj = new ScanWorkItemResult();

        // if(obj!=null){
        try {
            obj = (Object)con.readObject();
        } catch (java.lang.NullPointerException npe) {
            System.out.println(
                "scanResultsServlet received an empty results set...no matches whatsoever.");
            return obj;
        }
        // }
        // System.out.println("scanresultsServlet: I successfully read in the byte array!");

        return obj;
    }

    public void doGet(HttpServletRequest request, HttpServletResponse response)
    throws ServletException, IOException {
        doPost(request, response);
    }

    public void doPost(HttpServletRequest request, HttpServletResponse response)
    throws ServletException, IOException {
        // System.out.println("Starting scanWorkItemResultsHandler!!");

        // set up a shepherd for DB transactions
        String context = "context0";

        context = ServletUtilities.getContext(request);
        // Shepherd myShepherd = new Shepherd(context);
        String nodeIdentifier = request.getParameter("nodeIdentifier");
        GridManager gm = GridManagerFactory.getGridManager();
        ArrayList<String> affectedScanTasks = new ArrayList<String>();

        // double cutoff=2;
        String statusText = "success";
        // System.out.println("scanWorkItemResultsHandler: I am starting up.");
        response.setContentType("application/octet-stream");
        ObjectInputStream inputFromApplet = null;
        PrintWriter out = null;

        try {
            // get an input stream and Vector of results from the applet

            // inputFromApplet = new ObjectInputStream(request.getInputStream());
            inputFromApplet = new ObjectInputStream(new BufferedInputStream(new GZIPInputStream(
                request.getInputStream())));

            Vector returnedResults = new Vector();
            returnedResults = (Vector)receiveObject(inputFromApplet);
            inputFromApplet.close();

            // send response to applet
            try {
                // setup the servlet output
                response.setContentType("text/plain");
                out = response.getWriter();
                out.println(statusText);
                out.close();
            } catch (Exception e) {
                e.printStackTrace();
            }
            int returnedSize = 0;
            if (returnedResults != null) { returnedSize = returnedResults.size(); }
            // System.out.println(".....trying to check in # results:  "+returnedSize);

            // int numComplete = gm.getNumWorkItemsCompleteForTask(st.getUniqueNumber());
            // int numComplete=0;
            // int numGenerated = gm.getNumWorkItemsIncompleteForTask(st.getUniqueNumber());
            int numGenerated = 0;
            // int numTaskTot = numComplete + numGenerated;
            int numTaskTot = 0;
            // String scanTaskID="";
            ArrayList<String> tasksAddressed = new ArrayList<String>();
            for (int m = 0; m < returnedSize; m++) {
                ScanWorkItemResult wir = (ScanWorkItemResult)returnedResults.get(m);

                // if(!wir.getUniqueNumberTask().equals(scanTaskID)){
                // String scanTaskID=wir.getUniqueNumberTask();
                // }

                // String swiUniqueNum = wir.getUniqueNumberWorkItem();
                String taskNum = wir.getUniqueNumberTask();
                if (!tasksAddressed.contains(taskNum)) { tasksAddressed.add(taskNum); }
                // if(!affectedScanTasks.contains(taskNum)){affectedScanTasks.add(taskNum);}

                gm.checkinResult(wir);

                // auto-generate XML file of results if appropriate
            }
            int numTasksAddressed = tasksAddressed.size();
            for (int m = 0; m < numTasksAddressed; m++) {
                String scanTaskID = tasksAddressed.get(m);
                int numComplete = gm.getNumWorkItemsCompleteForTask(scanTaskID);
                // numGenerated = gm.getNumWorkItemsIncompleteForTask(scanTaskID);
                // numTaskTot = numComplete + numGenerated;
                // ScanTask st=myShepherd.getScanTask(scanTaskID);
                // if ((numComplete > 0) && (numComplete >= st.getNumComparisons())) {
                if ((numComplete > 0) && (gm.getNumWorkItemsIncompleteForTask(scanTaskID) == 0)) {
                    // if(!tasksCompleted.contains(scanTaskID)){

                    Shepherd myShepherd = new Shepherd(context);
                    myShepherd.setAction("ScanWorkItemResultsHandler.class");
                    myShepherd.beginDBTransaction();
                    try {
                        ScanTask st = myShepherd.getScanTask(scanTaskID);
                        if (!st.hasFinished()) { finishScanTask(scanTaskID, request); }
                    } catch (Exception e) {
                        e.printStackTrace();
                    } finally {
                        myShepherd.rollbackDBTransaction();
                        myShepherd.closeDBTransaction();
                    }
                    // tasksCompleted.add(scanTaskID);
                    // }
                }
            }
            if (returnedSize > 0) {
                GridNode node = gm.getGridNode(nodeIdentifier);
                if (node != null) {
                    node.checkin(returnedSize);
                }
                gm.incrementCompletedWorkItems(returnedSize);
            }
            // check if we can wrap up any of these tasks

            /*int numAffectedTasks=affectedScanTasks.size();
               myShepherd.beginDBTransaction();
               for(int i=0;i<numAffectedTasks;i++){

               String thisTaskNum=affectedScanTasks.get(i);
               ScanTask st=myShepherd.getScanTask(thisTaskNum);
               int numTotal = st.getNumComparisons();

               int numComplete = gm.getNumWorkItemsCompleteForTask(st.getUniqueNumber());

               int numGenerated = gm.getNumWorkItemsIncompleteForTask(st.getUniqueNumber());

               int numTaskTot = numComplete + numGenerated;

               if ((numComplete > 0) && (numComplete >= numTaskTot)) {

                //OK, now write it out
                //TBD


               }

               }
             */

            // myShepherd.rollbackDBTransaction();
            // myShepherd.closeDBTransaction();
        } catch (Exception e) {
            System.out.println("scanWorkItemResultsHandler registered the following error...");
            e.printStackTrace();
            inputFromApplet.close();
            // statusText="failure";
        }
    }

    private void finishScanTask(String scanTaskID, HttpServletRequest request) {
        // prep our streaming variables
        URL u = null;
        // InputStream inputStreamFromServlet=null;
        // BufferedReader in=null;
        HttpURLConnection finishConnection = null;
        DataOutputStream wr = null;

        try {
            u = new URL("https://" + CommonConfiguration.getURLLocation(request) + "/" +
                CommonConfiguration.getProperty("patternMatchingEndPointServletName",
                ServletUtilities.getContext(request)));
            String urlParameters = "number=" + scanTaskID;
            byte[] postData = urlParameters.getBytes(Charset.forName("UTF-8"));
            int postDataLength = postData.length;

            System.out.println("...writing out scanTask result: " + scanTaskID + " to URL: " +
                u.toString());
            if (request.getScheme().equals("https")) {
                finishConnection = (HttpsURLConnection)u.openConnection();
            } else {
                finishConnection = (HttpURLConnection)u.openConnection();
            }
            finishConnection.setDoOutput(true);
            finishConnection.setDoInput(true);
            finishConnection.setInstanceFollowRedirects(false);
            finishConnection.setRequestMethod("POST");
            finishConnection.setRequestProperty("Content-Type",
                "application/x-www-form-urlencoded");
            finishConnection.setRequestProperty("charset", "utf-8");
            finishConnection.setRequestProperty("Content-Length", Integer.toString(postDataLength));
            finishConnection.setUseCaches(false);
            wr = new DataOutputStream(finishConnection.getOutputStream());
            // wr.write( postData );
            wr.writeBytes(urlParameters);
            wr.flush();
            // wr.close();

            int responseCode = finishConnection.getResponseCode();

            // System.out.println("     Post parameters : " + urlParameters);
            System.out.println("     Response Code : " + responseCode);

            BufferedReader in = new BufferedReader(new InputStreamReader(
                finishConnection.getInputStream()));
            String inputLine;
            StringBuffer response = new StringBuffer();
            while ((inputLine = in.readLine()) != null) {
                response.append(inputLine);
            }
            in.close();

            // print result
            System.out.println("     " + response.toString());

            // process the returned line however needed
        } catch (MalformedURLException mue) {
            System.out.println(
                "!!!!!I hit a MalformedURLException in ScanWorkItemResultsHandler: " +
                u.toString());
            mue.printStackTrace();
        } catch (IOException ioe) {
            System.out.println("!!!!!I hit an IO exception in ScanWorkItemResultsHandler: " +
                u.toString());
            ioe.printStackTrace();
        } catch (Exception e) {
            System.out.println("!!!!!I hit an Exception in ScanWorkItemResultsHandler: " +
                u.toString());
            e.printStackTrace();
        } finally {
            try {
                wr.close();
                finishConnection.disconnect();
                finishConnection = null;
                wr = null;
                // inputStreamFromServlet.close();
                // in.close();
            } catch (Exception ex) {
                ex.printStackTrace();
            }
        }
    }
}
