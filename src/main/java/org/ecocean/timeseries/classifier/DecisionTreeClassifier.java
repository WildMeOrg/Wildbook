/**
 * 
 */

package org.ecocean.timeseries.classifier;

import java.io.File;
import java.io.FileReader;
import java.io.LineNumberReader;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Hashtable;
import java.util.Iterator;
import java.util.PriorityQueue;
import java.util.Set;
import java.util.StringTokenizer;

import org.ecocean.timeseries.utils.Statistics;
import org.ecocean.timeseries.core.*;

/**
 * This is the decision tree induction classifier using any distance measure for
 * time series data, on the UCR data sets.
 */

/**
 * @author Hui
 *
 */
public class DecisionTreeClassifier extends Classifier {
	/** the root node of the decision tree */
	TreeNode m_treeroot = null;
	
	/** minimum number of objects per class */
	int m_minobjects = 2;
	
	/** confidence level */
	double m_cf = 0.25f;
	
	/**
	 * 
	 * @param dataset
	 * @param minobjects
	 */		
	public DecisionTreeClassifier(int minobjects) {
		// call the super class to load the data sets
		
		m_minobjects = minobjects;
	}
	
	public void trainClassifier(Collection<Trajectory> trainset,
								Collection<Integer> trainlabels) {
		m_trainset = trainset;
		m_trainlabels = trainlabels;
		
		buildDecisionTree();
		
		pruneDecisionTree();
	}
	
	/**
	 * build a decision tree from the training data set
	 * Collection<Trajectory> trainset
	 * Collection<Integer> trainlabels 
	 */
	protected void buildDecisionTree() {
		if ( !needSplit(m_trainset, m_trainlabels) ) {
			// no need to construct the tree, 
			m_treeroot = new LeafNode(m_trainset, m_trainlabels, 0);
		} else {
			m_treeroot = new TreeNode(0);
			// the members of the new treenode will be set in standardsplit
			standardSplit(m_trainset, m_trainlabels, m_treeroot);
		}
	}
	
	/**
	 * use C4.5 algorithm to prune the decision tree to reduce error ratio
	 */
	protected void pruneDecisionTree() {
		pruneNode(m_treeroot, m_trainset, m_trainlabels);
	}
	
	public void showDecisionTree() {
		showNode(m_treeroot, 0);
	}
	
	protected void showNode(TreeNode n, int level) {
		String out = "level:" + level + "  ";
		if (n == null) {
			out += " null!";
		} else if (n instanceof LeafNode) {
			out += "Leaf label:" + ((LeafNode)n).m_label;
		} else {
			// internal node
			out += "Internal node: " + n.m_distance;
			showNode(n.m_left, level+1);
			showNode(n.m_right, level+1);
		}
		System.out.println(out);
	}
	
