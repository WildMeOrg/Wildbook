package fap.core.data;

/**
 * One point of series data.
 * 
 * @author Aleksa Todorovic
 * @version 1.0
 */
public class DataPoint {
	
	/**
	 * x coordinate of point (usually denotes time)
	 */
	private double x;

	/**
	 * @return the x
	 */
	public double getX() {
		return x;
	}

	/**
	 * y coordinate of point (usually denotes time)
	 */
	private double y;

	/**
	 * @return the y
	 */
	public double getY() {
		return y;
	}

	/**
	 * Default constructor
	 */
	public DataPoint() {
		this(0.0, 0.0);
	}

	/**
	 * Copy constructor
	 */
	public DataPoint(DataPoint source) {
		this(source.getX(), source.getY());
	}

	/**
	 * Constructor with initial parameters
	 * @param x initial value of x
	 * @param y initial value of y
	 */
	public DataPoint(double x, double y) {
		this.x = x;
		this.y = y;
	}

	/**
	 * Textual representation of point
	 */
	public String toString() {
		return "(" + Double.toString(getX()) + "," + Double.toString(getY()) + ")";
	}

}
