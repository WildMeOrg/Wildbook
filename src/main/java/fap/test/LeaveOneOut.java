package fap.test;

import fap.core.classifier.Classifier;
import fap.core.series.Serie;
import fap.core.series.SerieList;
import fap.core.similarities.SimilarityComputor;
import fap.core.util.Callback;

/**
 * Leave-One-Out (without similarity computer tuning).
 * @author Zoltan Geller
 * @version 19.07.2010.
 */
public class LeaveOneOut extends SerializableTest {

	private static final long serialVersionUID = 1L;
	
	private int starti = 0;
	private double stepSize = 0;
	private double progress = 0;
	private int steps  = 1;
	
	/**
	 * Creates a new LeaveOneOut object with default field values.
	 */
	public LeaveOneOut() {}
	
	/**
	 * Creates a new LeaveOneOut object.
	 * @param classifier classifier
	 * @param simcomp similarity computor
	 * @param callback callback object
	 */
	public LeaveOneOut(Classifier classifier, SimilarityComputor simcomp, Callback callback) {
		setClassifier(classifier);
		setSimilarityComputor(simcomp);
		setCallback(callback);
	}

	private void init() {
		starti = 0;
		bad = 0;
		errorRatio = 0.0;
		progress = 0;
		if (callback!=null)
			callback.init(0);
		steps = 1;
		insideLoop = true;
	}

	@Override
	public void test(SerieList<Serie> dataSet) throws Exception {
		if (done) return;
		// initializing callback
		boolean callbackNotNull = callback!=null;
		if (callbackNotNull) {
			stepSize = (double)callback.getCallbackCount() / (double)dataSet.size();
			if (stepSize>1) {
				callback.setCallbackCount(dataSet.size());
				stepSize = 1;
			}
		}
		// testing initialization
		if (!insideLoop)
			init();
		else
			callback.init(steps);
		// testing
		for (int i=starti; i<dataSet.size(); i++)
		{
			Serie testSerie = dataSet.remove(i);
			classifier.build(dataSet);
			classifier.reset();
			if (testSerie.getLabel()!=classifier.classify(testSerie))
				bad++;
			dataSet.add(i, testSerie);
			// calling back
			if (callbackNotNull)
			{
				progress += stepSize;
				if (progress>steps)
				{
					steps++;
					starti = i+1;
					errorRatio = (double)bad / (double)(i+1);
					callback.callback();
				}
			}
		}
		// finalizing
		errorRatio = (double)bad / (double)dataSet.size();
		insideLoop = false;
		done = true;
		if (callbackNotNull)
			callback.callback(); 
	}

}
