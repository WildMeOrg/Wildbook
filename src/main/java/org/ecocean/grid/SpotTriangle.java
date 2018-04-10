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

import java.awt.geom.Point2D;


public class SpotTriangle implements java.io.Serializable {

  static final long serialVersionUID = -5400879461028598731L;
  //3 spots of the triangle
//public spot s1, s2, s3;
  public double Dij, Dik, Djk, D12, D13, D23;
  public double Dxl, Dyl, Dxs, Dys;
  public double CrossProd, VarFact;
  public Spot v1, v2, v3;
  public double logPerimeter;
  public boolean clockwise;
  public double ratioLong2Short, toleranceRatioLong2Short;
  public double cosineAtVertex1, toleranceInCosineAtVertex1;
  public double R, C, S2, tR2, tC2;


  public double r2, r3;


  public SpotTriangle(Spot i, Spot j, Spot k, double epsilon) {
    this.Dij = Point2D.distance(i.getCentroidX(), i.getCentroidY(), j.getCentroidX(), j.getCentroidY());
    this.Dik = Point2D.distance(i.getCentroidX(), i.getCentroidY(), k.getCentroidX(), k.getCentroidY());
    this.Djk = Point2D.distance(j.getCentroidX(), j.getCentroidY(), k.getCentroidX(), k.getCentroidY());
    // We now know the lengths of all three sides, so
    // can figure out short, middle, long -- will get
    // six cases.
    if ((Dik >= Djk) && (Dik >= Dij)) {
      v2 = j;
      D13 = Dik;
      if (Djk >= Dij) {
        // ik = long, jk = middle, ij = short
        Dxl = i.getCentroidX() - k.getCentroidX();
        Dyl = i.getCentroidY() - k.getCentroidY();
        Dxs = j.getCentroidX() - i.getCentroidX();
        Dys = j.getCentroidY() - i.getCentroidY();
        D12 = Dij;
        D23 = Djk;
        v1 = i;
        v3 = k;
      } else {
        // ik = long, ij = middle, jk = short
        Dxl = -i.getCentroidX() + k.getCentroidX();
        Dyl = -i.getCentroidY() + k.getCentroidY();
        Dxs = -k.getCentroidX() + j.getCentroidX();
        Dys = -k.getCentroidY() + j.getCentroidY();
        D12 = Djk;
        D23 = Dij;
        v1 = k;
        v3 = i;
      }
    } else if ((Djk > Dik) && (Djk >= Dij)) {
      v2 = i;
      D13 = Djk;
      if (Dik >= Dij) {
        // jk = long, ik = middle, ij = short
        Dxl = -k.getCentroidX() + j.getCentroidX();
        Dyl = -k.getCentroidY() + j.getCentroidY();
        Dxs = -j.getCentroidX() + i.getCentroidX();
        Dys = -j.getCentroidY() + i.getCentroidY();
        D12 = Dij;
        D23 = Dik;
        v1 = j;
        v3 = k;
      } else {
        // jk = long, ij = middle, ik = short
        Dxl = k.getCentroidX() - j.getCentroidX();
        Dyl = k.getCentroidY() - j.getCentroidY();
        Dxs = i.getCentroidX() - k.getCentroidX();
        Dys = i.getCentroidY() - k.getCentroidY();
        D12 = Dik;
        D23 = Dij;
        v1 = k;
        v3 = j;
      }
    } else {
      v2 = k;
      D13 = Dij;
      if (Dik >= Djk) {
        // ij = long, ik = middle, jk = short
        Dxl = j.getCentroidX() - i.getCentroidX();
        Dyl = j.getCentroidY() - i.getCentroidY();
        Dxs = k.getCentroidX() - j.getCentroidX();
        Dys = k.getCentroidY() - j.getCentroidY();
        D12 = Djk;
        D23 = Dik;
        v1 = j;
        v3 = i;
      } else {
        // ij = long, jk = middle, ik = short
        Dxl = -j.getCentroidX() + i.getCentroidX();
        Dyl = -j.getCentroidY() + i.getCentroidY();
        Dxs = -i.getCentroidX() + k.getCentroidX();
        Dys = -i.getCentroidY() + k.getCentroidY();
        D12 = Dik;
        D23 = Djk;
        v1 = i;
        v3 = j;
      }
    }
    r3 = D13;
    r2 = D12;
    R = r3 / r2;
    C = -(Dxl * Dxs + Dyl * Dys) / (r2 * r3);
    S2 = 1.0 - (C * C);
    VarFact = 1.0 / (r3 * r3) - C / (r3 * r2) + 1.0 / (r2 * r2);
    tR2 = R * R * epsilon * epsilon * 2 * VarFact;
    tC2 = 2 * S2 * epsilon * epsilon * VarFact + 3 * C * C * Math.pow(epsilon, 4) * VarFact * VarFact;
    logPerimeter = Math.log(D12 + D13 + D23);
    CrossProd = Dxs * Dyl - Dxl * Dys;
    if (CrossProd <= 0.0) {
      clockwise = false;
    } else {
      clockwise = true;
    }
  }

