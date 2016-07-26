/**
 * 
 */
package org.ecocean.timeseries.core;

/**
 * @author Hui Ding
 *
 */

import java.util.ArrayList;
import java.util.Collection;
import java.util.Iterator;

import org.ecocean.timeseries.core.distance.DistanceOperator;
import org.ecocean.timeseries.core.distance.EuclideanOperator;

import org.ecocean.timeseries.spatialindex.spatialindex.Region;

public class Trajectory implements Cloneable {
	protected int m_id = 0;

	/** the array storing the coordinates */
	protected double[] m_coords = null;

	/** number of points in the trajectory */
	protected int m_numofpoints = 0;

	/** minimum bounding box of the trajectory */
	protected Region m_mbb = null;

	/** a given distance operator for the trajectory */
	protected DistanceOperator m_distoperator = null;
	
	/**
	 * constructor
	 * 
	 * @param id
	 * @param data
	 */
	public Trajectory(int id, double[] data, DistanceOperator op) 
	throws Exception {
		createTrajectory(id, data, op);
	}

	/**
	 * constructor
	 * 
	 * @param id
	 * @param data
	 */
	public Trajectory(String id, double[] data, DistanceOperator op) 
	throws Exception {
		int validID = new Integer(id).intValue();
		createTrajectory(validID, data, op);
	}

	/**
	 * 
	 * @param id
	 * @param data
	 * @throws Exception
	 */
	public Trajectory(String id, Collection<Point> data, DistanceOperator op) 
	throws Exception {
		int validID = new Integer(id).intValue();
		createTrajectory(validID, data, op);
	}

	/**
	 * 
	 * @param id
	 * @param data
	 * @throws Exception
	 */
	public Trajectory(int id, Collection<Point> data, DistanceOperator op) 
	throws Exception {
		createTrajectory(id, data, op);
		//System.out.println(".....Create a trajectory with this many spots: "+m_numofpoints);
	}

	/**
	 * 
	 * @param data
	 * @param id
	 */
	/*
	public Trajectory(int id, Collection<Double> data) throws Exception {
		double[] coords = new double[data.size()];
		Iterator<Double> itor = data.iterator();
		int i = 0;
		while (itor.hasNext()) {
			coords[i] = itor.next().doubleValue();
			i++;
		}
		createTrajectory(id, coords);
	}
	*/

	/**
	 * Create a trajectory using the provided data.
	 * NOTE: the data array is copied 
	 * @param id
	 * @param data
	 * @throws Exception
	 */
	protected void createTrajectory(int id, double[] data, DistanceOperator op) 
	throws Exception {
		m_id = id;
		m_coords = new double[data.length];
		System.arraycopy(data, 0, m_coords, 0, data.length);

		if (m_coords.length % Point.DIMENSION != 0) {
			throw new Exception("Trajectory coord array broken!");
		}
		
		m_numofpoints = m_coords.length / Point.DIMENSION;
		
		// use java reflection to dynamically create the distance operator
		/*
		Constructor con = Class.forName(op).
								getConstructor(new Class[]{Trajectory.class});
		m_distoperator = (DistanceOperator)con.newInstance();
		
		m_distoperator = (DistanceOperator)Class.forName(op).newInstance();
		//*/
		m_distoperator = op;
	}

	/**
	 * overloaded version
	 * @param id
	 * @param data
	 * @throws Exception
	 */
	protected void createTrajectory(int id, 
									Collection<Point> data, 
									DistanceOperator op) throws Exception {
		m_id = id;
		
		m_coords = new double[data.size() * Point.DIMENSION];
		int i = 0;
		Point p = null;
		Iterator<Point> itor = data.iterator();
		while( itor.hasNext() ) {
			p = itor.next();
			m_coords[i] = p.getXPos();
			i++;
			m_coords[i] = p.getYPos();
			i++;
			m_coords[i] = p.getTime();
			i++;
		}
		m_numofpoints = m_coords.length / Point.DIMENSION;
		//System.out.println("......created a Trajectory with m_numofpoints: "+m_numofpoints);
		
		// use java reflection to dynamically create the distance operator
		/*
		Constructor con = Class.forName(op).
								getConstructor(new Class[]{Trajectory.class});
		m_distoperator = (DistanceOperator)con.newInstance();
		
		m_distoperator = (DistanceOperator)Class.forName(op).newInstance();
		//*/
		m_distoperator = op;
	}

