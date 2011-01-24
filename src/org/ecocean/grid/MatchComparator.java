package org.ecocean.grid;

import java.util.Comparator;


public class MatchComparator implements Comparator {

  public int compare(Object a, Object b) {
    MatchObject a1 = (MatchObject) a;
    MatchObject b1 = (MatchObject) b;

    double a1_adjustedValue = a1.getMatchValue() * a1.getAdjustedMatchValue();
    double b1_adjustedValue = b1.getMatchValue() * b1.getAdjustedMatchValue();

    if (a1_adjustedValue > b1_adjustedValue) {
      return -1;
    } else if (a1_adjustedValue == b1_adjustedValue) {
      return 0;
    } else {
      return 1;
    }
  }
}