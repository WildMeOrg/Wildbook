package org.ecocean.movement;

import java.util.ArrayList;
import java.util.UUID;

import org.ecocean.*;

/**
* @author Colin Kingen
* 
* A path is a collection of pointLocation objects. Each of these pointLocations contains
* GPS coordinent data, and a group of them for a particular survey 
* gives you the path or paths that a team or individual followed during 
* a specific pointLocation in time. 
*
*
*/

public class Path implements java.io.Serializable {

  /**
   * 
   */
  private static final long serialVersionUID = -8130232817853279715L;
  
  private UUID pathID = null;
  
  private ArrayList<PointLocation> pointLocations;
  
  public Path(){};
  
  public Path(PointLocation pnt) {
    this.pointLocations.add(pnt);
    generateUUID();
  }
  
  public Path(ArrayList<PointLocation> pts) {
    if (pts.size() >= 1) {
      for (int i=0; i<pts.size(); i++ ) {
        this.pointLocations.add(pts.get(i));
      }
      generateUUID();
    }
  }  
  
  public UUID getID() {
    return pathID;
  }
  
  public PointLocation getLocation(UUID id) {
    if (id !=null) {
      for (int i=0; i <= pointLocations.size(); i++) {
        if (pointLocations.get(i).getID() == id) {
          return pointLocations.get(i);
        }
      }
    }
    return null;
  }
  
  public void addLocation(PointLocation p) {
    if (this.getLocation(p.getID()) == null) {
      pointLocations.add(p);
    }
  }
  
  public void addLocationsArray(ArrayList<PointLocation> pts) {
    if (pts.size() >= 1) {
      for (int i=0; i<pts.size(); i++ ) {
        if (this.getLocation(pts.get(i).getID()) == null) {
          pointLocations.add(pts.get(i));
        }
      }
    }
  }
  
  private void generateUUID() {
    this.pathID = UUID.randomUUID();
  }
  
}