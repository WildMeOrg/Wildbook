package fap.core.data;

import java.util.Collection;
import java.util.LinkedList;
import java.util.List;

/**
 * Serie data which stores data in an array.
 * 
 * @author Aleksa Todorovic
 * @version 1.0
 */
public class DataPointSerieLinkedList extends DataPointSerie {

	/**
	 * Collection of points stored as array.
	 */
	private LinkedList<DataPoint> points = new LinkedList<DataPoint>();

	/**
	 * Default constructor 
	 */
	public DataPointSerieLinkedList() {
	}

	/**
	 * Constructor from collection.
	 * @param C collection of points
	 */
	public DataPointSerieLinkedList(Collection<? extends DataPoint> C) {
		super(C);
	}

	/**
	 * @inherit
	 */
	protected List<DataPoint> getPoints() {
		return points;
	}

}
