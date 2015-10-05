package fap.core.util;

/**
 * Callback interface. Declares common methods for callback objects.
 * @author Zoltan Geller
 * @version 19.07.2010.
 */
public interface Callback {

	/**
	 * Indicates how many callbacks we want.
	 * @return number of callbacks
	 */
	public int getCallbackCount();
	
	/**
	 * Sets the number of callbacks.
	 * @param cbcount number of callbacks
	 */
	public void setCallbackCount(int cbcount);
	
	/**
	 * Initializes the callback object.
	 * @param value initialization value for the callback object
	 */
	public void init(int value);
	
	/**
	 * Callback method.
	 * @throws Exception if an error occurs
	 */
	public void callback() throws Exception;
	
}
