/**
 * 
 */
package org.ecocean.timeseries.core.distance;

import java.util.Collection;
import java.util.Iterator;
import java.util.Vector;
import java.util.logging.Logger;

import org.ecocean.timeseries.core.Point;
import org.ecocean.timeseries.core.Trajectory;
import org.ecocean.timeseries.core.TrajectoryException;

import org.ecocean.timeseries.classifier.Classifier;
import org.ecocean.timeseries.classifier.ClassifierManager;

/**
 * @author Hui
 *
 */
public class TQuESTOperator extends DistanceOperator {
	public static double m_threshold = 0.0;
	
	public static double m_maxthreshold = 0.0;
	
	public static double m_minthreshold = 0.0;
	
	public static double m_step = 0.0;

	/**
	 * @param t
	 */
	public TQuESTOperator() {
		// TODO Auto-generated constructor stub
	}
	
	 public TQuESTOperator( double m_threshold,double m_maxthreshold,double m_minthreshold, double m_step) {
	    this.m_step=m_step;
	    this.m_threshold=m_threshold;
	    this.m_maxthreshold=m_maxthreshold;
	    this.m_minthreshold=m_minthreshold;
	  }

	/* (non-Javadoc)
	 * @see core.DistanceOperator#computeDistance(core.Trajectory),
	 */
	@Override
	public double computeDistance(Trajectory tr1, Trajectory tr2) {
		/*
		 * first compute the threshold-crossing time interval sequence
		 * then using these sequence to compute the threshold-distance.
		 */
		Vector<TimeInterval> tcseq1 = computeTCSequence(tr1);
		Vector<TimeInterval> tcseq2 = computeTCSequence(tr2);
		
		double distsum1 = 0.0, distsum2 = 0.0;
		TimeInterval itv1 = null, itv2 = null;
		for (int i = 0; i < tcseq1.size(); i++) {
			double dist = 0.0, mindist = Double.MAX_VALUE;
			itv1 = tcseq1.get(i);
			for (int j = 0; j < tcseq2.size(); j++) {
				itv2 = tcseq2.get(j);
				dist = itv1.distance(itv2); 
				if (dist < mindist) {
					 mindist = dist;
				}					
			}
			distsum1 += mindist;
		}
		
		for (int j = 0; j < tcseq2.size(); j++) {
			double dist = 0.0, mindist = Double.MAX_VALUE;
			itv2 = tcseq2.get(j);
			for (int i = 0; i < tcseq1.size(); i++) {
				itv1 = tcseq1.get(i);
				dist = itv2.distance(itv1);
				if (dist < mindist) {
					mindist = dist;
				}
			}
			distsum2 += mindist;
		}
		
		return distsum1 / tcseq1.size() + distsum2 / tcseq2.size();
	}
	
	private Vector<TimeInterval> computeTCSequence(Trajectory tr) {
		Vector<TimeInterval> sequence = new Vector<TimeInterval>();
		
		double tstart = 0.0, tend = 0.0;
		double[] coords = {0.0,0.0,0.0};
		Point last = null, pt = null, origin = new Point(coords);
		int n = tr.getNumOfPoints();
		for (int i = 0; i < n; i++) {
			last = pt;
			pt = tr.getPoint(i);
			if (last == null) {
				if (pt.distance(origin) >= m_threshold) {
					tstart = pt.getTime();
				}
			}
			else if (last.distance(origin) < m_threshold &&  
					pt.distance(origin) >= m_threshold) {
				tstart = pt.getTime();
			}
			else if (last.distance(origin) >= m_threshold && 
					pt.distance(origin) < m_threshold) {
				tend = last.getTime();
				sequence.add(new TimeInterval(tstart, tend));
			}
		}
		// handle the boundary case
		if (pt.distance(origin) >= m_threshold) {
			// one more crossing
			sequence.add(new TimeInterval(tstart, pt.getTime()));
		}
		
		return sequence;
	}

	/* (non-Javadoc)
	 * @see core.DistanceOperator#toString()
	 */
	@Override
	public String toString() {
		String output = "TQuEST operator:\n";
		output += "m_threshold: " + m_threshold;
		return output;
	}

	@Override
	public double computeLowerBound(Trajectory tr1, Trajectory tr2)
			throws TrajectoryException {
		return Double.MIN_VALUE;
	}

	@Override
	public boolean hasLowerBound() {
		// TODO Auto-generated method stub
		return false;
	}

	@Override
	public boolean needTuning() {
		// TODO Auto-generated method stub
		return true;
	}

	@Override
	public void tuneOperator(Collection<Trajectory> trainset, Collection<Integer> labelset, Classifier classifier) {
		//Logger lg = ClassifierManager.getLogger();
	  
	  System.out.println(".....Starting TQUEST tuning....");
		
		double bestw = 0;
		double besterror = Double.MAX_VALUE;
		
		// transfer data into a vector for easy leave-one-out manipulation
		Vector<Trajectory> vdata = new Vector<Trajectory>(); 
		Vector<Integer> vlabels = new Vector<Integer>();
		
		Iterator<Trajectory> it1 = trainset.iterator();
		Iterator<Integer> it2 = labelset.iterator();
		while (it1.hasNext() && it2.hasNext()) {
			Trajectory tr = it1.next();
			int label = it2.next();
			vdata.add(tr);
			vlabels.add(label);
		}
		
		/* collect statistics about the trainset: avg, min, max, std */
		double setavg = 0.0, setstd = 0.0;
		Iterator<Trajectory> it3 = trainset.iterator();
		while(it3.hasNext() ) {
			Trajectory temp = it3.next();
			setavg += temp.getXAverage();
			setstd += temp.getStdDeviation();
			/*
			if (setmin > temp.getMBB().getLow(Point.X_DIM)) {
				setmin = temp.getMBB().getLow(Point.X_DIM);
			}
			if (setmax < temp.getMBB().getHigh(Point.X_DIM)) {
				setmax = temp.getMBB().getHigh(Point.X_DIM);
			}
			//*/
		}
		setavg /= trainset.size();
		setstd /= trainset.size();
		System.out.println("...setavg="+setavg);
		System.out.println("...setstd="+setstd);
		
		/* determine the min, max and step */
		m_minthreshold = setavg - setstd;
		m_maxthreshold = setavg + setstd;
		m_step = 0.02 * setstd;
		 
		for (double w = m_minthreshold; w <= m_maxthreshold; w+=m_step) {
			System.out.println("...tuning with threshold:" + w+" (setavg:"+setavg+";setstd:"+setstd+")");
			m_threshold = w;
			double error = tuneByLeaveOneOut(vdata, vlabels, classifier);
			if (error < besterror) {
				bestw = w;
				besterror = error;
			}
		}
		
		System.out.println("min:" + m_minthreshold + " max:" + m_maxthreshold);
		System.out.println("best w:" + bestw);
		// set the parameter
		m_threshold = bestw;
		
	}

}

class TimeInterval {
	double[] bounds = new double[2];
	
	TimeInterval(double lbound, double ubound) {
		bounds[0] = lbound;
		bounds[1] = ubound;
	}
	
	double distance(TimeInterval ti) {
		double sum = (bounds[0] - ti.bounds[0]) * (bounds[0] - ti.bounds[0]);
		sum += (bounds[1] - ti.bounds[1]) * (bounds[1] - ti.bounds[1]);
		return Math.sqrt(sum);
	}
}