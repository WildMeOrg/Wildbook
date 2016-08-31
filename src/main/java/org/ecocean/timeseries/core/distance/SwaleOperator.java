/**
 * 
 */
package org.ecocean.timeseries.core.distance;


import java.util.Collection;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.Vector;

import org.ecocean.timeseries.core.Point;
import org.ecocean.timeseries.core.Trajectory;
import org.ecocean.timeseries.core.TrajectoryException;
import org.ecocean.timeseries.spatialindex.spatialindex.Region;
import org.ecocean.timeseries.classifier.Classifier;

/**
 * @author Hui
 * 
 * Source paper: http://wwweb.eecs.umich.edu/db/files/sigmod07timeseries.pdf
 *
 */
public class SwaleOperator extends DistanceOperator {
  public static double m_threshold = 1;

  
  public static double m_matchreward = 50;
  
  public static double m_gappenalty = 5;

  /**
   * @param t
   */
  public SwaleOperator() {
    // TODO Auto-generated constructor stub
  }
  
  public SwaleOperator(double penalty, double reward, double epsilon) {
    m_threshold = epsilon;
    m_matchreward = reward;
    m_gappenalty = penalty;
  }




  /* (non-Javadoc)
   * @see core.DistanceOperator#toString()
   */
  @Override
  public String toString() {
    return "SwaleOperator:\n" +
        "m_threshold: " + m_threshold + "\n" +
        "m_matchreward: " + m_matchreward + "\n" +
        "m_gappenalty: " + m_gappenalty + "\n";
  }

  @Override
  public double computeLowerBound(Trajectory tr1, Trajectory tr2)
      throws TrajectoryException {
    // TODO Auto-generated method stub
    return Double.MIN_VALUE;
  }

  @Override
  public boolean hasLowerBound() {
    return false;
  }

  @Override
  public boolean needTuning() {
    // TODO Auto-generated method stub
    return false;
  }

  @Override
  public void tuneOperator(Collection<Trajectory> trainset, Collection<Integer> labelset, Classifier classifier) {
    
    
    try{
      //Logger lg = ClassifierManager.getLogger();
      
      double bestt = 0;
      double bestp = 0;
      double besterror = Double.MAX_VALUE;
      //int maxlength = trainset.iterator().next().getNumOfPoints();
      
      // transfer data into a vector for easy leave-one-out manipulation
      Vector<Trajectory> vdata = new Vector<Trajectory>(trainset); 
      Vector<Integer> vlabels = new Vector<Integer>(labelset);
      
      /* collect statistics about the trainset: maxstd */
      double setstd = 0.0;
      for (int i = 0; i < vdata.size(); i++) {
        setstd += vdata.get(i).getStdDeviation();
      }
      setstd /= vdata.size();
      
      double tstep = setstd * 0.02;
      
      // try different combinations of penalty and threshold
      for (double p = 0; p <= m_matchreward; p+=1) {
        double penalty = -p;
        System.out.println("...Swale tuning with penalty:" + (penalty));
        m_gappenalty = penalty;
        for (int t = 1; t <= 50; t++) {
          System.out.println("......tuning with threshold:" + t * tstep);
          m_threshold = t * tstep;
          double error = tuneByLeaveOneOut(vdata, vlabels, classifier);
          if (error < besterror) {
            bestp = p;
            bestt = t * tstep;
            besterror = error;
          }
        }
      }
      
      System.out.println("best p:" + bestp + "\t best t:" + bestt);
      // set the parameter
      m_gappenalty = bestp;
      m_threshold = bestt;
    }
    catch(Exception e){
      e.printStackTrace();
    }
    
  }
  

  
  public static void normalize(Trajectory tr) {
    double stdx = tr.getStdDeviation();
    
    double avgx = tr.getXAverage();
    
    for (int i = 0; i < tr.getNumOfPoints(); i++) {
      //tr.m_coords[i * 3] = (tr.m_coords[i * 3] - avgx) / stdx;
      Point pt = tr.getPoint(i);
      pt.m_pCoords[Point.X_DIM] = (pt.m_pCoords[Point.X_DIM] - avgx) / stdx; 
    }
  }
  

