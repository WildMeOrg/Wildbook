/*
 * The Shepherd Project - A Mark-Recapture Framework
 * Copyright (C) 2011 Jason Holmberg
 *
 * This program is free software; you can redistribute it and/or
 * modify it under the terms of the GNU General Public License
 * as published by the Free Software Foundation; either version 2
 * of the License, or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program; if not, write to the Free Software
 * Foundation, Inc., 51 Franklin Street, Fifth Floor, Boston, MA  02110-1301, USA.
 */

package org.ecocean.grid;


import java.io.*;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLConnection;
import java.util.Random;
import java.util.Vector;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;


public class WorkAppletHeadless {


  private int numComparisons = 0;

  //number of potential matches made by this node
  private int numMatches = 0;
  private static String version = "1.2";

  //thread pool handling comparison threads
  ThreadPoolExecutor threadHandler;

  public static final String thisURLRoot = "localhost:8080/wildbook-5.4.0-DEVELOPMENT";

  //polling heartbeat thread
  AppletHeartbeatThread hb;

  //constructor
  public WorkAppletHeadless() {
  }


  /*
  *Obtain a connection to the server to send/receive serialized content
  */
  private URLConnection getConnection(String action, String newEncounterNumber, int groupSize, String nodeID, int numProcessors) throws IOException {
    String encNumParam = "";
    if (!newEncounterNumber.equals("")) {
      encNumParam = "&newEncounterNumber=" + newEncounterNumber;
    }
    URL u = new URL("http://" + thisURLRoot + "/scanAppletSupport?version=" + version + "&nodeIdentifier=" + nodeID + "&action=" + action + encNumParam + "&groupSize=" + groupSize + "&numProcessors=" + numProcessors);
    System.out.println("...Using nodeIdentifier: " + nodeID + "...");
    URLConnection con = u.openConnection();
    con.setDoInput(true);
    con.setDoOutput(true);
    con.setUseCaches(false);
    con.setDefaultUseCaches(false);
    con.setRequestProperty("Content-type", "application/octet-stream");
    con.setAllowUserInteraction(false);
    return con;
  }

  /*
  *Send serialized content to the server.
  */
  private void sendObject(ObjectOutputStream con, Object obj) throws IOException {
    System.out.println("     : Sending returned results...");
    //new modification
    ObjectOutputStream out = con;
    out.reset();
    if (obj != null) {
      out.writeObject(obj);
    }
    out.close();
    System.out.println("     : Transmission complete. Waiting for response...");
  }


  public static void main(String args[]) {


    WorkAppletHeadless a = new WorkAppletHeadless();
    a.getGoing();
  }


