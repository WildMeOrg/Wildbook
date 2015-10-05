package fap.similarities;

import fap.core.data.DataPointSerie;
import fap.core.exceptions.IncomparableSeriesException;
import fap.core.series.Serie;

/**
 * Lp similarity computor, assumes that series are sorted by time (x axes) component.
 * Series must be the same length.
 * @author Zoltan Geller
 * @version 19.07.2010.
 */
public class LpSimilarityComputor extends SerializableSimilarityComputor {
	
	private static final long serialVersionUID = 1L;
	
	/**
	 * The p parameter. Default value is 2.
	 */
	private double p = 2;
	
	/**
	 * Creates a new LpnfinitySimilarityComputor object with default parameter's value p=2.
	 */
	public LpSimilarityComputor() {}
	
	/**
	 * Creates a new LpnfinitySimilarityComputor object with the given value of p.
	 * @param p the value of p
	 */
	public LpSimilarityComputor(double p) {
		this.setP(p);
	}
	
	/**
	 * Sets the value of the parameter p.
	 * @param p the p to set
	 */
	public void setP(double p)	{
		this.p = p;
	}
	
	/**
	 * Returns the value of the parameter p.
	 * @return the p
	 */
	public double getP() {
		return this.p;
	}

	/** 
	 * @throws IncomparableSeriesException if the series aren't the same length
	 */
	@Override
	public double similarity(Serie serie1, Serie serie2) throws IncomparableSeriesException {
		
		double result=0;
		
		DataPointSerie data1 = serie1.getData();
		DataPointSerie data2 = serie2.getData();
		
		int len1 = data1.getPointsCount();
		int len2 = data2.getPointsCount();
		
		if (len1!=len2)
			throw new IncomparableSeriesException();
		
		for (int i=0; i<len1; i++)
		{
			double x1 = data1.getPoint(i).getX();
			double y1 = data1.getPoint(i).getY();
			double x2 = data2.getPoint(i).getX();
			double y2 = data2.getPoint(i).getY();
			
			if (x1!=x2)
				throw new IncomparableSeriesException();
			
			double tmp = Math.abs(y1 - y2);
			
			if (p==Double.POSITIVE_INFINITY)
				result = Math.max(result, tmp);
			else
				result += Math.pow(tmp, p);
		}
		
		if (p<Double.POSITIVE_INFINITY)
			result = Math.pow(result, 1/p);
		
		return result;
		
	}

	@Override
	public String toString() {
		return this.getClass().getName() + "(p=" + p + ")";
	}

}
