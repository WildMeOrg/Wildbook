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


import java.io.Serializable;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.TreeSet;

import org.apache.commons.math3.linear.RealMatrix;
import org.apache.commons.math3.linear.RealVector;
import org.apache.commons.math3.linear.ArrayRealVector;
import org.apache.commons.math3.linear.MatrixUtils;

import com.ecostats.flukes.Matrix2D;
import com.reijns.I3S.Affine;

/**
 * TraceCompare
 * <p/>
 * Compares test Fluke pattern to list of known Fluke patterns.
 */
public class TraceCompare {
  
  static final long serialVersionUID = 1;
  
  // ?? What is the point of these, and are they still needed ??
  static final double ALPHAS=0.46; //true height/width of sperm whale fluke 
  static final int RESOLUTION=99; //Some older Matlat code that defined this as a variable: -log(((xr(1)-xr(2))^2+(yr(1)-yr(2))^2)/(w*w));resolution=max([99 resolution]);
  //?? What is the point of these, and are they still needed ??
  
  static final double HALF_VALUE=0.5;//value of fluke notches matches in matching
  static final double MINVAL=0.1;//minimum value for consideration in match list
  public ArrayRealVector vvl;
  public ArrayRealVector vv;
  public ArrayRealVector dd;
  
  /**
   * Constructor
   */
  public TraceCompare() {
    //this.points=points;
    System.out.println("         Starting TraceCompare in ctor");
    this.doSetup();
  }
  
  /**
   * Sets up basic reused matrix and array parameters used in the class.
   */
  public void doSetup(){
    
    //System.out.println("          Starting doSetup!");
    // loads values for V and D matrices (original matrix values from matrix.m Matlab file)
    double[][] vmat={ { 1.0, 1.5, 1.5, 0.7, 0.7, 0.0, 0.0, 0.0, 0.0, 0.0, 0.3},
                      { 1.5, 2.0, 0.0, 1.5, 0.0, 0.8, 0.0, 0.5, 0.0, 0.0, 0.0},
                      { 1.5, 0.0, 2.0, 0.0, 1.5, 0.0, 0.8, 0.0, 0.5, 0.0, 0.0},
                      { 0.7, 1.5, 0.0, 2.0, 0.0, 1.0, 0.0, 0.5, 0.0, 0.0, 0.0},
                      { 0.7, 0.0, 1.5, 0.0, 2.0, 0.0, 1.0, 0.0, 0.5, 0.0, 0.0},
                      { 0.0, 0.8, 0.0, 1.0, 0.0, 1.0, 0.0, 0.0, 0.0, 0.0, 0.0},
                      { 0.0, 0.0, 0.8, 0.0, 1.0, 0.0, 1.0, 0.0, 0.0, 0.0, 0.0},
                      { 0.0, 0.5, 0.0, 0.5, 0.0, 0.0, 0.0, 2.0, 0.0, 0.0, 0.0},
                      { 0.0, 0.0, 0.5, 0.0, 0.5, 0.0, 0.0, 0.0, 2.0, 0.0, 0.0},
                      { 0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 2.0, 0.0},
                      { 0.3, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 2.0} };
    
    //System.out.println("          vmat done!");
    
    double[][] dmat={ { 0.025, 0.04, 0.04, 0.04, 0.04, 0.0, 0.0, 0.0, 0.0, 0.0, 0.025},
                      { 0.04, 0.04, 0.0, 0.04, 0.0, 0.06, 0.0, 0.04, 0.0, 0.0, 0.0},
                      { 0.04, 0.0, 0.04 , 0.0, 0.04 , 0.0, 0.06 , 0.0, 0.04 , 0.0, 0.0},
                      { 0.04, 0.04 , 0.0, 0.04 , 0.0, 0.06 , 0.0, 0.04 , 0.0, 0.0, 0.0},
                      { 0.04, 0.0, 0.04, 0.0, 0.04 , 0.0, 0.06, 0.0, 0.045, 0.0, 0.0},
                      { 0.0, 0.06 , 0.0, 0.06 , 0.0, 0.06666667, 0.0, 0.0, 0.0, 0.0, 0.0},
                      { 0.0, 0.0, 0.06 , 0.0, 0.06 , 0.0, 0.06666667, 0.0, 0.0, 0.0, 0.0},
                      { 0.0, 0.04 , 0.0, 0.04 , 0.0, 0.0, 0.0, 0.04, 0.0, 0.0, 0.0},
                      { 0.0, 0.0, 0.04 , 0.0, 0.045, 0.0, 0.0, 0.0, 0.04, 0.0, 0.0},
                      { 0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.06 , 0.0},
                      { 0.025, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.04} };
    
    //System.out.println("          dmat done!");
    
    // Create a 13x13 matrix for local variables ddm and vvm.
    Matrix2D ddm=new Matrix2D(13,13);
    Matrix2D vvm=new Matrix2D(13,13);
    //System.out.println("          Mid-way through doSetup!");
    // set all values to zero 
    ddm.fillMatrix(0);
    vvm.fillMatrix(0);
    // Replace the first 11x11 matrix values of ddm and vvm  
    // starting at row (0), column (0) using matrix data dmat and vmat.
    //System.out.println("          Matrices filled!");
    ddm.setSubMatrix(dmat, 0, 0);
    vvm.setSubMatrix(vmat, 0, 0);
    // using the above variables, set the public parameters
    this.vvl=(ArrayRealVector) vvm.getDiagonalVector(); 
    this.vv=this.matrixToColumnVector(vvm); // creates a single column vector from the 13x13 matrix
    this.dd=this.matrixToColumnVector(ddm); // creates a single column vector from the 13x13 matrix
    //System.out.println("          doSetup done!");
  }
  
