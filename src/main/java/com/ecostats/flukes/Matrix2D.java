/**
 * 
 * @author Ecolgoical Software Solutions LLC
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
import java.lang.Math;
import java.util.ArrayList;
import java.util.Arrays;

import org.apache.commons.math3.linear.RealMatrix;
import org.apache.commons.math3.linear.RealVector;
import org.apache.commons.math3.linear.ArrayRealVector;
import org.apache.commons.math3.linear.Array2DRowRealMatrix;
import org.apache.commons.math3.linear.MatrixUtils;


/*
 * NOTE: All methods without information comment headers are simply re-exposed methods from the parent class
 * returning a Matrix2D class type. See the org.apache.commons.math3.linear help for information on these
 * methods.
 */

public class Matrix2D extends Array2DRowRealMatrix{

	/**
	 * serialVersionUID
	 */
	private static final long serialVersionUID = -4563008511979224612L;
	
	private static final int maxIterations=100;
	public static final int EQ=0; // equal
	public static final int LT=1; // less than
	public static final int GT=2; // greater than
	
	/*
	 * Constructors
	 */
	
	public Matrix2D() {
		super();
	}

	public Matrix2D(int rowDimension, int columnDimension) {
		super(rowDimension, columnDimension);
	}

	public Matrix2D(double[] v) {
		super(v);
	}

	public Matrix2D(double[][] d) {
		super(d);
	}

	public Matrix2D(RealVector v) {
		super(v.toArray());
	}

  public Matrix2D(RealMatrix m) {
    this(m.getData());
  }

  /**
   * Provides a method to subtract a scalar value from each item in a matrix
   * @param d double : value to subtract
   * @return Matrix2D with values d subtracted
   */
  public Matrix2D scalarSubtract(double d){
		final RealMatrix rm = this.scalarAdd(-d);
		return new Matrix2D(rm);
	}
	
  /**
   * Just a different method name for scalarSubtract
   * @param d double : value to subtract
   * @return Matrix2D with values d subtract
   */
  public Matrix2D subtract(double d){
    final RealMatrix rm = this.scalarAdd(-d);
    return new Matrix2D(rm);
  }

  /**
   * Method add will do an element by element subtraction. 
   * Matrix m must be of the same dimensionality (i.e. row and column size must match).
   * @param m RealMatrix : Matrix B element to subtract to Matrix A elements (define 'this' to be Matrix A in standard notation).
   * @return RealMatrix : Matrix with subtracted element values
   */
  public Matrix2D subtract(final Matrix2D m){
    final RealMatrix rm = super.subtract(m);
    return new Matrix2D(rm);
  }

  /**
   * Just a different method name for scalarAdd available in the parent class
   * @param d double : value to add
   * @return Matrix2D with values d added
   */
  public Matrix2D add(double d){
    final RealMatrix rm = super.scalarAdd(d);
    return new Matrix2D(rm);
	}

  /**
   * Method add will do an element by element addition. 
   * Matrix m must be of the same dimensionality (i.e. row and column size must match).
   * @param m RealMatrix : Matrix B element to add to Matrix A elements (define 'this' to be Matrix A in standard notation).
   * @return Matrix2D : Matrix with added element values
   */
  public Matrix2D add(final RealMatrix m){
	  final RealMatrix rm = super.add(m);
    return new Matrix2D(rm);
  }
  
  /**
   * Method add will do an element by element addition. 
   * Matrix m must be of the same dimensionality (i.e. row and column size must match).
   * @param m Matrix2D : Matrix B element to add to Matrix A elements (define 'this' to be Matrix A in standard notation).
   * @return Matrix2D : Matrix with added element values
   */
  public Matrix2D add(final Matrix2D m){
    final RealMatrix rm = super.add(m);
    return new Matrix2D(rm);
  }

  /**
   * Just a different method name for scalarMultiply available in the parent class
   * @param d double : value to multiply
   * @return Matrix2D with values multiplied by d
   */
  public Matrix2D multiply(double d){
		final RealMatrix rm = this.scalarMultiply(d);
		return new Matrix2D(rm);
	}

  /**
   * Method add will do an element by element multiplication. 
   * Matrix m must be of the same dimensionality (i.e. row and column size must match).
   * @param m Matrix2D : Matrix B element to multiply to Matrix A elements (define 'this' to be Matrix A in standard notation).
   * @return Matrix2D : Matrix with multiplied element values
   */
  public Matrix2D multiply(Matrix2D m){
    final RealMatrix rm = super.multiply(m);
    return new Matrix2D(rm);
  }
  
