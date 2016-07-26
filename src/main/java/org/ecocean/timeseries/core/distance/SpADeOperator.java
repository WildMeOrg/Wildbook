/**
 * 
 */
package org.ecocean.timeseries.core.distance;

import java.util.Collection;
import java.util.Hashtable;
import java.util.Iterator;
import java.util.Vector;
import java.util.logging.Logger;

import org.ecocean.timeseries.core.Trajectory;
import org.ecocean.timeseries.core.TrajectoryException;

import org.ecocean.timeseries.classifier.Classifier;
import org.ecocean.timeseries.classifier.ClassifierManager;

/**
 * @author Hui
 *
 */
public class SpADeOperator extends DistanceOperator {
	/* These parameters should not be changed for different data sets */
	private static final int MAXAMPSCALE = 4;
	private static final int MAXTIMESCALE = 4;
	private static final double TIMEINTERVAL = 0.1;
	private static final double AMPINTERVAL = 0.1;
	private static final int INDEXSIZE = 18;
	private static final int DIVIDTH = 4;

	/* These are the parameters that need to be tuned */
	private int m_optscale = 0;
	private int m_opascale = 0;
	private int m_oppatternlength = 0;
	private int m_opslidestep = 0;
	private int m_opshiftgap = Integer.MAX_VALUE;
	private double m_opashiftpenalty = 0.0;
	private double m_optshiftpenalty = 0.0;
	
	private TrainManager m_trainmanager = null;
	private Vector<Trajectory> m_trainset = null;
	private Vector<Integer> m_trainlabels = null;
	
	private Hashtable<Integer, Integer> m_dict = null;
	
	/* may need another data structure to store the VAfile associated with
	 * a given training set (not parameter tuning set)
	 **/
	
	/**
	 * @param t
	 */
	public SpADeOperator() {
		super();
	}
	
	/* (non-Javadoc)
	 * @see core.DistanceOperator#computeDistance(core.Trajectory)
	 */
	@Override
	public double computeDistance(Trajectory tr1, Trajectory tr2) 
									throws TrajectoryException {
		//*
		if (m_dict == null || !m_dict.containsKey(tr1.getID())) {
			throw new TrajectoryException("Training set not ready for SpADe");
		}
		else {
			throw new TrajectoryException(
									"Use classifyTrajectory method in SpADe");
		}
		//*/
		
		/*
		int m = tr1.getNumOfPoints();
		int n = tr2.getNumOfPoints();
		
		Vector<Trajectory> trainset = new Vector<Trajectory>();
		Vector<Integer> labelset = new Vector<Integer>();
		trainset.add(tr1);
		labelset.add(-1);
		
		TrainManager tm = trainSpadeOperatorImpl(trainset, 
												labelset, 
												m_oppatternlength, 
												m_optscale, 
												m_opascale);  
		tm.slidingfactor = m_opslidestep;
		double[] dist = computeSpADe(tm, tr2);
		return dist[0];
		//*/
	}
	

	public int classifyTrajectory(Trajectory tr) throws TrajectoryException {
		if (m_trainmanager == null) {
			throw new TrajectoryException("SpADe training manager not ready!");
		}
		double[] dists = computeSpADe(m_trainmanager, tr);
		int neighbor = -1; 
		double mindist = Double.MAX_VALUE;
		for (int i = 0; i < dists.length; i++) {
			if (dists[i] < mindist) {
				mindist = dists[i];
				neighbor = i;
			}
		}
		return m_trainlabels.get(neighbor);
	}

