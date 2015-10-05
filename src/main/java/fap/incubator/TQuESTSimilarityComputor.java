package fap.incubator;

import fap.core.data.DataPoint;
import fap.core.data.DataPointSerie;
import fap.core.data.DataPointSerieArray;
import fap.core.series.Serie;
import fap.core.similarities.SimilarityComputor;

/**
 * TQuEST (Threshold query based similarity search) similarity computor, assumes that series are represented in TCTIS form.
 * TCTIS (Threshold-Crossing Time Interval Sequence) DataPoint field intepretation:
 * X - threshold-crossing time interval start point
 * Y - threshold-crossing time interval end point
 * 
 * @author Zoltan Geller
 * @version 1.0
 */
public class TQuESTSimilarityComputor implements SimilarityComputor {

	private static final long serialVersionUID = 1L;
	
	private double threshold;
	
	public TQuESTSimilarityComputor(double threshold)
	{
		this.threshold = threshold;
	}
	
	public void setThreshold(double threshold)
	{
		this.threshold = threshold;
	}
	
	public double getThreshold()
	{
		return threshold;
	}
	
	// returns the Threshold-Crossing Time Interval Sequence of Serie s
	// assumes that series are sorted by time (x axes) component
	// TCTIS DataPoint field interpretation:
	// X - threshold-crossing time interval start point
	// Y - threshold-crossing time interval end point
	public Serie getTCTIS(Serie s)
	{
		
		DataPointSerie dps = s.getData();
		DataPointSerie TCTdps = new DataPointSerieArray();
		
		int count = dps.getPointsCount();
		
		int i=0;
		
		while (i<count)
		{
			DataPoint dp = dps.getPoint(i);
			if (dp.getY()<=threshold)
				i++;
			else
			{
				double start = dp.getX();
				
				i++;
				while (i<count && dps.getPoint(i).getY()>threshold) i++;
				
				dp = dps.getPoint(i-1);
				
				TCTdps.addPoint(new DataPoint(start,dp.getX()));
				
				i++;
				
			}
		}
		
		return new Serie(TCTdps);
		
	}

	// DataPoint field interpretation:
	// X - threshold-crossing time interval start point
	// Y - threshold-crossing time interval end point
	@Override
	public double similarity(Serie serie1, Serie serie2) {
		
		DataPointSerie data1 = serie1.getData();
		DataPointSerie data2 = serie2.getData();
		
		int len1 = data1.getPointsCount();
		int len2 = data2.getPointsCount();
		
		double min1[] = new double[len1];
		double min2[] = new double[len2];
		
		double sum1=0;
		double sum2=0;

		// initialization
		for (int i=0; i<len1; i++) min1[i]=Double.POSITIVE_INFINITY;
		for (int j=0; j<len2; j++) min2[j]=Double.POSITIVE_INFINITY;
		
		for (int i=0; i<len1; i++)
		{
			double x1 = data1.getPoint(i).getX();
			double y1 = data1.getPoint(i).getY();
			
			for (int j=0; j<len2; j++)
			{
				double x2 = data2.getPoint(j).getX();
				double y2 = data2.getPoint(j).getY();
				
				double x = x1-x2;
				double y = y1-y2;
				
				double dist = Math.sqrt(x*x+y*y);
				
				if (dist<min1[i]) min1[i]=dist;
				if (dist<min2[j]) min2[j]=dist;
			}
			sum1 += min1[i];
		}
		
		for (int j=0; j<len2; j++)
			sum2 += min2[j];
		
		double result=0;
		
		if (len1>0) result += sum1/len1;
		if (len2>0) result += sum2/len2;
		
		return result;
		
	}
	
	@Override
	public String toString() {
		return "threshold="+threshold;
	}

}
