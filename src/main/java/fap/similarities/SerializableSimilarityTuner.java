package fap.similarities;

import java.io.Serializable;

import fap.core.similarities.AbstractSimilarityTuner;
import fap.core.similarities.SimilarityComputor;

/**
 * Serializable abstract SimilarityTuner class. Defines common methods and fields for SimilarityTuner classes.
 * @author Zoltan Geller
 * @version 18.07.2010.
 */
public abstract class SerializableSimilarityTuner extends AbstractSimilarityTuner implements Serializable {

	private static final long serialVersionUID = 1L;
	
	/**
	 * SimilarityComputor object. Default value is null.
	 */
	protected SimilarityComputor similarityComputor = null;

	/**
	 * Indicates whether the tuning has donw. Default value is false.
	 */
	protected boolean done = false;

	/**
	 * Indicates whether the tuning has started. Default value is false.
	 */
	protected boolean insideLoop = false;

	@Override
	public void setSimilarityComputor(SimilarityComputor similarityComputor) {
		this.similarityComputor = similarityComputor;
	}

	@Override
	public SimilarityComputor getSimilarityComputor() {
		return this.similarityComputor;
	}
	
	/**
	 * Returns the value of done.
	 * @return true the done
	 */
	public boolean isDone() {
		return this.done;
	}
	
	@Override
	public void reset() {
		super.reset();
		this.done = false;
		this.insideLoop = false;
	}
	
}
