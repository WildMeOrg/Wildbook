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

import javax.swing.*;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.io.*;
import java.net.URL;
import java.net.URLConnection;
import java.util.Random;
import java.util.Vector;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.CancellationException;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

/**
 * workApplet3 is a client-side application used to shift spot pattern matching in the whaleshark.org Wildbook to the client-side in a grid.
 * workApplet3 also attempts to use multiple client cores for parallel processing where available.
 * The applet downloads a Vector of comparisons (scanWorkItem objects) to make, spins each comparison off into a separate thread, and uses a ThreadPoolExecutor
 * to manage the worker threads, limiting the number of executing threads to the number of available processors on the client.
 * <p/>
 * This applet also uses two additional threads: one to manage UI updates and a heartbeat thread to let the managing server know the node is active and available.
 * <p/>
 * <p/>
 * To do list:
 * <p/>
 * 1. Add a pulldown menu to allow users to select how many processors/threads the client can grab. Some people may have multicore processors
 * but may want to give us a 'nice' number of threads to allow them to continue to work. The pulldown menu would:
 * a. list 1-n processors, where n is the number of processors available on the host machine
 * b. set variable numProcessors accordingly, or reset the setting of the ThreadPoolExecutor
 * <p/>
 * 2. Add a Text field that sets variable holdEncNumber. holdEncNumber is a variable used to target the client at a particular scanTask number. This allows a researcher who has kicked off a scan to
 * jump his/her own machine ahead in the queue and work on only the encounter of interest to them. It should also cause the progress bar to switch from indeterminate to a status bar showing the percentage of the selected scanTask
 * complete.
 */
public class WorkApplet3 extends JApplet {

  //visible progress bar
  private JProgressBar status;

  //GUI display that lists number of comparisons made by this node
  private JLabel comparisons;

  //GUI display that lists number of potential matches made by this node
  private JLabel potentialMatches;

  //number of comparisons made by this node
  private int numComparisons = 0;

  //number of potential matches made by this node
  private int numMatches = 0;

  //applet architecture version used by server...any other version will be rejected
  //this is NOT the applet UI version displayed
  private static String version = "1.2";

  //polling heartbeat thread
  org.ecocean.grid.AppletHeartbeatThread hb;

  //thread pool handling comparison threads
  ThreadPoolExecutor threadHandler;

  ImageIcon i;

  //origin of the applet
  public static String thisURLRoot = "";

  //constructor
  public WorkApplet3() {
  }


  /*
  *Obtain a connection to the server to send/receive serialized content
  */
  private URLConnection getConnection(String action, String newEncounterNumber, int groupSize, String nodeIdentifier, int numProcessors) throws IOException {
    String encNumParam = "";
    if (!newEncounterNumber.equals("")) {
      encNumParam = "&newEncounterNumber=" + newEncounterNumber;
    }
    URL u = new URL(thisURLRoot + "/ScanAppletSupport?version=" + version + "&nodeIdentifier=" + nodeIdentifier + "&action=" + action + encNumParam + "&groupSize=" + groupSize + "&numProcessors=" + numProcessors);
    System.out.println("...Using nodeIdentifier: " + nodeIdentifier + "...");
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

    thisURLRoot = args[0];
    JFrame frame = new JFrame("Grid Client");

    frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);

