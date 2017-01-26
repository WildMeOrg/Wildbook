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


import com.reijns.I3S.Pair;
import org.ecocean.Shepherd;

import javax.jdo.Extent;
import javax.jdo.Query;
import java.util.ArrayList;
import java.util.List;


public class GridCleanupThread implements Runnable {

  public Thread threadCleanupObject;
private String context="context0";

  /**
   * Constructor to create a new thread object
   */
  public GridCleanupThread(String context) {

    threadCleanupObject = new Thread(this, "gridCleanup");
    threadCleanupObject.start();
    this.context=context;
  }


  /**
   * main method of the shepherd thread
   */
  public void run() {
    cleanup();
  }


  public void cleanup() {
    Shepherd myShepherd = new Shepherd(context);
    myShepherd.setAction("GridCleanupThread.class");

    myShepherd.beginDBTransaction();


    //Iterator vpms=myShepherd.getAllPairsNoQuery();
    Extent encClass = myShepherd.getPM().getExtent(Pair.class, true);
    Query query = myShepherd.getPM().newQuery(encClass);
    int count = 0;
    int size = 1;
    while (size > 0) {
      try {

        List<Pair> pairs = myShepherd.getPairs(query, 50);
        size = pairs.size();
        for (int m = 0; m < size; m++) {
          Pair mo = (Pair) pairs.get(m);
          myShepherd.getPM().deletePersistent(mo);
        }
        myShepherd.commitDBTransaction();
        myShepherd.beginDBTransaction();
      } catch (Exception e) {
        System.out.println("I failed while constructing the workItems for a new scanTask.");
        e.printStackTrace();
        myShepherd.rollbackDBTransaction();
        myShepherd.beginDBTransaction();
      }
    }
	query.closeAll();
    myShepherd.commitDBTransaction();
    myShepherd.closeDBTransaction();
  }


}