  /**
   * Method add will do an element by element multiplication. 
   * Matrix m must be of the same dimensionality (i.e. row and column size must match).
   * @param m RealVector : Matrix B element to multiply to Matrix A elements (define 'this' to be Matrix A in standard notation).
   * @return Matrix2D : Matrix with multiplied element values
   */
  public Matrix2D multiply(RealVector v){
    Matrix2D m = new Matrix2D(v);
    final RealMatrix rm = super.multiply(m);
    return new Matrix2D(rm);
  }

  /**
   * Provides a method to divide each item in a matrix by a scalar value
   * @param d double : value to divide
   * @return Matrix2D with values divided by d
   */
  public Matrix2D scalarDivide(double d){
    final RealMatrix rm = this.scalarMultiply(1/d);
    return new Matrix2D(rm);
  }
  
  /**
   * Just a different method name for scalarDivide
   * @param d double : value to divide
   * @return Matrix2D with values divided by d
   */
  public Matrix2D divide(double d){
    final RealMatrix rm = this.scalarMultiply(1/d);
    return new Matrix2D(rm);
  }

	/**
	 * Division solves a system of equations that has a unique solution to: x*A = B.
	 * @param b RealVector : The B vector in the equation above. Matrix A is define as 'this'.
	 * @return Matrix2D : Solution to equation.
	 * <p/>
	 * Test example:
	 * A = {{1, 1, 3},{2, 0, 4},{-1, 6, -1}};
	 * B = {2, 19, 8};
	 * x = B/A
	 * x =  1.0000    2.0000    3.0000
	 */
	public Matrix2D divide(RealVector b){
    /* The IterativeLinearSolver class defines a solver for the linear system Ax = B.
	   * Where A is a matrix and B is a vector.
	   * Further:
	   *   x = B/A is the solution to the equation xA = B
	   *   x = A\B is the solution to the equation Ax = B
	   *   B/A = (A'\B')'.  Where ' is a notation for the transpose of the matrix.
	   * So we re-arrange input values to fit the IterativeLinearSolver class
	   */
	  return null; // solving a linear equation not currently needed.
	}
	
	/**
	 * Return the sum of all elements in a matrix
	 * @return
	 */
	public double sum(){
	  double result = 0;
    for (int i=0;i<this.getRowDimension();i++){
      for (int j=0;j<this.getColumnDimension();j++){
        result+=this.getEntry(i, j);
      }
    }	  
    return result;
	}

	public Matrix2D transpose(){
		final RealMatrix rm = super.transpose();
		return new Matrix2D(rm);
	}

	/**
	 * Method elementsMultiply will do an element by element multiplication. The two input
	 * matrix params must be of the same dimensionality (i.e. row and column size must match).
	 * <p/>
	 * Test: ma={{1,2,3},{4,5,6}}, mb={{2,2,2},{3,3,5}}
	 * Result: mc={{2.0,4.0,6.0},{12.0,15.0,30.0}}
	 * @param m RealMatrix : Matrix B to multiply by Matrix A (define 'this' to be Matrix A in standard notation).
	 * @return RealMatrix : Matrix with multiplied element values
	 */
	public Matrix2D elementsMultiply(RealMatrix m){
		if (this.getColumnDimension()==m.getColumnDimension() & this.getRowDimension()==m.getRowDimension()){
			double value;
			Matrix2D mc = new Matrix2D(this.getRowDimension(),this.getColumnDimension());
			for (int row=0;row<this.getRowDimension();row++){
				for(int column=0;column<this.getColumnDimension();column++){
					value = this.getEntry(row, column)*m.getEntry(row, column);
					mc.setEntry(row,column,value);
				}
			}
			return mc;
		}else{
			return null;
		}
	}
	
	/**
	 * Method elementsDivide will do an element by element division. The two input
	 * matrix params must be of the same dimensionality (i.e. row and column size must match).
	 * Same as ./ in Matlab code
	 * <p/>
	 * Test: ma={{1,2,3},{4,5,6}}, mb={{2,2,2},{3,3,5}}
	 * Result: mc={{2.0,4.0,6.0},{12.0,15.0,30.0}}
	 * @param m RealMatrix : Matrix B to divide Matrix A (define 'this' to be Matrix A in standard notation).
	 * @return RealMatrix : Matrix with divided element values
	 */
	public Matrix2D elementsDivide(RealMatrix m){
		if (this.getColumnDimension()==m.getColumnDimension() & this.getRowDimension()==m.getRowDimension()){
			double value;
			Matrix2D mc = new Matrix2D(this.getRowDimension(),this.getColumnDimension());
			for (int row=0;row<this.getRowDimension();row++){
				for(int column=0;column<this.getColumnDimension();column++){
					value = this.getEntry(row, column)/m.getEntry(row, column);
					mc.setEntry(row,column,value);
				}
			}
			return mc;
		}else{
			return null;
		}
	}    
  
