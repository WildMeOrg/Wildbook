package fap.core.input;

import fap.core.data.DataPoint;

/**
 * Produces data points from comma-separated list of values.
 * @author Aleksa Todorovic, Zoltan Geller
 * @version 1.1
 */
public class CSVDataPointFactory implements DataPointFactory {

	/**
	 * Parser used to parse input string.
	 */
	private CSVParser parser;
	
	/**
	 * Tells if list of input values contain x-coordinates of points.
	 */
	private boolean hasXValue;
	
	/**
	 * Used in automatic generation of x-coordinate, if there isn't one in input string.
	 */
	private int autoX;
	
	/**
	 * Last successfully parsed point.
	 */
	private DataPoint lastPoint;
	
	/**
	 * New data point producer from comma-separated list of values.
	 * @param values list of comma-separated values
	 * @param hasXValue if true, list contains values for x, otherwise list contains only y components of points, x is set automaticly
	 */
	public CSVDataPointFactory(String values, boolean hasXValue) {
		parser = new CSVParser(values);
		this.hasXValue = hasXValue;
		autoX = 0;
	}
	
	/**
	 * New data point producer from list of values separated by the given character.
	 * @param values list of values
	 * @param separator character used to separate values in list  
	 * @param hasXValue if true, list contains values for x, otherwise list contains only y components of points, x is set automaticly 
	 */
	public CSVDataPointFactory(String values, char separator, boolean hasXValue)
	{
		parser = new CSVParser(values,separator);
		this.hasXValue = hasXValue;
		autoX = 0;
	}
	
	/**
	 * @inherit
	 */
	public boolean hasNextPoint() throws IllegalArgumentException {
		boolean ok = false;

		if (parser.hasNextValue()) {
			String invalidMessage = null; 

			double x = 0.0;
			double y = 0.0;
			
			if (hasXValue) {
				String xStr = parser.nextValue();
				try {
					x = Double.parseDouble(xStr);
					ok = parser.hasNextValue();
				} catch (NumberFormatException e) {
					invalidMessage = "Invalid number format - " + xStr;
				}
			} else {
				x = autoX;
				++autoX;
				ok = true;
			}
			
			if (ok) {
				ok = false;
				
				String yStr = parser.nextValue();
				try {
					y = Double.parseDouble(yStr);
					ok = true;
				} catch (NumberFormatException e) {
					invalidMessage = "Invalid number format - " + yStr;
				}

				if (ok) {
					lastPoint = new DataPoint(x, y);
				}
			} else {
				invalidMessage = "Invalid number of points in list";
			}
			
			if (invalidMessage != null) {
				throw new IllegalArgumentException(invalidMessage);
			}
		}
		
		return ok;
	}

	/**
	 * @inherit
	 */
	public DataPoint nextPoint() {
		return lastPoint;
	}

}