	/**
	 *  given a node, will determine the optimal split, then recursively call 
	 *  this method to further split its children
	 *  
	 */ 
	protected void standardSplit(Collection<Trajectory> dataset, 
								Collection<Integer> labelset, 
								TreeNode node) {
		double maxgainratio = 0.0;
		Trajectory splitreference = null;
		double splitdistance = 0.0;
		
		Distribution distribution = new Distribution(dataset, labelset);
		
		// compute minimum number of trajectories required in each subset
		double minsplit = 0.1 * (double)dataset.size() / distribution.m_perclass.size();
		if (minsplit < m_minobjects) {
			minsplit = m_minobjects;
		} else if (minsplit > 25) {
			minsplit = 25;
		}
		
		try {
			Iterator<Trajectory> itor1 = dataset.iterator();
			for ( ; itor1.hasNext(); ) {
				Trajectory reference = itor1.next();
				// insert all the trajectories into a queue, 
				// ordered by their distance to the reference
				PriorityQueue<SplitItem> splitqueue 
					= new PriorityQueue<SplitItem>();
				Iterator<Trajectory> itor2 = dataset.iterator();
				Iterator<Integer> itor4 = labelset.iterator(); 
				for (; itor2.hasNext() && itor4.hasNext(); ) {
					Trajectory candidate = itor2.next();
					int label = itor4.next().intValue();
					checkIntegrety(candidate, label);
					splitqueue.add(new SplitItem(candidate, label, 
							candidate.getDistance(reference)));
				}
				
				// now let's try the splitting
				ArrayList<Trajectory> leftgroup = new ArrayList<Trajectory>();
				ArrayList<Integer> leftlabels = new ArrayList<Integer>();
				ArrayList<Trajectory> rightgroup = new ArrayList<Trajectory>();
				ArrayList<Integer> rightlabels = new ArrayList<Integer>();
				
				// initially assign all the items to the rightgroup which
				// contain trajectories larger than the split distance
				Iterator<SplitItem> itor3 = splitqueue.iterator();
				for ( ; itor3.hasNext(); ) {
					SplitItem item = itor3.next();
					rightgroup.add(item.t);
					rightlabels.add(item.label);
					checkIntegrety(item.t, item.label);
				}
				
				while (splitqueue.size() > 0) {
					double w = splitqueue.poll().distance;
					leftgroup.add(rightgroup.remove(0));
					leftlabels.add(rightlabels.remove(0));
					
					// check if enough trajectories in each subset
					if (leftgroup.size()<minsplit || rightgroup.size()<minsplit) {
						continue;
					}
					
					// now calculate the gain ratio
					double gainratio = computeGainRatio(dataset, 
														labelset, 
														leftgroup, 
														leftlabels, 
														rightgroup, 
														rightlabels);
					if (gainratio >= maxgainratio) {
						maxgainratio = gainratio;
						splitreference = reference;
						splitdistance = w;
					}
				}
			}
			
			// now that we've found the split with max gain ratio, 
			// let's split them
			ArrayList<Trajectory> leftgroup = new ArrayList<Trajectory>();
			ArrayList<Integer> leftlabels = new ArrayList<Integer>();
			ArrayList<Trajectory> rightgroup = new ArrayList<Trajectory>();
			ArrayList<Integer> rightlabels = new ArrayList<Integer>();
			
			Iterator<Trajectory> itor5 = dataset.iterator();
			Iterator<Integer> itor6 = labelset.iterator();
			for ( ; itor5.hasNext() && itor6.hasNext(); ) {
				Trajectory t = itor5.next();
				int label = itor6.next();
				checkIntegrety(t, label);
				if (t.getDistance(splitreference) <= splitdistance) {
					// assign to left group
					leftgroup.add(t);
					leftlabels.add(label);
				} else {
					// assign to right group
					rightgroup.add(t);
					rightlabels.add(label);
				}
			}
			
			System.out.println("Left size: " + leftgroup.size());
			System.out.println("Right size: " + rightgroup.size());
			
			// we should also keep the split information in the node
			node.m_reference = splitreference;
			node.m_distance = splitdistance;
			distribution = 
				new Distribution(leftgroup, leftlabels, rightgroup, rightlabels);
			node.setDistribution(distribution);
			
			// now continue to split left group and right group
			if (!needSplit(leftgroup, leftlabels)) {
				// no need to construct the tree,
				node.m_left = new LeafNode(leftgroup, leftlabels, node.m_level + 1);
			} else {
				node.m_left = new TreeNode(node.m_level + 1);
				standardSplit(leftgroup, leftlabels, node.m_left);
			}
			if (!needSplit(rightgroup, rightlabels)) {
				// no need to construct the tree, 
				 node.m_right = new LeafNode(rightgroup, rightlabels, node.m_level + 1);
			} else {
				node.m_right = new TreeNode(node.m_level + 1);
				standardSplit(rightgroup, rightlabels, node.m_right);
			}
		} catch (Exception e) {
			e.printStackTrace();
		}	
	}
	
	/**
	 * Decide whether we need to further split the data set
	 * @param dataset
	 * @param labelset
	 * @return
	 */
	private boolean needSplit(Collection<Trajectory> dataset,
								Collection<Integer> labelset) {
		if (computeInformation(dataset, labelset) == 0.0 ||
				dataset.size() < m_minobjects * 2) {
			return false;
		} else
			return true;
	}	
	
