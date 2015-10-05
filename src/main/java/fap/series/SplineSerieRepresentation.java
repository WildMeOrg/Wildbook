package fap.series;

import java.util.Iterator;

import fap.core.data.DataPoint;
import fap.core.data.DataPointSerie;
import fap.core.math.Polynomial;
import fap.core.series.SerieRepresentation;

/**
 * Representation of serie which uses splines.
 * @author Aleksa Todorovic, Vladimir Kurbalija
 * @version 1.0
 */
public class SplineSerieRepresentation implements SerieRepresentation {

	private DataPointSerie data;
	private Polynomial[] splines;
	private double max = 0.0; // maximal value used for calculating saturation
	private double saturation = 0.0; // time of the saturation
	private double scale = 1.0;

	public SplineSerieRepresentation(DataPointSerie data) {
		this.data = data;
		this.splines = new Polynomial[data.getPointsCount()];
		for (int i = 0; i < data.getPointsCount(); i++) {
			splines[i] = new Polynomial(3); // this is a cubic spline
		}
		initializeSplines();
		calculateMax();
		calculateSaturation();
	}

	public int getSplinesCount() {
		return splines.length;
	}
	
	public Polynomial getSpline(int i) {
		return Polynomial.mul(scale, splines[i]);
	}

	public double getMax() {
		return max;
	}

	public double getSaturation() {
		return saturation;
	}

	public double getScale() {
		return scale;
	}

	public void setScale(double scale) {
		this.scale = scale;
	}

	public double getValue(double x) {
		// returns P(x); if x is not in the interval then returns -1000
		for (int i = 0; i < data.getPointsCount() - 1; i++) {
			if ((x >= data.getPoint(i).getX()) && (x <= data.getPoint(i + 1).getX())) {
				return scale * splines[i].value(x);
			}
		}
		return getOutboundValue();
	}

	public int getOutboundValue() {
		return -1000;
	}

	private void initializeSplines() {
		double[] mainDiag = new double[data.getPointsCount()];// main diagonale in matrix
		// of system
		double[] d = new double[data.getPointsCount()];// first derivations in points
		double[] rightSide = new double[data.getPointsCount()];// right side of the
		// system
		for (int i = 0; i < data.getPointsCount(); i++) {
			if ((i == 0) || (i == data.getPointsCount() - 1)) {
				mainDiag[i] = 2;
			} else {
				mainDiag[i] = 4;
			}
			if (i == 0) {
				rightSide[i] = 3 * (data.getPoint(1).getX() - data.getPoint(0).getX());
			} else if (i == data.getPointsCount() - 1) {
				rightSide[i] = 3 * (data.getPoint(i).getX() - data.getPoint(i - 1).getX());
			} else {
				rightSide[i] = 3 * (data.getPoint(i + 1).getX() - data.getPoint(i - 1).getX());
			}
		}
		for (int i = 1; i < data.getPointsCount(); i++) {
			mainDiag[i] = mainDiag[i] - 1 / mainDiag[i - 1];
			rightSide[i] = rightSide[i] - rightSide[i - 1] / mainDiag[i - 1];
		}
		d[data.getPointsCount() - 1] = rightSide[data.getPointsCount() - 1] / mainDiag[data.getPointsCount() - 1];
		for (int i = data.getPointsCount() - 2; i >= 0; i--) {
			d[i] = (rightSide[i] - 1) / mainDiag[i];
		}
	
		// calculating the coefficients of splines t is in interval [0,1]
		for (int i = 0; i < data.getPointsCount() - 1; i++) {
			splines[i].coefficients[0] = data.getPoint(i).getY();
			splines[i].coefficients[1] = d[i];
			splines[i].coefficients[2] = 3 * (data.getPoint(i + 1).getY() - data.getPoint(i).getY())
					- 2 * d[i] - d[i + 1];
			splines[i].coefficients[3] = 2 * (data.getPoint(i).getY() - data.getPoint(i + 1).getY())
					+ d[i] + d[i + 1];
		}
	
		// making substitions t=(x-xa)/(xb-xa) in every spline so that x is in
		// the interval [a,b]
		for (int i = 0; i < data.getPointsCount() - 1; i++) {
			Polynomial temp = new Polynomial(1);
			temp.coefficients[0] = -data.getPoint(i).getX() / (data.getPoint(i + 1).getX() - data.getPoint(i).getX());
			temp.coefficients[1] = 1 / (data.getPoint(i + 1).getX() - data.getPoint(i).getX());
			splines[i] = Polynomial.PofP(splines[i], temp);
		}
	}

	void calculateMax() {
		boolean first = true;
		for (Iterator<DataPoint> iterator = data.iterator(); iterator.hasNext(); ) { 
			double y = iterator.next().getY();
			if (first) {
				this.max = y;
				first = false;
			} else if (getMax() < y) {
				this.max = y;
			}
		}
	}

	void calculateSaturation() {
		int j = data.getPointsCount() - 1;
		double last = data.getPoint(j).getY();
		while ((j > 0) &&
				(data.getPoint(j - 1).getY() > 0.98 * last) &&
				(data.getPoint(j - 1).getY() < 1.02 * last)) {
			j--;
		}
		this.saturation = data.getPoint(j).getX();
	}
	
}
