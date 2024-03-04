package org.ecocean.grid;


import java.util.Comparator;


public class ScoreComparator implements Comparator {

  public ScoreComparator() {
  }

  public int compare(Object a, Object b) {
    VertexPointMatch a1 = (VertexPointMatch) a;
    VertexPointMatch b1 = (VertexPointMatch) b;
    if (a1.points > b1.points) {
      return -1;
    } else if (a1.points == b1.points) {
      return 0;
    } else {
      return 1;
    }

  }


}