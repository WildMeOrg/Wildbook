package fap.test;

import java.io.Serializable;

import fap.core.classifier.Classifier;
import fap.core.similarities.SimilarityComputor;
import fap.core.similarities.SimilarityTuner;
import fap.core.test.AbstractTest;

/**
 * Serializable abstract Test class. Defines common methods and fields for Test classes.
 * @author Zoltan Geller
 * @version 19.07.2010
 */
public abstract class SerializableTest extends AbstractTest implements Serializable {

	private static final long serialVersionUID = 1L;
	
	/**
	 * Average error ratio. Default value is 0.0.
	 */
	protected double errorRatio = 0.0;
	
	/**
	 * Number of misclassified time series. Default value is 0.
	 */
	protected int bad = 0;

	/**
	 * SimilarityComputor object. Default value is null.
	 */
	protected SimilarityComputor similarityComputor = null;

	/**
	 * Similarity tuner. Default value is null.
	 */
	protected SimilarityTuner similarityTuner = null;
	
	/**
	 * Classifier. Default value is null.
	 */
	protected Classifier classifier = null;
	
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

	@Override
	public void setSimilarityTuner(SimilarityTuner tuner) {
		this.similarityTuner = tuner;
	}

	@Override
	public SimilarityTuner getSimilarityTuner() {
		return this.similarityTuner;
	}

	@Override
	public void setClassifier(Classifier classifier) {
		this.classifier = classifier;
	}

	@Override
	public Classifier getClassifer() {
		return this.classifier;
	}

	@Override
	public double getErrorRatio() {
		return errorRatio;
	}

	@Override
	public int getMisclassified() {
		return bad;
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
