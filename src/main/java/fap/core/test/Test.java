package fap.core.test;

import fap.core.classifier.Classifier;
import fap.core.series.Serie;
import fap.core.series.SerieList;
import fap.core.similarities.SimilarityComputor;
import fap.core.similarities.SimilarityTuner;
import fap.core.util.Callback;

/**
 * Test interface. Declares basic methods for evaluating the performance of a classifier.
 * @author Zoltan Geller
 * @version 19.07.2010.
 */
public interface Test {

	/**
	 * Returns the average error ratio.
	 * @return average error ratio
	 */
	public double getErrorRatio();
	
	/**
	 * Returns the number of misclassified time series.
	 * @return number of misclassified time series
	 */
	public int getMisclassified();
	
	/**
	 * Starts testing using the given data set.
	 * @param dataSet the data set
	 * @throws Exception if an error occurs
	 */
	public void test(SerieList<Serie> dataSet) throws Exception;

	/**
	 * Resets the tuner for reuse.
	 */
	public void reset();
	
	/**
	 * Sets the similarity computor.
	 * @param simcomp the SimilarityComputor to set
	 */
	public void setSimilarityComputor(SimilarityComputor simcomp);
	
	/**
	 * Returns the similarity computor.
	 * @return the SimilarityComputor object
	 */
	public SimilarityComputor getSimilarityComputor();
	
	/**
	 * Sets the similarity tuner.
	 * @param simtun the SimilarityTuner to set
	 */
	public void setSimilarityTuner(SimilarityTuner simtun);
	
	/**
	 * Returns the similarity tuner.
	 * @return the SimilarityTuner object
	 */
	public SimilarityTuner getSimilarityTuner();
	
	/**
	 * Sets the classifier.
	 * @param classifier the Classifier to set
	 */
	public void setClassifier(Classifier classifier);
	
	/**
	 * Returns the classifier.
	 * @return the Classifier object
	 */
	public Classifier getClassifer();

	/**
	 * Sets the callback object.
	 * @param callback the Callback to set
	 */
	public void setCallback(Callback callback);

	/**
	 * Returns the callback object.
	 * @return the Callback object
	 */
	public Callback getCallback();
	
}
