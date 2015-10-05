package fap.similarities;

import fap.core.data.DataPointSerie;
import fap.core.series.Serie;

/**
 * Swale (Sequence Weighted Alignment model) similarity computor, assumes that series are sorted by time (x axes) component.
 * @author Zoltan Geller
 * @version 19.07.2010.
 */
public class SwaleSimilarityComputor extends SerializableSimilarityComputor {

	private static final long serialVersionUID = 1L;
	
	/**
	 * Threshold parameter.
	 * Two points from two time series are considered to match if their distance is not greater than the treshold parameter epsilon.
	 * Default value is 0.
	 */
	private double epsilon = 0;
	
	/**
	 * Gap penalty. Default value is 0.
	 */
	private double penalty  = 0;
	
	/**
	 * Match reward. Default value is 50.
	 */
	private double reward = 50;
	
	/**
	 * Creates a new SwaleSimilarityComputor object with default parameter values:
	 * epsilon=0, penalty=0, reward=50.
	 */
	public SwaleSimilarityComputor() {}
	
	/**
	 * Creates a new SwaleSimilarityComputor object with the given parameters. Must be epsilon>=0.
	 * @param penalty penalty cost
	 * @param reward match revard
	 * @param epsilon threshold value, must be epsilon>=0
	 */
	public SwaleSimilarityComputor(double penalty, double reward, double epsilon) {
		this.setEpsilon(epsilon);
		this.penalty = penalty;
		this.reward = reward;
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
	 * Sets the gap penalty value.
	 * @param penalty the penalty to set
	 */
	public void setGap(double penalty) {
		this.penalty = penalty;
	}
	
	/**
	 * Returns the gap penalty value.
	 * @return the penalty
	 */
	public double getGap() {
		return this.penalty;
	}
	
	/**
	 * Sets the match reward value.
	 * @param reward the reward to set
	 */
	public void setReward(double reward) {
		this.reward = reward;
	}
	
	/**
	 * Returns the match reward value.
	 * @return the reward
	 */
	public double getReward() {
		return this.reward;
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
			
		double Swale[][] = new double[2][slen+1];
		
		// initialization
		Swale[0][0]=0;
		for (int i=1; i<=slen; i++)
			Swale[0][i] = i*penalty;
		
		for (int i=1; i<=glen; i++)
		{
			int in = i % 2;
			Swale[in][0] = i*penalty;
			int im1 = (i-1) % 2;
			
			double y1 = gdata.getPoint(i-1).getY();
			
			for (int j=1; j<=slen; j++)
			{
				double y2 = sdata.getPoint(j-1).getY();
				
				if (Math.abs(y1-y2)<=epsilon)
					Swale[in][j] = reward + Swale[im1][j-1];
				else
					Swale[in][j] = penalty + Math.max(Swale[im1][j], Swale[in][j-1]);
				
			}
		}
		
		int i = glen % 2;
		
		return -Swale[i][slen];
	}
	
	@Override
	public String toString() {
		return this.getClass().getName() + "(epsilon=" + epsilon + ", reward=" + reward + ", gap=" + penalty + ")";
	}
	
}
