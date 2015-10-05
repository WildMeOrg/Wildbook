package fap.similarities;

import fap.core.data.DataPointSerie;
import fap.core.series.Serie;

/**
 * ERP (Edit distance with Real Penalty) similarity computor, assumes that series are sorted by time (x axes) component.
 * @author Zoltan Geller
 * @version 26.07.2010.
 */
public class ERPSimilarityComputor extends SerializableSimilarityComputor {

	private static final long serialVersionUID = 1L;
	
	/**
	 * Creates a new ERPSimilarityComputor object.
	 */
	public ERPSimilarityComputor() {}

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
		
		double ERP[][] = new double[2][slen+1];

		// initialization
		ERP[0][0] = 0;
		for (int i=1; i<=slen; i++)
			ERP[0][i] = ERP[0][i-1] + Math.abs(sdata.getPoint(i-1).getY());
		
		for (int i=1; i<=glen; i++)
		{
			int in = i % 2;
			int im1 = (i-1) % 2;
			
			double y1 = gdata.getPoint(i-1).getY();
			
			ERP[in][0] = ERP[im1][0] + Math.abs(y1);

			for (int j=1; j<=slen; j++)
			{
				double y2 = sdata.getPoint(j-1).getY();
				
				double E1 = ERP[im1][j-1] + Math.abs(y1-y2);
				double E2 = ERP[in][j-1] + Math.abs(y2);
				double E3 = ERP[im1][j] + Math.abs(y1);
				
				ERP[in][j] = Math.min(E1, 
						              Math.min(E2, E3));
			}
		}
		
		int i = glen % 2;
		
		return ERP[i][slen];
		
	}
	
	@Override
	public String toString() {
		return this.getClass().getName();
	}

}
