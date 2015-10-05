package fap.core.input;

import fap.core.data.DataPointSerie;

/**
 * Generic interface for classes which produce data points.
 * @author Aleksa Todorovic
 * @version 1.0
 */
public interface DataPointSerieFactory {

	/**
	 * Test if there is another data point serie which can be read.
	 * @return true if there is data point serie available, false otherwise
	 */
	public boolean hasNextPointSerie() throws IllegalArgumentException;
	
	/**
	 * Returns next available data point serie.
	 * @return next available data point serie.
	 */
	public DataPointSerie nextPointSerie();
	
}
