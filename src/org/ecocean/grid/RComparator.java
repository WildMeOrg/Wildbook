package org.ecocean.grid;

import java.util.Comparator;


public class RComparator implements Comparator {

  public RComparator() {
  }

  public int compare(Object a, Object b) {
    SpotTriangle a1 = (SpotTriangle) a;
    SpotTriangle b1 = (SpotTriangle) b;
    if (a1.R < b1.R) {
      return -1;
    } else if (a1.R == b1.R) {
      return 0;
    } else {
      return 1;
    }

  }


}
    