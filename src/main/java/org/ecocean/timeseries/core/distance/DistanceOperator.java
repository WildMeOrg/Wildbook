/**
 * 
 */
package org.ecocean.timeseries.core.distance;

import java.util.Collection;
import java.util.Vector;

import org.ecocean.timeseries.core.Point;
import org.ecocean.timeseries.core.Trajectory;
import org.ecocean.timeseries.core.TrajectoryException;

import org.ecocean.timeseries.classifier.Classifier;

/**
 * abstract class for the trajectory distance operator
 * 
 * @author Hui
 *
 */
public abstract class DistanceOperator {
	
	/**
	 * 
	 * @param t
	 */
	public DistanceOperator() {}
	
	/**
	 * 
	 * @param t
	 * @return
	 * @throws TrajectoryException
	 */
	public abstract double computeDistance(Trajectory tr1, Trajectory tr2)
			throws TrajectoryException;
	
	/**
	 * 
	 * @return whether the parameters for the distance operator need to be tuned
	 */
	public abstract boolean needTuning();
	
	/**
	 * tune the operator parameters using leave-one-out
	 * @param trainset
	 * @param labelset
	 */
	public abstract void tuneOperator(Collection<Trajectory> trainset, 
										Collection<Integer> labelset,
										Classifier classifier);
	
	public abstract boolean hasLowerBound();
	
	public abstract double computeLowerBound(Trajectory tr1, Trajectory tr2)
			throws TrajectoryException;
	
	/**
	 * 
	 * @param trainset
	 * @param trainlabels
	 * @param classifier
	 * @return the classification error ratio, using leave-one-out
	 */
	protected double tuneByLeaveOneOut(Vector<Trajectory> trainset,
										Vector<Integer> trainlabels,
										Classifier classifier) {
		double error = 0.0;
		for (int i = 0; i < trainset.size(); i++) {
			Trajectory testant = trainset.remove(i);
			int testlabel = trainlabels.remove(i);
			
			classifier.trainClassifier(trainset, trainlabels);
			int result = classifier.classifyTrajectory(testant);
			if (result != testlabel) {
				error = error + 1.0;
			}
			trainset.add(i, testant);
			trainlabels.add(i, testlabel);
		}
		return error / trainset.size();
	}
	
	/**
	 * return the name of the operator, and possibly a set of parameters
	 */
	public abstract String toString();
	
	/**
	 * 
	 * @param t
	 * @return true if the two trajectories have the same temporal duration,
	 * false otherwise
	 */
	protected boolean checkTimeConsistency(Trajectory tr1, Trajectory tr2) {
		/*
		double duration1 = tr1.
				m_coords[(tr1.m_numofpoints-1)*3 + Point.TIME_DIM]
				- tr1.m_coords[0 * 3 + Point.TIME_DIM];
		double duration2 = 
			tr2.m_coords[(tr2.m_numofpoints-1)*3 + Point.TIME_DIM]
				- tr2.m_coords[0 * 3 + Point.TIME_DIM];
		
		if (duration1 != duration2) {
			return false;
		}
		else {
			return true;
		}
		
		if ((tr1.m_coords[0 * Point.DIMENSION + Point.TIME_DIM] ==
			tr2.m_coords[0 * Point.DIMENSION + Point.TIME_DIM]) && 
			(tr1.m_coords[(tr1.m_numofpoints-1)*Point.DIMENSION+Point.TIME_DIM]
		== tr2.m_coords[(tr2.m_numofpoints-1)*Point.DIMENSION+Point.TIME_DIM])) 
		{
			return true;
		}
		else {
			return false;
		}
		//*/
		if (tr1.getFirstPoint().getTime() == tr2.getFirstPoint().getTime() &&
				tr1.getLastPoint().getTime() == tr2.getLastPoint().getTime()) {
			return true;
		}
		else {
			return false;
		}
	}
}