  /* 
   * First a few useful matrix and vector methods needed by the MAIN METHODS below
   */
  
  /**
   * Turns a matrix into a single column vector.
   * @param m RealMatrix : The input matrix to return as a vector 
   * @return ArrayRealVector
   */
  public ArrayRealVector matrixToColumnVector(RealMatrix m){
    ArrayRealVector v = new ArrayRealVector(m.getRowDimension()*m.getColumnDimension());
    int i;
    int j;
    int k=0;
    for (i=0;i<m.getRowDimension();i++){
      for (j=0;j<m.getColumnDimension();j++){
        v.setEntry(k, m.getEntry(i, j));
        k+=1;
      }
    }
    return v;
  }
       
  /**
   * Subsets a source vector v where all the values in the RealVector indices parameter 
   * are the values by index to subset and return from the v parameter vector.
   * @param v RealVector : A vector of data to subset
   * @param indicies RealVector : A vector of index values to be extracted from the source vector
   * @param index_start int : start index of the vectors (should be set to 1 for Matlab like vectors, else set to 0)
   * @return RealVector subset of original source RealVector
   * <p/>
   * Test Example: 
   *   source = [3,5,1,7,9,4,2,6,8]
   *   indices = [1,5,7]
   *   result = [5,4,6]
   */
  public RealVector subsetVector(RealVector v, RealVector indicies, int index_start){
    RealVector r = this.subsetVector(v, indicies.toArray(), index_start);
    return r;
  }
  
  /**
   * Same as subsetVector with index_start parameter set to 1 by default
   * @param v RealVector : A vector of data to subset
   * @param indicies RealVector : A vector of index values to be extracted from the source vector
   * @return RealVector subset of original source RealVector
   */
  private RealVector subsetVector(RealVector v, RealVector indicies){
    RealVector r = this.subsetVector(v, indicies, 1);
    return r;
  }
  
  /**
   * Subsets a source vector v where all the values in the double[] indices parameter 
   * are the values by index to subset and return from the v parameter vector.
   * @param v RealVector : A vector of data to subset
   * @param indiciesRealVector : A vector of index values to be extracted from the source vector
   * @param index_start int : start index of the vectors (should be set to 1 for Matlab like vectors, else set to 0)
   * @return RealVector subset of original source RealVector
   * 
   * Example:
   *   v = {16,27,58,9,17,67}
   *   indices = {4,2}
   *   result = {17,58}
   */
  private RealVector subsetVector(RealVector v, double[] indicies, int index_start){
    
    
    System.out.print("     v values: "+v.toString());

    System.out.print("     index values: {");
    for(int i=0;i<indicies.length;i++){
      System.out.print(indicies[i]+",");
    }
    System.out.println("}");
    
    
    RealVector r = new ArrayRealVector(indicies.length);
    for (int i=0;i<indicies.length;i++){
      int index = (int) Math.floor(indicies[i]); 
      System.out.println("           Inside subsetVector with index: "+index);
      // note: had to subtract 1 from index since in Matlab vectors indexes start at 1, but Java vectors start as 0.
      
      //jason note: this makes no sense. why would the value of a mark-type be a positional index in another array?
      //especially since some mark_types have a negative value.
      try{
        r.setEntry(i, v.getEntry(index - index_start)); 
      }
      catch(Exception e){
        
        //now what? how do we handle common points, like FinTrace.POINT and FinTrace.TIP?
        
      }
    
    
    }
    return r;
  }

