package fap.core.similarities;

import fap.core.series.Serie;

/**
 * SimilarityComputor interface. Declares common methods for similarity computors.
 * @author Aleksa Todorovic, Zoltan Geller
 * @version 18.07.2010.
 */
public interface SimilarityComputor {

	/**
	 * Computes the similarity between two time series.
	 * @param serie1 first time serie
	 * @param serie2 second time serie
	 * @return similarity between two series of data
	 */
	public double similarity(Serie serie1, Serie serie2);
	
}
