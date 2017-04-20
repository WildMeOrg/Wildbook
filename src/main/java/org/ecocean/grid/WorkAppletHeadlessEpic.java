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
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLConnection;

import javax.net.ssl.HttpsURLConnection;

import java.util.ArrayList;
import java.util.GregorianCalendar;
import java.util.Random;
import java.util.Vector;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;


public class WorkAppletHeadlessEpic {


  private int numComparisons = 0;

  //number of potential matches made by this node
  private int numMatches = 0;
  private static String version = "1.3";

  //thread pool handling comparison threads
  ThreadPoolExecutor threadHandler;

  //public static String thisURLRoot = "https://www.whaleshark.org";
  public static ArrayList<String> urlArray = new ArrayList<String>(); 
  
  
  
  //polling heartbeat thread
  AppletHeartbeatThread hb;

  //constructor
  public WorkAppletHeadlessEpic() {
  }


  /*
  *Obtain a connection to the server to send/receive serialized content

  private URLConnection getConnection(String action, String newEncounterNumber, int groupSize, String nodeID, int numProcessors) throws IOException {
    String encNumParam = "";
    if (!newEncounterNumber.equals("")) {
      encNumParam = "&newEncounterNumber=" + newEncounterNumber;
    }
    URL u = new URL("http://" + thisURLRoot + "/scanAppletSupport?version=" + version + "&nodeIdentifier=" + nodeID + "&action=" + action + encNumParam + "&groupSize=" + groupSize + "&numProcessors=" + numProcessors);
    System.out.println("...Using nodeIdentifier: " + nodeID + "...with URL: "+u.toString());
    URLConnection con = u.openConnection();
    con.setDoInput(true);
    con.setDoOutput(true);
    con.setUseCaches(false);
    con.setDefaultUseCaches(false);
    con.setRequestProperty("Content-type", "application/octet-stream");
    con.setAllowUserInteraction(false);
    return con;
  }
    */




  public static void main(String args[]) {

    urlArray.add("https://www.whaleshark.org");
    urlArray.add("http://www.spotashark.com");
    // IP for Bass Server
    urlArray.add("http://34.209.17.78");

    WorkAppletHeadlessEpic a = new WorkAppletHeadlessEpic();
    if(args[0]!=null)urlArray.add(args[0]);
    a.getGoing();
  }


