package fap.similarities;

import fap.classifier.NNClassifier;
import fap.core.series.Serie;
import fap.core.series.SerieList;
import fap.core.similarities.SimilarityComputor;
import fap.core.util.Callback;
import fap.test.LeaveOneOut;

/**
 * CLCS (Constrained Longest Common Subsequence) similarity computer tuner.
 * Series must be the same length.
 * @author Zoltan Geller
 * @version 19.07.2010.
 */
public class CLCSSimilarityTuner extends SerializableSimilarityTuner {
	
	private static final long serialVersionUID = 1L;
	
	private double factor = 0.02;
	private int bestw  = -1;
	private double bestEpsilon = 0;
	private int minbad = Integer.MAX_VALUE;
	private int startw = 1;
	private int starti = 1;
	private double stepSize = 0;
	private double progress = 0;
	private int steps  = 1;
	
	/**
	 * Creates a new CLCSSimilarityTuner object with default field values.
	 */
	public CLCSSimilarityTuner() {}
	
	/**
	 * Creates a new CLCSSimilarityTuner object with the given arguments.
	 * @param simcomp similarity computor
	 * @param callback callback object
	 */
	public CLCSSimilarityTuner(SimilarityComputor simcomp, Callback callback) {
		this.setSimilarityComputor(simcomp);
		this.setCallback(callback);
	}
	
	private void init() {
		bestw = -1;
		bestEpsilon = 0;
		minbad = Integer.MAX_VALUE;
		startw = 1;
		starti = 1;
		progress = 0;
		if (callback!=null)
			callback.init(0);
		steps = 1;
		insideLoop = true;
	}

	@Override
	public void tune(SerieList<Serie> dataSet) throws Exception {
		CLCSSimilarityComputor dist = (CLCSSimilarityComputor)similarityComputor;
		if (done) {
			dist.setW(bestw);
			dist.setEpsilon(bestEpsilon);
			return;
		}
		// creating tune set
		SerieList<Serie> tuneSet = dataSet.getStratifiedSplit(2).get(0);
		// creating NNClassifier
		NNClassifier classifier = new NNClassifier();
		classifier.setSimilarityComputor(similarityComputor);
		// initializing callback
		boolean callbackNotNull = callback!=null;
		int size = tuneSet.get(0).getData().getPointsCount();
		if (callbackNotNull) {
			stepSize = (double)callback.getCallbackCount() / (size*0.25*50);
			if (stepSize>1) {
				callback.setCallbackCount((int)(size*0.25*50));
				stepSize = 1;
			}
		}
		// tuning initialization
		if (!insideLoop)
			init();
		else
			callback.init(steps);
		// tuning
		double StDev = tuneSet.getStdDev();
		double step = factor * StDev;
		double endw = size*0.25;
		for (int w=startw; w<=endw; w++)
		{
			dist.setW(w);
			for (int i=starti; i<=50; i++)
			{
				double epsilon = i*step;
				dist.setEpsilon(epsilon);
				LeaveOneOut test = new LeaveOneOut(classifier,dist,null);
				test.test(tuneSet);
				int bad = test.getMisclassified();
				if (bad<minbad)
				{
					minbad = bad;
					bestw = w;
					bestEpsilon = epsilon;
				}
				// calling back
				if (callbackNotNull)
				{
					progress += stepSize;
					if (progress>steps)
					{
						steps++;
						startw = w;
						starti = i+1;
						callback.callback();
					}
				}
			}
			if (w<endw) starti=1;
		}
		// finalizing
		dist.setW(bestw);
		dist.setEpsilon(bestEpsilon);
		insideLoop = false;
		done = true;
		if (callbackNotNull)
			callback.callback(); 
	}

	/**
	 * @throws ClassCastException if similarityComputor's type is not CLCSSimilarityComputor
	 */
	@Override
	public void setSimilarityComputor(SimilarityComputor similarityComputor) {
		if (similarityComputor instanceof CLCSSimilarityComputor)
			super.setSimilarityComputor(similarityComputor);
		else
			throw new ClassCastException("similarityComputor must be of type CLCSSimilarityComputor");
	}

}
