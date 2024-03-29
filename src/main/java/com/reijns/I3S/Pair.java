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