  /**
   * Subsets a source vector v where all the values in the RealMatrix indices parameter 
   * are the values by index to subset and return from the v parameter vector. 
   * @param v RealVector : A vector of data to subset
   * @param indiciesRealVector : A vector of index values to be extracted from the source vector
   * @return Matrix2D of subset values in each row from the v RealVector parameter
   * 
   * Example:
   *   v = {16,27,58,9,17,67}
   *   indices = {{4,2},{5,1}}
   *   result = {{17,58},{67,27}}
   */
  private Matrix2D subsetVector(RealVector v, RealMatrix indicies){
    Matrix2D m = new Matrix2D(indicies.getRowDimension(),indicies.getColumnDimension());
    for (int i=0;i<indicies.getRowDimension();i++){
      RealVector r = this.subsetVector(v,indicies.getRow(i), 1);
      m.setRowVector(i, r);
    }
    return m;
  }
  
  /**
   * Subsets a source vector v where all the values in the RealMatrix indices parameter 
   * are the values by index to subset and return from the v parameter vector. 
   * @param v RealVector : A vector of data to subset
   * @param indiciesRealVector : A vector of index values to be extracted from the source vector
   * @param index_start int : start index of the vectors (should be set to 1 for Matlab like vectors, else set to 0)
   * @return Matrix2D of subset values in each row from the v RealVector parameter
   * 
   * Example:
   *   v = {16,27,58,9,17,67}
   *   indices = {{4,2},{5,1}}
   *   result = {{17,58},{67,27}}
   */
  public Matrix2D subsetVector(RealVector v, RealMatrix indicies, int index_start){
    Matrix2D m = new Matrix2D(indicies.getRowDimension(),indicies.getColumnDimension());
    for (int i=0;i<indicies.getRowDimension();i++){
      RealVector r = this.subsetVector(v,indicies.getRow(i), index_start);
      m.setRowVector(i, r);
    }
    return m;
  }
  
  /**
   * Return a RealVector of values that equal the input parameter value
   * @param v RealVector : the vector to test
   * @param value double : the value to test
   * @return RealVector of matching values
   */
  public RealVector find(RealVector v, double value){
    return this.find(v, value, Matrix2D.EQ);
  }
  
  /**
   * Return a RealVector of values that equals (EQ), greater than (GT) or less than (LT) the input parameter value
   * @param v RealVector : the vector to test
   * @param value double : the value to test
   * @param int comparison : the comparison to make: equals (EQ), greater than (GT) or less than (LT)
   * @return RealVector of matching values
   */
  public RealVector find(RealVector v, double value, int comparison){
    Matrix2D m = new Matrix2D(v);
    m = m.find(value, comparison);
    if (m!=null){
      return m.getColumnVector(2);
    }else{
      return null;
    }
  }

  /**
   * Returns a RealVector of index locations where a values occurs in the test paramater RealVector v
   * @param v RealVector : the vector to test for values
   * @param value double : the value to test
   * @return RealVector of indexes where the parameter value was found in v
   */
  public RealVector findIndexOf(RealVector v, double value){
    RealVector r = new ArrayRealVector();
    int d = v.getDimension();
    for (int i=0;i<d;i++){
       if (v.getEntry(i)==value){
          r.append(i);
       }
    }
    return r;    
  }  

  /**
   * Returns a RealVector of index locations where a values occurs in the test paramater RealVector v
   * @param v RealVector : the vector to test for values
   * @param value double : the value to test
   * @param comparison int : the type of comparison to make; 
   *                         constant values of comparison in Matrix2D: equals (EQ), less than (LT) or greater than (GT),
   * @return RealVector of indexes where the parameter value was found in v
   */
  public RealVector findIndexOf(RealVector v, double value, int comparison){
    Matrix2D m = new Matrix2D(v);
    m = m.find(value, comparison);
    if (m!=null){
      // return a vector of index positions where the match was true
      return m.getColumnVector(1); 
    }else{
      return null;
    }
  }

  /**
   * Sums the element values in a vector and return a numeric value
   * @param source RealVector : Source vector with the elements to sum.
   * @return double : value as the sum of the source vector elements
   */
  public double sumVector(RealVector source){
    return sumVector(source.toArray());    
  }
  
  /**
   * Sums the element values in an array and return a numeric value
   * @param source double[] : Source array with the elements to sum.
   * @return double : value as the sum of the source vector elements
   */
  public double sumVector(double[] source){
    double sum = 0;
    for (int i=0;i<source.length;i++){ 
      sum+=source[i];
    }
    return sum;    
  }  
  