  public void getGoing() {


    String holdEncNumber = "";

    //check whether this applet is working on a specific task
    String targeted = "";


    long startTime=(new GregorianCalendar()).getTimeInMillis();
    
    //server connection
    URLConnection con = null;

    //whether this is a right-side pattern scan or a left-side
    boolean rightScan = false;

    //set up the random identifier for this "node"
    Random ran = new Random();
    int nodeIdentifier = ran.nextInt();
    String nodeID = "Amazon_Consolidated_" + (new Integer(nodeIdentifier)).toString();


    //if targeted on a specific task, show a percentage bar for progress
    int b = 0;


    boolean repeat = true;

    try {
        // keeps track of where we are in the array of machines.
        int i = 0;
      
        //let's allocate an object to handle an OutOfMemoryError
        //URL recoverURL = new URL("http://" + thisURLRoot + "/encounters/sharkGrid.jsp?groupSize=1&autorestart=true" + targeted + "&numComparisons=" + numComparisons);
        
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
        hb = new AppletHeartbeatThread(sNodeIdentifier, numProcessors, urlArray.get(i), version);
  
  
        //repeating comparison work of the applet
        while (repeat) {
  
          //cleanup anything previously in scope
          //System.gc();
  
  
          try {
  
            long currentTime=(new GregorianCalendar()).getTimeInMillis();
            long timeDiff=currentTime-startTime;
            long sleepTime=30000;
            
            //allow 55 minutes
            long allowedDiff=55*60*1000;
            
           
            
            
            //set up our thread processor for each comparison thread
            ArrayBlockingQueue abq = new ArrayBlockingQueue(500);
            threadHandler = new ThreadPoolExecutor(numProcessors, numProcessors, 0, TimeUnit.SECONDS, abq);
  
            boolean successfulConnect = true;
  
            ScanWorkItem swi = new ScanWorkItem();
            Vector workItems = new Vector();
            Vector workItemResults = new Vector();
            ObjectInputStream inputFromServlet=null;
            try {
              //let's get some work from the server
              System.out.println("\n\nLooking for some work to do...running time: "+(currentTime-startTime)/60000+" minutes");
              
              
              //con = getConnection("getWorkItemGroup", holdEncNumber, groupSize, nodeID, numProcessors);
              String encNumParam = "&newEncounterNumber=" + holdEncNumber;
             
              java.net.URL u = new java.net.URL(urlArray.get(i) + "/scanAppletSupport?version=" + version + "&nodeIdentifier=" + nodeID + "&action=" + "getWorkItemGroup" + encNumParam + "&groupSize=" + groupSize + "&numProcessors=" + numProcessors);
              System.out.println("...Using nodeIdentifier: " + nodeID + "...with URL: "+u.toString());
             
              
              if (urlArray.get(i).substring(0, 5).equals("https")) {
                con = (HttpsURLConnection)u.openConnection();     
              } else {
                con = (HttpURLConnection)u.openConnection();
               }
              
              con.setDoInput(true);
              con.setDoOutput(true);
              con.setUseCaches(false);
              con.setDefaultUseCaches(false);
              con.setRequestProperty("Content-type", "application/octet-stream");
              con.setAllowUserInteraction(false);
              
              System.out.println("     Opened a URL connection to: "+con.getURL().toString());
              
              inputFromServlet = new ObjectInputStream(con.getInputStream());
              workItems = (Vector) inputFromServlet.readObject();
              
              if((workItems!=null)&&(workItems.size()>0)){
                swi = (ScanWorkItem) workItems.get(0);
                successfulConnect = true;
              }
              else{
                System.out.println("...No work to do... Gonna take a nap then check the next server...");
                
                int c = urlArray.size();
                if (i == (c - 1)) {
                  System.out.println("...Back to the beginning of the Array!...");
                  i = 0;
                } else {
                  System.out.println("...Done I'm done and I'm onto the next one...");
                  i += 1;                  
                }
                
                successfulConnect=false;
                if (timeDiff<allowedDiff) {
  
                  Thread.sleep(sleepTime);
                  //System.exit(0);
                  
                }
                else {
                  System.out.println("\n\nI hit the timeout and am shutting down after "+(timeDiff/1000/60)+" minutes.");
                  inputFromServlet.close();
                  System.exit(0);
                  
                }
                
              }
              inputFromServlet.close();
              inputFromServlet = null;
            } 
            catch (Exception ioe) {
              if(inputFromServlet!=null)inputFromServlet.close();
              ioe.printStackTrace();
              successfulConnect = false;
              //Thread.sleep(60000);
              //System.exit(0);
              //long currentTime=(new GregorianCalendar()).getTimeInMillis();
              //long timeDiff=currentTime-startTime;
              if (timeDiff<allowedDiff) {
  
                Thread.sleep(sleepTime);
                //System.exit(0);
                
              }
              else {
                System.exit(0);
              }
              
            }
  
  
            if (successfulConnect) {
  
              //if no work is returned, there are three options
              if (swi.getTotalWorkItemsInTask() <= 0) {
  
                try {
  
  
                  //waiting for last workItem to finish elsewhere, wait quietly and check later
                  //long currentTime=(new GregorianCalendar()).getTimeInMillis();
                  //long timeDiff=currentTime-startTime;
                  if ((swi.getTotalWorkItemsInTask() == -1)&&(timeDiff<allowedDiff)) {
  
                    Thread.sleep(sleepTime);
                    //System.exit(0);
                    
                  }
                  else {
                    System.exit(0);
  
                  }
  
                } 
                catch (NullPointerException npe) {
                  npe.printStackTrace();
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
  
                //lastWorkItemsTime=(new GregorianCalendar()).getTimeInMillis();
                int vectorSize = workItems.size();
                System.out.println("...received " + vectorSize + " comparisons to make...");
  
                //spawn the thread for each comparison
                for (int q = 0; q < vectorSize; q++) {
                  ScanWorkItem tempSWI = (ScanWorkItem) workItems.get(q);
  
                  //we also pass in workItemResults, which is a threadsafe vector of the results returned from each thread
                  threadHandler.submit(new AppletWorkItemThread(tempSWI, workItemResults));
                }
                System.out.println("...done spawning threads...");
  
  
                //block until all threads are done
                long vSize = vectorSize;
                while (threadHandler.getCompletedTaskCount() < vSize) {}
                
                
                System.out.println("...all threads done!...");
  
                //check the results and make variable changes as needed
                int resultsSize = workItemResults.size();
                
                System.out.println("Trying to return num results:"+resultsSize);
                
                /**
                for (int d = 0; d < resultsSize; d++) {
                  b++;
                  
                  ScanWorkItemResult swir = (ScanWorkItemResult) workItemResults.get(d);
                  MatchObject thisResult = swir.getResult();
                  if ((thisResult.getMatchValue() * thisResult.getAdjustedMatchValue()) >= 115) {
                    numMatches++;
                  }
                }
                */
  
  
                //if we have results to send, send 'em!
                if (resultsSize > 0) {
  
                  URL finishScan = new URL(urlArray.get(i)+"/ScanWorkItemResultsHandler2?" + "group=true&nodeIdentifier=" + nodeID);
                  System.out.println("Trying to send results to: "+finishScan.toString());
                  URLConnection finishConnection = null;
                  
                  if (urlArray.get(i).substring(0, 5).equals("https")) {
                    finishConnection = (HttpsURLConnection)finishScan.openConnection();
                  } else {
                    finishConnection = (HttpURLConnection)finishScan.openConnection();
                  }
                  
  
                  // inform the connection that we will send output and accept input
                  finishConnection.setDoInput(true);
                  finishConnection.setDoOutput(true);
  
                  // Don't use a cached version of URL connection.
                  finishConnection.setUseCaches(false);
                  finishConnection.setDefaultUseCaches(false);
  
                  // Specify the content type that we will send binary data
                  finishConnection.setRequestProperty("Content-Type", "application/octet-stream");
  
                  ObjectOutputStream outputToFinalServlet=null;
                  InputStream inputStreamFromServlet=null;
                  String line="";
                  
                  try{
                    // send the results Vector to the servlet using serialization
                    outputToFinalServlet = new ObjectOutputStream(finishConnection.getOutputStream());
  
                    //sendObject(outputToFinalServlet, workItemResults);
                    System.out.println("     : Sending returned results...");
                    //new modification
                    ObjectOutputStream out=null;
                    try{
                      //out = con;
                      outputToFinalServlet.reset();
                      if (workItemResults != null) {
                        outputToFinalServlet.writeObject(workItemResults);
                      }
                      outputToFinalServlet.close();
                     }
                    catch(Exception e){
                      if(out!=null)out.close();
                      System.out.println("     : Transmission exception in sendObject.");
                      e.printStackTrace();
                    }
                    System.out.println("     : Transmission complete. Waiting for response...");
  
                    outputToFinalServlet.close();
                    outputToFinalServlet = null;
  
                    inputStreamFromServlet = finishConnection.getInputStream();
                    BufferedReader in = new BufferedReader(new InputStreamReader(inputStreamFromServlet));
                    line = in.readLine();
                    in.close();
                    System.out.println("     : Checkin response received...");
                    inputStreamFromServlet.close();
                    in = null;
                    inputStreamFromServlet = null;
                  
                  }
                  catch(Exception except){
                    if(outputToFinalServlet!=null){outputToFinalServlet.close();}
                    if(inputStreamFromServlet!=null){inputStreamFromServlet.close();}
                    except.printStackTrace();
                  }
                  
                  if (line.equals("success")) {
                    System.out.println("Successful transmit to and return code from the servlet.");
  
                    numComparisons += (workItemResults.size());
                    //recoverURL = new URL("http://" + thisURLRoot + "/encounters/sharkGrid.jsp?groupSize=1&autorestart=true" + targeted + "&numComparisons=" + numComparisons);
  
  
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
  
          } 
          catch (OutOfMemoryError oome) {
            //oome.printStackTrace();
            //hb.setFinished(true);
            System.exit(0);
  
          } catch (Exception e) {
            e.printStackTrace();
  
          }
  
  
        } //end while
    } 
    catch (Exception mue) {
      System.out.println("I hit an Exception while trying to create the recoverURL for OutOfMemoryErrors");
      //mue.printStackTrace();
      mue.printStackTrace();
      System.exit(0);

    }
    System.exit(0);
  }        //end getGoing method


}