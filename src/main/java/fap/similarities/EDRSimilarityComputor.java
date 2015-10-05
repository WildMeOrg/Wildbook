package fap.similarities;

import fap.core.data.DataPointSerie;
import fap.core.series.Serie;

/**
 * EDR (Edit Distance on Real sequence) similarity computor, assumes that series are sorted by time (x axes) component.
 * @author Zoltan Geller
 * @version 19.07.2010.
 */
public class EDRSimilarityComputor extends SerializableSimilarityComputor {

	private static final long serialVersionUID = 1L;
	
	/**
	 * Threshold parameter.
	 * Two points from two time series are considered to match if their distance is not greater than the treshold parameter epsilon.
	 * Default value is 0.
	 */
	private double epsilon = 0;
	
	/**
	 * Creates a new EDRSimilarityComputor object with default threshold value:
	 * epsilon=0.
	 */
	public EDRSimilarityComputor() {}
	
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
	
	@Override
	public double similarity(Serie serie1, Serie serie2) {
		
		DataPointSerie data1 = serie1.getData();
		DataPointSerie data2 = serie2.getData();
		DataPointSerie sdata, gdata;
		
		int len1 = data1.getPointsCount();
		int len2 = data2.getPointsCount();
		
		int slen,glen;
		
		if (len1<len2)
		{
			slen = len1;
			glen = len2;
			sdata = data1;
			gdata = data2;
		}
		else
		{
			slen = len2;
			glen = len1;
			sdata = data2;
			gdata = data1;
		}
			
		long EDR[][] = new long[2][slen+1];

		// initialization
		EDR[0][0]=0;
		for (int i=1; i<=slen; i++)
			EDR[0][i] = i;
		
		for (int i=1; i<=glen; i++)
		{
			int in = i % 2;
			EDR[in][0] = i;
			int im1 = (i-1) % 2;

			double y1 = gdata.getPoint(i-1).getY();

			for (int j=1; j<=slen; j++)
			{
				double y2 = sdata.getPoint(j-1).getY();
				
				int subcost = Math.abs(y1-y2)<=epsilon ? 0 : 1;
				EDR[in][j] = Math.min(EDR[im1][j-1] + subcost, 
						              1 + Math.min(EDR[im1][j], EDR[in][j-1]));
			}
		}
		
		int i = glen % 2;
		
		return EDR[i][slen];
		
	}

	@Override
	public String toString() {
		return this.getClass().getName() + "(epsilon=" + epsilon + ")";
	}
	
}
