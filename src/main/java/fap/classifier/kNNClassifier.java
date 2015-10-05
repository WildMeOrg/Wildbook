package fap.classifier;

import java.util.ArrayList;

import fap.core.exceptions.IncomparableSeriesException;
import fap.core.series.Serie;
import fap.exceptions.EmptyDataSetException;

/**
 * kNN classifier.
 * @author Zoltan Geller
 * @version 19.07.2010.
 */
public class kNNClassifier extends SerializableClassifier {

	private static final long serialVersionUID = 1L;
	
	/**
	 * The number of nearest neighbours to consider. Default value is 10.
	 */
	private int k = 10;
	
	/**
	 * Indicates weighted kNN. Default value is false.
	 */
	private boolean weighted = false;
	
	/**
	 * Creates a new kNNClassifier object with default parameters:
	 * k=10, weighted=false.
	 */
	public kNNClassifier() {}

	/**
	 * Creates a new kNNClassifier object with the given parameters.
	 * @param k number of nearest neighbours, must be k>=1
	 * @param weighted true for weighted kNN
	 */
	public kNNClassifier(int k, boolean weighted)	{
		if (k<1) throw new IllegalArgumentException("k must be >0");
		this.k = k;
		this.weighted = weighted;
	}

	/** Returns k.
	 * @return the k
	 */
	public int getK() {
		return k;
	}

	/** Sets k.
	 * @param k the k to set
	 */
	public void setK(int k) {
		this.k = k;
	}

	/** Returns weighted.
	 * @return the weighted
	 */
	public boolean isWeighted() {
		return weighted;
	}

	/** Sets weighted.
	 * @param weighted the weighted to set
	 */
	public void setWeighted(boolean weighted) {
		this.weighted = weighted;
	}
	
	/**
	 * @throws NullPointerException the training set or the distance is null
	 * @throws EmptyDataSetException the training set is empty
	 * @throws IncomparableSeriesException serie is incomparable with a serie from the training set
	 */
	@Override
	public double classify(Serie serie) {

		// SRB da li nam trebaju ove provere?
		if (serie==null) throw new NullPointerException("serie can't be null");
		if (dataSet==null) throw new NullPointerException("dataSet can't be null");
		if (similarityComputor==null) throw new NullPointerException("similarity computor can't be null");
		if (dataSet.isEmpty()) throw new EmptyDataSetException("dataSet can't be empty");

		SortedList list = new SortedList(k);
		
		for (Serie train : dataSet)
		{
			double dist = similarityComputor.similarity(serie, train);
			list.add(train.getLabel(), dist);
		}
		
		ArrayList<Double> labels = new ArrayList<Double>();
		double weight[] = new double[k];
		
		int listCount=0;
		
		Node node = list.getFirst();
		while (node!=null)
		{
			listCount++;
			int index = labels.indexOf(node.label);
			if (index==-1)
			{
				labels.add(node.label);
				index = labels.size()-1;
				if (weighted)
					weight[index] = 1 / node.distance*node.distance;
				else
					weight[index] = 1;
			}
			else {
				if (weighted)
					weight[index] = weight[index] + 1 / node.distance * node.distance;
				else
					weight[index] = weight[index] + 1;
			}
			node = node.next;
		}
		
		
		int besti=0;
		double bestw=weight[0];
		for (int i=1; i<labels.size(); i++)
			if (bestw<weight[i]) {
				besti=i;
				bestw = weight[i];
			}
		
		return labels.get(besti);
		
	}

	/**
	 * Helper class.
	 * @author Zoltan Geller
	 * @version 1.0
	 */
	private class Node {
		public double label;
		public double distance;
		public Node prev;
		public Node next;
		
		public Node(double label, double dist, Node prev, Node next)
		{
			this.label = label;
			this.distance = dist;
			this.prev = prev;
			this.next = next;
		}
		
	}

	/**
	 * Simple sorted list of Nodes.
	 * @author Zoltan Geller
	 * @version 14.07.2010.
	 */
	private class SortedList{
		
		private int count;
		private int len;
		private Node first;
		private Node last;
		
		/**
		 * Creates a SortedList object with given length.
		 * @param len length of the list
		 */
		public SortedList(int len) {
			this.len = len;
			this.count = 0;
			this.first = null;
			this.last = null;
		}
		
		/**
		 * Returns the first element of the list.
		 * @return the first element of the list
		 */
		public Node getFirst() {
			return first;
		}
		
		/**
		 * Adds the given label and distance to the list.
		 * @param label label
		 * @param dist distance
		 */
		public void add(double label, double dist) {
			// if the length of the list is less than one, do nothing
			if (len<1) 
				return; 
			// if the list is empty
			if (count==0) 
			{
				first = new Node(label,dist,null,null);
				last = first;
				count++;
			}
			// if the list isn't empty
			else
			{
				// if the distance is bigger than the distance of the biggest distance
				if (dist>last.distance)
				{
					// if the list is full, do nothing
					if (count==len) 
						return;
					// if the list isn't full, append to the end
					Node node = new Node(label,dist,last,null);
					last.next = node;
					last = node;
					count++;
				}
				// if the distance is smaller than the biggest distance
				// and the length is 1, just replace the node
				else if (len==1)
				{
					first = new Node(label,dist,null,null);
					last = first;
					count = 1;
				}
				// if the distance is smaller than the biggest distance
				// and the length is greater than 1, insert the new node
				// and remove the last one if the list is full
				else
				{
					Node node = first;
					while (dist>node.distance)
						node=node.next;
					Node newNode = new Node(label,dist,node.prev,node);
					if (node!=first) 
						node.prev.next = newNode;
					node.prev=newNode;
					if (count==len)
					{
						Node tmp = last;
						last = tmp.prev;
						last.next = null;
						tmp.prev = null;
					}
					else 
						count++;
					if (first==node)
						first=newNode;
				}
			}
		}
		
	}

}
