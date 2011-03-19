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


//import java.io.*;


//import java.io.*;

public class ScanTaskCleanupThread implements Runnable, ISharkGridThread {

  public Thread threadCleanupObject;
  String taskID;
  public boolean finished = false;

  /**
   * Constructor to create a new thread object
   * test
   */
  public ScanTaskCleanupThread(String taskID) {
    this.taskID = taskID;
    threadCleanupObject = new Thread(this, ("sharkGridCleanup_" + taskID));
    //threadCleanupObject.start();
  }


  /**
   * main method of the shepherd thread
   */
  public void run() {
    cleanup();
  }

  public boolean isFinished() {
    return finished;
  }


  public void cleanup() {
    //shepherd myShepherd=new shepherd();

    //myShepherd.beginDBTransaction();


    try {


      GridManager gm = GridManagerFactory.getGridManager();
      gm.removeWorkItemsForTask(taskID);
      gm.removeCompletedWorkItemsForTask(taskID);


    } catch (Exception e) {
      System.out.println("scanTaskCleanupThread: Failed on cleanup!");
      e.printStackTrace();
      //myShepherd.rollbackDBTransaction();
    }


    //myShepherd.closeDBTransaction();
    finished = true;
  }


}