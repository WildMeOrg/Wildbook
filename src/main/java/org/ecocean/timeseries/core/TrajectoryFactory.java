/**
 * 
 */
package org.ecocean.timeseries.core;

import java.io.File;
import java.io.FileFilter;
import java.io.FileReader;
import java.io.LineNumberReader;
import java.util.Collection;
import java.util.Iterator;
import java.util.Random;
import java.util.StringTokenizer;
import java.util.Vector;

import org.ecocean.timeseries.core.distance.DistanceOperator;

import org.ecocean.timeseries.classifier.Classifier;
import org.ecocean.timeseries.classifier.NNClassifier;

/**
 * @author Hui
 * Trajectory factory is in charge of loading raw data from file, and generating
 * trajectory given a distance measure
 */
public class TrajectoryFactory {
	private static int nextid = 0;
	
	/* based directory for all data sets, each data set is placed in a subdir */
	String m_basedir = "data//";

	/* filefilter for loading data */
	FileFilter m_filefilter = new UCRRawDataFilter();
	
	/* raw time series data set, each is a collection of points */
	Collection<Collection<Point>> m_rawdata = null;
	
	/* class labels for all the data */
	Collection<Integer> m_labels = null;
	
	/**
	 * default constructor
	 */
	public TrajectoryFactory() {}
	
	/**
	 * 
	 * @param datadir
	 * @param filter
	 */
	protected void loadData(String datadir, FileFilter filter) {
		String realdir = m_basedir + datadir;
		File dir = new File(realdir);
		File[] list = null;
		if (dir != null) {
			list = dir.listFiles(filter);
		} 
		else {
			System.out.println("Cannot open data directory!");
			System.exit(-1);
		}
		
		if (filter == null) {
			System.out.println("Filefilter cannot be null!");
			System.exit(-1);
		}
		
		m_rawdata = new Vector<Collection<Point>>();
		m_labels = new Vector<Integer>();
		
		for (int i = 0; i < list.length; i++) {
			//System.out.println("Reading file: " + list[i].getName());
			readRawData(list[i], m_rawdata, m_labels);
		}
	}
	
	/**
	 * read raw trajectories into collections of data and labels
	 * @param rawdata raw time series data file name
	 * @param dataset a collection of point collections
	 * @param labels the label collection
	 */
	protected void readRawData(File rawdata, 
								Collection<Collection<Point>> dataset, 
								Collection<Integer> labels) {
		try {
			LineNumberReader lr = new LineNumberReader(new FileReader(rawdata));
			String line = null;
			while ((line = lr.readLine()) != null) {
				// first get the label
				StringTokenizer st = new StringTokenizer(line);
				double label = new Double(st.nextToken()).doubleValue();
				labels.add(new Integer((int)label));
				
				// next get the point set
				Collection<Point> pts = new Vector<Point>();
				double xpos;
				int time = 0;
				while (st.hasMoreTokens()) {
					xpos = new Double(st.nextToken()).doubleValue();
					double[] coords = {xpos, 0.0, time};
					pts.add(new Point(coords));
					time++;
				}
				dataset.add(pts);
			}
		} catch (Exception e) {
			e.printStackTrace();
			System.exit(-1);
		}
	}	
	
	public Collection<Trajectory> getTrajectories(String datasetname,
													DistanceOperator op) 
												throws Exception {
		// first load the data
		loadData(datasetname, m_filefilter);
		
		Vector<Trajectory> dataset = new Vector<Trajectory>(m_rawdata.size());
		
		for (Iterator<Collection<Point>> it = m_rawdata.iterator(); 
			it.hasNext(); 
			) {
			dataset.add(new Trajectory(nextid++, it.next(), op));
		}
		
		m_rawdata = null;
		
		return dataset;
	}
	
	public Collection<Integer> getLabels() {
		Vector<Integer> labelset = new Vector<Integer>(m_labels);
		m_labels = null;
		return labelset;
	}
	
	/**
	 * 
	 * @param basedir
	 */
	public void setBaseDir(String basedir) {
		m_basedir = new String(basedir);
	}
	
	/**
	 * 
	 * @return
	 */
	public String getBaseDir() {
		return new String(m_basedir);
	}
	
	/**
	 * 
	 * @param f
	 */
	public void setFileFilter(FileFilter f) {
		m_filefilter = f;
	}

	/**
	 * @param args
	 */
	public static void main(String[] args) {
		TrajectoryFactory factory = new TrajectoryFactory();
		
		Vector<Trajectory> dataset = null;
		try {
			DistanceOperator op 
			= (DistanceOperator)Class.forName("core.distance.ERPOperator").newInstance();
			
			dataset = 
				new Vector<Trajectory>(factory.getTrajectories("ECG200", op));
		} catch (Exception e) {
			e.printStackTrace();
		}
		Vector<Integer> labelset = new Vector<Integer>(factory.getLabels());
		
		System.out.println("dataset size:" + dataset.size());
		
		// now normalize all the trajectories in this data set, so that the 
		// maximum scale is 1.0
		double maxscale = 0.0;
		for (int i = 0; i < dataset.size(); i++) {
			Trajectory tr = dataset.get(i);
			double scale = 
			tr.getMBB().getHigh(Point.X_DIM) - tr.getMBB().getLow(Point.X_DIM);
			if (Math.abs(scale) > maxscale) {
				maxscale = Math.abs(scale);
			}
		}
		
		for (int i = 0; i < dataset.size(); i++) {
			Trajectory tr = dataset.get(i);
			tr.scaleX((1.0/maxscale));
		}
		
		
		int id = new Random().nextInt(dataset.size());
		
		Trajectory query = dataset.remove(id);
		
		for (int i = 0; i < dataset.size(); i++) {
			double dist = query.getDistance(dataset.get(i));
			double bound = query.getLowerBound(dataset.get(i));
			if (dist < bound) {
				System.err.println("bound:" + bound + " dist:" + dist);
				//System.exit(0);
			}
			else {
				//System.out.print("Good!");
				//System.out.println("bound:" + bound + " dist:" + dist);
			}
		}
		/*
		int id = new Random().nextInt(dataset.size());
		
		Trajectory query = dataset.remove(id);
		//int label = labelset.remove(id);
		
		Classifier classifier = new NNClassifier();
		classifier.trainClassifier(dataset, labelset);
		
		long time1 = System.currentTimeMillis();
		for (int i = 0; i < 1000; i++)
			query.getDistance(dataset.get(new Random().nextInt(dataset.size())));
		//classifier.classifyTrajectory(query);
		long time2 = System.currentTimeMillis();
		System.err.println("Time:" + (time2-time1));
		//*/	
	}

}

class UCRRawDataFilter implements FileFilter {
	public boolean accept(File pathname) {
		if (pathname.getName().contains("train") || 
			pathname.getName().contains("test") ||
			pathname.getName().contains("TRAIN") ||
			pathname.getName().contains("TEST")) {
			//System.out.println("Reading file: " + list[i].getName());
			return true;
		}
		else {
			return false;
		}
	}
}

class UCRRawTrainingDataFilter implements FileFilter {
	public boolean accept(File pathname) {
		if (pathname.getName().contains("train") || 
			pathname.getName().contains("TRAIN")) {
			//System.out.println("Reading file: " + list[i].getName());
			return true;
		}
		else {
			return false;
		}
	}		
}