	/**
	 * return the split info value of this dataset
	 * if the dataset contains trajectories of the same class, 
	 * the split info is zero; otherwise, the split info is greater than zero
	 * 
	 */
	private double computeInformation(Collection<Trajectory> dataset, 
										Collection<Integer> labelset) {
		double information = 0.0;
		// hashtable<classkey, count>
		Hashtable<Integer, Integer> dict = new Hashtable<Integer, Integer>();
		Iterator<Integer> labelitor = labelset.iterator();
		for ( ; labelitor.hasNext(); ) {
			int label = labelitor.next().intValue();
			int count = 1; 
			if (dict.containsKey(label)) {
				count = dict.get(label).intValue();
				count++;
			}
			dict.put(label, count);
		}
		
		Set<Integer> keyset = dict.keySet();
		double size = (double)labelset.size();
		Iterator<Integer> keyitor = keyset.iterator();
		for ( ; keyitor.hasNext(); ) {
			Integer key = keyitor.next();
			double count = (double)dict.get(key).intValue();
			information -= (count/size) * (Math.log(count/size)/Math.log(2));  
		}
		return information;
	}
	
	private double computeGainRatio(Collection<Trajectory> dataset, 
									Collection<Integer> labelset,
									Collection<Trajectory> leftset, 
									Collection<Integer> leftlabels,
									Collection<Trajectory> rightset, 
									Collection<Integer> rightlabels)
	{
		double totalinfo = computeInformation(dataset, labelset);
		double leftinfo = computeInformation(leftset, leftlabels);
		double rightinfo = computeInformation(rightset, rightlabels);
		double leftsize = (double)leftset.size();
		double rightsize = (double)rightset.size();
		double totalsize = (double)dataset.size();
		
		double splitinfo = -leftsize / totalsize * (Math.log(leftsize/totalsize)/Math.log(2.0))
						- rightsize / totalsize * (Math.log(rightsize/totalsize)/Math.log(2.0));
		double infogain = totalinfo - leftinfo * leftsize / totalsize 
						- rightinfo * rightsize / totalsize;
		assert(infogain > 0.0);
		return infogain / splitinfo;
	}
	
	/**
	 * prune a tree node to reduce error ratio
	 * @param n the node to be pruned
	 */	
	protected void pruneNode(TreeNode n, 
							Collection<Trajectory> trainset,
							Collection<Integer> trainlabels) {
		double largestbrancherror;
		double leaferror;
		double treeerror;
		
		if (n instanceof LeafNode) {
			return;
		} else {
			ArrayList<Trajectory> leftset = new ArrayList<Trajectory>();
			ArrayList<Integer> leftlabels = new ArrayList<Integer>();
			ArrayList<Trajectory> rightset = new ArrayList<Trajectory>();
			ArrayList<Integer> rightlabels = new ArrayList<Integer>();
			
			Iterator<Trajectory> itor1 = trainset.iterator();
			Iterator<Integer> itor2 = trainlabels.iterator();
			for ( ; itor1.hasNext() && itor2.hasNext(); ) {
				Trajectory t = itor1.next(); 
				int classlabel = itor2.next();
				try {
					if (t.getDistance(n.m_reference) <= n.m_distance) {
						leftset.add(t);
						leftlabels.add(classlabel);
					} else {
						rightset.add(t);
						rightlabels.add(classlabel);
					}
				} catch (Exception e) {
					e.printStackTrace();
				}
			}			
			
			pruneNode(n.m_left, leftset, leftlabels);
			pruneNode(n.m_right, rightset, rightlabels);
			
			// compute error for largest branch
			int maxbag = n.m_distribution.maxBag(); 
			if (maxbag == 0) {
				largestbrancherror = getEstimatedErrorForBranch(
												leftset, leftlabels, n.m_left);
			} else {
				assert(maxbag == 1);
				largestbrancherror = getEstimatedErrorForBranch(
											rightset, rightlabels, n.m_right);
			}
			
			// compute error if this tree would be leaf
			leaferror = getEstimatedErrorForDistribution(n.m_distribution);
			
			// compute error for the whole subtree
			treeerror = getEstimatedError(n);
			
			// decide if leaf is best choice
			if ( (leaferror <= treeerror + 0.1) && 
					(leaferror <= largestbrancherror + 0.1) ) {
				// should not split this node
				n = new LeafNode(trainset, trainlabels, n.m_level);
			}
			
			// decide if largest branch is better choice than whole subtree
			if (largestbrancherror <= treeerror + 0.1) {
				if (maxbag == 0) {
					n = n.m_left;
				} else {
					assert(maxbag == 1);
					n = n.m_right;
				}
				//pruneNode(n, trainset, trainlabels);
			}
		}		
	}
	
