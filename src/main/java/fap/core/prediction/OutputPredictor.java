package fap.core.prediction;

import fap.core.series.Serie;
import fap.core.series.SerieList;

/**
 * Generic parent for all predictors which predict output based on input serie list and one testing serie.
 * @author Aleksa Todorovic
 * @version 1.0
 */
public abstract class OutputPredictor<T extends Serie> implements Predictor {

	/**
	 * Testing input serie.
	 */
	protected T testInputSerie;
	
	/**
	 * List of input series.
	 */
	protected SerieList<T> inputSeries;
	
	/**
	 * Constructor
	 * @param testInputSerie testing input serie
	 * @param inputSeries list of input series
	 * @param inputOutputMapping map of output series for input series
	 */
	protected OutputPredictor(T testInputSerie, SerieList<T> inputSeries) {
		this.testInputSerie = testInputSerie;
		this.inputSeries = inputSeries;
	}
	
}
