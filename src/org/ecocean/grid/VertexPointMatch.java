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