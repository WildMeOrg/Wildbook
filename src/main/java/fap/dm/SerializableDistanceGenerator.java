package fap.dm;

import java.io.Serializable;

import fap.core.series.Serie;
import fap.core.series.SerieList;
import fap.core.similarities.SimilarityComputor;
import fap.core.util.Callback;

/**
 * Serializable abstract DistanceObject. Defines common methods and fields for distance objects.
 * @author Zoltan Geller
 * @version 01.08.2010.
 */
public abstract class SerializableDistanceGenerator implements Serializable {

	private static final long serialVersionUID = 1L;

	/**
	 * Data set. Default value is null.
	 */
	protected transient SerieList<Serie> dataSet = null;
	
	/**
	 * Similarity computor. Default value is null.
	 */
	protected SimilarityComputor similarityComputor = null;

	/**
	 * Callback object. Default value is null.
	 */
	protected transient Callback callback = null;
	
	/**
	 * SRB Oznacava da li treba generisati dijagonalnu matricu (simetricnu). Podrazumevana vrednost je true.
	 */
	protected boolean symmetrical = true;

	/**
	 * SRB Prvi red matrice. Podrazumevana vrednost je 0.
	 */
	protected int first = 0;
	
	/**
	 * SRB Poslednji red matrice. Pozdrazumevana vrednost je -1, sto znaci da treba ici do poslednjeg reda.
	 */
	protected int last = -1;
	
	/**
	 * Indicates whether generating the distances has done. Default value is false.
	 */
	protected boolean done = false;
	
	/**
	 * Indicates whether generating the distances has started. Default value is false.
	 */
	protected boolean insideLoop = false;
	
	/**
	 * Sets the data set.
	 * @param dataSet the data set to set
	 */
	public void setDataSet(SerieList<Serie> dataSet) {
		this.dataSet = dataSet;
	}
	
	/**
	 * Returns the data set.
	 * @return the data set
	 */
	public SerieList<Serie> getDataSet() {
		return this.dataSet;
	}
	
	/**
	 * Sets the similarity computor.
	 * @param simcomp the similarity computor to set
	 */
	public void setSimilarityComputor(SimilarityComputor simcomp) {
		this.similarityComputor = simcomp;
	}
	
	/**
	 * Returns the similarity computor.
	 * @return the similarity computor
	 */
	public SimilarityComputor getSimilarityComputor() {
		return this.similarityComputor;
	}
	
	/**
	 * Sets the callback object.
	 * @param callback the callback object
	 */
	public void setCallback(Callback callback) {
		this.callback = callback;
	}
	
	/**
	 * Returns the callback object.
	 * @return the callback object
	 */
	public Callback getCallback() {
		return this.callback;
	}
	
	/**
	 * SRB Postavlja simetricnost.
	 * @param symmetrical vrednost za simetricnost
	 */
	public void setSymmetrical(boolean symmetrical) {
		this.symmetrical = symmetrical;
	}
	
	/**
	 * SRB Vraca simetricnost.
	 * @return simetricnost
	 */
	public boolean isSymmetrical() {
		return this.symmetrical;
	}
	
	/**
	 * Sets the first row.
	 * @param first the first row
	 */
	public void setFirst(int first) {
		this.first = first;
	}
	
	/**
	 * Returns the first row.
	 * @return the first row
	 */
	public int getFirst() {
		return this.first;
	}
	
	/**
	 * Sets the last row.
	 * @param last the last row
	 */
	public void setLast(int last) {
		this.last = last;
	}
	
	/**
	 * Returns the last row
	 * @return the last row
	 */
	public int getLast() {
		return this.last;
	}
	
	/**
	 * Computes the distances.
	 * @throws Exception if an error occurs
	 */
	public abstract void compute() throws Exception;
	
	/**
	 * Returns the distance object.
	 * @return
	 */
	public abstract Object getDistanceObject();
	
	/**
	 * Resets the distance generator for reuse.
	 */
	public void reset() {
		this.done = false;
		this.insideLoop = false;
	}
	
}