  /**
   * The following overloaded methods of 'transpose' will seamlessly transpose different types of array, matrix or vector   
   */
  
  /**
   * Transpose a matrix of double values
   * @param data double[][] : matrix as a two dimensional array of double values, 
   *                          such as double[][] data = { { 1.0, 2.0, 3.0 }, { 4, 5, 6 } }; <-- note data need not include a decimal to become a double
   * @return RealMatrix : the transposed matrix
   */
  public Matrix2D transpose(double[][] data) {
    Matrix2D matrix = (Matrix2D) MatrixUtils.createRealMatrix(data);
    return this.transpose(matrix);
  }

  /**
   * Transpose a matrix
   * @param data : matrix of RealMatrix
   * @return RealMatrix : the transposed matrix
   */
  public Matrix2D transpose(Matrix2D data) {
    return data.transpose();
  }

  /**
   * Transpose a vector of double values and return as a RealMatrix
   * @param data double[] : vector array of double values, such as double[] data = { 1.0, 2.0, 3.0 };
   * @return RealMatrix : the transposed matrix
   */
  public Matrix2D transpose(double[] data) {
    RealMatrix matrix = MatrixUtils.createColumnRealMatrix(data);
    return new Matrix2D(matrix);
  }

  /**
   * Transpose a RealVector and return as a RealMatrix
   * @param data : vector of RealVector
   * @return RealMatrix : the transposed matrix
   */
  public Matrix2D transpose(RealVector data) {
    return this.transpose(data.toArray());
  }
  
  /**
   * Reverse the data in a vector (i.e. make a mirror image of the data).
   * @param data RealVector : the vector to reverse
   * @return ArrayRealVector : a new vector that has its values reversed
   */
  public RealVector reverseVector(RealVector data) {
    int d = data.getDimension();
    ArrayRealVector v = new ArrayRealVector(d);
    for (int i=0;i<d;i++){
      v.setEntry(d-i-1, data.getEntry(i));
    }
    return v;
  }
  
  /**
   * Returns a union (common values only) of one or more RealVectors
   * @param v RealVectors : 1 or more RealVectors to union
   * @return RealVector : union of all v RealVectors
   */
  public RealVector union(RealVector... v){
    ArrayList<Double> a = new ArrayList<Double>();
    for (RealVector r : v){
      for(int i=0;i<r.getDimension();i++){
        double value = r.getEntry(i);
        if(!ifContains(a, value)){
          a.add(new Double(value));
        }
      }
    }
    RealVector t = new ArrayRealVector(a.size());
    for(int i = 0; i < a.size(); i++){
      t.setEntry(i,a.get(i).doubleValue());
    }
    return t;
  }

  /**
   * Private method of union method that tests if the resulting union vector already contains a value or not.
   * @param a ArrayList : List to test
   * @param value double : value to test
   * @return Boolean: true if value is already in List "a" false otherwise
   */
  private boolean ifContains(ArrayList<Double> a, double value){
    if (a.contains(new Double(value))) return true;
    return false;
  }
  
  
  /*  MAIN METHODS  */

  
  /**
   * Get fluke "distance" measurement data, get some default parameter values
   * @param fluke Fluke : a Fluke object
   * @return double summary value of tip curve and notch type (open or close)
   */
  public double getFlukePt(Fluke fluke) {
    double[] notch_curl = fluke.notchCurl();
    return this.pt(fluke, notch_curl);
  }
  
  /**
   * Returns a "pt" value for known flukes in a database (never figured out what "pt" was suppose to mean in original code).
   * @param fluke Fluke : Fluke to process
   * @param notch_curl double[] : a boolean (i.e. 0 or 1) 1x4 array of notch type and left or right fluke curl
   * @return double : value of "pt"
   */
  private double pt(Fluke fluke, double[] notch_curl){
    RealVector v = this.markTypesNoNotchTip(fluke); // note, mark types will be a vector of values, not a single number
    try{
    	return this.sumVector(this.subsetVector(this.vv,(v.mapSubtract(1)).mapMultiply(13).add(v)))+sumVector(notch_curl)*HALF_VALUE;
    }catch (Exception e){
    	return 0.0;
    }
  }
  
  /**
   * Returns the mark types as a RealVector after stripping out the tip and notch mark types
   * @param fluke Fluke : the fluke to process
   * @return RealVector : a RealVector of mark types
   */
  public RealVector markTypesNoNotchTip(Fluke fluke){
    // returns same as urttx in original Matlab code
    RealVector l = fluke.getVectorMarkTypes(Fluke.LEFT); // getVectorMarkTypes is same as typu in original code
    l = l.getSubVector(1, l.getDimension()-2);
    RealVector r = fluke.getVectorMarkTypes(Fluke.RIGHT);
    r = r.getSubVector(1, r.getDimension()-2);
    return l.append(r);
  }
  