	/**
	 * 
	 * @param trainset
	 * @param plstart pattern length lower bound
	 * @param plend pattern length upper bound
	 * @param plstep pattern length step
	 */
	public void trainSpadeOperator(Collection<Trajectory> trainset, 
									Collection<Integer> trainlabels,
									int plstart, int plend, int plstep) {
		Logger lg = ClassifierManager.getLogger();
		int bestlength = 0, bestampscale = 0, besttimescale = 0, bestslide = 0;
		double bestaccuracy = 0.0;
		for (int pl = plstart; pl <= plend; pl += plstep) {
			for (int as = 0; as < SpADeOperator.MAXAMPSCALE; as++) {
				for (int ts = 0; ts < SpADeOperator.MAXTIMESCALE; ts++) {
					lg.fine("tuning with plength:" + pl +
							" ampscale:" + as + " tscale:" + ts);
					TrainManager tm = trainSpadeOperatorImpl(
											trainset, trainlabels, pl, as, ts);
					double accuracy = learnSliding(trainset, trainlabels, tm);
					if (accuracy > bestaccuracy) {
						bestaccuracy = accuracy;
						bestlength = pl;
						bestampscale = as;
						besttimescale = ts;
						bestslide = tm.slidingfactor;
					}
				}
			}
		}
		
		//*
		TrainManager tm = trainSpadeOperatorImpl(
				trainset, trainlabels, bestlength, bestampscale, besttimescale);
		if (plend > 8) {
			learnSliding(trainset, trainlabels, tm);
		}
		//*/
		lg.fine("best plength:" + bestlength + " bestascale:" + bestampscale + 
				" best tscale:" + besttimescale + " best slide:" + bestslide);
		this.m_oppatternlength = bestlength;
		this.m_opascale = bestampscale;
		this.m_optscale = besttimescale;
		this.m_opslidestep = bestslide;
		this.m_trainmanager = tm;
		this.m_trainset = new Vector<Trajectory>(trainset);
		this.m_trainlabels = new Vector<Integer>(trainlabels);
		this.m_dict = new Hashtable<Integer, Integer>();
		for (int i = 0; i < m_trainset.size(); i++) {
			m_dict.put(m_trainset.get(i).getID(), i);
		}
	}
	
	private TrainManager trainSpadeOperatorImpl(Collection<Trajectory> trainset,
										Collection<Integer> trainlabels,
										int plength, 
										int timescale, 
										int ampscale) {
		// construct a new train manager
		TrainManager tm = new TrainManager();
		tm.trainset = trainset;
		tm.trainlabels = trainlabels;
		tm.numofsamples = trainset.size();
		tm.maxlength = trainset.iterator().next().getNumOfPoints();
		tm.patternlength = plength;
		tm.maxnumofpatterns = tm.maxlength / plength;
		tm.shiftgap = (int)Math.ceil(tm.maxlength * 0.15);
		tm.largestgap = 5 * plength;
		tm.slidingfactor = (plength/8) < 1 ? 1: (plength/8);
		tm.tscale = timescale;
		tm.ascale = ampscale;
		
		double[] total = new double[4], 
					coeffecients = new double[4], 
					segment = new double[plength];
		double totalamplitude = 0.0;
		
		/* compute all local patterns */
		int index = 0; // id is just the index
		for (Iterator<Trajectory> it = trainset.iterator(); 
				it.hasNext(); 
				index++) {
			Trajectory traj = it.next();
			for (int ts = -tm.tscale; ts <= tm.tscale; ts++) {
				double tscale = 1 + SpADeOperator.TIMEINTERVAL * ts;
				for (int as = -tm.ascale; as <= tm.ascale; as++) {
					double ascale = 1 + SpADeOperator.AMPINTERVAL * as;
					for (int j = plength/2; 
							j < tm.maxlength-plength/2; 
							j += plength) {
						boolean outregion = false;
						for (int t = 0; t < plength; t++) {
							double tf = j + tscale * (t - plength / 2);
							if ( tf < 0) {
								outregion = true;
								break;
							}
							else if (tf >= tm.maxlength - 1) {
								outregion = true;
								break;
							}
							else {
								int ti = (int)tf; 
								segment[t] = 
									(tf - ti) * traj.getPoint(ti+1).getXPos() + 
									(ti + 1 - tf) * traj.getPoint(ti).getXPos();
							}
							segment[t] = segment[t]*ascale;
						}
						
						if ( outregion ) {
							continue;
						}
						LocalPattern lp = new LocalPattern();
						lp.amplitude = 
							waveTransform(segment, coeffecients, plength/4);
						lp.trajindex = index;
						lp.pos = j/plength;
						for (int k = 0; k < 4; k++) {
							lp.coefficients[k] = coeffecients[k];
							total[k] += coeffecients[k];
						}
						totalamplitude += lp.amplitude;
						tm.lparray.add(lp);
					} // end j loop
				} // end as for loop
			} // end ts for loop
		} // end iterator for loop
		
		/* compute some statistics */
		double[] totaldiff = new double[4];
		for (int k = 0; k < 4; k++) {
			tm.averages[k] = total[k] / tm.lparray.size();
		}
		
		double avgamplitude = totalamplitude / tm.lparray.size(), 
				amplitudediff = 0.0, amplitudevar = 0.0;
		for (Iterator<LocalPattern> it = tm.lparray.iterator(); it.hasNext();) {
			LocalPattern lp = it.next();
			for (int k = 0; k < 4; k++) {
				totaldiff[k] += Math.pow(lp.coefficients[k]-tm.averages[k], 2);
			}
			amplitudediff += Math.pow(lp.amplitude-avgamplitude ,2);
		}
		
		for (int k = 0; k < 4; k++) {
			tm.vars[k] = Math.sqrt(totaldiff[k] / tm.lparray.size());
		}
		amplitudevar = amplitudediff / tm.lparray.size();
		
		/* insert all local patterns into a va-file like data structure */
		int[] cellid = new int[4];
		for (Iterator<LocalPattern> it = tm.lparray.iterator(); it.hasNext();) {
			LocalPattern lp = it.next();
			for (int k = 0; k < 4; k++) {
				cellid[k] = 
						(int)((SpADeOperator.DIVIDTH * 
						(lp.coefficients[k] - tm.averages[k])) 
						/ tm.vars[k] + SpADeOperator.INDEXSIZE / 2);
				if (cellid[k] < 0) {
					cellid[k] = 0;
				}
				if (cellid[k] >= SpADeOperator.INDEXSIZE) {
					cellid[k] = SpADeOperator.INDEXSIZE - 1;
				}
			}
			
			// now insert the pattern
			if (tm.vafile[cellid[0]][cellid[1]][cellid[2]][cellid[3]].size() 
					== 0) {
				tm.vafile[cellid[0]][cellid[1]][cellid[2]][cellid[3]].add(lp);
			}
			else {
				LocalPattern last = 
					tm.vafile[cellid[0]][cellid[1]][cellid[2]][cellid[3]].
					lastElement();
				if (last.trajindex == lp.trajindex && last.pos == lp.pos) {
					// do not insert this lp
					continue;
				}
				tm.vafile[cellid[0]][cellid[1]][cellid[2]][cellid[3]].add(lp);
			}
		}
		
		tm.ashiftpenalty = 1.0 / amplitudevar;
		tm.tshiftpenalty = 1.0 / plength;
		
		tm.lpmfile = createLPMFile(trainset.size(), tm.maxnumofpatterns); 
		
		return tm;
	}
	
