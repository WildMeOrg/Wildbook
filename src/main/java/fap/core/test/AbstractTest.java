package fap.core.test;

import fap.core.util.Callback;


/**
 * Abstract Test implements common methods and fields.
 * @author Zoltan Geller
 * @version 19.07.2010.
 */
public abstract class AbstractTest implements Test {

	/**
	 * Callback object. Default value is null.
	 */
	protected Callback callback = null;
	
	@Override
	public void reset() {
	}

	@Override
	public void setCallback(Callback callback) {
		this.callback = callback;
	}

	@Override
	public Callback getCallback() {
		return this.callback;
	}
	
}