  public double computeDistance(Trajectory serie1, Trajectory serie2) {
    

    
    int len1 = serie1.getNumOfPoints();
    int len2 = serie2.getNumOfPoints();
    int slen,glen;
    Trajectory sdata, gdata;
    
    if (len1<len2)
    {
      slen = len1;
      glen = len2;
      sdata = serie1;
      gdata = serie2;
    }
    else
    {
      slen = len2;
      glen = len1;
      sdata = serie2;
      gdata = serie1;
    }
      
    double Swale[][] = new double[2][slen+1];
    
    // initialization
    Swale[0][0]=0;
    for (int i=1; i<=slen; i++)
      Swale[0][i] = i*m_gappenalty;
    
    for (int i=1; i<=glen; i++)
    {
      int in = i % 2;
      Swale[in][0] = i*m_gappenalty;
      int im1 = (i-1) % 2;
      
      double y1 = gdata.getPoint(i-1).getYPos();
      
      for (int j=1; j<=slen; j++)
      {
        double y2 = sdata.getPoint(j-1).getYPos();
        
        if (Math.abs(y1-y2)<=m_threshold)
          Swale[in][j] = m_matchreward + Swale[im1][j-1];
        else
          Swale[in][j] = m_gappenalty + Math.max(Swale[im1][j], Swale[in][j-1]);
        
      }
    }
    
    int i = glen % 2;
    
    //why return a negative score?
    //return -Swale[i][slen];
    
    return Swale[i][slen];
  }
  
  
  