  /**
   * Returns a "pt" value for test fluke (never figured out what "pt" was suppose to mean in original code).
   * @param fluke Fluke : the Fluke to process
   * @return double : the "pt" value
   */
  public double getPtx(Fluke fluke){
    // value of fluke
    RealVector urttx = this.markTypesNoNotchTip(fluke);
    System.out.println("     RealVector urttx is: "+urttx.toString());
    System.out.println("     about to callSumVector with: "+(this.subsetVector(vvl,urttx)));
    System.out.println("     and then going to add: "+HALF_VALUE*this.sumVector(fluke.notchCurlVector()));
    double ptx=this.sumVector(this.subsetVector(vvl,urttx))+HALF_VALUE*this.sumVector(fluke.notchCurlVector());
    return ptx;
  }
  
  /**
   * Returns a RealVector of calculated adjusted and standardized relative "distance" value for each point location.
   * @param fluke Fluke : a Fluke object with data
   * @param adjust double : an x shift adjustment value added to the final result
   * @return RealVector : a calculated adjusted and standardized relative "distance" value for each tracing point location
   */
  public RealVector tracingDistanceIndex(Fluke fluke){
    // returns same value as xltx in original code
    RealVector v_left;
    RealVector v_right;
    // get the left fluke reduced distance vector
    RealVector x = fluke.getVectorX(Fluke.LEFT);
    RealVector y = fluke.getVectorY(Fluke.LEFT);
    v_left = this.flukeSideDistanceIndex(x, y, 0);
    // get the right fluke reduced distance vector 
    // Note: Reverse the right vectors so points go with increasing X values to "notch" 
    //       so the single tracePos method works for both left and right flukes.
    x = this.reverseVector(fluke.getVectorX(Fluke.RIGHT));
    y = this.reverseVector(fluke.getVectorY(Fluke.RIGHT));
    v_right = this.flukeSideDistanceIndex(x, y, HALF_VALUE);
    // append the right distance vector to the left distance vector
    return v_left.append(v_right);
  }
  
  /**
   * Calculates the reduced distance vector for each side of a fluke.
   * @param x RealVector : Vector of x trace position locations.
   * @param y RealVector : Vector of x trace position locations.
   * @param adjust double : Shift adjustment for the vector values
   * @return RealVector that is the reduced distance vector for the fluke side
   */
  private RealVector flukeSideDistanceIndex(RealVector x, RealVector y, double adjust){
    RealVector v = new ArrayRealVector();
    int notch = x.getDimension(); // fluke notch should be the last value of the tracing vector
    if (notch<=2){
    	return v;
    }
    // get sub-vectors between fluke tip index and notch index, non-inclusive
    RealVector xn=x.getSubVector(1,notch-2);
    RealVector yn=y.getSubVector(1,notch-2);
    // assign some intermediate variable values
    double x0 = x.getEntry(0); // first x element value
    double y0 = y.getEntry(0); // first y element value
    double xl = x.getEntry(notch-1); // last x element value
    double yl = y.getEntry(notch-1); // last y element value   
    double xd = x0-xl; // distance between tip and notch (non-inclusive), x direction
    double yd = y0-yl; // distance between tip and notch (non-inclusive), y direction
    double xyd = (Math.pow((xd),2)+Math.pow((yd),2));
    // calculate a standardized distance value for each point
    RealVector vx = (xn.mapMultiply(-1).mapAdd(x0));
    RealVector vy = (yn.mapMultiply(-1).mapAdd(y0));
    //NOTE: vx.combine(xd,yd,vy) is the same as ((vx.mapMultiply(xd)).add(vy.mapMultiply(yd)));
    v = vx.combine(xd, yd, vy).mapDivide(xyd).mapMultiply(HALF_VALUE).mapAdd(adjust);
    return v;       
  }
  
