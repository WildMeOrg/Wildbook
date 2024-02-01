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

import org.ecocean.Spot;

public class VertexPointMatch implements java.io.Serializable {

  static final long serialVersionUID = 9122107217335010239L;

  public int points = 0;
  public double newX, newY, oldX, oldY;


  //used by JDO enhancer
  public VertexPointMatch() {
  }


  public VertexPointMatch(Spot newSpot, Spot oldSpot) {
    this.newX = newSpot.getCentroidX();
    this.newY = newSpot.getCentroidY();
    this.oldX = oldSpot.getCentroidX();
    this.oldY = oldSpot.getCentroidY();
  }

  public VertexPointMatch(Spot newSpot, Spot oldSpot, int points) {
    this.newX = newSpot.getCentroidX();
    this.newY = newSpot.getCentroidY();
    this.oldX = oldSpot.getCentroidX();
    this.oldY = oldSpot.getCentroidY();
    this.points = points;
  }

  public VertexPointMatch(double newX, double newY, double oldX, double oldY, int points) {
    this.newX = newX;
    this.newY = newY;
    this.oldX = oldX;
    this.oldY = oldY;
    this.points = points;
  }


  public double getNewX() {
    return newX;
  }

  public double getNewY() {
    return newY;
  }

  public double getOldX() {
    return oldX;
  }

  public double getOldY() {
    return oldY;
  }

  public int getPoints() {
    return points;
  }


}