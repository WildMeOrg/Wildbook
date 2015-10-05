package fap.core.prediction;

import fap.core.data.DataPoint;
import fap.core.series.SerieRepresentation;

/**
 * Predictor is object used to predict some point or value of unknown serie.
 * @author Aleksa Todorovic
 * @version 1.0
 */
public interface Predictor {

	/**
	 * Predicts value of unknown serie in some point x.
	 * @param x x-coordinate for which value of serie is being looked for
	 * @param param used to send predictor-specific parameters to function
	 * @return value of unknown serie for x
	 */
	public double predictValue(double x, Object param);
	
	/**
	 * Predicts value of some special point inside unknown serie. Semantics of this point depends on concrete type of predictor.
	 * @param param used to send predictor-specific parameters to function
	 * @return unknown point, null if predictor cannot predict point
	 */
	public DataPoint predictPoint(Object param);

	/**
	 * Generates some representation of unknown serie.
	 * @param param used to send predictor-specific parameters to function
	 * @return some representation of unknown serie
	 */
	public SerieRepresentation predictRepr(Object param);
	
}
