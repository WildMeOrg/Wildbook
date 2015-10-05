package fap.core.series;

import java.util.ArrayList;
import java.util.Random;

/**
 * ArrayList of time series.
 * @author Zoltan Geller
 * @version 30.07.2010.
 * @param <T>
 */
public class SerieList<T extends Serie> extends ArrayList<T> {

	private static final long serialVersionUID = 1L;

	/**
	 * SRB konstruktor
	 */
	public SerieList() {
		super();
	}

	/**
	 * SRB konstruktor
	 * @param initialCapacity
	 */
	public SerieList(int initialCapacity) {
		super(initialCapacity);
	}

	/**
	 * SRB konstruktor
	 * @param c
	 */
	public SerieList(SerieList<T> c) {
		super(c);
	}
	
	/**
	 * SRB konstruktor
	 * @param list
	 */
	public SerieList(ArrayList<SerieList<T>> list) {
		super();
		for (int i=0; i<list.size(); i++)
			this.addAll(list.get(i));
	}
	
	/**
	 * SRB konstruktor
	 * @param list
	 * @param except
	 */
	public SerieList(ArrayList<SerieList<T>> list, int except) {
		super();
		for (int i=0; i<except; i++)
			this.addAll(list.get(i));
		for (int i=except+1; i<list.size(); i++)
			this.addAll(list.get(i));
	}
	
	/**
	 * Returns the distribution of series according to the given list of labels.
	 * @param labels list of labels
	 * @return distribution of series
	 */
	public ArrayList<Integer> getDistribution(ArrayList<Double> labels)	{
		ArrayList<Integer> list = new ArrayList<Integer>();
		for (int i=0; i<labels.size(); i++)
			list.add(0);
		for (T s: this) {
			int index = labels.indexOf(s.getLabel());
			int elem = list.get(index);
			elem++;
			list.set(index, elem);
		}
		return list;
	}
	
	/**
	 * Returns the distribution of series according to labels.
	 * @return distribution of series
	 */
	public ArrayList<Integer> getDistribution() {
		return getDistribution(getLabels());
	}
	
	/**
	 * Returns list of labels of the SerieList.
	 * @return list of labels
	 */
	public ArrayList<Double> getLabels() {
		ArrayList<Double> list = new ArrayList<Double>();
		for (Serie s: this) {
			double label = s.getLabel();
			if (list.indexOf(label)==-1)
				list.add(label);
		}
		return list;
	}
	
	/**
	 * Returns list of series which belongs to the same class.
	 * @return list of series which belongs to the same class
	 */
	public ArrayList<SerieList<T>> getSeriesByClasses() {
		ArrayList<Double> labels = this.getLabels();
		ArrayList<SerieList<T>> list = new ArrayList<SerieList<T>>();
		for (int i=0; i<labels.size(); i++)
			list.add(new SerieList<T>());
		for (T s: this) {
			int index = labels.indexOf(s.getLabel());
			SerieList<T> tmp = list.get(index);
			tmp.add(s);
		}
		return list;
	}
	
	/**
	 * Groups series by class attribute.
	 * @return list of series
	 */
	public SerieList<T> groupList() {
		SerieList<T> groupList = new SerieList<T>();
		ArrayList<SerieList<T>> serieList = getSeriesByClasses();
		for (SerieList<T> list: serieList)
			groupList.addAll(list);
		return groupList;
	}
	