    WorkApplet3 a = new WorkApplet3();
    a.init();  // Do what browser normally does
    frame.getContentPane().add(a);   // Add applet to frame
    frame.setSize(WIDTH, HEIGHT);   // Set the size of the frame
    frame.setVisible(true);   // Show the frame
    frame.setSize(800, 225);


  }


  /*
  *Initialize the applet
  */
  public void init() {

    Container contentPane = getContentPane();
    contentPane.setLayout(new BoxLayout(contentPane, BoxLayout.Y_AXIS));
    Dimension minSize = new Dimension(5, 5);
    status = new JProgressBar();
    setBackground(Color.white);

    JButton start = new JButton("Join sharkGrid");
    start.setSize(150, 40);
    start.setAlignmentX(CENTER_ALIGNMENT);
    start.setAlignmentY(CENTER_ALIGNMENT);

    JLabel instructions = new JLabel("Your computer is now helping to match spot patterns!");

    JLabel version = new JLabel("Grid Client Node 1.22");

    comparisons = new JLabel("Total comparisons made by your computer: 0");
    potentialMatches = new JLabel("Potential matches found by your computer: 0");

    status.setSize(600, 45);
    status.setAlignmentX(CENTER_ALIGNMENT);
    status.setAlignmentY(CENTER_ALIGNMENT);

    instructions.setBackground(Color.black);
    instructions.setAlignmentX(CENTER_ALIGNMENT);
    instructions.setAlignmentY(CENTER_ALIGNMENT);

    comparisons.setBackground(Color.black);
    comparisons.setAlignmentX(CENTER_ALIGNMENT);
    comparisons.setAlignmentY(CENTER_ALIGNMENT);

    potentialMatches.setBackground(Color.black);
    potentialMatches.setAlignmentX(CENTER_ALIGNMENT);
    potentialMatches.setAlignmentY(CENTER_ALIGNMENT);


    version.setAlignmentX(CENTER_ALIGNMENT);
    version.setAlignmentY(CENTER_ALIGNMENT);
    version.setSize(600, 45);
    version.setHorizontalAlignment(SwingConstants.CENTER);
    version.setVerticalAlignment(SwingConstants.CENTER);
    version.setBackground(Color.black);
    Font aaaFont = version.getFont();
    Font font3 = aaaFont.deriveFont(9.0f);  // Size will be 9.0
    version.setFont(font3);


    scanAppletActionListener saal = new scanAppletActionListener(status, start, comparisons, potentialMatches);
    start.addActionListener(saal);


    JPanel subPanel2 = new ImagePanel("sharkGrid.gif");
    subPanel2.setMaximumSize(new Dimension(800, 75));
    subPanel2.setMinimumSize(new Dimension(800, 75));
    subPanel2.setPreferredSize(new Dimension(800, 75));
    contentPane.add(subPanel2);


    contentPane.add(instructions);
    contentPane.add(new Box.Filler(minSize, minSize, minSize));
    contentPane.add(status);
    //contentPane.add(new Box.Filler(minSize, minSize, minSize));
    //contentPane.add(start);
    contentPane.add(new Box.Filler(minSize, minSize, minSize));
    contentPane.add(new Box.Filler(minSize, minSize, minSize));
    contentPane.add(comparisons);
    contentPane.add(new Box.Filler(minSize, minSize, minSize));
    contentPane.add(potentialMatches);

    contentPane.add(new Box.Filler(minSize, minSize, minSize));
    contentPane.add(new Box.Filler(minSize, minSize, minSize));
    contentPane.add(version);
    getContentPane().setBackground(Color.white);


    start.doClick();

  }


  //private appletSwingWorker thread
  private class gridSwingWorker extends AppletSwingWorker {

    protected String construct() throws InterruptedException {
      return "Success";
    }

    protected void finished() {

      try {
        String newTextComparisons = "Total comparisons made by your computer: " + numComparisons;
        comparisons.setText(newTextComparisons);

        String newPotentialMatches = "Potential matches found by your computer: " + numMatches;
        potentialMatches.setText(newPotentialMatches);


      } catch (CancellationException ex) {
        // status was assigned when cancelled
        ex.printStackTrace();
      }


    }

  }


  //button listener
  private class scanAppletActionListener implements ActionListener {
    boolean go;
    JProgressBar status;
    JButton start;
    JLabel comparisons;
    JLabel potentialMatches;
    //Container myPane;

    scanAppletActionListener(JProgressBar status, JButton start, JLabel comparisons, JLabel potentialMatches) {
      super();
      this.status = status;
      this.start = start;
      this.comparisons = comparisons;
      this.potentialMatches = potentialMatches;
      //this.myPane=myPane;
      this.go = true;

    }

    public void actionPerformed(ActionEvent buttonActionEvent) {
      if (go) {
        runWIScan myScan = new runWIScan(status, comparisons, potentialMatches);
        go = false;
      }
    }
  }


  //main applet execution thread
  private class runWIScan implements Runnable {
    Thread thrd;
    JProgressBar status;
    JLabel comparisons;
    JLabel potentialMatches;

    runWIScan(JProgressBar status, JLabel comparisons, JLabel potentialMatches) {
      thrd = new Thread(this, "runScan");
      this.status = status;
      this.comparisons = comparisons;
      this.potentialMatches = potentialMatches;
      thrd.start();
    }

    public void run() {
      getGoing(status, comparisons, potentialMatches);
    }


    public void getGoing(JProgressBar status, JLabel comparisons, JLabel potentialMatches) {


      //if this node is not working on a particular task, use the Cylon, indeterminate progress bar
      try {

        if (getParameter("encounter").equals("null")) status.setIndeterminate(true);
      } catch (NullPointerException npe) {
        status.setIndeterminate(true);
      }

      String holdEncNumber = "";

      //check whether this applet is working on a specific task
      String targeted = "";

      try {

        if (!getParameter("encounter").equals("null")) {
          holdEncNumber = getParameter("encounter");
          targeted = "&number=" + holdEncNumber;
        }
      } catch (NullPointerException npe) {
      }

      //server connection
      URLConnection con;


      //whether this is a right-side pattern scan or a left-side
      boolean rightScan = false;

      //set up the random identifier for this "node"
      Random ran = new Random();
      int nodeIdentifier = ran.nextInt();
      String nodeID = (new Integer(nodeIdentifier)).toString();


      //set up the progress bar
      try {

        if (!getParameter("encounter").equals("null")) status.setMinimum(0);
      } catch (NullPointerException npe) {
      }

      //if targeted on a specific task, show a percentage bar for progress
      int b = 0;

      try {

        if (!getParameter("encounter").equals("null")) status.setMaximum(100);
        if (!getParameter("encounter").equals("null")) status.setValue(0);
        if (!getParameter("encounter").equals("null")) status.setStringPainted(true);
      } catch (NullPointerException npe) {
      }

      boolean repeat = true;

      try {

        //check the number of processors
        Runtime rt = Runtime.getRuntime();
        int numProcessors = rt.availableProcessors();


        System.out.println();
        System.out.println();
        System.out.println("***Welcome to the Shepherd Grid!***");
        System.out.println("...I have " + numProcessors + " processor(s) to work with...");

        //set the start groupSize used-i.e. number of scans to tackle in the first returned Vector of work items
        //this is actually ignored and controlled by the server
        int groupSize = 10 * numProcessors;

        //set the max group size
        //int groupSizeCap=60*numProcessors;

        //start the heartbeat that periodically lets' the grid know it's out there
        String sNodeIdentifier = (new Integer(nodeIdentifier)).toString();

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
              successfulConnect = false;
              Thread.sleep(90000);
            }


            if (successfulConnect) {

              //if no work is returned, there are three options
              if (swi.getTotalWorkItemsInTask() <= 0) {

                try {


                  //the scan is finished, go see the results

                  if ((!getParameter("encounter").equals("null")) && (swi.getTotalWorkItemsInTask() == 0)) {

                    //the case of a targeted applet looking for a specific encounter
                    //go to the URL
                    System.out.println("...let's go see the results...");
                    //Thread.sleep(10000);
                    repeat = false;
                    //we now advance the web page to view the results
                  }


                  //waiting for last workItem to finish elsewhere, wait quietly and check later
                  else if ((!getParameter("encounter").equals("null")) && (swi.getTotalWorkItemsInTask() == -1)) {

                    System.out.println("...waiting for the scan to finish elsewhere...");
                    Thread.sleep(15000);

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
                  Thread.sleep(90000);
                }

              }

              //otherwise, if we've got work to do
              //kick it like Poison!
              else {


                //figure out the maximum status bar value from the first returned workItem in the Vector

                try {

                  if (!getParameter("encounter").equals("null"))
                    status.setMaximum(swi.getTotalWorkItemsInTask());
                  if (!getParameter("encounter").equals("null"))
                    status.setValue(swi.getWorkItemsCompleteInTask());

                } catch (NullPointerException npe) {
                }

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

                    if (!getParameter("encounter").equals("null"))
                      status.setValue(swi.getWorkItemsCompleteInTask() + d);
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

                  URL finishScan = new URL(thisURLRoot + "/ScanWorkItemResultsHandler2?" + targeted + "group=true&nodeIdentifier=" + nodeIdentifier);
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
                    //recoverURL=new URL(thisURLRoot+"/encounters/sharkGrid.jsp?groupSize=1&autorestart=true"+targeted+"&numComparisons="+numComparisons);

                    //kick off a thread to update the UI labels

                    gridSwingWorker worker = new gridSwingWorker();
                    worker.start();
                    worker = null;


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

            //if(appletContext!=null)appletContext.showDocument(recoverURL);
          } catch (Exception e) {
            e.printStackTrace();

          }


        } //end while

      } catch (Exception mue) {
        System.out.println("I hit a MalformedURLException while trying to create the recoverURL for OutOfMemoryErrors");
        mue.printStackTrace();

      }

      //for a targeted scan, go see the results
      String sideAddition = "";
      if (rightScan) {
        sideAddition = "&rightSide=true";
      }
      try {
        URL resultsURL = new URL(thisURLRoot + "/writeOutScanTask?number=" + getParameter("encounter"));
        hb.setFinished(true);
        //getAppletContext().showDocument(resultsURL);
      } catch (Exception npe23) {
        System.out.println("I failed while trying to advance to the finish!");
      }

    }        //end getGoing method


  }

  private class ImagePanel extends JPanel {

    public ImagePanel(String theimage) {
      image = Toolkit.getDefaultToolkit().getImage("images/" + theimage);
      MediaTracker tracker = new MediaTracker(this);
      tracker.addImage(image, 0);
      try {
        tracker.waitForID(0);
      } catch (InterruptedException exception) {
      }

    }


    public void paintComponent(Graphics g) {
      super.paintComponent(g);
      g.drawImage(image, 0, 0, null);

    }

    private Image image;
  }


}
	
	