	/**
	 * 
	 * @param trainset
	 * @param trainlabels
	 * @param tm
	 * @return accuracy with the best sliding factor
	 */
	private double learnSliding(Collection<Trajectory> trainset, 
								Collection<Integer> trainlabels, 
								TrainManager tm) {
		Vector<Trajectory> dataset = new Vector<Trajectory>(trainset);
		Vector<Integer> labelset = new Vector<Integer>(trainlabels);
		
		int ls = tm.patternlength / 8;
		if (ls < 1) {
			ls = 1;
		}
		int plstep = ls/4;
		if (plstep < 1) {
			plstep = 1;
		}
		
		int bestaccuracy = 0;
		int bestslide = 0;
		for (int slide = plstep; slide <= ls; slide += plstep) {
			// accuracy test, leave-one-out
			tm.slidingfactor = slide;
			int matches = 0;
			for (int i = 0; i < dataset.size(); i++) {
				Trajectory tr = dataset.get(i);
				int label = labelset.get(i);
				int predictlabel = 0;
				double[] distances = computeSpADe(tm, tr);
				double mindist = Double.MAX_VALUE;
				for (int j = 0; j < distances.length; j++) {
					if (distances[j] < mindist && j != i) {
						mindist = distances[j];
						predictlabel = labelset.get(j);
					}
				}
				if (predictlabel == label) {
					matches++;
				}
			}
			if (matches > bestaccuracy) {
				bestaccuracy = matches;
				bestslide = slide;
			}	
		}
		
		tm.slidingfactor = bestslide;
		
		return bestaccuracy * 1.0 / trainset.size();
	}
	
	private void onDetectLocalPatternMatch(TrainManager tm, LocalPattern lp,
			int pos, double lpamplitude, double error) {
		int plen = tm.patternlength;
		int id = lp.trajindex;
		int columnpos = lp.pos;
		int normalpos = columnpos * plen + plen / 2;
		if (Math.abs(pos - normalpos) > tm.shiftgap) {
			return;
		}

		/* create a local pattern match */
		LocalPatternMatch lpm = new LocalPatternMatch();
		lpm.tshift = pos - normalpos;
		lpm.ashift = lpamplitude - lp.amplitude;
		lpm.pos = pos;
		lpm.error = error;

		if (tm.lpmfile[id][columnpos].size() == 0) {
			tm.lpmfile[id][columnpos].add(lpm);
		} 
		else {
			LocalPatternMatch last = tm.lpmfile[id][columnpos].lastElement();
			if (last.pos == pos) {
				if (error < last.error) {
					tm.lpmfile[id][columnpos].
								remove(tm.lpmfile[id][columnpos].size() - 1);
					tm.lpmfile[id][columnpos].add(lpm);
				}
			} 
			else {
				tm.lpmfile[id][columnpos].add(lpm);
			}
		}
	}
	
