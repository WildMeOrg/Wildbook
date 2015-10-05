package fap.dm;

import fap.core.series.Serie;
import fap.core.series.SerieList;
import fap.core.similarities.SimilarityComputor;
import fap.core.util.Callback;

/**
 * SRB Racuna matricu rastojanja u obliku double[][] objekta.
 * @author Zoltan Geller
 * @version 01.08.2010.
 * */
public class DistanceMatrix extends SerializableDistanceGenerator {

	private static final long serialVersionUID = 1L;
	
	/**
	 * SRB Matrica rastojanja. Podrazumevana vrednost je null.
	 */
	private double distMatrix[][] = null;
	
	
	private int starti = 0;
	private int startj = 0;
	private double stepSize = 0;
	private double progress = 0;
	private int steps  = 1;
	
	public DistanceMatrix() {}
	
	public DistanceMatrix(SerieList<Serie> dataSet, int first, int last, boolean symmetrical, SimilarityComputor simcomp, Callback callback) {
		this.setDataSet(dataSet);
		this.setFirst(first);
		this.setLast(last);
		this.setSymmetrical(symmetrical);
		this.setSimilarityComputor(simcomp);
		this.setCallback(callback);
		this.distMatrix = null;
	}

	public DistanceMatrix(SerieList<Serie> list, int first, int last, SimilarityComputor distance, Callback callback) {
		this(list,first,last,true,distance,callback);
	}
	
	public DistanceMatrix(SerieList<Serie> list, SimilarityComputor distance, boolean symmetrical, Callback callback) {
		this(list,0,list.size()-1,symmetrical,distance,callback);
	}
	
	public DistanceMatrix(SerieList<Serie> list, SimilarityComputor distance, Callback callback) {
		this(list,distance,true,callback);
	}

	
	/**
	 * SRB Vraca matricu rastojanja.
	 * @return matrica rastojanja
	 */
	@Override
	public Object getDistanceObject() {
		return this.distMatrix;
	}
	
	private void init()
	{
		starti = first;
		startj = 0;
		distMatrix = new double[dataSet.size()][];
		progress = 0;
		if (callback!=null)
			callback.init(0);
		steps = 1;
		insideLoop = true;
	}
	
	/**
	 * Generates the distance matrix.
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
			int n = (dataSet.size()-first);
			int max = n*(n+1)/2;
			n = (dataSet.size()-last);
			int min = n*(n+1)/2;
			cnt = max - min + 1;
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
		if (!insideLoop)
			init();
		else
			callback.init(steps);
		
		// generating
		for (int i=starti; i<=last; i++) {
			
			a = dataSet.get(i);
			
			int endj = symmetrical ? i+1 : dataSet.size(); 
			if (distMatrix[i]==null) {
				distMatrix[i] = new double[endj];
				startj=0;
			}
			
			for (int j=startj; j<endj; j++) {
				b = dataSet.get(j);
				distMatrix[i][j] = similarityComputor.similarity(a, b);
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
