package fap.similarities;

import fap.classifier.NNClassifier;
import fap.core.series.Serie;
import fap.core.series.SerieList;
import fap.core.similarities.SimilarityComputor;
import fap.core.util.Callback;
import fap.test.LeaveOneOut;

/**
 * LCS (Longest Common Subsequence) similarity computer tuner.
 * @author Zoltan Geller
 * @version 19.07.2010.
 */
public class LCSSimilarityTuner extends SerializableSimilarityTuner {

	private static final long serialVersionUID = 1L;
	
	private double factor = 0.02;
	private double bestEpsilon = 0;
	private int minbad = Integer.MAX_VALUE;
	private int starti = 1;
	private double stepSize = 0;
	private double progress = 0;
	private int steps  = 1;

	/**
	 * Creates a new LCSSimilarityTuner object with default field values.
	 */
	public LCSSimilarityTuner() {}
	
	/**
	 * Creates a new LCSSimilarityTuner object with the given arguments.
	 * @param simcomp similarity computor
	 * @param callback callback object
	 */
	public LCSSimilarityTuner(SimilarityComputor simcomp, Callback callback) {
		this.setSimilarityComputor(simcomp);
		this.setCallback(callback);
	}
	
	private void init() {
		bestEpsilon = 0;
		minbad = Integer.MAX_VALUE;
		starti = 1;
		progress = 0;
		if (callback!=null)
			callback.init(0);
		steps = 1;
		insideLoop = true;
	}

	@Override
	public void tune(SerieList<Serie> dataSet) throws Exception {
		LCSSimilarityComputor dist = (LCSSimilarityComputor)similarityComputor;
		if (done) {
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
		if (callbackNotNull) {
			stepSize = (double)callback.getCallbackCount() / 50.00;
			if (stepSize>1) {
				callback.setCallbackCount(50);
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
				bestEpsilon = epsilon;
			}
			// calling back
			if (callbackNotNull)
			{
				progress += stepSize;
				if (progress>steps)
				{
					steps++;
					starti = i+1;
					callback.callback();
				}
			}
		}
		// finalizing
		dist.setEpsilon(bestEpsilon);
		insideLoop = false;
		done = true;
		if (callbackNotNull)
			callback.callback(); 
	}

	/**
	 * @throws ClassCastException if similarityComputor's type is not LCSSimilarityComputor
	 */
	@Override
	public void setSimilarityComputor(SimilarityComputor similarityComputor) {
		if (similarityComputor instanceof LCSSimilarityComputor)
			super.setSimilarityComputor(similarityComputor);
		else
			throw new ClassCastException("similarityComputor must be of type LCSSimilarityComputor");
	}

}
