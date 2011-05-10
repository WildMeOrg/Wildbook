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

import javax.servlet.http.HttpServletRequest;
import java.util.Locale;

public class GridNode {

  public long startTime;
  public long lastCheckin = 1;
  public long lastCheckout = 2;
  public long lastHeartbeat;
  public String nodeIdentifier;
  //public long numSpots=0;
  public long numComparisons = 0;
  public long totalTimeSinceStart;
  public String ipAddress;
  public Locale locale = new Locale("unknown", "unknown");
  public int numProcessors = 1;
  public boolean hasMadeFirstCheckin = false;
  public int groupSize;
  //public boolean isZombie = false;

  //whether this node is dedicated to a single scanTask (true) or running as a generic node (false)
  public boolean targeted = false;


  public GridNode(HttpServletRequest request, int startGroupSize) {
    String nodeIdentifier = request.getParameter("nodeIdentifier");
    String np = request.getParameter("numProcessors");
    this.numProcessors = (new Integer(np)).intValue();
    this.nodeIdentifier = nodeIdentifier;
    this.startTime = System.currentTimeMillis();
    //this.lastCheckin=System.currentTimeMillis();
    this.lastHeartbeat = System.currentTimeMillis();
    this.groupSize = startGroupSize;
    this.ipAddress = request.getRemoteAddr();
    if ((request.getParameter("newEncounterNumber") != null) && (!request.getParameter("newEncounterNumber").equals(""))) {
      targeted = true;
    }
  }


  public String getNodeIdentifier() {
    return nodeIdentifier;
  }

  public long getNumComparisons() {
    return numComparisons;
  }

  public void checkin(int num) {
    //this.numSpots+=numSpots;
    if (lastCheckin != 1) {
      totalTimeSinceStart += (System.currentTimeMillis() - lastCheckin);
    } else {
      totalTimeSinceStart += (System.currentTimeMillis() - lastCheckout);
    }

    lastCheckin = System.currentTimeMillis();
    numComparisons = numComparisons + num;
    hasMadeFirstCheckin = true;

    if (num < groupSize) {
      groupSize = num;
      if (num < 1) {
        num = 1;
      }
    }
  }

  public void registerHeartbeat() {
    lastHeartbeat = System.currentTimeMillis();
  }

  public int getNextGroupSize(long checkoutTimeout, int maxGroupSize) {
    if (lastCheckout != 2) {
      long timeDiff = lastCheckin - lastCheckout;
      if (Math.abs(timeDiff) < 60000) {
        groupSize = groupSize + (numProcessors);
      }
      else {
        groupSize--;
      }

      //long multiplier=1/(timeDiff/60000);
      //groupSize=(int)(groupSize*multiplier*0.9);

      if (groupSize < 1) {
        groupSize = 1;
      } else if (groupSize > maxGroupSize) {
        groupSize = maxGroupSize;
        return maxGroupSize;
      }
      return groupSize;
    } else {
      return groupSize;
    }
  }

  public long getLastCheckin() {
    return lastCheckin;
  }

  public long getLastCheckout() {
    return lastCheckout;
  }

  public void setLastCheckout(long lco) {
    lastCheckout = lco;
  }

  public long getLastHeartbeat() {
    return lastHeartbeat;
  }

  public void setGroupSize(int size) {
    groupSize = size;
  }

  public String getDisplayCountry() {
    return locale.getDisplayCountry();
  }

  public String ipAddress() {
    return ipAddress;
  }

  public boolean isTargeted() {
    return targeted;
  }

  /*
  public void setAsZombie() {
    isZombie = true;
  }

  public void setNotZombie() {
    isZombie = false;
  }
*/
}
