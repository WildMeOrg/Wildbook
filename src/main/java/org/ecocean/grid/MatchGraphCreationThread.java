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
import org.ecocean.MarkedIndividual;
//import org.ecocean.MarkedIndividual;
//import org.ecocean.Occurrence;
import org.ecocean.Shepherd;
import org.ecocean.media.MediaAsset;
import org.ecocean.servlet.ServletUtilities;

import java.util.ArrayList;
import java.util.Collection;

//import java.util.Collection;
//import java.util.Iterator;
//import java.util.List;
//import java.util.Vector;

import javax.jdo.PersistenceManager;
import javax.jdo.PersistenceManagerFactory;
import javax.jdo.Query;
import javax.servlet.http.HttpServletRequest;


public class MatchGraphCreationThread implements Runnable, ISharkGridThread {

  public Thread threadCreationObject;
  java.util.Properties props2 = new java.util.Properties();
  GridManager gm;
  String context="context0";
  String jdoql="SELECT FROM org.ecocean.Encounter WHERE catalogNumber != null";
  boolean finished = false;
  
  
  /**
   * Constructor to create a new thread object
   */
  public MatchGraphCreationThread(HttpServletRequest request) {

    gm = GridManagerFactory.getGridManager();
    threadCreationObject = new Thread(this, ("MatchGraphCreationThread.class"));
    this.context=ServletUtilities.getContext(request);

  }
  
  /**
   * Constructor to create a new thread object
   */
  public MatchGraphCreationThread(HttpServletRequest request, String jdoql) {

    gm = GridManagerFactory.getGridManager();
    threadCreationObject = new Thread(this, ("MatchGraphCreationThread.class"));
    this.context=ServletUtilities.getContext(request);
    this.jdoql=jdoql;

  }
  
  public MatchGraphCreationThread() {

    gm = GridManagerFactory.getGridManager();
    threadCreationObject = new Thread(this, ("MatchGraphCreationThread.class"));

  }
  
  public MatchGraphCreationThread(String jdoql) {
    this.jdoql=jdoql;
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
    
    PersistenceManager pm=myShepherd.getPM();
    PersistenceManagerFactory pmf = pm.getPersistenceManagerFactory();

    //optimize with fetch groups
    javax.jdo.FetchGroup grp2 = pmf.getFetchGroup(Encounter.class, "encSearchResults");
    grp2.addMember("sex").addMember("catalogNumber").addMember("year").addMember("hour").addMember("month").addMember("minutes").addMember("day").addMember("spots").addMember("rightSpots").addMember("leftReferenceSpots").addMember("rightReferenceSpots").addMember("individual");
    javax.jdo.FetchGroup grp = pmf.getFetchGroup(MarkedIndividual.class, "indySearchResults");
    grp.addMember("individualID");
    myShepherd.getPM().getFetchPlan().setGroup("encSearchResults");
    myShepherd.getPM().getFetchPlan().addGroup("indySearchResults");

    myShepherd.beginDBTransaction();

    try {
      
      Query q=myShepherd.getPM().newQuery(jdoql);
      Collection results = (Collection) (q.execute());
      ArrayList<Encounter> resultList = new ArrayList<Encounter>(results);
      q.closeAll();

      gm.resetMatchGraphWithInitialCapacity(resultList.size());


      for (Encounter enc:resultList) {
        try {
          if (((enc.getRightSpots() != null) && (enc.getRightSpots().size() > 0))||((enc.getSpots() != null) && (enc.getSpots().size() > 0))) {
              EncounterLite el=new EncounterLite(enc);
              gm.addMatchGraphEntry(enc.getCatalogNumber(), el);
            } 
        }
        catch(Exception internal) {
          internal.printStackTrace();
        }
      }

      finished=true;

    } 
    catch (Exception e) {
      System.out.println("I failed while constructing the EncounterLites in MatchGraphCreationThread.");
      e.printStackTrace();

    }
    finally{
      //if(query!=null){query.closeAll();}
      myShepherd.rollbackDBTransaction();
      myShepherd.closeDBTransaction();
    }
    
    System.out.println("Ending MatchGraphCreationThread!");

  }
  
  public boolean isFinished() {
    return finished;
  }


}