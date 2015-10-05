package fap.test;

import java.util.ArrayList;

import fap.core.classifier.Classifier;
import fap.core.series.Serie;
import fap.core.series.SerieList;
import fap.core.similarities.SimilarityComputor;
import fap.core.similarities.SimilarityTuner;
import fap.core.util.Callback;

/***
 * Holdout method (without similarity computer tuning). 
 * @author Zoltan Geller
 * @version 29.07.2010.
 */
public class Holdout extends SerializableTest {

	private static final long serialVersionUID = 1L;

	/**
	 * Percentage of the training set. Default value is 0.5.
	 */
	private double percentage = 0.5;
	
	/**
	 * Initial seed for random splitting. Default value is 0.
	 */
	private long seed = 0;
	
	/**
	 * Testing object.
	 */
	private TuneTrainTest test;
	

	/**
	 * Creates a Holdout object with default field values.
	 */
	public Holdout() {}
	
	/***
	 * Creates a new Holdout object.
	 * @param percentage percentage of the data set used for training, must be in range [0..1]
	 * @param classifier classifier
	 * @param simcomp similarity computor
	 * @param tuner similarity tuner
	 * @param callback callback object
	 */
	public Holdout(double percentage, long seed, Classifier classifier, SimilarityComputor simcomp, SimilarityTuner tuner, Callback callback) {
		setPercentage(percentage);
		setClassifier(classifier);
		setSimilarityComputor(simcomp);
		setSimilarityTuner(tuner);
		setCallback(callback);
		test = null;
	}
	
	/**
	 * Creates a new Holdout object with default random seed (0).
	 * @param percentage percentage of the data set used for training, must be in range [0..1]
	 * @param classifier classifier
	 * @param simcomp similarity computor
	 * @param tuner similarity tuner
	 * @param callback callback object
	 */
	public Holdout(double percentage, Classifier classifier, SimilarityComputor simcomp, SimilarityTuner tuner, Callback callback) {
		this(percentage,0,classifier,simcomp,tuner,callback);
	}
	
	/**
	 * Sets the percentage of the training set.
	 * @param percentage the percentage to set
	 */
	public void setPercentage(double percentage) {
		this.percentage = percentage;
	}
	
	/**
	 * Returns the percentage of the training set.
	 * @return the percentage
	 */
	public double getPercentage() {
		return this.percentage;
	}
	
	/**
	 * Sets the initial seed for random splitting.
	 * @param rndSeed the rndSeed to set
	 */
	public void setSeed(long rndSeed) {
		this.seed = rndSeed;
	}
	
	/**
	 * Returns the initial seed of random splitting.
	 * @return the rndSeed
	 */
	public long getSeed() {
		return this.seed;
	}
	
	@Override
	public void test(SerieList<Serie> dataSet) throws Exception {
		ArrayList<SerieList<Serie>> list = dataSet.getPercentageSplit(percentage,seed);
		SerieList<Serie> trainSet = list.get(0);
		SerieList<Serie> testSet = list.get(1);
		if (test==null)
			test = new TuneTrainTest(trainSet,trainSet,classifier,similarityComputor,similarityTuner,callback);
		else {
			test.setTrainSet(trainSet);
		}
		test.test(testSet);
	}

	@Override
	public double getErrorRatio() {
		if (test!=null)
			return test.getErrorRatio();
		else
			return errorRatio;
	}

	@Override
	public int getMisclassified() {
		if (test!=null)
			return test.getMisclassified();
		else
			return bad;
	}

	@Override
	public boolean isDone() {
		if (test!=null)
			return test.isDone();
		else
			return false;
	}
	
	@Override
	public void reset() {
		if (test!=null)
			test.reset();
	}
	
}