	private double getEstimatedError(TreeNode node) {
		double error = 0.0;
		if (node instanceof LeafNode) {
			return getEstimatedErrorForDistribution(node.m_distribution);
		} else {
			error += 
				getEstimatedError(node.m_left) + getEstimatedError(node.m_right);
		}
		return error;
	}
	
	/**
	 * compute estimated errors for one branch
	 * @param dataset
	 * @param labelset
	 * @param node
	 * @return
	 */
	private double getEstimatedErrorForBranch(Collection<Trajectory> dataset,
												Collection<Integer> labelset,
												TreeNode node) {
		double error = 0.0;
		if (node instanceof LeafNode) {
			return getEstimatedErrorForDistribution(
										new Distribution(dataset, labelset));
		} else {
			// split the data based on the new distribution
			ArrayList<Trajectory> leftset = new ArrayList<Trajectory>();
			ArrayList<Integer> leftlabels = new ArrayList<Integer>();
			ArrayList<Trajectory> rightset = new ArrayList<Trajectory>();
			ArrayList<Integer> rightlabels = new ArrayList<Integer>();
			
			Iterator<Trajectory> itor1 = dataset.iterator();
			Iterator<Integer> itor2 = labelset.iterator();
			for ( ; itor1.hasNext() && itor2.hasNext(); ) {
				Trajectory t = itor1.next(); 
				int classlabel = itor2.next();
				try {
					if (t.getDistance(node.m_reference) <= node.m_distance) {
						leftset.add(t);
						leftlabels.add(classlabel);
					} else {
						rightset.add(t);
						rightlabels.add(classlabel);
					}
				} catch (Exception e) {
					e.printStackTrace();
				}
			}
			
			// get the error estimation from children
			error += getEstimatedErrorForBranch(leftset,leftlabels,node.m_left) 
				+ getEstimatedErrorForBranch(rightset,rightlabels,node.m_right);
		}
		return error;
	}
	
	/**
	 * compute estimated error for leaf
	 * @param distribution
	 * @return
	 */
	private double getEstimatedErrorForDistribution(Distribution distribution) {
		double error = 0.0;
		if (distribution.total() != 0) {
			error = distribution.numMiss() + 
			Stats.addErrs((double)distribution.total(), 
							(double)distribution.numMiss(), 
							m_cf);
		}
		return error;
	}
	
	
	public void run() {
		runImpl(m_trainset, m_trainlabels);
		runImpl(m_testset, m_testlabels);
	}
	
	protected void runImpl(Collection<Trajectory> dataset, 
							Collection<Integer> labelset) {
		int correct = 0;
		
		Iterator<Integer> it1 = labelset.iterator();
		Iterator<Trajectory> it2 = dataset.iterator();
		int counter = 0;
		for ( ; it1.hasNext() && it2.hasNext(); ) {
			counter++;
			if (it1.next().intValue() == classifyTrajectory(it2.next())) {
				correct++;
			}
		}
		double rate = ((double)(labelset.size()-correct))/labelset.size();
		System.out.println("The error rate is: " + rate);
	}
	
	public int classifyTrajectory(Trajectory t) {
		return compareNode(m_treeroot, t);
	}
	
	protected int compareNode(TreeNode n, Trajectory t) {
		if (n instanceof LeafNode) {
			return ((LeafNode)n).m_label;
		} else {
			double distance = 0.0;
			try {
				 distance = t.getDistance(n.m_reference);
			} catch (Exception e) {
				e.printStackTrace();
			}
			if (distance <= n.m_distance) {
				return compareNode(n.m_left, t);
			} else {
				return compareNode(n.m_right, t);
			}
		}
	}
	/**
	 * @param args
	 */
	public static void main(String[] args) {
		// TODO Auto-generated method stub
		System.out.println("This is the decision tree classifier to work on" +
				"the UCRiverside data sets");
		
		
		String[] datasets = {"Beef"};
		
		for (int i = 0; i < datasets.length; i++) {
			System.out.println("start running dataset: " + datasets[i]);
			DecisionTreeClassifier classifier = new DecisionTreeClassifier(2);
			//classifier.showDecisionTree();
			classifier.run();	
		}
	}

}

class TreeNode {
	/** the tree level, root level is 0 */
	int m_level = -1;
	
	/** the reference trajectory for splitting at this node */
	Trajectory m_reference = null; 
	
	/** w is the split distance value to the reference trajectory */
	double m_distance = 0.0; 
	
	/** the left child node and the right child node */
	TreeNode m_left = null, m_right = null;
	
