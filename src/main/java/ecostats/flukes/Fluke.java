/**
 * @author Ecological Software Solutions LLC
 * @version 0.1 Alpha
 * @copyright 2014 
 * @license This program is free software; you can redistribute it and/or
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
 *
 */
package com.ecostats.flukes;

import org.apache.commons.math3.linear.ArrayRealVector;
import org.apache.commons.math3.linear.RealVector;

/**
 * Fluke
 * <p/>
 * Fluke class for points along a fluke tracing, left and right side. 
 */

public class Fluke implements java.io.Serializable {
  
  private static final long serialVersionUID = -1317654319011769206L;
  
  /**
   * TracePoint Variables
   * <p/>
   * Comments: The constants initiated here and referenced
   * in the entire package as a TracePoint immutable value
   * for each point type.
   */
  public final static int LEFT = 0;  // Left fluke constant ID
  public final static int RIGHT = 1; // Right fluke constant ID
  public final static int ALL = 2; // Both left and right fluke constant ID
  private double id = 0;
  private double matchvalue = 0;
  private String photo;
  private double[] mark_types; // a reduced mark type array combining both the left and right flukes 
  private FinTrace left_fluke;
  private FinTrace right_fluke;

  /**
   * Fluke Constructor
   * <p/>
   * Comments: Basic constructor method.
   */
  public Fluke() {

  }

  /**
   * Fluke Constructor
   * <p/>
   * @param left FlukeTrace : the left fluke trace
   * @param right FlukeTrace : the right fluke trace
   */
  public Fluke(FinTrace left, FinTrace right) {
    this.left_fluke = left;
    this.right_fluke = right;
  }

  /**
   * Fluke Constructor
   * <p/>
   * @param left FlukeTrace : the left fluke trace
   * @param right FlukeTrace : the right fluke trace
   */
  public Fluke(FinTrace left, FinTrace right, double[] mark_types) {
    this.left_fluke = left;
    this.right_fluke = right;
    this.setMarkTypes(mark_types);
  }

  /**
   * Fluke Public Methods
   */  
  
  /**
   * Returns a new Fluke with only points of mark_type retained
   * @param mark_type int : The type of mark to retain
   * @return Fluke : new Fluke object with the mark_type points retained
   */
  public Fluke findMarkType(int mark_type){
    FinTrace left = this.left_fluke.returnMarkType(mark_type);
    FinTrace right = this.right_fluke.returnMarkType(mark_type);
    Fluke f = new Fluke(left, right);
    return f;
  }
  
  /**
   * Returns a new Fluke with points of mark_type removed
   * @param mark_type int : The type of mark to remove
   * @return Fluke : new Fluke object with the mark_type points removed
   */
  public Fluke removeMarkType(int mark_type){
    FinTrace left = this.left_fluke.removeMarkType(mark_type);
    FinTrace right = this.right_fluke.removeMarkType(mark_type);
    Fluke f = new Fluke(left, right);
    return f;
  }
  
  /**
   * Gets all the X parts of the point locations as a RealVector
   * @param whatpart int : which part of the fluke to return, left, right or all points
   * @return RealVector of X point values
   */
  public RealVector getVectorX(int whatpart) {
    RealVector result = null;
    switch (whatpart){ 
      case LEFT : result = new ArrayRealVector(this.left_fluke.getX()); break;
      case RIGHT : result = new ArrayRealVector(this.right_fluke.getX()); break;
      case ALL : result = new ArrayRealVector(this.left_fluke.getX()).append(new ArrayRealVector(this.right_fluke.getX())); break; 
    }
    return result;
  }

  /**
   * Gets all the Y parts of the point locations as a RealVector
   * @param whatpart int : which part of the fluke to return, left, right or all points
   * @return RealVector of Y point values
   */
  public RealVector getVectorY(int whatpart) {
    RealVector result = null;
    switch (whatpart){ 
      case LEFT : result = new ArrayRealVector(this.left_fluke.getY()); break;
      case RIGHT : result = new ArrayRealVector(this.right_fluke.getY()); break;
      case ALL : result = new ArrayRealVector(this.left_fluke.getY()).append(new ArrayRealVector(this.right_fluke.getY())); break; 
    }
    return result;
  }

  /**
   * Gets all the Mark Type  values at each point locations as a RealVector.
   * See the FlukeTrace class for a list of Mark Types.
   * @param whatpart int : which part of the fluke to return, left, right or all points
   * @return RealVector of Mark Type point values
   */
  public RealVector getVectorMarkTypes(int whatpart) {
    RealVector result = null;
    switch (whatpart){ 
      case LEFT : result = new ArrayRealVector(this.left_fluke.getTypes()); break;
      case RIGHT : result = new ArrayRealVector(this.right_fluke.getTypes()); break;
      case ALL : result = new ArrayRealVector(this.left_fluke.getTypes()).append(new ArrayRealVector(this.right_fluke.getTypes())); break; 
    }
    return result;
  }
  
  /**
   * Gets either the Left, Right, or All FlukeTrace values.
   * @param whatpart int : which part of the fluke to return, left, right or all points
   * @return FlukeTrace of with requested whatpart values
   */
  public FinTrace getFlukeTrace(int whatpart){
    FinTrace result = null;
    switch (whatpart){ 
      case LEFT : result = this.left_fluke; break;
      case RIGHT : result = this.right_fluke; break;
      case ALL : result = this.left_fluke.append(this.right_fluke); break; 
    }
    return result;
  }
  
