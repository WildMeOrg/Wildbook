package com.ecostats.flukes.test;

import static org.junit.Assert.*;

import java.util.Arrays;

import org.junit.After;
import org.junit.AfterClass;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;

import com.ecostats.flukes.Matrix2D;

public class Matrix2DTest {

	static double[] v1={1,2,3};
	static double[][] d1={{1,2,3},{4,5,6}};
	static double[][] d2={{2,2,2},{3,3,5}};
	static double[][] d3={{2,4,2},{6,8,12}};

	
	@BeforeClass
	public static void setUpBeforeClass() throws Exception {

	}

	@AfterClass
	public static void tearDownAfterClass() throws Exception {
	}

	@Before
	public void setUp() throws Exception {

	}

	@After
	public void tearDown() throws Exception {
	}

	@Test
	public void testMatrix2DIntInt() {
		Matrix2D m = new Matrix2D();
		// Tests
		assertEquals("Empty matrix has no columns", 0, m.getColumnDimension());
		assertEquals("Empty matrix has no rows", 0, m.getRowDimension());
	}

	@Test
	public void testMatrix2DDoubleArray() {
		// creates a column array from the vector (i.e. number or rows will equal vector length)
		double[] v={1,2,3};
	  Matrix2D m = new Matrix2D(v);
		// Tests
	  double[][] vt={{1.0},{2.0},{3.0}};
	  assertTrue("Matrix should match construction vector.", Arrays.deepEquals(m.getData(),vt));
	}

	@Test
	public void testMatrix2DDoubleArrayArray() {
	  Matrix2D m = new Matrix2D(d1);
		// Tests
	  //  System.out.print(m);
	  assertTrue("Matrix should match construction array.", Arrays.deepEquals(m.getData(),d1));
	}

	@Test
	public void testMatrix2DRealMatrix() {
		// create a Matrix2D matrix from another Matrix2D matrix
	  Matrix2D m = new Matrix2D(d1);
	  Matrix2D mm = new Matrix2D(m);
		// Tests
	  assertTrue("Matrix should match construction matrix array.", Arrays.deepEquals(mm.getData(),d1));
	}

	@Test
	public void testScalarSubtract() {
		// Subtract value from each matrix element
	  Matrix2D m = new Matrix2D(d1);
	  Matrix2D mm = m.scalarSubtract(1);
		// Tests
	  double[][] dt={{0,1,2},{3,4,5}};
	  assertTrue("Matrix subtract should not change original matrix.", Arrays.deepEquals(m.getData(),d1));
	  assertTrue("Matrix subtract not correct.", Arrays.deepEquals(mm.getData(),dt));
	}

	@Test
	public void testScalarDivide() {
		// divide each matrix element by a value
	  Matrix2D m = new Matrix2D(d3);
	  Matrix2D mm = m.divide(2);
		// Tests
	  double[][] dt={{1,2,1},{3,4,6}};
	  assertTrue("Matrix divide should not change original matrix.", Arrays.deepEquals(m.getData(),d3));
	  assertTrue("Matrix divide not correct.", Arrays.deepEquals(mm.getData(),dt));
	}

	@Test
	public void testAddDouble() {
		// same results expected as scalarAdd
	  Matrix2D m = new Matrix2D(d1);
	  Matrix2D mm = m.add(1);
		// Tests
	  double[][] dt={{2,3,4},{5,6,7}};
	  assertTrue("Matrix add should not change original matrix.", Arrays.deepEquals(m.getData(),d1));
	  assertTrue("Matrix add not correct.", Arrays.deepEquals(mm.getData(),dt));
	}

  @Test
  public void testsum() {
    // create a matrix of values
    Matrix2D m = new Matrix2D(d1);
    double s = m.sum();
    // Tests
    assertEquals("Matrix sum not correct.", 21, s, 0);
  }
  
  @Test
  public void testAddRealMatrix() {
    // Element by element addition of two matrices
    Matrix2D m1 = new Matrix2D(d1);
    Matrix2D m2 = new Matrix2D(d2);
    Matrix2D mm = m1.add(m2);
    // Tests
    double[][] dt={{3.0,4.0,5.0},{7.0,8.0,11.0}};
    assertTrue("Matrix add should not change original matrix.", Arrays.deepEquals(m1.getData(),d1));
    assertTrue("Matrix add not correct.", Arrays.deepEquals(mm.getData(),dt));
  }
  
  @Test
	public void testSubtractDouble() {
		// same results expected as scalarSubtract
	  Matrix2D m = new Matrix2D(d1);
	  Matrix2D mm = m.subtract(1);
		// Tests
	  double[][] dt={{0,1,2},{3,4,5}};
	  assertTrue("Matrix subtract should not change original matrix.", Arrays.deepEquals(m.getData(),d1));
	  assertTrue("Matrix subtract not correct.", Arrays.deepEquals(mm.getData(),dt));
	}

