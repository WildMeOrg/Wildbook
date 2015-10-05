package fap.similarities;

import fap.classifier.NNClassifier;
import fap.core.series.Serie;
import fap.core.series.SerieList;
import fap.core.similarities.SimilarityComputor;
import fap.core.util.Callback;
import fap.test.LeaveOneOut;

/**
 * Swale (Sequence Weighted Alignment model) similarity computer tuner.
 * @author Zoltan Geller
 * @version 19.07.2010.
 */
public class SwaleSimilarityTuner extends SerializableSimilarityTuner {

	private static final long serialVersionUID = 1L;

	private double factor = 0.02;
	private double bestEpsilon = 0;
	private final double bestReward = 25;
	private double bestGap = 0;
	private int minbad = Integer.MAX_VALUE;
	private double starte = 0;
	private int startg = 0;
	private double stepSize = 0;
	private double progress = 0;
	private int steps  = 1;
	
	/**
	 * Creates a new SwaleSimilarityTuner object with default field values.
	 */
	public SwaleSimilarityTuner() {}
	
	/**
	 * Creates a new SwaleSimilarityTuner object with the given arguments.
	 * @param simcomp similarity computor
	 * @param callback callback object
	 */
	public SwaleSimilarityTuner(SimilarityComputor simcomp, Callback callback) {
		this.setSimilarityComputor(simcomp);
		this.setCallback(callback);
	}
	
	private void init() {
		bestEpsilon = 0;
		bestGap = 0;
		minbad = Integer.MAX_VALUE;
		startg = 0;
		progress = 0;
		if (callback!=null)
			callback.init(0);
		steps = 1;
		insideLoop = true;
	}
	
	@Override
	public void tune(SerieList<Serie> dataSet) throws Exception {
		SwaleSimilarityComputor dist = (SwaleSimilarityComputor)similarityComputor;
		if (done) {
			dist.setEpsilon(bestEpsilon);
			dist.setGap(bestGap);
			return;
		}
		// creating tune set
		SerieList<Serie> tuneSet = dataSet.getStratifiedSplit(2).get(0);
		// creating NNClassifier
		NNClassifier classifier = new NNClassifier();
		classifier.setSimilarityComputor(similarityComputor);
		// initializing callback
		boolean callbackNotNull = callback!=null;
		double StDev = tuneSet.getStdDev();
		double step = factor * StDev;
		if (callbackNotNull) {
			stepSize = (double)callback.getCallbackCount() / ((1/factor)*51);
			if (stepSize>1) {
				callback.setCallbackCount((int)((1/factor)*51));
				stepSize = 1;
			}
		}
		// tuning initialization
		if (!insideLoop) {
			init();
			starte = factor * StDev;
		}
		else
			callback.init(steps);
		// tuning
		double ende = StDev;
		dist.setReward(bestReward);
		for (double epsilon=starte; epsilon<=ende; epsilon+=step)
		{
			dist.setEpsilon(epsilon);
			for (int gap=startg; gap<=50; gap++)
			{
				dist.setGap(-gap); // -gap
				LeaveOneOut test = new LeaveOneOut(classifier,dist,null);
				test.test(tuneSet);
				int bad = test.getMisclassified();
				if (bad<minbad)
				{
					minbad = bad;
					bestEpsilon = epsilon;
					bestGap = -gap; // -gap
				}
				// calling back
				if (callbackNotNull)
				{
					progress += stepSize;
					if (progress>steps)
					{
						steps++;
						starte = epsilon;
						startg = gap+1;
						callback.callback();
					}
				}
			}
			if (epsilon<ende) startg=0;
		}
		// finalizing
		dist.setEpsilon(bestEpsilon);
		dist.setGap(bestGap);
		insideLoop = false;
		done = true;
		if (callbackNotNull)
			callback.callback(); 
	}

	/**
	 * @throws ClassCastException if similarityComputor's type is not SwaleSimilarityComputor
	 */
	@Override
	public void setSimilarityComputor(SimilarityComputor similarityComputor) {
		if (similarityComputor instanceof SwaleSimilarityComputor)
			super.setSimilarityComputor(similarityComputor);
		else
			throw new ClassCastException("similarityComputor must be of type SwaleSimilarityComputor");
	}

}
