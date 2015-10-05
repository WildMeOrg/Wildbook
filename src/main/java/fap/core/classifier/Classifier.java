package fap.core.classifier;

import fap.core.series.Serie;
import fap.core.series.SerieList;
import fap.core.similarities.SimilarityComputor;
import fap.core.util.Callback;

/**
 * Classifier interface. Declares common methods for classifiers.
 * @author Zoltan Geller
 * @version 18.07.2010.
 */
public interface Classifier {
	
	/**
	 * Builds the classifier using the similarity computor and the given data set.
	 * @param dataSet the data set
	 * @throws Exception if an error occurs
	 */
	public void build(SerieList<Serie> dataSet) throws Exception;
	
	/**
	 * Resets the classifier for reuse.
	 */
	public void reset();
	
	/**
	 * Classifies a time serie using the similarity computor.
	 * @param serie the time serie to be classified
	 * @return the predicted class of the time serie
	 * @throws Exception if an error occurs
	 */
	public double classify(Serie serie) throws Exception;
	
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
	 * Sets the callback object.
	 * @param callback the Callback to set
	 */
	public void setCallback(Callback callback);

	/**
	 * Returns the callback object.
	 * @return the callback
	 */
	public Callback getCallback();
	
}
