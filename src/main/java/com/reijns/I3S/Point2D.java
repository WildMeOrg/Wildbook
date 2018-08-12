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

// In the C++ source, protected vars and methods could be accessible from a 
// friend classes; in Java we get similar behavior by including
// classes in the same package.  But see comments below.
//TBD
public class Point2D implements java.io.Serializable {

  static final long serialVersionUID = 9122107317335010239L;

  /**
   * Point2D Variables
   * <p/>
   * Comments: In original C++ source the protected x and y values are
   * treated as properties accessible from within Friend classes (there are
   * no setter methods).  Beware of bending OO concepts by maintaining
   * this system.  The DBL_INIT constant is initiated here and referenced
   * in the entire package as a Point2D immutable value.
   */
  public double x, y;
  public final static int DBL_INIT = -1000000000;

  /**
   * Point2D Constructor
   * <p/>
   * Comments: Second constructor method added.
   */
  public Point2D() {
    x = DBL_INIT;
    y = DBL_INIT;
  }

  public Point2D(double x, double y) {
    this.x = x;
    this.y = y;
  }

  public Point2D(Point2D orig) {
    this.x = orig.getX();
    this.y = orig.getY();
  }

  /**
   * Point2D Public Methods
   * <p/>
   * Comments: Normally getter methods bend Encapsulation, but since the
   * Point2D class can be considered as only a Value Object access to the
   * data is required by the processor objects.  The getters are actually not
   * needed since the protected x and y values are accessible to all classes
   * in the Wildbook package, but to maintain the code base they are
   * retained.  However we should restrict data getting only to within the
   * Wildbook package.  Thus the getter methods are set for package
   * level access (i.e. the default for undeclared method types) rather than
   * public.
   */
  public double getX() {
    return x;
  }

  public double getY() {
    return y;
  }

  void setX(double x) {
    this.x = x;
  }

  void setY(double y) {
    this.y = y;
  }

} 

