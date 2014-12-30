package com.ecostats.flukes.test;

import static org.junit.Assert.*;

import java.util.TreeSet;

import org.apache.commons.math3.linear.ArrayRealVector;
import org.apache.commons.math3.linear.RealVector;
import org.junit.After;
import org.junit.AfterClass;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;

import com.ecostats.flukes.Fluke;
import com.ecostats.flukes.FinTrace;
import com.ecostats.flukes.Flukes;
import com.ecostats.flukes.Matrix2D;
import com.ecostats.flukes.TraceCompare;

public class TraceCompareTest {

  double[] typu_p_left = {-1, 1, 4, 5, 1, 2, 3, 6, 7, 6, 7, 2, 3, 0};
  double[] typu_p_right = {4, 5, 2, 3, 4, 5, -1};

  double[] pu_p_left = {7.0021, 9.0021, 11.0021, 13.0020, 15.0020, 17.0020, 19.0020, 21.0020, 23.0020, 25.0020, 27.0020, 29.0020, 31.0020, 33.0020};
  double[] pu_p_right = {35.0020, 37.0020, 39.0020, 41.0020, 43.0020, 45.0020, 47.0020};

  double[] tu_p_left = {8.0021, 10.0021, 12.0020, 14.0020, 16.0020, 18.0020, 20.0020, 22.0020, 24.0020, 26.0020, 28.0020, 30.0020, 32.0020, 34.0020};  
  double[] tu_p_right = {36.0020, 38.0020, 40.0020, 42.0020, 44.0020, 46.0020, 48.0020};

  double[] xx_left = {20.4318, 40.7703, 76.5661, 118.8703, 127.8193, 160.3609, 179.0724, 191.2755, 236.0203, 240.9016, 282.3922, 288.9005, 323.8828, 387.3391};
  double[] xx_right = {748.5516, 686.7224, 635.4693, 612.6901, 598.8599, 587.4703, 560.6234, 387.3391};

  double[] yy_left = {83.7714, 82.9578, 82.1443, 78.8901, 79.7036, 73.1953, 66.6870, 66.6870, 65.0599, 67.5005, 68.3141, 68.3141, 72.3818, 139.9057};
  double[] yy_right = {87.0255, 64.2464, 47.1620, 44.7214, 43.9078, 47.1620, 47.9755, 139.9057};
  
  double[][] m = {{-1,6,7,0},{-1,1,0}}; // urtt (mark types = 6 and 7 are the start and end of a wave on left fluke, and 1 is a Nick on right fluke)
  
  double[] dist_index_left = {1444.2,1861.2}; //{0.7407, 0.8102};
  double[] dist_index_right = {1942.8}; //{0.8238};


  FinTrace ft_left;
  FinTrace ft_right;
  Fluke fluke;
  Flukes flukes;
  
  @BeforeClass
  public static void setUpBeforeClass() throws Exception {
    // notch (0) at 14, 21 total points  
  }

  @AfterClass
  public static void tearDownAfterClass() throws Exception {
  }

  @Before
  public void setUp() throws Exception {
    // create a left fluke tracing object
    this.ft_left = new FinTrace(xx_left,yy_left,m[0]);
    this.ft_left.setNotchOpen(false);
    this.ft_left.setCurled(false);
    this.ft_left.setPositions(dist_index_left);
    // create a right fluke tracing object
    this.ft_right = new FinTrace(xx_right,yy_right,m[1]);
    this.ft_right.setNotchOpen(false);
    this.ft_right.setCurled(false);
    this.ft_right.setPositions(dist_index_right);
    // create a complete fluke object from the right and left tracing
    this.fluke = new Fluke(ft_left,ft_right);
    double[] mt = {6,7,1};
    this.fluke.setMarkTypes(mt);
    // create a Flukes object to hold the fluke
    this.flukes = new Flukes();
    flukes.add(fluke);
  }

  @After
  public void tearDown() throws Exception {
  }

  @Test
  public void testTraceCompare() {
    // Note, this also tests testMatrixToColumnVector by default
    TraceCompare t = new TraceCompare();
    // Test setup of default variables used in the comparison system
    double[] vvl = {1,2,2,2,2,1,1,2,2,2,2,0,0};
    double[] dd = {0.025,0.04,0.04,0.04,0.04,0,0,0,0,0,0.025,0,0,0.04,0.04,0,0.04,0,0.06,0,0.04,0,0,0,0,0,0.04,0,0.04,0,0.04,0,0.06,0,0.04,0,0,0,0,0.04,0.04,0,0.04,0,0.06,0,0.04,0,0,0,0,0,0.04,0,0.04,0,0.04,0,0.06,0,0.045,0,0,0,0,0,0.06,0,0.06,0,0.0667,0,0,0,0,0,0,0,0,0,0.06,0,0.06,0,0.0667,0,0,0,0,0,0,0,0.04,0,0.04,0,0,0,0.04,0,0,0,0,0,0,0,0.04,0,0.045,0,0,0,0.04,0,0,0,0,0,0,0,0,0,0,0,0,0,0.06,0,0,0,0.025,0,0,0,0,0,0,0,0,0,0.04,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0};
    assertArrayEquals(vvl, t.vvl.getDataRef(), 0.0);
    assertArrayEquals(dd, t.dd.toArray(), 0.0001);
  }

  /*
  @Test
  public void testMatrixToColumnVector() {
    fail("Not yet implemented");
  }
  */