	/**
	 * compute all the distances between the training set and this trajectory
	 * @param tm
	 * @param tr
	 * @return
	 */
	private double[] computeSpADe(TrainManager tm, Trajectory tr) {
		double[] segment = new double[tm.patternlength], 
		coefficients = new double[4];

		/* check the distance of given sample to all the other samples */
		for (int i = 0; 
				i < tr.getNumOfPoints() - tm.patternlength; 
				i+= tm.slidingfactor) {
			for (int j = 0; j < tm.patternlength; j++) {
				segment[j] = tr.getPoint(i + j).getXPos(); 
			}
		
			double amplitude = 
					waveTransform(segment, coefficients, tm.patternlength/4);
			int[] cellid = new int[4];
			for (int k = 0; k < 4; k++) {
				cellid[k] = (int)((SpADeOperator.DIVIDTH * (coefficients[k] - 
					tm.averages[k]))/tm.vars[k] + SpADeOperator.INDEXSIZE / 2);
				if (cellid[k]<1)
					cellid[k] = 1;
				if (cellid[k]>= SpADeOperator.INDEXSIZE - 1)
					cellid[k] = SpADeOperator.INDEXSIZE - 2;
			}
			
			for (int r = cellid[0] - 1; r <= cellid[0] + 1; r++) {
				for (int s = cellid[1] - 1; s <= cellid[1] + 1; s++) {
					for (int t = cellid[2] - 1; t <= cellid[2] + 1; t++) {
						for (int u = cellid[3] - 1; u <= cellid[3] + 1; u++) {
							Vector<LocalPattern> lparray 
														= tm.vafile[r][s][t][u];
							for (Iterator<LocalPattern> it = lparray.iterator();
								it.hasNext(); ) {
								LocalPattern lp = it.next();
								double error 
									= Math.abs(coefficients[0] - lp.amplitude);
								onDetectLocalPatternMatch(tm, 
														lp, 
														i+tm.patternlength/2, 
														amplitude, 
														error);
							}
						}
					}
				}
			}
		} // end of i loop
		
		return computeSpADeImpl(tm);
	}
	
	private double[] computeSpADeImpl(TrainManager tm) {
		double[] distances = new double[tm.trainset.size()];
		int plen = tm.patternlength;
		
		for (int i = 0; i < distances.length; i++) {
			distances[i] = Double.MAX_VALUE;
			
			int columnpos = 0;
			for ( ; columnpos < tm.maxnumofpatterns; columnpos++) {
				for (Iterator<LocalPatternMatch> it 
						= tm.lpmfile[i][columnpos].iterator(); it.hasNext(); ) {
					LocalPatternMatch lpm = it.next();
					int tpos = lpm.pos;
					double bestsadc = tm.tshiftpenalty * 
					(columnpos * plen + tpos - plen/2);
					lpm.sadc = bestsadc;
					
					// to find the best previous lpm
					for (int j = 1; j <= 5; j++) {
						int column = columnpos - j;
						if (column < 0) {
							break;
						}
						double ed1 =(i-1) * plen * tm.tshiftpenalty;
						Iterator<LocalPatternMatch> it2 = 
											tm.lpmfile[i][column].iterator();
						for ( ; it2.hasNext() ; ) {
							LocalPatternMatch plpm = it2.next();
							if (plpm.pos < tpos) {
								if (ed1 + plpm.sadc < bestsadc) {
									int posdiff = tpos - plpm.pos - plen;
									if (posdiff < 0)
										posdiff = 0;
									// largest gap???
									if (posdiff < tm.largestgap) {
										double dist = 
												ed1 + tm.tshiftpenalty * posdiff 
												+ lpm.distance(plpm, 
															tm.tshiftpenalty, 
															tm.ashiftpenalty);
										if ((dist + plpm.sadc) < bestsadc) {
											bestsadc = dist + plpm.sadc;
										}
									}
								}
							}
						}
					} // end of j loop for searching best previous LPM
					
					lpm.sadc = bestsadc;
					// detect new matching subsequence and update bound
					if (bestsadc < distances[i]) {
						int tobottom = tm.maxlength - tpos - plen/2;
						int toright = (tm.maxnumofpatterns - columnpos - 1) * 
										plen;
						if (toright < 0) {
							toright = 0;
						}
						double ptdist = 
							bestsadc + tm.tshiftpenalty * (tobottom + toright);
						if (ptdist < distances[i]) {
							// found a matching subsequence
							distances[i] = ptdist;
						}
					}
				} // end of lpm loop
			}
		}
		
		return distances;
	}
	
