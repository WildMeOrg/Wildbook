package fap.core.input;

/**
 * Parses comma(or any other character)-separated values and produces values. Has interface similar to Interator.
 * @author Aleksa Todorovic
 * @version 1.0
 */
public class CSVParser {

	/**
	 * String with list of values which is parsed.
	 */
	private String values;
	
	/**
	 * Character used to separate values.
	 */
	private char separator;
	
	/**
	 * Current parsing position.
	 */
	private int position;
	
	/**
	 * Last values parsed.
	 */
	private String lastValue;
	
	/**
	 * New CSV parser for comma-separated list. 
	 * @param values list of comma-separated string
	 */
	public CSVParser(String values) {
		this(values, ',');
	}

	/**
	 * New CSV parser for separator-separated list. 
	 * @param values list of separator-separated string
	 * @param separator character used to separate values in list
	 */
	public CSVParser(String values, char separator) {
		this.values = values;
		this.separator = separator;
		position = 0;
	}

	/**
	 * Checks if there is another value available to read from values.
	 * @return true if there is another value available, false otherwise
	 */
	public boolean hasNextValue() {
		while ((position < values.length()) && (values.charAt(position) == separator)) {
			++position;
		}
		
		int firstPosition = position;
		while ((position < values.length()) && (values.charAt(position) != separator)) {
			++position;
		}
		
		if (position == firstPosition) {
			return false;
		} else {
			lastValue = values.substring(firstPosition, position);
			return true;
		}
	}

	/**
	 * Returns last value successfully parsed from values.
	 * @return last value successfully parsed
	 */
	public String nextValue() {
		return lastValue;
	}

}