  public void getGoing() {


    String holdEncNumber = "";

    //check whether this applet is working on a specific task
    String targeted = "";


    //server connection
    URLConnection con;


    //whether this is a right-side pattern scan or a left-side
    boolean rightScan = false;

    //set up the random identifier for this "node"
    Random ran = new Random();
    int nodeIdentifier = ran.nextInt();
    String nodeID = "Murdoch_" + (new Integer(nodeIdentifier)).toString();


    //if targeted on a specific task, show a percentage bar for progress
    int b = 0;


    boolean repeat = true;

    try {

      //let's allocate an object to handle an OutOfMemoryError
      URL recoverURL = new URL("http://" + thisURLRoot + "/encounters/sharkGrid.jsp?groupSize=1&autorestart=true" + targeted + "&numComparisons=" + numComparisons);

      //check the number of processors
      Runtime rt = Runtime.getRuntime();
      int numProcessors = rt.availableProcessors();


      System.out.println();
      System.out.println();
      System.out.println("***Welcome to sharkGrid!***");
      System.out.println("...I have " + numProcessors + " processor(s) to work with...");

      //set the start groupSize used-i.e. number of scans to tackle in the first returned Vector of work items
      //this is actually ignored and controlled by the server
      int groupSize = 10 * numProcessors;

      //start the heartbeat that periodically lets' the grid know it's out there
      String sNodeIdentifier = nodeID;

      //start the heartbeat yo!
      hb = new AppletHeartbeatThread(sNodeIdentifier, numProcessors, thisURLRoot, version);


      //repeating comparison work of the applet
      while (repeat) {

        //cleanup anything previously in scope
        System.gc();


        try {


          //set up our thread processor for each comparison thread
          ArrayBlockingQueue abq = new ArrayBlockingQueue(500);
          threadHandler = new ThreadPoolExecutor(numProcessors, numProcessors, 0, TimeUnit.SECONDS, abq);

          boolean successfulConnect = true;

          ScanWorkItem swi = new ScanWorkItem();
          Vector workItems = new Vector();
          Vector workItemResults = new Vector();
          try {
            //let's get some work from the server
            System.out.println("\n\nLooking for some work to do...");
            con = getConnection("getWorkItemGroup", holdEncNumber, groupSize, nodeID, numProcessors);
            ObjectInputStream inputFromServlet = new ObjectInputStream(con.getInputStream());
            workItems = (Vector) inputFromServlet.readObject();
            swi = (ScanWorkItem) workItems.get(0);
            successfulConnect = true;
            inputFromServlet.close();
            inputFromServlet = null;
          } catch (Exception ioe) {
            ioe.printStackTrace();
            successfulConnect = false;
            //Thread.sleep(90000);
            System.exit(0);
          }


          if (successfulConnect) {

            //if no work is returned, there are three options
            if (swi.getTotalWorkItemsInTask() <= 0) {

              try {


                //waiting for last workItem to finish elsewhere, wait quietly and check later
                if (swi.getTotalWorkItemsInTask() == -1) {

                  //Thread.sleep(15000);
                  System.exit(0);

                }

                //no work to do, sleepy time
                else {


                }

              } catch (NullPointerException npe) {
                //generic, non-specific applet operation
                //just sleep because there are no other tasks to do
                //if(!getParameter("encounter").equals("null")) status.setValue(0);
                System.out.println("...nothing to do...sleeping...");
                groupSize = 10;
                //Thread.sleep(90000);
                System.exit(0);
              }

            }

            //otherwise, if we've got work to do
            //kick it like Poison!
            else {


              int vectorSize = workItems.size();
              System.out.println("...received " + vectorSize + " comparisons to make...");

              //spawn the thread for each comparison
              for (int q = 0; q < vectorSize; q++) {
                ScanWorkItem tempSWI = (ScanWorkItem) workItems.get(q);

                //we also pass in workItemResults, which is a threadsafe vector of the results returned from each thread
                threadHandler.submit(new AppletWorkItemThread(tempSWI, workItemResults));
              }


              //block until all threads are done
              long vSize = vectorSize;
              while (threadHandler.getCompletedTaskCount() < vSize) {
              }

              //check the results and make variable changes as needed
              int resultsSize = workItemResults.size();
              for (int d = 0; d < resultsSize; d++) {
                b++;
                try {

                  //if(!getParameter("encounter").equals("null")) status.setValue(swi.getWorkItemsCompleteInTask()+d);
                } catch (NullPointerException npe) {
                }
                ScanWorkItemResult swir = (ScanWorkItemResult) workItemResults.get(d);
                MatchObject thisResult = swir.getResult();
                if ((thisResult.getMatchValue() * thisResult.getAdjustedMatchValue()) >= 115) {
                  numMatches++;
                }
              }


              //if we have results to send, send 'em!
              if (resultsSize > 0) {

                URL finishScan = new URL("http://" + thisURLRoot + "/scanWorkItemResultsHandler2?" + targeted + "group=true&nodeIdentifier=" + nodeID);
                URLConnection finishConnection = finishScan.openConnection();

                // inform the connection that we will send output and accept input
                finishConnection.setDoInput(true);
                finishConnection.setDoOutput(true);

                // Don't use a cached version of URL connection.
                finishConnection.setUseCaches(false);
                finishConnection.setDefaultUseCaches(false);

                // Specify the content type that we will send binary data
                finishConnection.setRequestProperty("Content-Type", "application/octet-stream");

                // send the results Vector to the servlet using serialization
                ObjectOutputStream outputToFinalServlet = new ObjectOutputStream(finishConnection.getOutputStream());

                sendObject(outputToFinalServlet, workItemResults);

                outputToFinalServlet.close();
                outputToFinalServlet = null;

                InputStream inputStreamFromServlet = finishConnection.getInputStream();
                BufferedReader in = new BufferedReader(new InputStreamReader(inputStreamFromServlet));
                String line = in.readLine();
                in.close();
                System.out.println("     : Checkin response received...");
                inputStreamFromServlet.close();
                in = null;
                inputStreamFromServlet = null;
                if (line.equals("success")) {
                  System.out.println("Successful transmit to and return code from the servlet.");

                  numComparisons += (workItemResults.size());
                  recoverURL = new URL("http://" + thisURLRoot + "/encounters/sharkGrid.jsp?groupSize=1&autorestart=true" + targeted + "&numComparisons=" + numComparisons);


                } else {
                  System.out.println("Unsuccessful results transmit error!");
                }

              }
            }

          }

          //cleanup thread handlers
          abq = null;
          threadHandler = null;
          workItems = null;
          workItemResults = null;
          swi = null;

        } catch (OutOfMemoryError oome) {
          oome.printStackTrace();
          hb.setFinished(true);
          System.exit(0);

        } catch (Exception e) {
          e.printStackTrace();

        }


      } //end while

    } catch (MalformedURLException mue) {
      System.out.println("I hit a MalformedURLException while trying to create the recoverURL for OutOfMemoryErrors");
      mue.printStackTrace();
      System.exit(0);

    }

  }        //end getGoing method


}
  
  
