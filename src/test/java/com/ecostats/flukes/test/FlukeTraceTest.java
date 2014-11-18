package com.ecostats.flukes.test;

import static org.junit.Assert.*;


import org.junit.Test;

import com.ecostats.flukes.FinTrace;

public class FlukeTraceTest {

  @Test
  public void testFlukeTrace() {
    FinTrace t = new FinTrace();
    // test
    assertNotNull(t);
  }

  @Test
  public void testFlukeTraceInt() {
    FinTrace t = new FinTrace(5);
    // test
    assertEquals("Inernal array sizes much match constructor", 5, t.getX().length);
  }
  
  @Test
  public void testFlukeTraceDoubleArrayDoubleArrayDoubleArray() {
    double[] x = {1,2,3,4,5};
    double[] y = {5,6,7,8,9};
    double[] m = {10,11,12,13,14};
    FinTrace t = new FinTrace(x,y,m);
    // test
    assertArrayEquals("X Array must have same values as input array",t.getX(), x, 0);
    assertArrayEquals("Y Array must have same values as input array",t.getY(), y, 0);
    assertArrayEquals("Type Array must have same values as input array",t.getTypes(), m, 0);
  }

  @Test
  public void testFlukeTraceFlukeTrace() {
    double[] x = {1,2,3,4,5};
    double[] y = {5,6,7,8,9};
    double[] m = {10,11,12,13,14};
    FinTrace t = new FinTrace(x,y,m);
    FinTrace f = new FinTrace(t);
    // test
    assertArrayEquals("X Array must have same values as construction object",t.getX(), f.getX(), 0);
    assertArrayEquals("Y Array must have same values as construction object",t.getY(), f.getY(), 0);
    assertArrayEquals("Type Array must have same values as construction object",t.getTypes(), f.getTypes(), 0);
  }

  @Test
  public void testAppend() {
    double[] x1 = {1,2,3,4,5};
    double[] y1 = {5,6,7,8,9};
    double[] m1 = {10,11,12,13,14};
    FinTrace t = new FinTrace(x1,y1,m1);
    double[] x2 = {1,2,3,4,5};
    double[] y2 = {5,6,7,8,9};
    double[] m2 = {10,11,12,13,14};
    FinTrace f = new FinTrace(x2,y2,m2);
    FinTrace k = f.append(t);
    // test
    double[] x3 = {1,2,3,4,5,1,2,3,4,5};
    double[] y3 = {5,6,7,8,9,5,6,7,8,9};
    double[] m3 = {10,11,12,13,14,10,11,12,13,14};
    assertArrayEquals("X array much be unchanged",f.getX(), x1, 0);
    assertArrayEquals("Y Array must be unchanged",f.getY(), y1, 0);
    assertArrayEquals("Type Array must be unchanged",f.getTypes(), m1, 0);
    assertArrayEquals("X array much match as appended",k.getX(), x3, 0);
    assertArrayEquals("Y Array must match as appended",k.getY(), y3, 0);
    assertArrayEquals("Type Array must match as appended",k.getTypes(), m3, 0);  }

  @Test
  public void testCombine() {
    double[] x1 = {1,2,3,4,5};
    double[] y1 = {5,6,7,8,9};
    double[] m1 = {10,11,12,13,14};
    FinTrace t = new FinTrace(x1,y1,m1);
    double[] x2 = {1,2,3,4,5};
    double[] y2 = {5,6,7,8,9};
    double[] m2 = {10,11,12,13,14};
    FinTrace f = new FinTrace(x2,y2,m2);
    t.combine(f);
    // test
    double[] x3 = {1,2,3,4,5,1,2,3,4,5};
    double[] y3 = {5,6,7,8,9,5,6,7,8,9};
    double[] m3 = {10,11,12,13,14,10,11,12,13,14};
    assertArrayEquals("X array much match as combined and changed in original object",t.getX(), x3, 0);
    assertArrayEquals("Y Array must match as combined and changed in original object",t.getY(), y3, 0);
    assertArrayEquals("Type Array must match as combined and changed in original object",t.getTypes(), m3, 0);
  }
  
  @Test
  public void testReverse() {
    double[] x = {1,2,3,4,5};
    double[] y = {5,6,7,8,9};
    double[] m = {10,11,12,13,14};
    FinTrace t = new FinTrace(x,y,m);
    FinTrace r = t.reverse();
    // Test
    double[] xr = {5,4,3,2,1};
    double[] yr = {9,8,7,6,5};
    double[] mr = {14,13,12,11,10};
    assertArrayEquals("X array much be reversed",r.getX(), xr, 0);
    assertArrayEquals("Y Array must be reversed",r.getY(), yr, 0);
    assertArrayEquals("Type Array must be reversed",r.getTypes(), mr, 0);
  }

}
