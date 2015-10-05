package fap.core.input;

import fap.core.data.DataPoint;

/**
 * Generic interface for classes which produce data points.
 * @author Aleksa Todorovic
 * @version 1.0
 */
public interface DataPointFactory {

	/**
	 * Test if there is another data point which can be read.
	 * @return true if there is data point available, false otherwise
	 */
	public boolean hasNextPoint() throws IllegalArgumentException;
	
	/**
	 * Returns next available data point.
	 * @return next available data point.
	 */
	public DataPoint nextPoint();
	
}