	@Test
	public void testMultiplyDouble() {
		// same results expected as scalarMultiply
	  Matrix2D m = new Matrix2D(d1);
	  Matrix2D mm = m.multiply(2);
		// Tests
	  double[][] dt={{2,4,6},{8,10,12}};
	  assertTrue("Matrix multiply should not change original matrix.", Arrays.deepEquals(m.getData(),d1));
	  assertTrue("Matrix multiply not correct.", Arrays.deepEquals(mm.getData(),dt));
	}

	@Test
	public void testDivideDouble() {
		// same results expected as scalarDivide
	  Matrix2D m = new Matrix2D(d3);
	  Matrix2D mm = m.divide(2);
		// Tests
	  double[][] dt={{1,2,1},{3,4,6}};
	  assertTrue("Matrix divide should not change original matrix.", Arrays.deepEquals(m.getData(),d3));
	  assertTrue("Matrix divide not correct.", Arrays.deepEquals(mm.getData(),dt));
	}

	@Test
	public void testMultiplyMatrix2D() {
		// Multiply two matrices
	  Matrix2D m1 = new Matrix2D(d1);
	  Matrix2D m2 = new Matrix2D(d2);
	  Matrix2D m3 = m1.multiply(m2.transpose());
		// Tests
	  double[][] dt={{12.0,24.0},{30.0,57.0}};
	  assertTrue("Matrix Multiply not correct.", Arrays.deepEquals(m3.getData(),dt));
	}

	/*
	@Test
	public void testDivideMatrix2D() {
		fail("Not yet implemented");
	}
	*/

	@Test
	public void testTranspose() {
		// transpose a  matrices
	  Matrix2D m1 = new Matrix2D(d2);
	  Matrix2D m2 = m1.transpose();
		// Tests
	  double[][] dt={{2.0,3.0},{2.0,3.0},{2.0,5.0}};
	  assertTrue("Matrix transpose not correct.", Arrays.deepEquals(m2.getData(),dt));
	}

	@Test
	public void testElementsMultiply() {
		// Element by element multiplication of two matrices
	  Matrix2D m1 = new Matrix2D(d1);
    Matrix2D m2 = new Matrix2D(d2);
	  Matrix2D m3 = m1.elementsMultiply(m2);
		// Tests
    double[][] dt={{2,4,6},{12,15,30}};
    assertTrue("Matrix element by element multiply not correct.", Arrays.deepEquals(m3.getData(),dt));
	}

	@Test
	public void testElementsDivide() {
		// Element by element multiplication of two matrices
	  Matrix2D m1 = new Matrix2D(d1);
	  Matrix2D m2 = new Matrix2D(d1);
	  Matrix2D m3 = m1.elementsDivide(m2);
		// Tests
	  double[][] dt={{1,1,1},{1,1,1}};
	  assertTrue("Matrix element by element division not correct.", Arrays.deepEquals(m3.getData(),dt));
	}

	@Test
	public void testGetDiagonal() {
		// create a 3x3 matrix and get the diagonal only values in a matrix
		double[][] d = {{1,3,5},{3,7,9},{4,9,6}};
	  Matrix2D m = new Matrix2D(d);
		Matrix2D mm = m.getDiagonal();
		// Tests
	  double[][] dt={{1,0,0},{0,7,0},{0,0,6}};
	  assertTrue("Matrix diagonal should not change original matrix.", Arrays.deepEquals(m.getData(),d));
	  assertTrue("Matrix diagonals only should have non-zero values.", Arrays.deepEquals(mm.getData(),dt));
	}
	
	@Test
	public void testgetDiagonalVector() {
	  
	}

	@Test
	public void testFillMatrix() {
		// create a 2x3 matrix
	  Matrix2D m = new Matrix2D(2,3);
	  // fill with ones
	  m.fillMatrix(1);
		// Tests
	  double[][] dt={{1,1,1},{1,1,1}};
	  assertTrue("Matrix fill should be all ones.", Arrays.deepEquals(m.getData(),dt));
	}
	
  @Test
  public void testabs() {
    // create a test matrix with positive and negative values
    double[][] d = {{1,-3,5},{3,-7,9},{-4,9,-6}};
    Matrix2D m = new Matrix2D(d);
    // create a new matrix of only absolute values
    Matrix2D mt = m.abs();
    // Tests
    double[][] dt = {{1,3,5},{3,7,9},{4,9,6}};   
    assertTrue("Matrix abs should be all positive values.", Arrays.deepEquals(mt.getData(),dt));
  }	

}
