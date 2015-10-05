package fap.similarities;

import fap.classifier.NNClassifier;
import fap.core.series.Serie;
import fap.core.series.SerieList;
import fap.core.similarities.SimilarityComputor;
import fap.core.util.Callback;
import fap.test.LeaveOneOut;

/**
 * Serializable CDTW (Constrained Dynamic Time Warping) similarity computer tuner in Keogh style.
 * Series must be the same length.
 * @author Zoltan Geller
 * @version 19.07.2010.
 */
public class CDTWSimilarityTuner extends SerializableSimilarityTuner {

	private static final long serialVersionUID = 1L;
	
	private int bestw  = -1;
	private int minbad = Integer.MAX_VALUE;
	private int startw = 1;
	private double stepSize = 0;
	private double progress = 0;
	private int steps  = 1;
	
	/**
	 * Creates a new CDTWSimilarityTuner object with default field values.
	 */
	public CDTWSimilarityTuner() {}
	
	/**
	 * Creates a new CDTWSimilarityTuner object with the given arguments.
	 * @param simcomp similarity computor
	 * @param callback callback object
	 */
	public CDTWSimilarityTuner(SimilarityComputor simcomp, Callback callback) {
		this.setSimilarityComputor(simcomp);
		this.setCallback(callback);
	}
	
	private void init()
	{
		bestw = -1;
		minbad = Integer.MAX_VALUE;
		startw = 1;
		progress = 0;
		if (callback!=null)
			callback.init(0);
		steps = 1;
		insideLoop = true;
	}
	
	@Override
	public void tune(SerieList<Serie> dataSet) throws Exception {
		CDTWSimilarityComputor dist = (CDTWSimilarityComputor)similarityComputor;
		if (done) {
			dist.setW(bestw);
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
			stepSize = (double)callback.getCallbackCount() / (size*0.25);
			if (stepSize>1) {
				callback.setCallbackCount((int)(size*0.25));
				stepSize = 1;
			}
		}
		// tuning initialization
		if (!insideLoop)
			init();
		else
			callback.init(steps);
		// tuning
		for (int w=startw; w<=size*0.25; w++)
		{
			dist.setW(w);
			LeaveOneOut test = new LeaveOneOut(classifier,dist,null);
			test.test(tuneSet);
			int bad = test.getMisclassified();
			if (bad<minbad)
			{
				minbad = bad;
				bestw = w;
			}
			// calling back
			if (callbackNotNull)
			{
				progress += stepSize;
				if (progress>steps)
				{
					steps++;
					startw = w+1;
					callback.callback();
				}
			}
		}
		// finalizing
		dist.setW(bestw);
		insideLoop = false;
		done = true;
		if (callbackNotNull)
			callback.callback(); 
	}

	/**
	 * @throws ClassCastException if similarityComputor's type is not CDTWSimilarityComputor
	 */
	@Override
	public void setSimilarityComputor(SimilarityComputor similarityComputor) {
		if (similarityComputor instanceof CDTWSimilarityComputor)
			super.setSimilarityComputor(similarityComputor);
		else
			throw new ClassCastException("similarityComputor must be subclass of type CDTWSimilarityComputor");
	}

}