	/**
	 * Construct a deep copy of the trajectory
	 * may be problematic with the distance operator, use with caution
	 */
	protected Object clone() {
		assert (false); // do not allow the use of clone
		Trajectory t = null;
		try {
			t = (Trajectory) super.clone();
		} catch (CloneNotSupportedException e) {
			System.err.println("Trajectory can't be cloned");
		}

		// must clone references
		// NOTE: so far only these two references are cloned!
		// more may be added with future changes to the design
		
		t.m_coords = new double[m_coords.length];
		System.arraycopy(m_coords, 0, t.m_coords, 0, m_coords.length);
		if (m_mbb != null)
			t.m_mbb = (Region) m_mbb.clone();
		else
			t.m_mbb = null;
		return t;
	}

	/**
	 * 
	 * @param tr
	 * @return the trajectory similarity
	 * @throws TrajectoryException
	 */
	public double getDistance(Trajectory tr) throws TrajectoryException {
		return m_distoperator.computeDistance(this, tr);
	}
	
	/**
	 * 
	 * @param tr
	 * @return the lowerbounding measure if one exists, or 0 otherwise
	 * @throws TrajectoryException
	 */
	public double getLowerBound(Trajectory tr) throws TrajectoryException {
		return m_distoperator.computeLowerBound(this, tr);
	}

	protected void calculateMBB() {
		Point pt = getPoint(0);
		double minX = pt.getXPos(), minY = pt.getYPos();
		double maxX = pt.getXPos(), maxY = pt.getYPos();

		for (int i = 1; i < m_numofpoints; i++) {
			pt = getPoint(i);
			if (pt.getXPos() < minX)
				minX = pt.getXPos();
			else if (pt.getXPos() > maxX)
				maxX = pt.getXPos();
			if (pt.getYPos() < minY)
				minY = pt.getYPos();
			else if (pt.getYPos() > maxY)
				maxY = pt.getYPos();
		}

		double[] pLow = new double[Point.DIMENSION];
		double[] pHigh = new double[Point.DIMENSION];
		pLow[0] = minX;
		pLow[1] = minY;
		pLow[2] = getFirstPoint().getTime();
		pHigh[0] = maxX;
		pHigh[1] = maxY;
		pHigh[2] = getLastPoint().getTime();

		m_mbb = new Region(pLow, pHigh);
		return;
	}

	/*
	public void transformTrajectory(Point reference,
										double xoffset, 
										double yoffset, 
										double angleoffset, 
										double timeoffset) {
	  double xref = 0.0;
	  double yref = 0.0;
	  if ( reference != null )
	  {
	    xref = reference.getXPos();
	    yref = reference.getYPos();
	  }
	  
	  for ( int i = 0; i < numofpoints; i++ )
	  {
	    Point newpt = getPoint(i);
	    newpt.addOffset(-xref, -yref, timeoffset);
	    newpt.rotate(angleoffset);
	    newpt.addOffset(xref + xoffset, yref + yoffset, 0);
	    setPoint(i, newpt);
	  }
	}
	*/

	public void addDisturbance(double maxdisturbance) {
		double radius = 0;
		double dir = 0;
		double epsilon = maxdisturbance;

		for (int i = 0; i < m_numofpoints; i++) {
			radius = (Math.random() + 0.0) * epsilon;
			dir = 2 * Math.PI * Math.random();
			Point newpt = this.getPoint(i);
			newpt.addOffset(radius * Math.cos(dir), radius * Math.sin(dir),
							0.0);
			setPoint(i, newpt);
		}
	}

	/**
	 * getter for m_id
	 * @return
	 */
	public int getID() {
		return this.m_id;
	}

	/**
	 * setter for m_id
	 * @param newid
	 */
	public void setID(int newid) {
		this.m_id = newid;
	}

	/**
	 * getter for the minimum bounding box, return a copy of the member array
	 * @return
	 */
	public Region getMBB() {
		if (m_mbb == null) {
			calculateMBB();
		}
		return new Region(m_mbb);
	}

	/**
	 * @return starting time stamp of the trajectory
	 */
	public double getMinTime() {
		if (m_mbb == null) {
			calculateMBB();
		}
		return m_mbb.getLow(Point.TIME_DIM);
	}

	/**
	 * 
	 * @return ending time stamp of the trajectory
	 */
	public double getMaxTime() {
		if (m_mbb == null) {
			calculateMBB();
		}
		return m_mbb.getHigh(Point.TIME_DIM);
	}

