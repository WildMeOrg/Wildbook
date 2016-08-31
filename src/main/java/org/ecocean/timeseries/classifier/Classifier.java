/**
 * 
 */
package org.ecocean.timeseries.classifier;

import java.util.Collection;
import java.util.Hashtable;
import java.util.Iterator;
import org.ecocean.timeseries.core.Trajectory;

/**
 * @author Hui Ding
 *
 */
public abstract class Classifier {

	Collection<Trajectory> m_trainset = null;
	Collection<Trajectory> m_testset = null;
	
	Collection<Integer> m_trainlabels = null;
	Collection<Integer> m_testlabels = null;
	
	Hashtable<Trajectory, Integer> m_classdict = 
		new Hashtable<Trajectory, Integer>();
	
	int m_numberofclasses = 0;
	
	public Classifier() {}
	
	/**
	 * 
	 * @param datadir the name of the data set
	 */
	public Classifier(Collection<Trajectory> trainset, 
						Collection<Integer> trainlabels,
						Collection<Trajectory> testset, 
						Collection<Integer> testlabels) {
		m_trainset = trainset;
		m_testset = testset;
		m_trainlabels = trainlabels;
		m_testlabels = testlabels;		
	}
	
	protected final void checkIntegrety(Trajectory t, int label) {
		if (m_classdict.get(t).intValue() != label) {
			System.out.println("Integrety check failed!");
			System.exit(-1);
		}
	}
	
	public double run(Collection<Trajectory> testset, 
					Collection<Integer> testlabels) {
		Iterator<Trajectory> it1 = testset.iterator();
		Iterator<Integer> it2 = testlabels.iterator();
		int errors = 0;
		for ( ; it1.hasNext() && it2.hasNext(); ) {
			Trajectory tr = it1.next();
			int label = it2.next();
			if (label != classifyTrajectory(tr)) {
				errors++;
			}
		}
		return ((double)errors)/testset.size();
	}
	
	public abstract void trainClassifier(Collection<Trajectory> trainset,
										Collection<Integer> trainlabels);
	
	public abstract int classifyTrajectory(Trajectory t);
	
	/**
	 * 
	 * @return
	 */
	public Collection<Trajectory> getTrainSet() {
		return m_trainset;
	}
	
	/**
	 * 
	 * @return
	 */
	public Collection<Integer> getTrainLabels() {
		return m_trainlabels;
	}
	
	/**
	 * 
	 * @return
	 */
	public Collection<Trajectory> getTestSet() {
		return m_testset;
	}
	
	/**
	 * 
	 * @return
	 */
	public Collection<Integer> getTestLabels() {
		return m_testlabels;
	}
}
