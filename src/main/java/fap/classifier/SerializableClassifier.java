package fap.classifier;

import java.io.Serializable;

import fap.core.classifier.AbstractClassifier;
import fap.core.series.Serie;
import fap.core.series.SerieList;
import fap.core.similarities.SimilarityComputor;

/**
 * Abstract serializable Classifier class. Defines common methods and fields for Classifier classes.
 * @author Zoltan Geller
 * @version 19.07.2010.
 */
public abstract class SerializableClassifier extends AbstractClassifier implements Serializable {
	
	private static final long serialVersionUID = 1L;

	/**
	 * Similarity computor. Default value is null.
	 */
	protected SimilarityComputor similarityComputor = null;

	/**
	 * Data set. Default value is null.
	 */
	protected transient SerieList<Serie> dataSet = null;
	
	/**
	 * Indicates whether building the classifier has donw. Default value is false.
	 */
	protected boolean done = false;

	/**
	 * Indicates whether building the classifier has started. Default value is false.
	 */
	protected boolean insideLoop = false;

	@Override
	public void setSimilarityComputor(SimilarityComputor simcomp) {
		this.similarityComputor = simcomp;
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
	public void build(SerieList<Serie> dataSet) {
		this.dataSet = dataSet;
	}
	
	@Override
	public void reset() {
		super.reset();
		this.done = false;
		this.insideLoop = false;
	}
	
}
