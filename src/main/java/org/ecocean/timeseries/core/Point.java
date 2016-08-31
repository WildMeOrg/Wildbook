/**
 * 
 */
package org.ecocean.timeseries.core;


/**
 * @author Hui Ding
 *
 */
public class Point extends org.ecocean.timeseries.spatialindex.spatialindex.Point {
	public static final int DIMENSION = 3;
	public static final int X_DIM = 0;
	public static final int Y_DIM = 1;
	public static final int TIME_DIM = DIMENSION -1;

	public Point(double[] coords) {
		super(coords);
		// TODO Auto-generated constructor stub
	}
	
	public Point(Point pt) {
		super(pt);
		// TODO Auto-generated constructor stub
	}

	/**
	 * getter
	 * @return the x coordinate
	 */
	public double getXPos() {
		return m_pCoords[X_DIM];
	}
	
	/**
	 * setter
	 * @param x the new x coordinate
	 */
	protected void setXPos(double x) {
		m_pCoords[X_DIM] = x;
	}
	
	/**
	 * getter
	 * @return the y coordinate
	 */
	public double getYPos() {
		return m_pCoords[Y_DIM];
	}
	
	/**
	 * setter
	 * @param y the new y coordinate
	 */
	protected void setYPos(double y) {
		m_pCoords[Y_DIM] = y;
	}
	
	/**
	 * getter
	 * @return the time stamp
	 */
	public double getTime() {
		return m_pCoords[TIME_DIM];
	}
	
	/**
	 * 
	 * @param time the new time stamp
	 */
	protected void setTime(double time) {
		m_pCoords[TIME_DIM] = time;
	}
	
	/**
	 * 
	 * @param pt
	 * @return the euclidean distance to a given point
	 */
	public double distancesquare(Point pt) {
		/*
		double x = pt.getXPos(), y = pt.getYPos();
		return Math.sqrt( (x-m_pCoords[X_DIM]) * (x-m_pCoords[X_DIM]) + 
							(y-m_pCoords[Y_DIM]) * (y-m_pCoords[Y_DIM]) );
		//*/
		
		/* a more general version of point distance calculation */
		double dist = 0.0;
		for (int i = 0; i < Point.TIME_DIM; i++) {
			double coord = pt.getCoord(i);
			dist += (m_pCoords[i] - coord) * (m_pCoords[i] - coord);
		}
		return dist;
	}
	
	public double distance(Point pt) {
		return Math.sqrt(distancesquare(pt));
	}
	
	/**
	 * add the specified offset to the point coordinates
	 * @param xoffset
	 * @param yoffset
	 * @param timeoffset
	 */
	public void addOffset(double xoffset, double yoffset, double timeoffset) {
		m_pCoords[X_DIM] += xoffset;
		m_pCoords[Y_DIM] += yoffset;
		m_pCoords[TIME_DIM] += timeoffset;
	}	

	public String toString() {
	    return "x: " + m_pCoords[X_DIM] + "  y: " + m_pCoords[Y_DIM] + 
	    				"  time: " + m_pCoords[TIME_DIM];
	}
}
