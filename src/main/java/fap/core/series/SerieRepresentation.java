package fap.core.series;

/**
 * Representation of a serie.
 * 
 * @author Aleksa Todorovic
 * @version 1.0
 */
public interface SerieRepresentation {

	/**
	 * Value of serie in point x.
	 * @param x value of x for searching point
	 * @return value of serie in point x
	 */
	public double getValue(double x);

	/**
	 * Value of serie outside range which is covered by this representation.
	 * @return value of serie outside covered range
	 */
	public int getOutboundValue();

}
