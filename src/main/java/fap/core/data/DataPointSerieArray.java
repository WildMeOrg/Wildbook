package fap.core.data;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

/**
 * Serie data which stores data in an array.
 * 
 * @author Aleksa Todorovic
 * @version 1.0
 */
public class DataPointSerieArray extends DataPointSerie {

	/**
	 * Collection of points stored as array.
	 */
	private ArrayList<DataPoint> points = new ArrayList<DataPoint>();

	/**
	 * Default constructor 
	 */
	public DataPointSerieArray() {
	}

	/**
	 * Constructor from collection.
	 * @param C collection of points
	 */
	public DataPointSerieArray(Collection<? extends DataPoint> C) {
		super(C);
	}

	/**
	 * @inherit
	 */
	protected List<DataPoint> getPoints() {
		return points;
	}

}
