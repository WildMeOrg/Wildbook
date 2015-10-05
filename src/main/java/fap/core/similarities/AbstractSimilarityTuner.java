/**
 * 
 */
package fap.core.similarities;

import fap.core.util.Callback;


/**
 * Abstract SimilarityTuner implements common methods and fields.
 * @author Zoltan Geller
 * @version 19.07.2010.
 */
public abstract class AbstractSimilarityTuner implements SimilarityTuner {

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
