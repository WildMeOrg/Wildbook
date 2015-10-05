package fap.classifier;

import fap.core.exceptions.IncomparableSeriesException;
import fap.core.series.Serie;
import fap.exceptions.EmptyDataSetException;

/**
 * 1NN (Nearest Neighbours) classifier.
 * @author Zoltan Geller
 * @version 19.07.2010.
 */
public class NNClassifier extends SerializableClassifier {
	
	private static final long serialVersionUID = 1L;

	/**
	 * Creates a new NNCalssifier object.
	 */
	public NNClassifier() {}

	/**
	 * @throws NullPointerException if the training set or the distance is null
	 * @throws EmptyDataSetException if the training set is empty
	 * @throws IncomparableSeriesException if the serie is incomparable with a serie from the training set
	 */
	@Override
	public double classify(Serie serie) {
		
		// SRB da li nam trebaju ove provere?
		if (serie==null) throw new NullPointerException("serie can't be null");
		if (dataSet==null) throw new NullPointerException("dataSet can't be null");
		if (similarityComputor==null) throw new NullPointerException("similarity computor can't be null");
		if (dataSet.isEmpty()) throw new EmptyDataSetException("dataSet can't be empty");
		
		double mindist = Double.POSITIVE_INFINITY;
		double label = 0;
		
		for (Serie data: dataSet)
		{
			double dist = similarityComputor.similarity(serie, data);
			if (dist<mindist)
			{
				mindist = dist;
				label = data.getLabel();
			}
		}
		
		return label;
		
	}

}