  public boolean containsSpot(Spot x) {

    if ((x.getCentroidX() == v1.getCentroidX()) && (x.getCentroidY() == v1.getCentroidY())) {
      return true;
    } else if ((x.getCentroidX() == v2.getCentroidX()) && (x.getCentroidY() == v2.getCentroidY())) {
      return true;
    } else if ((x.getCentroidX() == v3.getCentroidX()) && (x.getCentroidY() == v3.getCentroidY())) {
      return true;
    }
    return false;
  }

  public Spot getVertex(int x) {

    if (x == 1) {
      return v1;
    } else if (x == 2) {
      return v2;
    } else {
      return v3;
    }

  }

  public double getMyVertexOneRotationInRadians() {
    double x1 = v1.getCentroidX();
    double y1 = v1.getCentroidY();
    double x2 = v2.getCentroidX();
    double y2 = v2.getCentroidY();
    double x3 = v3.getCentroidX();
    double y3 = v3.getCentroidY();


    //now calculate the centroid
    double centroidX = (x1 + x2 + x3) / 3;
    double centroidY = (y1 + y2 + y3) / 3;

    //let's use vertex one to measure angle of rotation
    //first we must normalize vertex one with repect to the center. in other words, rotation
    //of the triangle is about the centroid at (0,0)

    x1 = x1 - centroidX;
    y1 = y1 - centroidY;
    double theta;

    //now the angle between v1 and the origin is
    /*if((y1==0)&&(x1>0)) {return 0;}
   else if((y1==0)&&(x1<0)) {return 3.14159;}
   else if((y1>0)&&(x1==0)) {return (3.14159/2);}
   else if((y1<0)&&(x1==0)) {return (3*3.14159/2);}
   //Quadrant 1 calc.
   else if((y1>0)&&(x1>0)) {theta=Math.asin(y1/x1);}
   //Quadrant 2 calculation
   else if((y1>0)&&(x1<0)) {theta=3.14159-Math.asin(y1/x1);}
   //Quadrant 3 calculation
   else if((y1<0)&&(x1<0)) {theta=3.14159+Math.asin(y1/x1);}
   //Quadrant 4 calculation
   else {theta=(2*3.14159)-Math.asin(y1/x1);}*/


    theta = Math.atan2(y1, x1);
    return theta;
  }

  public double getTriangleCentroidX() {
    double x1 = v1.getCentroidX();
    //double y1=v1.getCentroidY();
    double x2 = v2.getCentroidX();
    //double y2=v2.getCentroidY();
    double x3 = v3.getCentroidX();
    //double y3=v3.getCentroidY();
    //now calculate the centroid
    return ((x1 + x2 + x3) / 3);

  }

  public double getTriangleCentroidY() {
    //double x1=v1.getCentroidX();
    double y1 = v1.getCentroidY();
    //double x2=v2.getCentroidX();
    double y2 = v2.getCentroidY();
    //double x3=v3.getCentroidX();
    double y3 = v3.getCentroidY();
    //now calculate the centroid
    return ((y1 + y2 + y3) / 3);

  }

}