  private RealVector getTransformedDistances(Fluke known, Fluke test){
	  FinTrace fin_left;
	  FinTrace fin_right;
	  RealVector result;
	  // To re-scale one Fluke on the other, we need three control points to create the transform matrix.
	  // The most likely to be comparable are the fluke tips and notch. So use the built in methods
	  // of the FinTrace class to return just the tip and notch of the left fluke and the tip of the right fluke.
	  int[] l = {FinTrace.TIP,FinTrace.NOTCH};
	  fin_left = known.getLeftFluke().returnMarkType(l);
	  fin_right = known.getLeftFluke().returnMarkType(FinTrace.TIP);
	  if (fin_left.getX().length<2){ return null;}
	  double from1x = fin_left.getX()[0];
	  double from1y = fin_left.getY()[0];
	  double from2x = fin_left.getX()[1];
	  double from2y = fin_left.getY()[1];
	  if (fin_right.getX().length==0){ return null;}
	  double from3x = fin_right.getX()[0]; 
	  double from3y = fin_right.getY()[0];
	  fin_left = test.getLeftFluke().returnMarkType(l);
	  fin_right = test.getLeftFluke().returnMarkType(FinTrace.TIP);
	  if (fin_left.getX().length<2){ return null;}
	  double to1x = fin_left.getX()[0];
	  double to1y = fin_left.getY()[0];
	  double to2x = fin_left.getX()[1];
	  double to2y = fin_left.getY()[1];
	  if (fin_right.getX().length==0){ return null;}
	  double to3x = fin_right.getX()[0]; 
	  double to3y = fin_right.getY()[0];
	  // use the com.reijns.I3S.Affine method to get the transformation matrix
	  double[] ident_matrix = {0,0,0,0,0,0}; 
	  Affine.calcAffine(from1x, from1y, from2x, from2y, from3x, from3y, to1x, to1y, to2x, to2y, to3x, to3y, ident_matrix);
	  // compute the transformation of each point in the known Fluke (save to change this class values, as changes will not be saved to the database).
	  this.doAffine(fin_left,ident_matrix);
	  this.doAffine(fin_right,ident_matrix);
	  // for comparisons, get the relative calculated distances between nodes base on this transformed node locations.
	  result = this.tracingDistanceIndex(known);
	  return result;
  }

  /**
   * @param matrix
   */
  private void doAffine(FinTrace fin, double[] matrix) {
	  double x,y;
	  for (int i = 0; i < fin.size(); i++) {
		  x = fin.getX(i);
		  y = fin.getY(i);		
		  x = matrix[0] * x + matrix[1] * y + matrix[2];
		  y = matrix[3] * x + matrix[4] * y + matrix[5];
		  fin.setX(i, x);
		  fin.setY(i, y);
	  }
  }

