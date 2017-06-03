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


import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLConnection;
import javax.net.ssl.HttpsURLConnection;

/**
 * COmment
 *
 * @author jholmber
 *         more
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
  public AppletHeartbeatThread(String appletID, int numProcessors, String thisURLRoot, String version) {
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
    //boolean ok2run=true;
    while (!finished) {
      try {
        sendHeartbeat(appletID);
        Thread.sleep(90000);
      } catch (Exception e) {
        System.out.println("     Heartbeat thread registering an exception while trying to sleep!");
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
    
    
    //prep our streaming variables
    URL u=null;
    InputStream inputStreamFromServlet=null;
    BufferedReader in=null;
    //HttpsURLConnection finishConnection=null;
    URLConnection finishConnection=null;
    
    try {
      
      u = new URL(rootURL + "/GridHeartbeatReceiver?nodeIdentifier=" + appletID + "&numProcessors=" + numProcessors + "&version=" + version);
      
      System.out.println("...sending heartbeat...thump...thump...to: "+u.toString());
      
      if (rootURL.substring(0, 5).equals("https")) {
        finishConnection = (HttpsURLConnection)u.openConnection();     
      } else {
        finishConnection = (HttpURLConnection)u.openConnection();
       }

      inputStreamFromServlet = finishConnection.getInputStream();
      in = new BufferedReader(new InputStreamReader(inputStreamFromServlet));
      String line = in.readLine();
      //in.close();
      //inputStreamFromServlet.close();


      //process the returned line however needed


    } 
    catch (MalformedURLException mue) {
      System.out.println("!!!!!I hit a MalformedURLException in the heartbeat thread!!!!!");
      mue.printStackTrace();
      //System.exit(0);

    } 
    catch (IOException ioe) {
      System.out.println("!!!!!I hit an IO exception in the heartbeat thread!!!!!");
      ioe.printStackTrace();
      //System.exit(0);
    } 
    catch (Exception e) {
      System.out.println("!!!!!I hit an Exception in the heartbeat thread!!!!!");
      e.printStackTrace();
      //System.exit(0);
    }
    finally{
      try{
        if(inputStreamFromServlet!=null)inputStreamFromServlet.close();
        if(in!=null)in.close();
        in=null;
        inputStreamFromServlet=null;
        finishConnection=null;
        u=null;
      }
      catch(Exception ex){
        System.exit(0);
      }
    }
  }


}