package fap.core.data;

import java.util.Collection;
import java.util.Iterator;
import java.util.List;

/**
 * A serie of input data.
 * 
 * @author Aleksa Todorovic
 * @version 1.0
 */
public abstract class DataPointSerie implements Iterable<DataPoint> {
	
	/**
	 * Default constructor.
	 */
	public DataPointSerie() {
	}

	/**
	 * Constructor from collection.
	 * @param C collection of points
	 */
	public DataPointSerie(Collection<? extends DataPoint> C) {
		clear();
		getPoints().addAll(C);
	}

	/**
	 * List of points.
	 * @return List of points in this serie data
	 */
	protected abstract List<DataPoint> getPoints();
	
	/**
	 * Number of points.
	 * @return number of points in this serie data
	 */
	public int getPointsCount() {
		return getPoints().size();
	}
	
	/**
	 * Get i-th point.
	 * @param i index of point to return
	 * @return value of i-th point
	 */
	public DataPoint getPoint(int i) {
		return getPoints().get(i);
	}

	/**
	 * Clears points collection.
	 */
	public void clear() {
		getPoints().clear();
	}
	
	/**
	 * Adds point to points collection.
	 * @param point new point to add
	 */
	public void addPoint(DataPoint point) {
		getPoints().add(point);
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
		return getPoints().toArray(new DataPoint[getPoints().size()]);
	}

}