	/**
	 * Returns just the diagonal values in a matrix and sets all other values to zero.
	 * @return Matrix2D : the new matrix of just diagonal values not set to zero
	 */  
	public Matrix2D getDiagonal(){
		if (this.isSquare()){
			RealMatrix result=MatrixUtils.createRealIdentityMatrix(this.getColumnDimension());
			return this.elementsMultiply(result);
		} else{ // else throw exception (better)
			return null;
		}
	}
  
  /**
   * Returns just the diagonal values in a matrix as a RealVector.
   * @return RealVector : the new vector of just diagonal values 
   */  
  public RealVector getDiagonalVector(){
    if (this.isSquare()){
      RealMatrix m=this.getDiagonal();
      int s = this.getColumnDimension();
      double[] d = new double[s];
      for (int i=0;i<s;i++){
        d[i]=this.getEntry(i, i);
      }
      RealVector v = new ArrayRealVector(d);
      return v;
    } else{ // else throw exception (better)
      return null;
    }
  }

  /**
   * Fills the current matrix with the value "fill_value".
   * @param fill_value : every element in the matrix will be set to this value
   */
	public void fillMatrix(double fill_value){
		for (int i=0;i<this.getRowDimension();i++){
			for (int j=0;j<this.getColumnDimension();j++){
				this.setEntry(i, j, fill_value);
			}
		}
	}
	
	/**
	 * Returns a copy of the matrix with every value set to its absolute value
	 * @return new matrix with absolute values
	 */
	public Matrix2D abs(){
	  Matrix2D mc = new Matrix2D(this.getRowDimension(),this.getColumnDimension());
	  for (int i=0;i<this.getRowDimension();i++){
	    for (int j=0;j<this.getColumnDimension();j++){
	      double val=this.getEntry(i, j);
	      mc.setEntry(i, j, Math.abs(val));
	    }
	  }
    return mc;  
	}
	
	/**
	 * Special shorthand case for "fine" methods where comparison is "equals"
	 * @param value double : the value to find and compare
	 * @return Matrix2D of Nx3 size, where each row is the the {original row, original col, value} of matching values.
	 */	
	public Matrix2D find(double value){
	  return this.find(value,EQ);
	}
	 
	/**
	 * Find all values in the matrix that matches value (equal to, less than or greater than) based on comparison parameter.
	 * @param value double : the value to find and compare
	 * @param comparison int : comparison constant in this class (EQ, LT, GT)
	 * @return Matrix2D : A matrix of size Nx3 where each row is the {original row, original col, value} of matching values.
	 */
	public Matrix2D find(double value, int comparison){
	  ArrayList<double[]> a = new ArrayList<double[]>();
    for (int i=0;i<this.getRowDimension();i++){
      for (int j=0;j<this.getColumnDimension();j++){
        double val=this.getEntry(i, j);
        if ((comparison==EQ & val==value) | (comparison==LT & val<value) | (comparison==GT & val>value)){
          double[] e = {i, j, val};
          a.add(e);
        }
      }
    }	  
    if (a.size()>0){
      Matrix2D m = new Matrix2D(a.size(),3); 
      for (int i=0;i<a.size();i++){
        m.setRow(i, a.get(i));
      }
  	  return m;
    }else{
      return null;
    }
	}
	
	/**
	 * Sorts a matrix based on values in one column
	 * @param which_column int : the column to sort by
	 * @return Matrix2D : a new sorted matrix
	 */
	public Matrix2D sort(int which_column){
	  Matrix2D m = new Matrix2D(this.getRowDimension(),this.getColumnDimension());
	  double[] sortArray = this.getColumn(which_column);
	  Arrays.sort(sortArray);
	  double[] colArray = this.getColumn(which_column);
	  for (int i=0;i<colArray.length;i++){
	    int r = Arrays.binarySearch(sortArray, colArray[i]);
	    m.setRow(r, this.getRow(i));
	  }
	  return m;
	}
	  
}
