package fap.core.prediction;

import java.util.Map;

import fap.core.series.Serie;
import fap.core.series.SerieList;

/**
 * Generic parent for all predictors which predict output based on input serie list, corresponding outputs and one testing serie.
 * @author Aleksa Todorovic
 * @version 1.0
 */
public abstract class InputOutputPredictor<T extends Serie> implements Predictor {

	/**
	 * Testing input serie.
	 */
	protected T testInputSerie;
	
	/**
	 * List of input series.
	 */
	protected SerieList<T> inputSeries;
	
	/**
	 * Map of output series for input series. Keys are input series, values are output series.
	 */
	protected Map<T, T> inputOutputMapping;
	
	/**
	 * Constructor
	 * @param testInputSerie testing input serie
	 * @param inputSeries list of input series
	 * @param inputOutputMapping map of output series for input series
	 */
	protected InputOutputPredictor(T testInputSerie, SerieList<T> inputSeries, Map<T, T> inputOutputMapping) {
		this.testInputSerie = testInputSerie;
		this.inputSeries = inputSeries;
		this.inputOutputMapping = inputOutputMapping;
	}
	
}