  /**
   * Gets all the position values at each point locations as a RealVector.
   * @param whatpart int : which part of the fluke to return, left, right or all points
   * @return RealVector of position values
   */
  public RealVector getVectorPositions(int whatpart) {
    RealVector result = null;
    switch (whatpart){ 
      case LEFT : result = new ArrayRealVector(this.left_fluke.getPositions()); break;
      case RIGHT : result = new ArrayRealVector(this.right_fluke.getPositions()); break;
      case ALL : result = new ArrayRealVector(this.left_fluke.getPositions()).append(new ArrayRealVector(this.right_fluke.getPositions())); break; 
    }
    return result;
  }
  
  /**
   * Gets all the position values at each point locations as a Matrix2D.
   * @param whatpart int : which part of the fluke to return, left, right or all points
   * @return Matrix2D of position values
   */
  public Matrix2D getMatrixPositions(int whatpart) {
    /* NOTE: new Matrix2D creates a column matrix, while Matlab creates a row matrix */
    RealVector v = getVectorPositions(whatpart);
    return new Matrix2D(v);
  }
  
  /**
   * Same as getVectorX, but returns a double[]
   * @param whatpart int : which part of the fluke to return, left, right or all points
   * @return double[] of X point values
   */
  public double[] getX(int whatpart) {
    return this.getVectorY(whatpart).toArray();
  }
  
  public double[] getXLeft(){
    return this.getX(LEFT);
  }
  
  public double[] getXRight(){
    return this.getX(RIGHT);
  }
  
  public double[] getXAll(){
    return this.getX(ALL);
  }
  
  /**
   * Same as getVectorY, but returns a double[]
   * @param whatpart int : which part of the fluke to return, left, right or all points
   * @return double[] of Y point values
   */
  public double[] getY(int whatpart) {
    return this.getVectorY(whatpart).toArray();
  }
  
  public double[] getYLeft(){
    return this.getY(LEFT);
  }
  
  public double[] getYRight(){
    return this.getY(RIGHT);
  }
  
  public double[] getYAll(){
    return this.getY(ALL);
  }
  
  /**
   * Same as getVectorMarkTypes, but returns a double[]
   * @param whatpart int : which part of the fluke to return, left, right or all points
   * @return double[] of mark type values at each point along the fluke tracing
   */
  public double[] getMarkTypes(int whatpart) {
    return this.getVectorMarkTypes(whatpart).toArray();
  }

  public double[] getMarkTypesLeft() {
    return this.getMarkTypes(LEFT);
  }
  
  public double[] getMarkTypesRight() {
    return this.getMarkTypes(RIGHT);
  }

  public double[] getMarkTypesAll() {
    return this.getMarkTypes(ALL);
  }

  /**
   * Same as getVectorPositions, but returns a double[]
   * @param whatpart int : which part of the fluke to return, left, right or all points
   * @return double[] of position values at each point along the fluke tracing
   */
  public double[] getPositions(int whatpart) {
    return this.getVectorPositions(whatpart).toArray();
  }

  public double[] getPositionsLeft() {
    return this.getPositions(LEFT);
  }
  
  public double[] getPositionsRight() {
    return this.getPositions(RIGHT);
  }

  public double[] getPositionsAll() {
    return this.getPositions(ALL);
  }

  /**
   * Sets the left or right trace for each fluke.
   * @param leftright int : Either the LEFT or RIGHT fluke
   * @param trace FlukeTrace : the actual trace of the fluke
   */
  public void setFluke(int leftright, FinTrace trace){
    switch (leftright){ 
      case LEFT : this.setLeftFluke(trace); break;
      case RIGHT : this.setRightFluke(trace); break;
    }
  }
  
  public void setLeftFluke(FinTrace trace) {
    this.left_fluke = trace;
  }

  public void setRightFluke(FinTrace trace) {
    this.right_fluke = trace;
  }

  public FinTrace getLeftFluke() {
    return this.left_fluke;
  }

  public FinTrace getRightFluke() {
    return this.right_fluke;
  }
  
  public double[] getMarkTypes(){
    return this.mark_types;
  }
  
  public void setMarkTypes(double[] mark_types){
    this.mark_types = mark_types;
  }
  
  /**
   * Returns a 4 element array of [notch open, notch closed, left curl, right culr]
   * @return double[] : return array of values
   */
  public double[] notchCurl(){
    double[] notch_curl = new double[4]; // kv will be a array such as [1,0,0,1] for a open notch with a curled right fluke
    if (this.left_fluke.getNotchOpen()){
      notch_curl[0]=1;
    }else{
      notch_curl[1]=1;
    }
    if (this.left_fluke.getCurled()){
      notch_curl[2]=1;
    }
    if (this.right_fluke.getCurled()){
      notch_curl[3]=1;
    }
    return notch_curl;
  }
  
  /**
   * Returns a 4 RealVector of [notch open, notch closed, left curl, right culr]
   * @return double[] : return RealVector of values
   */
  public RealVector notchCurlVector(){
    return new ArrayRealVector(this.notchCurl());
  }

  public String getPhoto() {
    return this.photo;
  }

  public void setPhoto(String photo) {
    this.photo = new String(photo);
  }

  public double getMatchValue() {
    return matchvalue;
  }

  public void setMatchValue(double matchvalue) {
    this.matchvalue = matchvalue;
  }

  public double getId() {
    return id;
  }

  public void setId(double id) {
    this.id = id;
  }

} 

