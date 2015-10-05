package fap.incubator;

import fap.core.data.DataPoint;
import fap.core.data.DataPointSerie;
import fap.core.data.DataPointSerieArray;
import fap.core.series.Serie;

public class Fourier {

	public static Serie DFT(Serie serie) {
		
		DataPointSerie input  = serie.getData();
		DataPointSerie output = new DataPointSerieArray();
		
		int n = input.getPointsCount();
		
		double w = 2.0 * Math.PI / n;
		
		for (int k=0; k<n-1; k++) {
			
			double a = 0.0; // real part
			double b = 0.0; // imaginary part
			
			for (int j=0; j<n-1; j++) {
				
				DataPoint dp = input.getPoint(j);
				double ai = dp.getY(); // real part
				double bi = dp.getX(); // imaginary part
				
				int jk = j*k;
				double cos = Math.cos(w*jk);
				double sin = Math.sin(w*jk);
				
				a += ai*cos + bi*sin;
				b += bi*cos - ai*sin;
				
			}
			
			output.addPoint(new DataPoint(b,a));
			
		}
		
		return new Serie(output);
	}
	
	
	/**
	 * @param args
	 */
	public static void main(String[] args) {
		// TODO Auto-generated method stub

	}

}
