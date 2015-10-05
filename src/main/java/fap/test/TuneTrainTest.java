package fap.test;

import fap.core.classifier.Classifier;
import fap.core.series.Serie;
import fap.core.series.SerieList;
import fap.core.similarities.SimilarityComputor;
import fap.core.similarities.SimilarityTuner;
import fap.core.util.Callback;

/**
 * Tune-Train-Test for testing classifiers. There are three possibilities:<br>
 * 1. Tuning the similarity computor, training the classifier and then testing<br>
 * 2. Training the classifier and testing (without tuning)<br>
 * 3. Testing the classifier (without tuning and training)
 * @author Zoltan Geller
 * @version 19.07.2010.
 */
public class TuneTrainTest extends SerializableTest {
	
	private static final long serialVersionUID = 1L;
	
	/**
	 * The training data set. Default value is null.
	 */
	private transient SerieList<Serie> trainSet = null;
	
	/**
	 * The tuning data set. Default value is null.
	 */
	private transient SerieList<Serie> tuneSet = null;
	
	private boolean needTrain;
	private boolean needTune;
	private int starti = 0;
	private double stepSize = 0;
	private double progress = 0;
	private int steps  = 1;

	/**
	 * Creates a new TuneTrainTest object with default field values.
	 */
	public TuneTrainTest() {}
	
	/**
	 * Creates a new TuneTrainTest object with tuning, training, and testing.
	 * @param tuneSet tuning data set
	 * @param trainSet training data set
	 * @param classifier classifier
	 * @param distance similarity computor
	 * @param tuner similarity tuner
	 * @param callback callback object
	 */
	public TuneTrainTest(SerieList<Serie> tuneSet, SerieList<Serie> trainSet, Classifier classifier, SimilarityComputor distance, SimilarityTuner tuner, Callback callback) {
		this.setTuneSet(tuneSet);
		this.setTrainSet(trainSet);
		this.setClassifier(classifier);
		this.setSimilarityComputor(distance);
		this.setSimilarityTuner(tuner);
		this.setCallback(callback);
		needTrain = true;
		needTune = true;
	}
	
	/**
	 * Creates a new TuneTrainTest object with training and testing. Assumes that there is no need for tuning the similarity computor.
	 * @param trainSet training data set
	 * @param classifier classifier
	 * @param distance similarity computor
	 * @param callback callback object
	 */
	public TuneTrainTest(SerieList<Serie> trainSet, Classifier classifier, SimilarityComputor distance, Callback callback) {
		setTrainSet(trainSet);
		setClassifier(classifier);
		setSimilarityComputor(distance);
		setCallback(callback);
		needTrain = true;
		needTune = false;
	}
	
	/**
	 * Creates a new TuneTrainTest object with testing only. Assumes that there is no need for training the classifier nor for tuning the similarity computor.
	 * @param testSet testing data set
	 * @param classifier classifier
	 * @param callback callback object
	 */
	public TuneTrainTest(Classifier classifier, Callback callback) {
		setClassifier(classifier);
		setCallback(callback);
		needTrain = false;
		needTune = false;
	}

	/**
	 * Sets the training data set.
	 * @param trainSet the training data set
	 */
	public void setTrainSet(SerieList<Serie> trainSet) {
		this.trainSet = trainSet;
	}
	
	/**
	 * Returns the training data set.
	 * @return the training data set
	 */
	public SerieList<Serie> getTrainSet() {
		return this.trainSet;
	}
	
	/**
	 * Sets the tuning data set.
	 * @param tuneSet the tuning data set.
	 */
	public void setTuneSet(SerieList<Serie> tuneSet) {
		this.tuneSet = tuneSet;
	}
	
	/**
	 * Returns the tuning data set.
	 * @return the tuning data set
	 */
	public SerieList<Serie> getTuneSet() {
		return this.tuneSet;
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
	public void test(SerieList<Serie> testSet) throws Exception {
		if (done) return;
		// tuning the similarity computor
		if (similarityTuner!=null && needTune)
			similarityTuner.tune(tuneSet);
		// building the classifier
		if (needTrain)
			classifier.build(trainSet);
		// initializing callback
		boolean callbackNotNull = callback!=null;
		if (callbackNotNull) {
			stepSize = (double)callback.getCallbackCount() / (double)testSet.size();
			if (stepSize>1) {
				callback.setCallbackCount(testSet.size());
				stepSize = 1;
			}
		}
		// testing initialization
		if (!insideLoop)
			init();
		else
			callback.init(steps);
		// testing
		for (int i=starti; i<testSet.size(); i++)
		{
			Serie testSerie = testSet.get(i);
			if (testSerie.getLabel()!=classifier.classify(testSerie)) 
				bad++;
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
		errorRatio = (double)bad / (double)testSet.size();
		insideLoop = false;
		done = true;
		if (callbackNotNull)
			callback.callback(); 
	}

}
