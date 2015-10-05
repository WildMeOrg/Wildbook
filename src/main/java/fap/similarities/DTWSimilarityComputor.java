package fap.similarities;

import fap.core.data.DataPointSerie;
import fap.core.series.Serie;

/**
 * DTW (Dynamic Time Warping) similarity computor, assumes that series are sorted by time (x axes) component.
 * @author Zoltan Geller
 * @version 19.07.2010.
 */
public class DTWSimilarityComputor extends SerializableSimilarityComputor {

	private static final long serialVersionUID = 1L;
	
	/**
	 * Creates a new DTWSimilarityComputor object.
	 */
	public DTWSimilarityComputor() {}

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
			
		double D[][] = new double[2][slen+1];

		// initialization
		D[0][0] = 0;
		for (int i=1; i<=slen; i++)
			D[0][i] = Double.POSITIVE_INFINITY;
		D[1][0] = Double.POSITIVE_INFINITY;
		
		for (int i=1; i<=glen; i++)
		{
			int in = i % 2;
			D[in][0] = Double.POSITIVE_INFINITY; // because of D[0][0] = 0
			int im1 = (i-1) % 2;

			double y1 = gdata.getPoint(i-1).getY();

			for (int j=1; j<=slen; j++)
			{
				double y2 = sdata.getPoint(j-1).getY();

				D[in][j] = (y1-y2)*(y1-y2) + //Math.abs(y1-y2) + 
				           Math.min(D[im1][j], 
						            Math.min(D[im1][j-1], D[in][j-1]));
			}
		}
		
		int i = glen % 2;
		
		return D[i][slen];
		
	}
	
	@Override
	public String toString() {
		return this.getClass().getName();
	}

}