	/***
	 * Randomnly divides the list into k subsets with given random seed.
	 * @param k number of subsets, must be in range [1..size]
	 * @param seed random seed
	 * @return list of subsets
	 */
	public ArrayList<SerieList<T>> getRandomSplit(int k, long seed) {
		int listSize = this.size();
		if (k<1 || k>listSize) 
			throw new IllegalArgumentException("k must be in range [1..size]");
		ArrayList<SerieList<T>> list = new ArrayList<SerieList<T>>();
		int foldSize = listSize / k;
		for (int i=0; i<k; i++)
			list.add(new SerieList<T>());
		SerieList<T> first = list.get(0);
		first.addAll(this);
		Random rnd = new Random(seed);
		for (int i=1; i<k; i++)
		{
			SerieList<T> ith = list.get(i);
			for (int j=0; j<foldSize; j++)
			{
				int index = rnd.nextInt(first.size()); 
				T serie = first.get(index);
				first.remove(index);
				ith.add(serie);
			}
		}
		if (first.size()>0 && first.size()+1>foldSize)
		{
			int nk = first.size()-foldSize;
			for (int i=1; i<nk; i++)
			{
				SerieList<T> ith = list.get(i);
				int index = rnd.nextInt(first.size());
				T serie = first.get(index);
				first.remove(index);
				ith.add(serie);
			}
		}
		return list;
	}
	
	/**
	 * Randomly divides the list into k subsets. 
	 * @param k number of subsets, must be in range [1..size]
	 * @return list of subsets
	 */
	public ArrayList<SerieList<T>> getRandomSplit(int k) {
		Random rnd = new Random();
		return getRandomSplit(k,rnd.nextLong());
	}

	/**
	 * Randomly divides the list into 2 subsets with given random seed.
	 * @param percentage percentage for the first subset, must be in range [0..1]
	 * @param seed random seed
	 * @return list of subsets
	 */
	public ArrayList<SerieList<T>> getPercentageSplit(double percentage, long seed) {
		if (percentage<0 || percentage>1) 
			throw new IllegalArgumentException("percentage must be in range [0..1]");
		ArrayList<SerieList<T>> list = new ArrayList<SerieList<T>>();
		SerieList<T> first = new SerieList<T>();
		SerieList<T> second = new SerieList<T>(this);
		list.add(first);
		list.add(second);
		long count =  Math.round(this.size() * percentage);
		Random rnd = new Random(seed);
		for (long i=0; i<count; i++)
		{
			int index = rnd.nextInt(second.size());
			T s = second.get(index);
			second.remove(index);
			first.add(s);
		}
		return list;
	}
	
	/**
	 * Randomly divides the list into 2 subsets. 
	 * @param percentage percentage for the first subset, must be in range [0..1]
	 * @return list of subsets
	 */
	public ArrayList<SerieList<T>> getPercentageSplit(double percentage) {
		Random rnd = new Random();
		return getPercentageSplit(rnd.nextLong());
	}
	
	/**
	 * Divides the list into k stratified subset.
	 * @param k number of subsets, must be k>=1
	 * @return list of stratified subsets
	 */
	public ArrayList<SerieList<T>> getStratifiedSplit(int k) {
		int listSize = this.size();
		if (k<2 || k>listSize) 
			throw new IllegalArgumentException("k must be in range [2..size]");
		ArrayList<SerieList<T>> list = new ArrayList<SerieList<T>>();
		for (int i=0; i<k; i++)
			list.add(new SerieList<T>());
		SerieList<T> groupList = this.groupList();
		for (int i=0; i<k; i++)
		{
			for (int j=i; j<groupList.size(); j+=k)
			{
				SerieList<T> ith = list.get(i);
				T serie = groupList.get(j);
				ith.add(serie);
			}
		}
		return list;
	}
	
	/**
	 * SRB Vraca srednju vrednost liste kao prosek srednjih vrednosti elemenata liste.
	 * @return srednja vrednost
	 */
	public double getMean()
	{
		double mean=0;
		for (int i=0; i<this.size(); i++)
			mean += this.get(i).getMean();
		return mean/this.size();
	}

	/**
	 * SRB Vraca standardnu devijaciju liste kao prosek standardnih devijacija elemenata liste.
	 * @return standradna devijacija
	 */
	public double getStdDev()
	{
		double stdDev=0;
		for (int i=0; i<this.size(); i++)
			stdDev += this.get(i).getStDev();
		return stdDev/this.size();
	}

}
