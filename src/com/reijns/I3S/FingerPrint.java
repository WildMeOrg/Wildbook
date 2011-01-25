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

package com.reijns.I3S;

import java.util.Iterator;
import java.util.Map;

public class FingerPrint {

  /**
   * FingerPrint Protected and Private Variables
   * Comments: Moving from a file based system to a database system, so all
   * references to file based serialization has been removed.
   */

  // If using Java 1.5, consider instead to use ArrayLists.
  //protected ArrayList<Point2D> fppal; // al = ArrayList
  //protected ArrayList<Point2D> origal;
  public Point2D[] fpp;
  public Point2D[] orig;
  // cnt = number of Point2D objects in the list
  private int cnt;
  // score is a cumulative distance score for all cnt Point2D objects
  private double score;
  private int paircnt;
  //Changed Jan 23, 2007
// Added protected property name for file open testing.
// Added protected property 
  public String name;
  public Point2D[] control;
//End change

  public FingerPrint() {
    cnt = -1;
    fpp = new Point2D[0];
    orig = new Point2D[0];
//Changed Jan 23, 2007
    control = new Point2D[0];
//End change
    score = Point2D.DBL_INIT;
    paircnt = -1;
    name = "not set";
  }

  /**
   * @param origin
   * @param pnts
   */
  public FingerPrint(Point2D[] origin, Point2D[] pnts) {
    /*Note: fppal is immutable since asList returns such*/
    //fppal = new ArrayList<Point2D>(Arrays.asList(pnts));
    cnt = pnts.length;
    //Point2D[] control=new Point2D[origin.length];
    //System.arraycopy(origin, 0, control, 0, origin.length);
//Changed Jan 23, 2007
//  Old : deleted System.arraycopy code and replaced it with setSpots method 
//  New :
    setSpots(origin, pnts, control);
//End change
    score = Point2D.DBL_INIT;
    paircnt = -1;
    name = "not set";
  }

  //Changed Jan 23, 2007
// New constructor:
  public FingerPrint(Point2D[] origin, Point2D[] pnts, Point2D[] control) {
    /*Note: fppal is immutable since asList returns such*/
    //fppal = new ArrayList<Point2D>(Arrays.asList(pnts));
    cnt = pnts.length;
    setSpots(origin, pnts, control);
    score = Point2D.DBL_INIT;
    paircnt = -1;
    name = "not set";

    //System.out.println("Fingerprint initialized with "+origin.length+" spots.");

  }
//End change

  //Changed Jan 23, 2007
// added private method setSpots
  private void setSpots(Point2D[] origin, Point2D[] pnts, Point2D[] control) {
    if (origin != null) {
      orig = new Point2D[origin.length];
      for (int i = 0; i < origin.length; i++) {
        orig[i] = new Point2D(origin[i].x, origin[i].y);
      }
    } else {
      orig = null;
    }

    if (pnts != null) {
      fpp = new Point2D[pnts.length];
      for (int i = 0; i < pnts.length; i++) {
        fpp[i] = new Point2D(pnts[i].x, pnts[i].y);
      }
    } else {
      fpp = null;
    }

    if (control != null) {
      this.control = new Point2D[control.length];
      for (int i = 0; i < control.length; i++) {
        this.control[i] = new Point2D(control[i].x, control[i].y);
      }
    } else {
      control = null;
    }
  }
//End change

  /**
   * @param f
   */
  public FingerPrint(FingerPrint f) {
    this.cnt = f.cnt;
    this.score = f.score;
    this.paircnt = f.paircnt;
//Changed Jan 23, 2007
// Old : deleted System.arraycopy code and replaced it with setSpots method 
// New :
    setSpots(f.orig, f.fpp, f.control);
    name = f.name;
// End change
  }