  @Test
  public void testFlukeSetup() {
    double[][] qpt = {{0,1,0,0},{1,0,0,0}}; // arrays of notch open, notch closed, left curled right curled for two flukes
    double[][] x = {{},{}}; // x and y values not needed to test flukeSetup
    double[][] y = {{},{}};
    double[][] xlt = {{0.7407, 0.8102, 0.8238},{0.2593,0.1898,0.1762}}; // xlt
    //double[][] m = {{6,7},{1}}; // urtt (mark types = 6 and 7 are the start and end of a wave on left fluke, and 1 is a Nick on right fluke)
    // create a left fluke tracing object
    FinTrace ft_left = new FinTrace(xx_left,yy_left,m[0]);
    ft_left.setNotchOpen(false);
    ft_left.setCurled(false);
    // create a right fluke tracing object
    FinTrace ft_right = new FinTrace(xx_right,yy_right,m[1]);
    ft_right.setNotchOpen(false);
    ft_right.setCurled(false);
    // create a complete fluke object from the right and left tracing
    Fluke fluke = new Fluke(ft_left,ft_right);
    // create a TraceCompare object
    TraceCompare t = new TraceCompare();
    // use the flukes object to test the flukeSetup
    double value = t.getFlukePt(fluke);
    // test (value of 3.5 output from original Matlab code test run)
    assertEquals(3.5000, value, 0.001);
  }

  @Test
  public void testSubsetVectorRealVectorRealMatrixInt() {
    TraceCompare t = new TraceCompare();
    double[] a = {16,27,58,9,17,67};
    double[][] aa = {{4,2},{5,1}};
    RealVector v = new ArrayRealVector(a);
    Matrix2D m = new Matrix2D(aa);
    Matrix2D dm = t.subsetVector(v,m,0);
    // test 
    double[][] am = {{17,58},{67,27}};
    assertArrayEquals(am[0], dm.getData()[0], 0.0);
    assertArrayEquals(am[1], dm.getData()[1], 0.0);
  }

  @Test
  public void testSumVector() {
    TraceCompare t = new TraceCompare();
    double [] a = {1.1, 2.2, 3.3};
    RealVector v = new ArrayRealVector(a);
    double d = t.sumVector(v);
    // test
    assertEquals(6.6, d, 0.0);
  }

  @Test
  public void testReverseVector() {
    double[] d = {0.7407, 0.8102, 0.8238};
    ArrayRealVector v = new ArrayRealVector(d);
    TraceCompare t = new TraceCompare();
    RealVector r = t.reverseVector(v);
    // test the vector was reversed
    double[] dt = {0.8238, 0.8102, 0.7407};
    assertArrayEquals(dt, r.toArray(), 0.0);
  }

  @Test
  public void testTracingPositions() {
    TraceCompare t = new TraceCompare();
    RealVector r = t.tracingDistanceIndex(this.fluke);
    // test
    // to 0.4017 are left fluke values, from 0.7531 are right fluke values
    double[] dt = {0.0269, 0.0744, 0.1301, 0.1422, 0.1842, 0.2078, 0.2240, 0.2833, 0.2903, 0.3457, 0.3543, 0.4017, 0.7531, 0.7896, 0.8057, 0.8243, 0.8547, 0.9207};
    assertArrayEquals(dt, r.toArray(), 0.001);
  }
  
  @Test
  public void testGetCorr(){
    TraceCompare t = new TraceCompare();
    double[] dd0 = {0.0269164701, 0.0744154995, 0.1300837217, 0.1421657051, 0.1841713721, 0.2077612374, 0.2240105682, 0.2832601055, 0.2902571209, 0.3456706736, 0.3543369567, 0.401747157, 0.7530702517, 0.7896140969, 0.8056946978, 0.824275731, 0.8546615065, 0.9207293766};
    double[] dd1 = {0.7407, 0.8102, 0.8238};
    double[] mm0 = {6, 7, 1};
    double[] mm1 = {1, 4, 5, 1, 2, 3, 6, 7, 6, 7, 2, 3, 4, 5, 2, 3, 4, 5};
    RealVector d0 = new ArrayRealVector(dd0);
    RealVector d1 = new ArrayRealVector(dd1); 
    RealVector m0 = new ArrayRealVector(mm0);
    RealVector m1 = new ArrayRealVector(mm1);
    double d = t.getCorr(d0, d1, m0, m1);
    assertEquals(d, 0.0, 0.001);
  }
  
  /*
  @Test
  public void testProcessCatalog() {
    double[][] qpt = {{0,1,0,0},{1,0,0,0}}; // arrays of notch open, notch closed, left curled right curled for two flukes
    double[][] x = {{},{}}; // x and y values not needed to test flukeSetup
    double[][] y = {{},{}};
    double[] marktype_left = {-1, 1, 4, 5, 1, 2, 3, 6, 7, 6, 7, 2, 3, 0};
    double[] marktype_right = {-1, 4, 5, 2, 3, 4, 5, 0};
    double[][] xlt = {{0.7407, 0.8102, 0.8238},{0.2593,0.1898,0.1762}}; // xlt
    //double[][] m = {{6,7},{1}}; // urtt (mark types = 6 and 7 are the start and end of a wave on left fluke, and 1 is a Nick on right fluke)
    // create a left fluke tracing object
    FinTrace ft_left = new FinTrace(xx_left,yy_left,marktype_left);
    ft_left.setNotchOpen(false);
    ft_left.setCurled(false);
    // create a right fluke tracing object
    FinTrace ft_right = new FinTrace(xx_right,yy_right,marktype_right);
    ft_right.setNotchOpen(false);
    ft_right.setCurled(false);
    // create a complete fluke object from the right and left tracing
    Fluke test_fluke = new Fluke(ft_left,ft_right);
    TraceCompare t = new TraceCompare();
    // test
    TreeSet<Fluke> ts = t.processCatalog(this.flukes, test_fluke);
    assertEquals(0.0962,ts.first().getMatchValue(),0.0001);
  }
  */
  
}