  /**
   * The Fluke comparison method. Given an input parameter test_fluke, check all input parameter flukes
   * to see if test_fluke matches any of them.
   * @param flukes Flukes : a Flukes class of flukes (from a database)
   * @param test_fluke Fluke : the Fluke to test
   * @return TreeSet : a sorted set of possible matching Flukes
   */
  public TreeSet<Fluke> processCatalog(List<Fluke> flukes, Fluke test_fluke){
    /*
     * Note: This method should be refactored more; into more sub-methods that encapsulate logic better by variable
     */
    double corrx;
    double corrc;
    int test_directions = 2;
    TreeSet<Fluke> ts = new TreeSet<Fluke>(new MatchComparator());
    // variables
    double[] mv = {0, 0};
    RealVector marktypes_known; // distance vector of known flukes from database
    RealVector distance_test = this.tracingDistanceIndex(test_fluke);
    if (distance_test.getDimension()==0){
    	// either the fluke to test has no data, or there are no node tags identifying the fluke structures
    	return null;
    }
    RealVector marktypes_test = this.markTypesNoNotchTip(test_fluke);
    double ptx = this.getPtx(test_fluke);
    // processing
    for (int c=0;c<flukes.size();c++){
      Fluke fluke = flukes.get(c);      
      RealVector distance_known = this.getTransformedDistances(fluke, test_fluke); //this.tracingDistanceIndex(fluke); //.getVectorPositions(Fluke.ALL); // distance_known is the distance index vector from a known fluke to compare with the test fluke
      if (distance_known == null){
    	  continue;
      }
      marktypes_known = this.markTypesNoNotchTip(fluke); // mark_types is an list of vectors with values of mark type (so mark_types is a vector of mark types for the current catalog record)    
      int length_mark_types = marktypes_known.getDimension(); // lengths of the array of mark type values (i.e. mark_types is an array of numbers)
      int length_mark_types_test_fluke = distance_test.getDimension();
      // test_direction = 1 do just one pass, else if test_direction = 2 test both directions (i.e. reverse)
      for (int test_direction=0; test_direction<test_directions; test_direction++){ 
          double[] notch_curl_temp = fluke.notchCurl(); // return the notch and fluke tips curl information array
          // swap data around depending on the direction of the test along the fluke
          if (test_direction==2){
              marktypes_known = this.reverseVector(marktypes_known); // reverse the order              
              distance_known = distance_known.mapMultiply(-1);
              // just reverse the left and right curl parts (index 2 and 3 of zero based array)
              double t = notch_curl_temp[2];
              notch_curl_temp[2] = notch_curl_temp[3];
              notch_curl_temp[3] = t;
          }
          RealMatrix notch_curl = this.transpose(notch_curl_temp);
          distance_known = distance_known.mapDivide(6000).mapAdd(HALF_VALUE); // why divide by the constant of 6000? (from original code)
          Matrix2D tm = new Matrix2D(test_fluke.notchCurlVector());
          Matrix2D cm = new Matrix2D(notch_curl.getColumnMatrix(0));
          // Note: RealMatrix * RealMatrix uses pre-multiple, Matlab uses post-multiply, so reverse the order.
          RealMatrix notch_curl_compare = cm.transpose().multiply(tm).scalarMultiply(HALF_VALUE);
          // Note: tm and cm are 1x4 and 4x1 matrices, so their multiplication should be a matrix with a single value
          mv[test_direction] = notch_curl_compare.getEntry(0, 0); //match value of notches, curled flukes
          if ((length_mark_types*length_mark_types_test_fluke)>0){
             Matrix2D dismat = this.distanceMatrix(distance_known, marktypes_known, distance_test, marktypes_test);
             // The find method of Matlab returns a vector of [original row, original column, value] for each found value. 
             // See the Matrix2D.find method for more details. 
             Matrix2D nzv = dismat.find(0,Matrix2D.GT); // return a matrix of all dismat values greater than zero
             if (nzv==null){continue;} // continue to next database fluke if there is no valid dismat data
             nzv = nzv.multiply(-1).sort(2).multiply(-1); // this sorts negative values in ascending order         
             Matrix2D qt1 = new Matrix2D(distance_test.getDimension(),1); // create a single column matrix
             Matrix2D qt2 = new Matrix2D(1,marktypes_known.getDimension()); // create a single row matrix  
             qt1.fillMatrix(1);
             qt2.fillMatrix(1);
             for (int l=0;l<nzv.getRowDimension();l++){ //Match value of points
               // Matrix2D only stores double values, but since columns 0 and 1 are row and column references safe to cast to int.
               int row = (int)nzv.getEntry(l, 0); 
               int col = (int)nzv.getEntry(l, 1);
               // sum current mv values (based on current test direction) only if qt1 and at2 are both non-zero
               if (qt1.getEntry(row,0)*qt2.getEntry(0,col)>0){ 
                  mv[test_direction] = mv[test_direction] + nzv.getEntry(l,2); 
                  // set qt1 and qt2 to zero to prevent these value references from being re-used again in the mv sum value
                  qt1.setEntry(row, 0, 0);
                  qt2.setEntry(0, col, 0);
               }
             } 
             // get "corr" values for test_fluke (x) and current fluke (c)
             corrx = getCorr(distance_test, distance_known, marktypes_known, marktypes_test);
             corrc = getCorr(distance_known, distance_test, marktypes_test, marktypes_known);     
          }else{
             corrx = 0;
             corrc = 0;
          }
          double ptx_corrx = ptx-corrx;
          double ptc_corrc = getFlukePt(fluke)-corrc;
          // get max value for the current test direction
          if (ptx_corrx>ptc_corrc){
            mv[test_direction] = mv[test_direction]/ptx_corrx;
          }else{
            mv[test_direction] = mv[test_direction]/ptc_corrc;
          }
      }
      // get the max value in either test direction
      int mv_max = 0;
      if (mv[1]>mv[0]){
        mv_max = 1;
      } 
      // set the "matchvalue" result between test_fluke and the current fluke from Flukes to fluke
      fluke.setMatchValue(mv[mv_max]);
      ts.add(fluke);
    }
    return ts;
  }
  