	/** the distribution associated with this node */
	Distribution m_distribution = null;
	
	/**
	 * 
	 */
	public TreeNode(int level) {
		m_level = level;
	}
	
	/**
	 * 
	 * @param t the trajectory for splitting reference, null means it is a leaf node
	 * @param w the distance for splitting
	 */
	public TreeNode(Trajectory t, double w, Distribution d) {
		m_reference = t;
		m_distance = w;
		m_distribution = d;
	}
	
	public void setDistribution(Distribution d) {
		m_distribution = d;
	}
}

class LeafNode extends TreeNode {
	int m_label = 0;
	
	/** 
	 * construct a leaf node, use the most class in this node as the leaf label
	 * @param labelset
	 */
	LeafNode(Collection<Trajectory> dataset, 
			Collection<Integer> labelset,
			int level) {
		super(level);
		// set the leaf class label to that of the majority of this labelset
		m_label = voteClass(labelset);
		m_distribution = new Distribution(dataset, labelset);
	}
	
	private int voteClass(Collection<Integer> labelset) {
		// must vote for the best labels
		Hashtable<Integer, Integer> dict = new Hashtable<Integer, Integer>();
		for (Iterator<Integer> itor = labelset.iterator(); itor.hasNext(); ) {
			//dict.put(itor.next(), "exist");
			int count = 0;
			int key = itor.next().intValue();
			if (dict.containsKey(key)) {
				count = dict.get(key);
			}
			count++;
			dict.put(key, count);
		}
		int maxclass = Integer.MAX_VALUE;
		int maxcount = 0;
		for (Iterator<Integer> itor=dict.keySet().iterator();itor.hasNext(); ) {
			int key = itor.next().intValue();
			int count = dict.get(key).intValue();
			if (count >= maxcount) {
				maxcount = count;
				maxclass = key;
			}
		}
		assert(maxclass != Integer.MAX_VALUE);
		return maxclass;
	}
}

class SplitItem implements Comparable {
	Trajectory t = null;
	int label = Integer.MAX_VALUE;
	double distance = 0.0;
	
	public SplitItem(Trajectory t, int l, double d) {
		this.t = t;
		this.label = l;
		this.distance = d;
	}
		
	public int compareTo(Object o) {
		if (this.distance > ((SplitItem)o).distance)
			return 1;
		else if (this.distance < ((SplitItem)o).distance)
			return -1;
		return 0;
	}
	
}

class Distribution {
	/** total weight of the instances included in this distribution */
	int m_total = 0;
	
	/** weight/count of trajectories per class */
	Hashtable<Integer, Integer> m_perclass = new Hashtable<Integer, Integer>();
	
	/** weight/count of trajectories per bag, 
	 * supposingly two bags (left and right) */ 
	int[] m_perbag; 
		
	/** weight/count of trajectories per class per bag. 
	 *  since we have at most two bags, we use this one for the left bag, when
	 *  there is only one bag, this member is set to null */
	Hashtable<Integer, Integer> m_perclassleftbag = null;
	
	public Distribution(Collection<Trajectory> dataset, 
						Collection<Integer> labelset) {
		// there is only one bag
		int numofbags = 1;
		m_perbag = new int[numofbags];
		m_perclassleftbag = null;
		m_total = 0;
		Iterator<Integer> itor1 = labelset.iterator();
		for ( ; itor1.hasNext(); ) {
			//add(0, itor1.next(), itor2.next());
			int classindex = itor1.next();
			int weight = 1; // all weights are equal to 1
			if (m_perclass.containsKey(classindex)) {
				weight = m_perclass.get(classindex) + 1;
			}
			m_perclass.put(classindex, weight);
			
			m_perbag[0]++;
			m_total++;
		}
	}
	
