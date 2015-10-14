/**
 * 
 */

package org.ecocean.timeseries.classifier;

import java.util.Collection;
import java.util.Iterator;
import java.util.logging.Logger;

import org.ecocean.timeseries.core.Trajectory;
import org.ecocean.timeseries.core.distance.DistanceOperator;
import org.ecocean.timeseries.core.distance.SpADeOperator;

/**
 * This is the nearest neighbor classifier to test the effectiveness of any distance measure for
 * time series data, on the UCR data sets.
 */

/**
 * @author Hui
 *
 */
public class NNClassifier extends Classifier{
	public NNClassifier() {
		super();
	}
	
	public void run() {
		System.out.println("place holder");
	}
	
	public int classifyTrajectory(Trajectory tr) {
		//*
		DistanceOperator op = tr.getDistanceOperator();
		if (op instanceof SpADeOperator) {
			Logger lg = ClassifierManager.getLogger();
			return classifyTrajectoryWithSpADe(tr, (SpADeOperator)op);
		}
		//*/
		
		boolean haslb = op.hasLowerBound();
		
		double mindist = Double.MAX_VALUE;
		int predictedclass = -1;
		Iterator<Integer> it1 = m_trainlabels.iterator();
		Iterator<Trajectory> it2 = m_trainset.iterator();
		for ( ; it1.hasNext() && it2.hasNext(); ) {
			Trajectory trainer = it2.next();
			int label = it1.next().intValue();
			
			if (haslb) {
				double lb = trainer.getLowerBound(tr);
				if (lb < mindist) {
					double dist = trainer.getDistance(tr);
					if (dist < mindist) {
						predictedclass = label;
						mindist = dist;
					}
				}
			}
			else {
				double dist = trainer.getDistance(tr);
				if (dist < mindist) {
					predictedclass = label;
					mindist = dist;
				}
			}
		}
		//System.out.println("label:" + predictedclass);
		return predictedclass;
	}
	
	private int classifyTrajectoryWithSpADe(Trajectory tr, SpADeOperator so) {
		return so.classifyTrajectory(tr);
	}

	@Override
	public void trainClassifier(Collection<Trajectory> trainset,
			Collection<Integer> trainlabels) {
		m_trainset = trainset;
		m_trainlabels = trainlabels;
	}

	/**
	 * @param args
	 */
	public static void main(String[] args) {
		System.out.println("This is the nearest neighbor classifier to work on" 
				+ "the UCRiverside data sets");
		
		String[] datasets = {"OliveOil"};
		
		for (int i = 0; i < datasets.length; i++) {
			System.out.println("start running dataset: " + datasets[i]);
			NNClassifier classifier = new NNClassifier();			
			classifier.run();
		}
	}
}