  /**
   * This caculates the distance and distance score comparing a FingerPrint
   * object to a visitor FingerPrint object passed as parameter f.
   *
   * @param f           FingerPrint : The visitor FingerPrint object that is passed.
   * @param pairs       HashMap : A passed reference key:value object map of distance:Pair values
   *                    that are calculated.
   * @param affine_corr int : a known affine correction value (skew, shear, rotate, etc.).
   * @return double : Returns the calculated distance score (note, this is not the
   *         total distance between points) with a default value of 1000000.0 returned if the
   *         comparison did not succeed.
   */
  // SupressWarnings for Java 1.5 where collection parameters are not
  // specified to make them Java 1.4x compatable.
  //@SuppressWarnings("unchecked")
  public double distance(FingerPrint f, Map pairs, int affine_corr) {

    //System.out.println("Starting the distance method...");

    if (f == null || pairs == null) {
      return -1;
    }

    double totaldist = 0;

    /*
       * Comments from original C++ file:
       * // process all possible point pairs. should become more efficient in later
       * // version if this is going to be a bottleneck
       * // JdH 30-12-2003
       */
    for (int i = 0; i < cnt; i++) {
      double mindist = 1000000000;
      double second = 1000000000;
      int minj = -1;

      for (int j = 0; j < f.cnt; j++) {
        double dist = pointDistance(f, i, j);
        //System.out.println("Distance is: "+dist);
        if (dist < mindist) {
          second = mindist;
          mindist = dist;
          minj = j;
        }
      }

      if (mindist * 4 <= second) {
        //System.out.println("Fingerprint.distance.if mindist*4<=second");
        Pair aPair = new Pair();
        if (pairs.containsKey((new Integer(minj))) == false) {
          totaldist += addPair(pairs, i, mindist, minj, aPair);
        } else {
          double oldDist = findPair(pairs, Math.sqrt(mindist), minj);
          if (oldDist > 0) {
            totaldist += addPair(pairs, i, mindist, minj, aPair) - oldDist;
          }
        }
      }
    }

    int paircnt = pairs.size() + affine_corr;

    if (paircnt == 0)
      score = 1000000.0;
    else
      score = totaldist / (paircnt * paircnt);
    return score;
  }

  /**
   * Adds the distance and array index reference between two points in two
   * different FingerPrints.
   *
   * @param pairs     Pair[] : Array of pairs to add this pair to.
   * @param totaldist double : Total distance between all valid pairs
   * @param i         int : Current index of the visitor FingerPrint point
   * @param mindist   double : Distance between two points
   * @param j         int : Current index of the source FingerPrint point
   * @param aPair     Pair : The Pair object to set values for and add to Pairs[]
   * @return totaldist double : Returns the new calculated total distance.
   */
  //@SuppressWarnings("unchecked")
  private double addPair(Map pairs, int i, double mindist, int j, Pair aPair) {
    double sqrtDist = Math.sqrt(mindist);
    aPair.m1 = i;
    aPair.m2 = j;
    aPair.dist = sqrtDist;
    pairs.put((new Integer(j)), aPair);
    return sqrtDist;
  }

  /**
   * Find the matching pair object with the same soure FingerPrint point
   * index
   *
   * @param pairs Map : Map of pairs to check
   * @param dist  double : Distance between current points
   * @param i     int : The point array index value to check as a match
   * @return Pair : Returns the matching pair, or null if none was found.
   */
  private double findPair(Map pairs, double dist, int i) {
    double result = -1;
    for (Iterator iter = pairs.values().iterator(); iter.hasNext();) {
      Pair aPair = (Pair) iter.next();
      if (aPair.m2 == i && aPair.dist > dist) {
        result = aPair.dist;
        iter.remove();
        break;
      } else {
        aPair = null;
      }
    }
    return result;
  }

  /**
   * Finds the distance between a point in the local fpp[] array and
   * that in a test FingerPrint class.
   *
   * @param f Fingerprint class : The class with a point to test
   * @param i int : Which location point to test from the local fpp[] Point2D array
   * @param j int : Which location point to test from passed f.fpp[] Point2D array
   * @return distance : distance between points i and j
   */
  private double pointDistance(FingerPrint f, int i, int j) {
    double fppxd = fpp[i].x - f.fpp[j].x;
    double fppyd = fpp[i].y - f.fpp[j].y;
    double dist = (fppxd * fppxd) + (fppyd * fppyd);
    return dist;
  }

  /**
   *
   *
   */
  public void resetScore() {
    score = 1000000.0;
  }

  /**
   * @param matrix
   */
  public void doAffine(double[] matrix) {
    if (fpp == null || matrix == null) {
      System.out.println("Returning without transformation because fpp or matrix was null!");
      return;
    }


    for (int i = 0; i < cnt; i++) {
      double x = fpp[i].x;
      double y = fpp[i].y;

      fpp[i].x = matrix[0] * x + matrix[1] * y + matrix[2];
      fpp[i].y = matrix[3] * x + matrix[4] * y + matrix[5];
    }
  }

  /**
   * Sets the total distance score for all the points in this class compared to
   * another FingerPrint class passed in via the distance method.
   *
   * @param s
   */
  public void setScore(double s) {
    score = s;
  }

  /**
   * @param p
   */
  public void setPairCnt(int p) {
    paircnt = p;
  }

  public int getCnt() {
    return cnt;
  }

  /**
   * @return
   */
  public double getScore() {
    return score;
  }

  /**
   * @return
   */
  public int getPairCnt() {
    return paircnt;
  }

  /* FingerPrint Private Methods
    * Comments: None
    */

  /**
   *
   */
  public Point2D getFpp(int i) {
    if (i < 0 || i >= cnt) {
      Point2D newPointInstance = new Point2D();
      return newPointInstance;
    }
    return fpp[i];
  }

}
