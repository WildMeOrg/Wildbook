package fap.similarities;

import fap.core.data.DataPointSerie;
import fap.core.exceptions.IncomparableSeriesException;
import fap.core.series.Serie;

/**
 * CLCS (Constrained Longest Common Subsequence with warping window) similarity computor using Sakoe-Chiba Band, assumes that series are sorted by time (x axes) component.
 * Series must be the same length. 
 * Two points from two time series are considered to match if their distance is not greater than the treshold parameter epsilon.
 * @author Zoltan Geller
 * @version 19.07.2010.
 */
public class CLCSSimilarityComputor extends SerializableSimilarityComputor {

	private static final long serialVersionUID = 1L;
	
	/**
	 * Length of the warping window in percentage.
	 * Default value is 100.
	 */
	private int r = 100;
	
	/**
	 * Length of the warping window.
	 * Default value is -1.
	 */
	private int w = -1;
	
	/**
	 * Threshold parameter.
	 * Two points from two time series are considered to match if their distance is not greater than the treshold parameter epsilon.
	 * Default value is 0.
	 */
	private double epsilon = 0;
	
	/**
	 * Creates a new CDTWSimilarityComputor object with default parameter values:
	 * r=100, w=-1, epsilon=0.
	 */
	public CLCSSimilarityComputor()	{}

	/**
	 * Sets the length of the warping window in percentage. Must be in range [0..100]. Default value of r is 100. Sets w to -1.
	 * @param r the r to set, must be in range [0..100]
	 */
	public void setR(int r)	{
		if (r<0 || r>100)
			throw new IllegalArgumentException("r must be in range [0..100]");
		this.r = r;
		this.w = -1;
	}
	
	/**
	 * Returns the length of the warping window in percentage.
	 * @return the r
	 */
	public int getR()	{
		return this.r;
	}
	
	/**
	 * Sets the length of the warping window. Must be w>=0. Sets r to -1.
	 * @param w the w to set, must be w>=0
	 */
	public void setW(int w)	{
		if (w<0)
			throw new IllegalArgumentException("must be w>=0");
		this.w = w;
		this.r = -1;
	}
	
	/**
	 * Returns the length of the warping window.
	 * @return the w
	 */
	public int getW()	{
		return this.w;
	}

	/**
	 * Sets the threshold parameter. Must be epsilon>=0.
	 * Two points from two time series are considered to match if their distance is not greater than the treshold parameter epsilon.
	 *  @param epsilon the epsilon to set, must be epsilon>=0
	 */
	public void setEpsilon(double epsilon) {
		if (epsilon<0)
			throw new IllegalArgumentException("must be epsilon>=0");
		this.epsilon = epsilon;
	}
	
	/**
	 * Returns the value of the threshold parameter epsilon.
	 * @return the epsilon
	 */
	public double getEpsilon()	{
		return this.epsilon;
	}
	
	/**
	 * @throws IncomparableSeriesException if the series aren't the same length
	 */
	@Override
	public double similarity(Serie serie1, Serie serie2) throws IncomparableSeriesException {

		DataPointSerie data1 = serie1.getData();
		DataPointSerie data2 = serie2.getData();
		
		int len1 = data1.getPointsCount();
		int len2 = data2.getPointsCount();

		if (len1!=len2)
			throw new IncomparableSeriesException();
			
		long L[][] = new long[2][len1+1];

		// initialization
		for (int i=0; i<=len1; i++)
			L[0][i] = 0;
	    L[1][0] = 0;
	    
		int lr;
		if (w<0)
			lr = len1*r/100;
		else
			lr = w;
	    
		
		for (int i=1; i<=len1; i++)
		{
			
			int in = i % 2;
			int im1 = (i-1) % 2;

			int startj = Math.max(1, i-lr);
			int endj = Math.min(len1, i+lr);
			
    		L[in][startj-1] = 0;
    		if (i+lr<len1)
    			L[im1][endj] = 0;
			
			double y1 = data1.getPoint(i-1).getY();
			
			for (int j=startj; j<=endj; j++)
			{
				double y2 = data2.getPoint(j-1).getY();
				
				if (Math.abs(y1-y2)<=epsilon)
					L[in][j] = 1 + L[im1][j-1];
				else
					L[in][j] = Math.max(L[im1][j], L[in][j-1]);
			}
		}
		
		int i = len1 % 2;
		
		if (len1>0)
			return (double)(len1-L[i][len1])/(double)len1;
		else
			return 0;
	}
	
	@Override
	public String toString() {
		return this.getClass().getName() +  "(r=" + r + ", w=" + w + ", epsilon=" + epsilon + ")";
	}
	
}
