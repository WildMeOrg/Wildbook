/**
 * 
 */
package org.ecocean.timeseries.core.distance;

import java.util.Collection;

import org.ecocean.timeseries.core.Point;
import org.ecocean.timeseries.core.Trajectory;
import org.ecocean.timeseries.core.TrajectoryException;
import org.ecocean.timeseries.classifier.Classifier;

/**
 * The distance operator that computes the Euclidean distance to a 
 * given trajectory
 * @author Hui
 *
 */
public final class EuclideanOperator extends DistanceOperator {
	/**
	 * @param t
	 */
	public EuclideanOperator() {
		super();
		// TODO Auto-generated constructor stub
	}

	public double computeDistance(Trajectory tr1, Trajectory tr2) {
		if ( !checkTimeConsistency(tr1, tr2)) {
			throw new TrajectoryException(
					"Trajectories incompatible for Eu distance,"
							+ "time interval not the same!");
		}
		
		int itor1 = 0, itor2 = 0;
		Point pt1 = null, pt2 = null;
		double dist = 0.0, eu = 0.0;
		int numofpoints1 = tr1.getNumOfPoints();
		int numofpoints2 = tr2.getNumOfPoints();
		
		while ( (itor1 < numofpoints1) && (itor2 < numofpoints2) ) {
	      /* 
	       * get next point, find its counter-part on the other trajectory, and
	       * calculate the distance between the two points, then update the
	       * euclidean distance
	       */ 			
	      pt1 = tr1.getPoint(itor1);
	      pt2 = tr2.getPoint(itor2);
	      
	      if ( pt1.getTime() == pt2.getTime()) {
	        dist = pt1.distance(pt2);
	        eu += dist * dist;
	        itor1++;
	        itor2++;
	      }
	      else if (pt1.getTime() < pt2.getTime()) {
	        Point last = tr2.getPoint(itor2-1);
	        // interpolate between last and pt2
	        double portion = (pt1.getTime() - last.getTime()) / 
	        					(pt2.getTime() - last.getTime());
	        double[] coords = new double[Point.DIMENSION];
	        coords[Point.X_DIM] = 
	        	last.getXPos() + (pt2.getXPos()-last.getXPos())* portion;
	        coords[Point.Y_DIM] = 
	        	last.getYPos() + (pt2.getYPos()-last.getYPos())* portion;
	        coords[Point.TIME_DIM] = pt1.getTime();
	        dist = pt1.distance(new Point(coords));
	        eu += dist * dist;
	        itor1++;
	      }
	      else if (pt1.getTime() > pt2.getTime()) {
	        Point last = tr1.getPoint(itor1-1);
	        // interpolate between last and pt2
	        double portion = (pt2.getTime() - last.getTime()) / 
	        					(pt1.getTime() - last.getTime());
	        double[] coords = new double[Point.DIMENSION];
	        coords[Point.X_DIM] = 
	        	last.getXPos() + (pt1.getXPos()-last.getXPos())* portion;
	        coords[Point.Y_DIM] = 
	        	last.getYPos() + (pt1.getYPos()-last.getYPos())* portion;
	        coords[Point.TIME_DIM] = pt2.getTime();
	        dist = pt2.distance(new Point(coords));
	        eu += dist * dist;
	        itor2++;        
	      }
	    }
	    return Math.sqrt(eu);
	}

	@Override
	public String toString() {
		String output = "Euclidean Operator:\n";
		return output;
	}

	@Override
	public void tuneOperator(Collection<Trajectory> trainset,
							Collection<Integer> labelset,
							Classifier classifier) {
		// TODO Auto-generated method stub
		
	}

	@Override
	public boolean needTuning() {
		// TODO Auto-generated method stub
		return false;
	}

	@Override
	public double computeLowerBound(Trajectory tr1, Trajectory tr2)
			throws TrajectoryException {
		// TODO Auto-generated method stub
		return Double.MIN_VALUE;
	}

	@Override
	public boolean hasLowerBound() {
		// TODO Auto-generated method stub
		return false;
	}
}