	/**
	 *
	 * @param index of the point in the trajectory
	 * @return the point object
	 */
	public Point getPoint(int index) {
		double[] coords = new double[Point.DIMENSION];
		coords[Point.X_DIM] = m_coords[index * 3];
		coords[Point.Y_DIM] = m_coords[index * 3 + 1];
		coords[Point.TIME_DIM] = m_coords[index * 3 + 2];
		return new Point(coords);
	}

	/**
	 * 
	 * @return the first point of the trajectory
	 */
	public Point getFirstPoint() {
		return getPoint(0);
	}

	/**
	 * 
	 * @return the second point of the trajectory
	 */
	public Point getLastPoint() {
		int index = m_numofpoints - 1;
		return getPoint(index);
	}

	/*
	 * 
	 * @param index
	 * @param pt
	 */
	public void setPoint(int index, Point pt) {
		m_coords[index * 3] = pt.getXPos();
		m_coords[index * 3 + 1] = pt.getYPos();
		m_coords[index * 3 + 2] = pt.getTime();
	}

	/**
	 * getter for m_numofpoints
	 * @return the number of points
	 */
	public int getNumOfPoints() {
		return m_numofpoints;
	}

	/**
	 * getter for m_coords
	 * @return a copy of the actual member array
	 */
	public double[] getCoordinates() {
		return (double[]) (m_coords.clone());
	}

	/*
	public void iteratePoints(IVisitor v)
	{
	  for (int i = 0; i < m_numofpoints; i++ )
	  {
	    v.visitPoint(new Point(m_coords[i*3], m_coords[i*3+1], m_coords[i*3+2]));
	  }
	}
	//*/
	
	/**
	 * 
	 */
	public DistanceOperator getDistanceOperator() {
		return m_distoperator;
	}
	
	/**
	 * setter for the trajectory distance operator
	 * @param distop
	 */
	public void setDistanceOperator(DistanceOperator distop) {
		m_distoperator = distop;
	}

	public double getXAverage() {
		double x = 0.0;
		for (int i = 0; i < m_numofpoints; i++) {
			x += m_coords[i * 3];
		}
		x /= m_numofpoints;
		return x;
	}
	
	public double getYAverage() {
		double y = 0.0;
		for (int i = 0; i < m_numofpoints; i++) {
			y += m_coords[i * 3 + 1];
		}
		y /= m_numofpoints;
		return y; 
	}
	
	public double getStdDeviation() {
		double std = 0.0, xavg = getXAverage(), yavg = getYAverage();
		for (int i = 0; i < m_numofpoints; i++) {
			std += (m_coords[i * 3] - xavg) * (m_coords[i * 3] - xavg);
			std += (m_coords[i * 3 + 1] - yavg) * (m_coords[i * 3 + 1] - yavg);
		}
		std /= m_numofpoints;
		return Math.sqrt(std);
	}

	public void scaleX(double scale) {
		for (int i = 0; i < m_coords.length / 3; i++) {
			m_coords[i * 3] *= scale;
		}
		
		calculateMBB();
		return;
	}

	public void shiftX(double shift) {
		for (int i = 0; i < m_coords.length / 3; i++) {
			m_coords[i * 3] += shift;
		}
		return;
	}
	
	/**
	 * return a string description of the trajectory
	 */
	public String toString() {
		String out = null;
		out += "Tid:" + m_id;
		out += " numofpoints:" + m_numofpoints + "\n";
		for (int i = 0; i < m_numofpoints; i++) {
			out += getPoint(i).toString() + "\n";
		}
		return out;
	}

	public static void main(String[] args) {
		ArrayList<Point> a1 = new ArrayList<Point>();
		a1.add(new Point(new double[]{1, 2, 1}));
		a1.add(new Point(new double[]{2, 3, 2}));
		a1.add(new Point(new double[]{3, 2, 3}));
		a1.add(new Point(new double[]{4, 2, 4}));

		ArrayList<Point> a2 = new ArrayList<Point>();
		a2.add(new Point(new double[]{1, 3, 1}));
		a2.add(new Point(new double[]{2, 4, 2}));
		a2.add(new Point(new double[]{3, 5, 3}));
		a2.add(new Point(new double[]{4, 3, 4}));

		try {
			Trajectory tr1 = new Trajectory("1", a1, new EuclideanOperator());
		    Trajectory tr2 = new Trajectory("2", a2, new EuclideanOperator());
		    //System.out.println(tr1.getDistance(tr2));
		} 
		catch (Exception e) {
			e.printStackTrace();
		}
	}
	

	

	
}