  /* (non-Javadoc)
   * @see core.DistanceOperator#computeDistance(core.Trajectory)
   * JasonHolmberg: Never could get this method to work
   */
  public double computeDistance_OLD_SCREWY(Trajectory tr1, Trajectory tr2) throws TrajectoryException {
    
    try{
    double score = 0.0;
    // assume all trajectories are normalized
    // then set up a grid for entries
    
    /*
     * set up a grid for entries between -2 and 2 in the normalized 
     * distribution, i.e, say that m_threshold = 0.25.  Then, 2dt = .5.  
     * So, we would have array entries for >2, 2-1.5, 1.5-1, 1-.5, .5-0, 
     * 0- -.5, -.5 - -1, -1, - -1.5, -1.5 - -2, < -2.
     * 
     * NOTE!!! different from the paper, the grid cells has a edge length
     * of 2 * m_threshold instead of 1 * m_threshold
     */
    double gridcellsize = 2 * m_threshold;
    /* must ensure no data item value is greater than 2.0, otherwise 
     * this is not going to work
     */
    int cellsperdim = 2 * (int)Math.ceil(1.5/gridcellsize);
    
    Cell[][] grid = new Cell[cellsperdim][cellsperdim];
    double base = - cellsperdim * gridcellsize / 2;
    
    /* compute cell boundary, seems not necessary */
    for (int i = 0; i < cellsperdim; i++) {
      for (int j = 0; j < cellsperdim; j++) {
        double[] pLow = 
              {base + i*gridcellsize, base + j*gridcellsize};
        double[] pHigh = 
              {pLow[0] + gridcellsize, pLow[1] + gridcellsize};
        grid[i][j] = new Cell(new Region(pLow, pHigh));
      }
    }
    
    // construct the mbr, insert the point into all the cells mbr intersects
    int m = tr1.getNumOfPoints();
    System.out.println("...m number of points: "+m);
    for (int i = 0; i < m; i++) {
      int index = m - i - 1;
      Point pt = tr1.getPoint(index);
      Point lowerleft = new Point(pt.m_pCoords);
      Point upright = new Point(pt.m_pCoords);
      lowerleft.addOffset(-m_threshold, -m_threshold, 0);
      upright.addOffset(m_threshold, m_threshold, 0);
      int lowx =  (int)Math.floor((lowerleft.getXPos() - base) / gridcellsize);
      int lowy = (int)Math.floor((lowerleft.getYPos() - base) / gridcellsize);
      int highx = (int)Math.floor((upright.getXPos() - base) / gridcellsize);
      int highy = (int)Math.floor((upright.getYPos() - base) / gridcellsize);
      
      for (int k = lowx; k <= highx; k++) {
        for (int l = lowy; l <= highy; l++) {
          // insert into grid cell [k][l]
          //double[] pLow = {lowx, lowy}, pHigh = {highx, highy};
          double[] pLow = {lowerleft.getXPos(), lowerleft.getYPos()}, 
              pHigh = {upright.getXPos(), upright.getYPos()};
          System.out.println("k:" + k + "  l:" + l+" cellsperdim: "+cellsperdim);
          grid[k][l].insert(
                new CellEntry(index, new Region(pLow,pHigh)));
        }
      }
    }
    
    /* 
     * maintain the best score
     * stores at position matches[i] the smallest value k for which i 
     * matches exists between the elements of S and r1, ... rk
     */
    int n = tr2.getNumOfPoints();
    // not sure why n+m+5, seems n+1 is enough?
    int[] matches = new int[n + m + 5]; 
    matches[0] = -1;
    for (int i = 1; i < m + n; i++)
      matches[i] = m + n + 2;
    int max = -1;
    
    // iterate through the elements of trajectory tr2
    for (int i = 0; i < n; i++) {
      // determine which grid cell it fits in
      Point pt = tr2.getPoint(i);
      int k = (int)Math.floor((pt.getXPos() - base) / gridcellsize);
      int l = (int)Math.floor((pt.getYPos() - base) / gridcellsize);
      
      if (grid[k][l].numentries == 0) {
        continue;
      }
      
      // if there are entries in that grid cell
      int temp = -1; // for overwritten
      int c = 0;
      int value = -1;
      Iterator<CellEntry> itor = grid[k][l].queue.iterator();
      for ( ; itor.hasNext(); ) {
        CellEntry e = itor.next();
        // compare with the mbr to see if it is a match
        double[] coords = {0.0,0.0};
        coords[0] = pt.getXPos();coords[1] = pt.getYPos();
        /*
        // currently just use the x dimension
        if (pt.getXPos() >= e.mbr.getLow(Point.X_DIM) &&
            pt.getXPos() <= e.mbr.getHigh(Point.X_DIM)) {
          value = e.id;
        }
        //*/
        
        if (e.mbr.contains(new Point(coords))) {
          value = e.id;
        }
        else {
          continue;
        }
        
        // try to increase the best possible score using these entries
        if (value > temp) {
          /*
          while (matches[c] < value && (c <= max || c <=1)) {
            c++;
          }
          //*/
          for ( ; matches[c] < value && (c <= max || c <= 1); c++)
            ;
          temp = matches[c];
          if (matches[c] > value) {
            matches[c] = value;
          }
          if ( max < c) {
            max = c;
          }
        }
      }
    }
    score = max;
    
    /* this is for calculating LCSS sequence */
    //return 1.0 - ((double)score)/Math.min(m, n);
    
    // this is the real score formula
    score = max * m_matchreward + (m + n - 2 * max) * m_gappenalty;
    
    //System.err.println("max:" + max + " score:" + score + 
    //    "return:" + (1.0 - ((double)score)/Math.min(m, n)));
    
    //return 0 - score;
    return 1.0 - ((double)score)/Math.min(m, n);
    //*/
    }
    catch(Exception e){
      e.printStackTrace();
      return -1.0;
    }
  }
  
  

}

class Cell {
  //PriorityQueue<CellEntry> queue = new PriorityQueue<CellEntry>();
  LinkedList<CellEntry> queue = new LinkedList<CellEntry>();
  int numentries = 0;
  Region r;
  
  Cell(Region r) {
    this.r = r;
  }
  
  void insert(CellEntry e) {
    queue.addFirst(e);
    numentries++;
  }
}

class CellEntry implements Comparable {
  int id;
  Region mbr;
  
  CellEntry(int id, Region r) {
    this.id = id;
    this.mbr = r;
  }

  public int compareTo(Object o) {
    if (this.id > ((CellEntry)o).id) {
      return 1;
    }
    else if (this.id == ((CellEntry)o).id) {
      return 0;
    }
    else {
      return -1;
    }
  }
  
  
}

class GridEntry {
  double[] low = new double[2];
  double[] high = new double[2];
  
  int series_id;
}