  /**
   * Get a correction factor for missing or invisible parts.
   * @param d0 RealVector : Distance vector
   * @param d1 RealVector : Distance vector
   * @param m0 RealVector : Mark types vector
   * @param m1 RealVector : Mark types vector
   * @return double: correction factor
   */
  public double getCorr(RealVector d0, RealVector d1, RealVector m0, RealVector m1){  
    /*  Some basic nomenclature for below variable names
     *  ip = invisible parts, mp = missing parts, s = start, e = end, x = index
     */
    double corr = 0;
    RealVector ipsx = this.findIndexOf(m1,12); // 12 = mark type code for start of invisible parts
    RealVector ipex = this.findIndexOf(m1,13); // 13 = mark type code for end of invisible parts 
    for (int m=0;1<ipsx.getDimension();m++){ // Correction for invisible for new fluke tracing being tested
      RealVector ipsd = this.findIndexOf(d1, d0.getEntry((int) ipsx.getEntry(m)), Matrix2D.GT);
      RealVector iped = this.findIndexOf(d1, d0.getEntry((int) ipex.getEntry(m)), Matrix2D.LT);
      RealVector start_end_index = this.union(ipsd, iped);
      RealVector vector_subset = this.subsetVector(vvl,this.subsetVector(m0,start_end_index));
      corr += this.sumVector(vector_subset);
    }
    RealVector mpsx = this.findIndexOf(m1,8); // 8 = mark type code for start of missing parts
    RealVector mpex = this.findIndexOf(m1,9); // 9 = mark type code for end of missing parts
    for (int m=0;1<mpsx.getDimension();m++){ // Correction for missing for new fluke tracing being tested
      RealVector mpsd = this.findIndexOf(d1, d0.getEntry((int) mpsx.getEntry(m)), Matrix2D.GT);
      RealVector mped = this.findIndexOf(d1, d0.getEntry((int) mpex.getEntry(m)), Matrix2D.LT);
      RealVector start_end_index = this.union(mpsd, mped);
      RealVector vector_subset = this.subsetVector(vvl,this.subsetVector(m0,start_end_index));
      corr += this.sumVector(vector_subset);
    }              
    return corr;
  }

  /** Return a distance matrix comparison between a known trace and a test trace
   * @param distance_known RealVector
   * @param marktypes_known RealVector
   * @param distance_test RealVector
   * @param marktypes_test RealVector
   * @return Matrix2D of the distance matrix
   */
  public Matrix2D distanceMatrix(RealVector distance_known, RealVector marktypes_known, RealVector distance_test, RealVector marktypes_test) {
    /* NOTE: new Matrix2D creates a column matrix, while Matlab creates a row matrix,
     *       so transpose operations will be reversed in this code compared to Matlab code.
     */
    Matrix2D row_matrix = new Matrix2D(1,marktypes_known.getDimension()); // create a single row matrix
    Matrix2D col_matrix = new Matrix2D(distance_test.getDimension(),1); // create a single column matrix
    Matrix2D urttx = new Matrix2D(marktypes_test); // convert vector to matrix needed for below linear algebra methods
    Matrix2D urttc = new Matrix2D(marktypes_known); // convert vector to matrix needed for below linear algebra methods
    Matrix2D xltx = new Matrix2D(distance_test); // convert vector to matrix needed for below linear algebra methods
    Matrix2D xltc = new Matrix2D(distance_known); // convert vector to matrix needed for below linear algebra methods
    row_matrix.fillMatrix(1); // fill row matrix with ones
    col_matrix.fillMatrix(1); // fill column matrix with ones
    // Process the data as follows
    Matrix2D m1 = urttx.multiply(row_matrix).subtract(1).multiply(13);
    Matrix2D m2 = col_matrix.multiply(urttc.transpose());
    Matrix2D ptcont=m1.add(m2);//posn on matrix
    Matrix2D vvp=this.subsetVector(vv,ptcont);
    Matrix2D ddp=this.subsetVector(dd,ptcont);
    if (distance_test.getDimension()==1){
      vvp=vvp.transpose();
      ddp=ddp.transpose();
    }        
    // original code: dismat = vvp.*(1-abs(xltx'*ones(1,le)-ones(lex,1)*xltc)./(0.0001+ddp))
    // new code: uses intermediate variable assignments (better for debugging)
    m1 = xltx.multiply(row_matrix);
    m2 = col_matrix.multiply(xltc.transpose());
    m2 = m1.subtract(m2).abs();
    m2 = m2.elementsDivide(ddp.add(0.0001)); // Add 0.0001 to prevent divide by zero errors?
    m2 = m2.multiply(-1).add(1); // class methods that are the same as 1 - m2
    Matrix2D dismat=vvp.elementsMultiply(m2); //similarity between points
    return dismat;
  }
  

}

/**
 * Sorts the TreeSet<Fluke> variable in the processCatalog method
 */
class MatchComparator implements Comparator<Fluke> {

  @Override
  public int compare(Fluke arg0, Fluke arg1) {
    if (arg0.getMatchValue() > arg1.getMatchValue()){
      return +1;
    }else if (arg0.getMatchValue() < arg1.getMatchValue()){
      return -1;
    }else{
      return 0;
    }
  }
  
}

