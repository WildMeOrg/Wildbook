package fap.custom;

import java.util.Iterator;
import java.util.List;
import java.util.ArrayList;

import fap.core.data.DataPoint;
import fap.core.data.DataPointSerie;

public class SimpleDataPointSerie extends DataPointSerie {
  
  
  ArrayList<DataPoint> dataPoints; 
  
  
  public SimpleDataPointSerie(){super();dataPoints=new ArrayList<DataPoint>();}
  
  

  @Override
  protected List<DataPoint> getPoints() {
    return dataPoints;
  }

  /**
   * Number of points.
   * @return number of points in this serie data
   */
  public int getPointsCount() {
    return dataPoints.size();
  }
  
  /**
   * Get i-th point.
   * @param i index of point to return
   * @return value of i-th point
   */
  public DataPoint getPoint(int i) {
    return dataPoints.get(i);
  }

  /**
   * Clears points collection.
   */
  public void clear() {
    dataPoints.clear();
  }
  
  /**
   * Adds point to points collection.
   * @param point new point to add
   */
  public void addPoint(DataPoint point) {
    dataPoints.add(point);
    System.out.println("Adding a Swale DataPoint!");
  }

  /**
   * New iterator over points in this serie data.
   * @return iterator over points
   */
  public Iterator<DataPoint> iterator() {
    return getPoints().iterator();
  }
  
  /**
   * Array of points in this serie data.
   * @return new array with points from this serie data
   */
  public DataPoint[] getPointsArray() {
    return dataPoints.toArray(new DataPoint[getPoints().size()]);
  }
  

}
