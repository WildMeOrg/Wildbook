package fap.test;

import java.util.ArrayList;

import fap.core.classifier.Classifier;
import fap.core.series.Serie;
import fap.core.series.SerieList;
import fap.core.similarities.SimilarityComputor;
import fap.core.util.Callback;

/**
 * Cross-Validation with random or stratified splitting in standard or Keogh style.
 * @author Zoltan Geller
 * @version 29.07.2010.
 */
public class CrossValidation extends SerializableTest {

	private static final long serialVersionUID = 1L;

	/**
	 * Number of folds. Default value is 10.
	 */
	private int k = 10;

	/**
	 * Initial seed for random splitting. Default value is 0.
	 */
	private long seed = 0;
	
	/**
	 * Indicates whether to do stratified or random splitting. Default value is true.
	 */
	private boolean stratified = true;
	
	/**
	 * Indicates whether to do Cross-Validation in Keogh style. Default value is false.
	 */
	private boolean keogh = false;
	
	private int foldBad = 0;
	private int starti = 0;
	private int startj = 0;
	private int tested = 0;
	private double stepSize = 0;
	private double progress = 0;
	private int steps  = 1;
	private boolean insideLoop = false;

	/**
	 * Creates a new CrossValidation object with default parameter values.
	 */
	public CrossValidation() {}
	
	/***
	 * Creates a new CrossValidation object (stratified or non-stratified with random split).
	 * @param k number of folds, must be k>1
	 * @param stratified true for stratified cross-validation
	 * @param keogh true for Keogh style of cross-validation
	 * @param classifier classifier
	 * @param distance similarity cumputor
	 * @param callback callback object
	 */
	public CrossValidation(int k, boolean stratified, boolean keogh, Classifier classifier, SimilarityComputor distance, Callback callback) {
		this.setK(k);
		this.setClassifier(classifier);
		this.setSimilarityComputor(distance);
		this.setCallback(callback);
		this.setStratified(stratified);
		this.setKeogh(keogh);
	}

	/**
	 * Creates a new non-stratified CrossValidation object with given random seed.
	 * @param k number of folds, must be in rage [2..dataSet.size]
	 * @param keogh true for Keogh style of cross-validation
	 * @param seed random seed
	 * @param classifier classifier
	 * @param distance similarity computor
	 * @param callback callback object
	 */
	public CrossValidation(int k, long seed, boolean keogh, Classifier classifier, SimilarityComputor distance, Callback callback) {
		this(k,false,keogh,classifier,distance,callback);
		this.setSeed(seed);
	}
	
	/**
	 * Sets the number of folds. Must be k>1.
	 * @param k the k to set, must be k>1
	 */
	public void setK(int k) {
		if (k<2)
			throw new IllegalArgumentException("must be k>1");
		this.k = k;
	}
	
	/**
	 * Returns the number of folds.
	 * @return the k
	 */
	public int getK() {
		return this.k;
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

	/**
	 * Selects between stratified and random splitting. True is for stratified splitting.
	 * @param stratified the stratified to set
	 */
	public void setStratified(boolean stratified) {
		this.stratified = stratified;
	}
	
	/**
	 * Returns true if stratified splitting is selected.
	 * @return the stratified
	 */
	public boolean isStratified() {
		return this.stratified;
	}

	/**
	 * Selects between Keogh style and standard Cross-Validation. True is for Keogh style.
	 * @param keogh the keogh to set
	 */
	public void setKeogh(boolean keogh) {
		this.keogh = keogh;
	}
	
	/**
	 * Returns true is Keogh style is selected.
	 * @return the keogh
	 */
	public boolean isKeogh() {
		return this.keogh;
	}


	private void init() {
		starti = 0;
		startj = 0;
		bad = 0;
		foldBad = 0;
		tested = 0;
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
		
		// callback initialization
		boolean callbackNotNull = callback!=null;
		if (callbackNotNull) {
			stepSize = (double)callback.getCallbackCount() / (double)dataSet.size();
			if (stepSize>1) {
				callback.setCallbackCount(dataSet.size());
				stepSize = 1;
			}
		}
		
		// test initialization 
		if (!insideLoop)
			init();
		else
			callback.init(steps);
		
		// splitting data set into folds
		ArrayList<SerieList<Serie>> list = null;
		if (stratified)
			list = dataSet.getStratifiedSplit(k);
		else
			list = dataSet.getRandomSplit(k,seed);
		
		// testing folds
		for (int i=starti; i<k; i++)
		{
			// selecting test and train sets
			SerieList<Serie> testSet;
			SerieList<Serie> trainSet;
			if (keogh) {
				trainSet = list.get(i);
				testSet = new SerieList<Serie>(list,i);
			}
			else {
				testSet = list.get(i);
				trainSet = new SerieList<Serie>(list,i);
			}
			
			// tuning
			similarityTuner.tune(trainSet);
			similarityTuner.reset();
			
			// building the classifier
			classifier.build(trainSet);
			classifier.reset();
			
			// testing current fold
			for (int j=startj; j<testSet.size(); j++)
			{
				Serie testSerie = testSet.get(j);
				if (testSerie.getLabel()!=classifier.classify(testSerie)) {
					foldBad++;
					bad++;
				}
				tested++;
				
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
			errorRatio += (double)foldBad / (double)testSet.size();
			foldBad = 0;
			if (i+1<k) startj=0;
		}
		
		// finalizing
		if (keogh)
			errorRatio = errorRatio / (double)k;
		insideLoop = false;
		done = true;
		if (callbackNotNull)
			callback.callback(); 
	}

}