	/**
	 * compute the lowest four wavelet transformation coefficients
	 * @param data the data to apply wavelet transformation
	 * @param coefficients coefficients array
	 * @param scale of transformation
	 * @return 
	 */
	private double waveTransform(double[] data, 
								double[] coefficients, 
								int scale) {
		double[] avg = new double[4], total = new double[4];
		double total1 = 0.0, avg1 = 0.0;
		for (int k = 0; k < 4; k++) {
			total[k] = 0;
			for (int i = 0; i < scale; i++) {
				total[k] += data[i + k * scale];
			}
			avg[k] = total[k] / scale;
			total1 += avg[k];
		}
		for (int k = 0; k < 4; k++) {
			coefficients[k] = avg[k] - avg1;
		}
		return avg1;
	}
	
	/**
	 * create a vafile structure with the given dimensionality
	 * @return
	 */
	private Vector<LocalPattern>[][][][] createVAFile() {
		int size = SpADeOperator.INDEXSIZE;
		Vector<LocalPattern>[][][][] vafile = (Vector<LocalPattern>[][][][])
											new Vector[size][size][size][size];
		for(int i = 0; i < size; i++) {
			for(int j = 0; j < size; j++) {
				for(int k = 0; k < size; k++) {
					for(int t = 0; t < size; t++) {
						vafile[i][j][k][t] = new Vector<LocalPattern>();
					}
				}
			}
		}
		return vafile;
	}
	
	private Vector<LocalPatternMatch>[][] createLPMFile(int row, int column) {
		Vector<LocalPatternMatch>[][] lpmfile = (Vector<LocalPatternMatch>[][])
													new Vector[row][column];
		for (int i = 0; i < row; i++) {
			for (int j = 0; j < column; j++) {
				lpmfile[i][j] = new Vector<LocalPatternMatch>();
			}
		}
		return lpmfile;
	}
	
	/* (non-Javadoc)
	 * @see core.DistanceOperator#toString()
	 */
	@Override
	public String toString() {
		String output = "SpADe operator:\n";
		return output;
	}

	@Override
	public double computeLowerBound(Trajectory tr1, Trajectory tr2)
			throws TrajectoryException {
		return Double.MIN_VALUE;
	}

	@Override
	public boolean hasLowerBound() {
		return false;
	}

	@Override
	public boolean needTuning() {
		return true;
	}

	@Override
	public void tuneOperator(Collection<Trajectory> trainset,
			Collection<Integer> labelset, Classifier classifier) {
		/* first train the parameters using the trainset */
		int plstart = 8;
		int plend = 64;
		int plstep = 8;
		
		trainSpadeOperator(trainset, labelset, plstart, plend, plstep);
	}
	
	class TrainManager {
		int numofsamples;
		int maxlength;
		int patternlength;
		int maxnumofpatterns; // rsize from original implementation
		int shiftgap;
		int largestgap;
		int slidingfactor;
		int tscale;
		int ascale;
		double ashiftpenalty;
		double tshiftpenalty;
		
		Collection<Trajectory> trainset = null;
		Collection<Integer> trainlabels = null;
		
		Vector<LocalPattern> lparray = null;
		double[] averages = null;
		double[] vars = null;
		Vector<LocalPattern>[][][][] vafile = null;
		Vector<LocalPatternMatch>[][] lpmfile = null;
		
		// default constructor
		TrainManager() {
			lparray = new Vector<LocalPattern>();
			averages = new double[4];
			vars = new double[4];
			vafile = createVAFile();
		}
	}
	
	class LocalPattern {
		int trajindex = -1;
		int pos = 0;
		double amplitude = 0.0;
		double[] coefficients = new double[4];
	}
	
	class LocalPatternMatch {
		double error = 0.0;
		int pos = 0;
		double sadc = 0.0;
		int tshift = 0;
		double ashift = 0.0;
		
		double distance(LocalPatternMatch lpm, 
						double tshiftpenalty, 
						double ashiftpenalty) {
			int warpdiff = 0;
			double dist = 0.0, ampdiff = 0.0;
			
			warpdiff = Math.abs(this.tshift - lpm.tshift);
			dist += warpdiff * tshiftpenalty;
			
			ampdiff = Math.abs(this.ashift - lpm.ashift);
			dist += ampdiff * ashiftpenalty;
				
			return dist;
		}
	}
}




