package fap.core.classifier;

import fap.core.util.Callback;

/**
 * Abstract Classifier implements common methods and fields.
 * @author Zoltan Geller
 * @version 19.07.2010.
 */
public abstract class AbstractClassifier implements Classifier {

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
