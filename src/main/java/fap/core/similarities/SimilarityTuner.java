package fap.core.similarities;

import fap.core.series.Serie;
import fap.core.series.SerieList;
import fap.core.util.Callback;

/**
 * SimilarityTuner interface. Declares common methods for parameter tuning of similarity computors.
 * @author Zoltan Geller
 * @version 19.07.2010.
 */
public interface SimilarityTuner {

	/**
	 * Tunes the similarity computor using the given data set.
	 * @param dataSet tuning data set
	 * @throws Exception if an error occurs
	 */
	public void tune(SerieList<Serie> dataSet) throws Exception;

	/**
	 * Resets the tuner for reuse.
	 */
	public void reset();
	
	/**
	 * Sets the similarity computor.
	 * @param similarityComputor the SimilarityComputor to set
	 */
	public void setSimilarityComputor(SimilarityComputor similarityComputor);
	
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
	 * @return the Callback object
	 */
	public Callback getCallback();
	
}
