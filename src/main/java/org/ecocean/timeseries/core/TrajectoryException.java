package org.ecocean.timeseries.core;


/**
 * 
 * @author Hui
 *
 */
public class TrajectoryException extends RuntimeException {
	/**
	 * 
	 */
	private static final long serialVersionUID = 1L;

	public TrajectoryException(String message) {
		super(" " + message);
	}
}