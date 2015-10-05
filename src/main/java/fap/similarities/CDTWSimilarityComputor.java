package fap.similarities;

import fap.core.data.DataPointSerie;
import fap.core.exceptions.IncomparableSeriesException;
import fap.core.series.Serie;

/**
 * Serializable CDTW (Constrained Dynamic Time Warping with warping window) similarity computor using Sakoe-Chiba Band, assumes that series are sorted by time (x axes) component.
 * Series must be the same length.
 * @author Zoltan Geller
 * @version 19.07.2010.
 */
public class CDTWSimilarityComputor extends SerializableSimilarityComputor {
	
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
	 * Creates a new CDTWSimilarityComputor object with default parameter values:
	 * r=100, w=-1.
	 */
	public CDTWSimilarityComputor() {}
	
	/**
	 * Sets the length of the warping window in percentage. Must be in range [0..100]. Default value of r is 100. Sets w to -1.
	 * @param r the r to set, must be in range [0..100]
	 */
	public void setR(int r) {
		if (r<0 || r>100)
			throw new IllegalArgumentException("r must be in range [0..100]");
		this.r = r;
		this.w = -1;
	}
	
	/**
	 * Returns the length of the warping window in percentage.
	 * @return the r
	 */
	public int getR() {
		return this.r;
	}
	
	/**
	 * Sets the length of the warping window. Must be w>=0. Sets r to -1.
	 * @param w the w to set, must be w>=0
	 */
	public void setW(int w) {
		if (w<0)
			throw new IllegalArgumentException("must be w>=0");
		this.w = w;
		this.r = -1;
	}
	
	/**
	 * Returns the length of the warping window.
	 * @return the w
	 */
	public int getW()
	{
		return this.w;
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
	
		int lr;
		if (w<0)
			lr = len1*r/100;
		else
			lr = w;
		
		double D[][] = new double[2][len1+1];
		
		// initialization
		D[0][0] = 0;
		for (int i=1; i<=len1; i++)
			D[0][i] = Double.POSITIVE_INFINITY;
		D[1][0] = Double.POSITIVE_INFINITY;
		
		for (int i=1; i<=len1; i++)
		{
			int in = i % 2;
			int im1 = (i-1) % 2;
			
			int startj = Math.max(1, i-lr);
			int endj = Math.min(len1, i+lr);
			
			D[in][startj-1] = Double.POSITIVE_INFINITY;
			if (i+lr<=len1)
				D[im1][endj] = Double.POSITIVE_INFINITY;
			
			double y1 = data1.getPoint(i-1).getY();
			
			for (int j=startj; j<=endj; j++)
			{
				
				double y2 = data2.getPoint(j-1).getY();
				
				D[in][j] = (y1-y2)*(y1-y2) + //Math.abs(y1-y2) + 
				           Math.min(D[im1][j], 
						            Math.min(D[im1][j-1], D[in][j-1]));
				
			}
		}
		
		int i = len1 % 2;
		
		return D[i][len1];
		
	}
	
	@Override
	public String toString() {
		return this.getClass().getName() + "(r=" + r + ", w=" + w + ")";
	}

}
