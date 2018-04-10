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

//unenhanced comment

/**
 * test
 */
public class Pair implements java.io.Serializable {
  static final long serialVersionUID = 9122107217335010239L;

  // protected vars and methods could be accessible from the
  // friend class FingerPrint by including it in the package
  public int m1, m2;
  public double dist;


  public Pair() {
    dist = Point2D.DBL_INIT;
    m1 = -1;
    m2 = -1;
  }

  public int getM1() {
    return m1;
  }

  public int getM2() {
    return m2;
  }

  public double getDist() {
    return dist;
  }


}