	public Distribution(Collection<Trajectory> leftset,
						Collection<Integer> leftlabels,
						Collection<Trajectory> rightset,
						Collection<Integer> rightlabels) {
		int numofbags = 2;
		m_perbag = new int[numofbags];
		m_perclassleftbag = new Hashtable<Integer, Integer>();
		m_total = 0;
		
		Iterator<Integer> itor1 = leftlabels.iterator();
		Iterator<Integer> itor2 = rightlabels.iterator();
		for ( ;itor1.hasNext(); ) {
			int classindex = itor1.next();
			int weight = 1;
			if (m_perclass.containsKey(classindex)) {
				weight = m_perclass.get(classindex) + 1;
			}
			m_perclass.put(classindex, weight);
			m_perclassleftbag.put(classindex, weight);
			
			m_perbag[0]++;
			m_total++;
		}
		for ( ;itor2.hasNext(); ) {
			int classindex = itor2.next();
			int weight = 1;
			if (m_perclass.containsKey(classindex)) {
				weight = m_perclass.get(classindex) + 1;
			}
			m_perclass.put(classindex, weight);
			
			m_perbag[1]++;
			m_total++;
		}
 	}
	
	public static Distribution resetDistribution(Collection<Trajectory> dataset, 
												Collection<Integer> labelset,
												Trajectory reference,
												double splitdistance) {
		ArrayList<Trajectory> leftset = new ArrayList<Trajectory>();
		ArrayList<Integer> leftlabels = new ArrayList<Integer>();
		ArrayList<Trajectory> rightset = new ArrayList<Trajectory>();
		ArrayList<Integer> rightlabels = new ArrayList<Integer>();
		
		Iterator<Trajectory> itor1 = dataset.iterator();
		Iterator<Integer> itor2 = labelset.iterator();
		for ( ; itor1.hasNext() && itor2.hasNext(); ) {
			Trajectory t = itor1.next(); 
			int classlabel = itor2.next();
			try {
				if (t.getDistance(reference) <= splitdistance) {
					leftset.add(t);
					leftlabels.add(classlabel);
				} else {
					rightset.add(t);
					rightlabels.add(classlabel);
				}
			} catch (Exception e) {
				e.printStackTrace();
			}
		}
		
		return new Distribution(leftset, leftlabels, rightset, rightlabels);
	}
	
	/**
	 * @return the total weight of this distribution, generally is the size of
	 * the data set of the distribution
	 */
	public int total() {
		return m_total;
	}
	
	public int maxBag() {
		if (m_perbag.length == 1) {
			return -1;
		} else {
			if (m_perbag[0] > m_perbag[1]) {
				return 0;
			} else {
				return 1;
			}
		}
	}
	
	public int maxClass() {
		int maxclass = Integer.MAX_VALUE;
		int maxcount = 0;
		Iterator<Integer> itor=m_perclass.keySet().iterator();
		for ( ;itor.hasNext(); ) {
			int key = itor.next();
			int count = m_perclass.get(key);
			if (count >= maxcount) {
				maxcount = count;
				maxclass = key;
			}
		}
		assert(maxclass != Integer.MAX_VALUE);
		return maxclass;
	}
	
	public int numHit() {
		return m_perclass.get(maxClass());
	}
	
	public int numMiss() {
		return m_total - m_perclass.get(maxClass());
	}
}

class Stats {
	/**
	 * Computes estimated extra error for given total number of instances
	 * and error using normal approximation to binomial distribution
	 * (and continuity correction).
	 *
	 * @param N number of instances
	 * @param e observed error
	 * @param CF confidence value
	 */
	public static double addErrs(double N, double e, double CF) {

		// Ignore stupid values for CF
		if (CF > 0.5) {
			System.err.println("WARNING: confidence value for pruning "
					+ " too high. Error estimate not modified.");
			return 0;
		}

		// Check for extreme cases at the low end because the
		// normal approximation won't work
		if (e < 1) {

			// Base case (i.e. e == 0) from documenta Geigy Scientific
			// Tables, 6th edition, page 185
			double base = N * (1 - Math.pow(CF, 1 / N));
			if (e == 0) {
				return base;
			}

			// Use linear interpolation between 0 and 1 like C4.5 does
			return base + e * (addErrs(N, 1, CF) - base);
		}

		// Use linear interpolation at the high end (i.e. between N - 0.5
		// and N) because of the continuity correction
		if (e + 0.5 >= N) {

			// Make sure that we never return anything smaller than zero
			return Math.max(N - e, 0);
		}

		// Get z-score corresponding to CF
		double z = Statistics.normalInverse(1 - CF);

		// Compute upper limit of confidence interval
		double f = (e + 0.5) / N;
		double r = (f + (z * z) / (2 * N) + z
				* Math.sqrt((f / N) - (f * f / N) + (z * z / (4 * N * N))))
				/ (1 + (z * z) / N);

		return (r * N) - e;
	}
}
