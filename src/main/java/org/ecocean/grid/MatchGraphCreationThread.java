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

import org.ecocean.Encounter;
//import org.ecocean.Occurrence;
import org.ecocean.Shepherd;
import org.ecocean.servlet.ServletUtilities;

import java.util.Collection;
import java.util.Iterator;
import java.util.Vector;

import javax.jdo.Query;
import javax.servlet.http.HttpServletRequest;


public class MatchGraphCreationThread implements Runnable, ISharkGridThread {

  public Thread threadCreationObject;
  java.util.Properties props2 = new java.util.Properties();
  GridManager gm;
  String context="context0";
  String jdoql="SELECT FROM org.ecocean.Encounter";
  boolean finished = false;
  /**
   * Constructor to create a new thread object
   */
  public MatchGraphCreationThread(HttpServletRequest request) {

    gm = GridManagerFactory.getGridManager();
    threadCreationObject = new Thread(this, ("MatchGraphCreationThread.class"));
    this.context=ServletUtilities.getContext(request);

  }
  
  public MatchGraphCreationThread() {

    gm = GridManagerFactory.getGridManager();
    threadCreationObject = new Thread(this, ("MatchGraphCreationThread.class"));

  }


  /**
   * main method of the shepherd thread
   */
  public void run() {
    createThem();
  }


  public void createThem() {
    System.out.println("Starting MatchGraphCreationThread!");
    Shepherd myShepherd = new Shepherd(context);
    myShepherd.setAction("MatchGraphCreationThread.class");
    GridManager gm = GridManagerFactory.getGridManager();


    myShepherd.beginDBTransaction();
    Query query=null;
    try {
      
      query=myShepherd.getPM().newQuery(jdoql);
      Collection c = (Collection) (query.execute());
      System.out.println("Num scans to do: "+c.size());
      Iterator encounters = c.iterator();
      
      int count = 0;

      while (encounters.hasNext()) {
        Encounter enc = (Encounter) encounters.next();
        if (((enc.getRightSpots() != null) && (enc.getRightSpots().size() > 0))||((enc.getSpots() != null) && (enc.getSpots().size() > 0))) {
            EncounterLite el=new EncounterLite(enc);
            gm.addMatchGraphEntry(enc.getCatalogNumber(), el);
            count++;
          } 

      }
      myShepherd.rollbackDBTransaction();
      finished=true;

    } 
    catch (Exception e) {
      System.out.println("I failed while constructing the EncounterLites in MatchGraphCreationThread.");
      e.printStackTrace();
      myShepherd.rollbackDBTransaction();
      
    }
    finally{
      if(query!=null){query.closeAll();}
      myShepherd.closeDBTransaction();
    }
    
    System.out.println("Ending MatchGraphCreationThread!");

  }
  
  public boolean isFinished() {
    return finished;
  }


}