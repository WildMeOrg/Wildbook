package fap.dm;

import java.util.ArrayList;

import fap.core.series.Serie;
import fap.core.series.SerieList;
import fap.core.similarities.SimilarityComputor;
import fap.core.util.Callback;

/**
 * SRB Racuna matricu rastojanja u obliku ArrayList<double> objekta.
 * @author Zoltan Geller
 * @version 01.08.2010.
 */
public class DistanceList extends SerializableDistanceGenerator {

	private static final long serialVersionUID = 1L;
	
	/**
	 * Distance list. Default value is null.
	 */
	private ArrayList<Double> distList = null;
	
	private int starti = 0;
	private int startj = 0;
	private double stepSize = 0;
	private double progress = 0;
	private int steps  = 1;
	
	/**
	 * Creates a new DistanceVector object with default parameter values.
	 */
	public DistanceList() {}

	/**
	 * Creates a new DistanceVector object with the given parameter values.
	 * @param dataSet the data set
	 * @param first first line
	 * @param last last line
	 * @param symmetrical true for diagonal matrix
	 * @param simcomp the similarity computor
	 * @param callback callback object
	 */
	public DistanceList(SerieList<Serie> dataSet, int first, int last, boolean symmetrical, SimilarityComputor simcomp, Callback callback) {
		this.setDataSet(dataSet);
		this.setFirst(first);
		this.setLast(last);
		this.setSymmetrical(symmetrical);
		this.setSimilarityComputor(simcomp);
		this.setCallback(callback);
		this.distList = null;
	}
	
	/**
	 * SRB Pravi novi DistancecVector objekat koji ce generisati simetricnu matricu rastojanja u obliku liste sa zadatim vrednostima parametara. 
	 * @param dataSet the data set
	 * @param first first line
	 * @param last last line
	 * @param distance the similarity computor
	 * @param callback callback object
	 */
	public DistanceList(SerieList<Serie> dataSet, int first, int last, SimilarityComputor distance, Callback callback) {
		this(dataSet,first,last,true,distance,callback);
	}
	
	/**
	 * SRB Pravi novi DistanceVector objekat sa osobinama first=1, last=dataSet.size() i sa zadatim vrednostima parametar.
	 * @param dataSet the data set
	 * @param distance the similarity computor
	 * @param symmetrical true for diagonal matrix
	 * @param callback callback object
	 */
	public DistanceList(SerieList<Serie> dataSet, SimilarityComputor distance, boolean symmetrical, Callback callback) {
		this(dataSet,1,dataSet.size(),symmetrical,distance,callback);
	}
	
	/**
	 * SRB Pravi novi DistanceVector objekat sa osobinama first=1, last=dataSet.size(), symmetric=true i sa zadatim vrednostima parametara.
	 * @param dataSet the data set
	 * @param distance the similarity computor
	 * @param callback callback object
	 */
	public DistanceList(SerieList<Serie> dataSet, SimilarityComputor distance, Callback callback) {
		this(dataSet,distance,true,callback);
	}

	/**
	 * Returns the ArrayList<Double> containing the distances.
	 * @return the ArrayList of distances
	 */
	@Override
	public Object getDistanceObject() {
		return this.distList;
	}
	
	private void init()
	{
		starti = first;
		startj = 0;
		distList = new ArrayList<Double>();
		progress = 0;
		if (callback!=null)
			callback.init(0);
		steps = 1;
		insideLoop = true;
	}
	
	/**
	 * Generates the distance list.
	 * @throws Exception if an error occurs
	 */
	@Override
	public void compute() throws Exception {
		
		if (done) return;
		Serie a,b;

		if (last<0)
			last = dataSet.size();
		
		// computing number of cells
		int cnt;
		if (symmetrical) {
			int max = last*(last+1)/2;
			int n = first-1; //
			int min = n*(n+1)/2;
			cnt = max - min;
		}
		else {
			cnt = (last-first+1)*dataSet.size();
		}
		
		// initializing callback
		boolean callbackNotNull = callback!=null;
		if (callbackNotNull) {
			stepSize = (double)callback.getCallbackCount() / cnt;
			if (stepSize>1) {
				callback.setCallbackCount(cnt);
				stepSize = 1;
			}
		}
		
		// generator initialization
		if (!insideLoop) {
			init();
			distList.ensureCapacity(cnt+1);
		}
		else
			callback.init(steps);
		
		// generating
		for (int i=starti; i<=last; i++) {
			
			a = dataSet.get(i);
			
			int endj = symmetrical ? i+1 : dataSet.size(); 
			
			for (int j=startj; j<endj; j++) {
				b = dataSet.get(j);
				distList.add(similarityComputor.similarity(a, b));
				// calling back
				if (callbackNotNull)
				{
					progress += stepSize;
					if (progress>steps)
					{
						steps++;
						starti = i;
						startj = j+1;
						callback.callback();
					}
				}
			}
			
			if (i<=last) startj=0;
			
		}

		// finalizing
		insideLoop = false;
		done = true;
		if (callbackNotNull)
			callback.callback();
		
	}

}
