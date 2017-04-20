package org.ecocean.movement;

import java.util.ArrayList;
import java.util.UUID;

import org.ecocean.*;

/**
* @author Colin Kingen
* 
* A path is a collection of point objects. Each of these points contains
* GPS coordinent data, and a group of them for a particular survey 
* gives you the path or paths that a team or individual followed during 
* a specific point in time. 
*
*
*/

public class Path implements java.io.Serializable {

  /**
   * 
   */
  private static final long serialVersionUID = -8130232817853279715L;
  
  private UUID pathID = null;
  
  private ArrayList<Point> points;
  
}