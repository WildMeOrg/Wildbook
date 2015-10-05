package fap.similarities;

import fap.core.data.DataPointSerie;
import fap.core.exceptions.IncomparableSeriesException;
import fap.core.math.Polynomial;
import fap.core.series.Serie;
import fap.series.SplineSerieRepresentation;

/**
 * Similarity computor which algorithm is based on SplineSerieRepresentation.
 * @author Aleksa Todorovic, Vladimir Kurbalija, Zoltan Geller
 * @version 15.07.2010.
 */
public class SplineSimilarityComputor extends SerializableSimilarityComputor {

	private static final long serialVersionUID = 1L;
	
	/**
	 * Creates a new SplineSimilarityComputor object.
	 */
	public SplineSimilarityComputor() {}

	/**
	 * @throws IncomparableSeriesException 
	 * @inherit
	 */
	public double similarity(Serie curve1, Serie curve2) throws IncomparableSeriesException {
		int ithis = 0;
		int itest = 0;
		double xmin;
		double tempsim = 0;
		
		DataPointSerie data1 = curve1.getData();
		DataPointSerie data2 = curve2.getData();
		
		SplineSerieRepresentation repr1 = curve1.getRepr(SplineSerieRepresentation.class);
		SplineSerieRepresentation repr2 = curve2.getRepr(SplineSerieRepresentation.class);
		
		if ((repr1 == null) || (repr2 == null))
			throw new IncomparableSeriesException();

		if (data1.getPoint(0).getX() < data2.getPoint(0).getX()) {
			xmin = data2.getPoint(0).getX();
			while (data1.getPoint(ithis).getX() < data2.getPoint(0).getX())
				ithis++;
		} else {
			xmin = data1.getPoint(0).getX();
			while (data2.getPoint(itest).getX() < data1.getPoint(0).getX())
				itest++;
		}
		while ((ithis < data1.getPointsCount()) && (itest < data2.getPointsCount())) {
			if (data2.getPoint(itest).getX() == data1.getPoint(ithis).getX())
				// if points have same x then we are looking for the smaller
				// next point
				if ((itest < data2.getPointsCount() - 1) && (ithis < data1.getPointsCount() - 1)) { 
					if (data2.getPoint(itest + 1).getX() == data1.getPoint(ithis + 1).getX()) {
						tempsim = tempsim
								+ Polynomial
										.square(
												Polynomial
														.sub(
																repr2.getSpline(itest),
																repr1.getSpline(ithis)))
										.integral(data1.getPoint(ithis).getX(),
												data1.getPoint(ithis + 1).getX());
						itest++;
						ithis++;
					} else if (data2.getPoint(itest + 1).getX() < data1.getPoint(ithis + 1).getX()) {
						itest++;
					} else {
						ithis++;
					}
				} else {
					// to break while
					itest++;
					ithis++;
				}
			else if (data2.getPoint(itest).getX() < data1.getPoint(ithis).getX()) {
				if ((itest < data2.getPointsCount() - 1)
						&& (data2.getPoint(itest + 1).getX() < data1.getPoint(ithis).getX())) {
					tempsim = tempsim
							+ Polynomial
									.square(
											Polynomial
													.sub(
															repr2.getSpline(itest),
															repr1.getSpline(ithis - 1)))
									.integral(data2.getPoint(itest).getX(),
											data2.getPoint(itest + 1).getX());
					itest++;
				} else if (itest >= data2.getPointsCount() - 1) {
					itest++; // to break while
				} else {
					tempsim = tempsim
							+ Polynomial
									.square(
											Polynomial
													.sub(
															repr2.getSpline(itest),
															repr1.getSpline(ithis - 1)))
									.integral(data2.getPoint(itest).getX(),
											data1.getPoint(ithis).getX());
					itest++;
				}
			} else {
				if ((ithis < data1.getPointsCount() - 1)
						&& (data1.getPoint(ithis + 1).getX() < data2.getPoint(itest).getX())) {
					tempsim = tempsim
							+ Polynomial.square(
									Polynomial.sub(repr2.getSpline(itest - 1), repr1.getSpline(ithis)))
									.integral(data1.getPoint(ithis).getX(),
											data1.getPoint(ithis + 1).getX());
					ithis++;
				} else if (ithis >= data1.getPointsCount() - 1) {
					ithis++; // to break while
				} else {
					tempsim = tempsim
							+ Polynomial.square(
									Polynomial.sub(repr2.getSpline(itest - 1), repr1.getSpline(ithis)))
									.integral(data1.getPoint(ithis).getX(),
											data2.getPoint(itest).getX());
					ithis++;
				}
			}
		}
		
		double result;
		if (itest >= data2.getPointsCount()) {
			result = tempsim / (data2.getPoint(data2.getPointsCount() - 1).getX() - xmin);
		} else {
			result = tempsim / (data1.getPoint(data1.getPointsCount() - 1).getX() - xmin);
		}
		return result;
	}

}
