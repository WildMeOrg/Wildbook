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

import java.util.Vector;

/**
 * Test comment
 *
 * @author jholmber
 */
public class AppletWorkItemThread implements Runnable {

  public Thread threadObject;
  public ScanWorkItem swi;
  public Vector results;


  /**
   * Constructor to create a new thread object
   */
  public AppletWorkItemThread(ScanWorkItem swi, Vector results) {
    this.swi = swi;
    threadObject = new Thread(this, ("sharkGrid_" + swi.getUniqueNumber()));
    threadObject.setPriority(Thread.MIN_PRIORITY);
    this.results = results;

  }


  public void run() {
    //executeComparison();

    System.out.println("...in the run method of AppletWorkItemThread...");
    try {
      org.ecocean.grid.MatchObject thisResult;
      thisResult = swi.execute();
      System.out.println("...thisResult returned!!!");
      results.add(new ScanWorkItemResult(swi.getTaskIdentifier(), swi.getUniqueNumber(), thisResult));
      System.out.println("results size: " +results.size());
    } 
    catch (OutOfMemoryError oome) {
      oome.printStackTrace();
    } 
    catch (Exception e) {
      e.printStackTrace();

    }
  }


  public void nullThread() {
    swi = null;
    threadObject = null;
  }

  public void executeComparison() {
  }


}