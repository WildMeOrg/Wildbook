/**
 * 
 */

package org.ecocean.timeseries.classifier;

import java.util.Hashtable;
import java.util.Iterator;
import java.util.PriorityQueue;
import java.util.Set;

import org.ecocean.timeseries.core.Trajectory;

/**
 * This is the k-nearet neighbor classifier to test the effectiveness of any distance measure for
 * time series data, on the UCR data sets.
 */

/**
 * @author Hui
 *
 */
public class KNNClassifier extends NNClassifier{
	int m_numnearestneighbor = -1; // k is the number of nearest neighbors used
	
	public KNNClassifier(String dataset, int numofnn) {
		// set the number of nearest neighbors for the classifier
		m_numnearestneighbor = numofnn;
	}
	
	// This is the k-nearest neighbor classifier
	public int classifyTrajectory(Trajectory t) {
		int predictedclass = Integer.MAX_VALUE;
		Iterator<Integer> it1 = m_trainlabels.iterator();
		Iterator<Trajectory> it2 = m_trainset.iterator();
		PriorityQueue<Neighbor> queue = new PriorityQueue<Neighbor>();
		int seq = 0;
		for ( ; it1.hasNext() && it2.hasNext(); ) {
			Trajectory trainer = it2.next();
			int label = it1.next().intValue();
			try {
				// calculate the distance, change for different distance measure
				double dist = trainer.getDistance(t);
				// add the result of this trainer to the queue
				queue.add(new Neighbor(seq, dist, label));
				seq++;
			} catch (Exception e) {
				e.printStackTrace();
			}
		}
		// find the k nearest neighbor, and decide the predict class
		Hashtable<Integer, Integer> htable = new Hashtable<Integer, Integer>();
		for (int i = 0; i < m_numnearestneighbor; i++) {
			Neighbor n = queue.poll();
			int count = 1; 
			if (htable.containsKey(n.label)) {
				count = htable.get(n.label).intValue();
				count++;
			}
			htable.put(n.label, count);
			//System.out.println("label: " + i + " : " + n.label);
		}
		int maxcount = 0;
		// traverse the hashtable to find the best class
		Set<Integer> keyset = htable.keySet(); 
		for (Iterator<Integer> it3 = keyset.iterator(); it3.hasNext(); ) {
			Integer key = it3.next();
			int count = htable.get(key).intValue();
			if (count > maxcount) {
				maxcount = count;
				predictedclass = key;
			}
		}

		//System.out.println("label: " + predictedclass);
		return predictedclass;
	}
	/**
	 * @param args
	 */
	public static void main(String[] args) {
		// TODO Auto-generated method stub
		System.out.println("This is the k-nearest neighbor classifier to work on" +
				"the UCRiverside data sets");
		
		String[] datasets = {"Lighting2", "FaceFour", "synthetic_control"};
		
		for (int i = 0; i < datasets.length; i++) {
			System.out.println("start running dataset: " + datasets[i]);
			KNNClassifier classifier = new KNNClassifier(datasets[i], 5);			
			classifier.run();
		}
		
		
	}

}

class Neighbor implements Comparable<Object> {
	public double dist; // dist to this training instance
	public int label; // the label of this training instance
	public int seq; // the sequence/id of this training instance
	
	public Neighbor(int sequence, double distance, int label)
	{
		this.seq = sequence;
		this.dist = distance;
		this.label = label;
	}
	
	public int compareTo(Object o) {
		// TODO Auto-generated method stub
		if ( this.dist > ((Neighbor)o).dist)
			return 1;
		if ( this.dist < ((Neighbor)o).dist)
			return -1;
		return 0;
	}
	
}


