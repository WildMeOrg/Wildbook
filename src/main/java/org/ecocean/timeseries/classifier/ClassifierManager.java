/**
 * 
 */
package org.ecocean.timeseries.classifier;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.PrintStream;
import java.net.InetAddress;
import java.util.Calendar;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.Properties;
import java.util.Random;
import java.util.SortedMap;
import java.util.TreeMap;
import java.util.Vector;
import java.util.Map.Entry;
import java.util.logging.FileHandler;
import java.util.logging.Formatter;
import java.util.logging.Handler;
import java.util.logging.Level;
import java.util.logging.LogRecord;
import java.util.logging.Logger;
import java.util.logging.StreamHandler;

import javax.activation.DataHandler;
import javax.activation.DataSource;
import javax.activation.FileDataSource;
import javax.mail.Authenticator;
import javax.mail.BodyPart;
import javax.mail.Message;
import javax.mail.Multipart;
import javax.mail.PasswordAuthentication;
import javax.mail.Session;
import javax.mail.Transport;
import javax.mail.internet.InternetAddress;
import javax.mail.internet.MimeBodyPart;
import javax.mail.internet.MimeMessage;
import javax.mail.internet.MimeMultipart;

import org.ecocean.timeseries.core.Point;
import org.ecocean.timeseries.core.Trajectory;
import org.ecocean.timeseries.core.TrajectoryFactory;
import org.ecocean.timeseries.core.distance.DistanceOperator;

/**
 * @author Hui
 *
 */
public class ClassifierManager {
	/* logger for the experiment */
	private static Logger logger = Logger.getLogger("ClassifierManager logger");
	
	/* distance operator associated with this classifier manager */
	private DistanceOperator m_distanceoperator;
	
	/* trajectory factory */
	private TrajectoryFactory m_factory = null;
	
	/* Trajectory data set */
	private Vector<Trajectory> m_dataset;
	
	/* class labels for all the data */
	private Vector<Integer> m_labelset;
	
	/* the class distribution of the data set */
	private HashMap<Integer, Integer> m_classdistribution; 
	
	/* file handler for detail logging */
	private FileHandler m_filehandler = null;
	
	private String m_logfilename = null;
	
	private String m_datasetname = null;
	
	private boolean m_splitonly = false;
	
	/**
	 * 
	 * @param dataset
	 * @param op
	 */
	public ClassifierManager(String dataset, DistanceOperator op) {
		m_dataset = new Vector<Trajectory>();
		m_labelset = new Vector<Integer>();
		
		m_distanceoperator = op;
		m_factory = new TrajectoryFactory();
		m_datasetname = dataset;
		
		try {
			// first clear all existing handlers
			Handler hl[] = logger.getHandlers();
			for (int i = 0; i < hl.length; i++) {
				logger.removeHandler(hl[i]);
			}
			String opname = op.getClass().getName();
			//opname = opname.substring(opname.lastIndexOf('.') + 1);
			m_logfilename = dataset + "_" + opname + "_history.txt";
			m_filehandler = new FileHandler(m_logfilename);
			m_filehandler.setLevel(Level.FINE);
			m_filehandler.setFormatter(new SimpleOutputFormatter());
			logger.addHandler(m_filehandler);
			
			
			StreamHandler stream = new StreamHandler(System.out, 
										new SimpleOutputFormatter());
			stream.setLevel(Level.WARNING);
			logger.addHandler(stream);
			logger.setLevel(Level.FINE);
			logger.setUseParentHandlers(false);
		} 
		catch (Exception e) {
			e.printStackTrace();
		}
	}
	
	/**
	 * 
	 * @param datasetname
	 */
	protected void loadData(String datasetname) {
		m_dataset.clear();
		m_labelset.clear();
		
		try {
			m_dataset.addAll(
					m_factory.getTrajectories(datasetname, m_distanceoperator));
			m_labelset.addAll(m_factory.getLabels());
		} catch (Exception e) {
			e.printStackTrace();
			System.exit(-1);
		}
		
		normalizeTrajectories();
	}
	
	/**
	 * 
	 * @param datasets
	 */
	protected void loadData(String[] datasets) {
		m_dataset.clear();
		m_labelset.clear();
		
		try {
			for (int i = 0; i < datasets.length; i++) {
				m_dataset.addAll(
					m_factory.getTrajectories(datasets[i], m_distanceoperator));
				m_labelset.addAll(m_factory.getLabels());
			}
		} catch (Exception e) {
			e.printStackTrace();
			System.exit(-1);
		}
		
		normalizeTrajectories();
	}
	
	/**
	 * 
	 * @param op
	 */
	protected void normalizeTrajectories() {
		/*
		try {
			m_dataset = new Vector<Trajectory>(m_factory.getTrajectories(op));
		} 
		catch (Exception e) {
			e.printStackTrace();
			return;
		}
		m_labelset = new Vector<Integer>(m_factory.getLabels());
		//*/
		
		// now normalize all the trajectories in this data set, so that the 
		// maximum scale is 1.0
		double maxscale = 0.0;
		for (int i = 0; i < m_dataset.size(); i++) {
			Trajectory tr = m_dataset.get(i);
			double scale = 
			tr.getMBB().getHigh(Point.X_DIM) - tr.getMBB().getLow(Point.X_DIM);
			if (Math.abs(scale) > maxscale) {
				maxscale = Math.abs(scale);
			}
		}
		
		for (int i = 0; i < m_dataset.size(); i++) {
			Trajectory tr = m_dataset.get(i);
			tr.scaleX((1.0/maxscale));
		}
		
		// may want to 0-normalize each trajectory
	}
	
	/**
	 * 
	 * @param classifier
	 * @param op
	 */
	public void runLeaveOneOut(Classifier classifier, DistanceOperator op) {
		// remove one trajectory and its label at a time, and classify it
		int errors = 0;
		for (int i = 0; i < m_dataset.size(); i++) {
			Trajectory testant = m_dataset.remove(i);
			int testlabel = m_labelset.remove(i);
			classifier.trainClassifier(m_dataset, m_labelset);
			if (op.needTuning()) {
				// tune the distance operator
				op.tuneOperator(m_dataset, m_labelset, new NNClassifier());
			}			
						
			int result = classifier.classifyTrajectory(testant);
			if (result != testlabel) {
				errors++;
			}
			// restore the testant trajectory
			m_dataset.add(i, testant);
			m_labelset.add(i, testlabel);
		}
		double accuracy = 1.0 - ((double)errors/m_dataset.size());
		logger.info("accuracy ratio: " + accuracy);
	}
	
	/**
	 * 
	 * @param classifier
	 * @param op
	 * @param ratio
	 */
	public void runRandomHoldout(Classifier classifier, 
								DistanceOperator op,
								double ratio) {
		System.out.println("currently just a place holder");
	}
	
	public void runCrossValidation(Classifier classifier, 
									DistanceOperator op, 
									int numofcrosses) {
		int k = numofcrosses;
		
		m_classdistribution = computeDistribution(m_labelset);
		
		/* first perform a stratified partition */
		Vector<Collection<Trajectory>> stratums 
										= new Vector<Collection<Trajectory>>();
		Vector<Collection<Integer>> stratumlabels
										= new Vector<Collection<Integer>>();
		
		for (int i = 0; i < k; i++) {
			stratums.add(new Vector<Trajectory>());
			stratumlabels.add(new Vector<Integer>());
		}
		
		/* 
		 * assign trajectories and their labels to stratums,
		 * multipass but simpler to implement
		 */
		Iterator<Map.Entry<Integer, Integer>> it 
									= m_classdistribution.entrySet().iterator();
		for ( ; it.hasNext(); ) {
			Map.Entry<Integer, Integer> entry = it.next();
			int classlabel = entry.getKey(), count = entry.getValue();
			int index = 0;
			int crosssize = count / k;
			// get a random ordering for trajectories of this class
			int[] ordering = randomShuffle(count);
			
			for (int i = 0; i < m_dataset.size(); i++) {
				int label = m_labelset.get(i);
				if (label == classlabel) {
					Trajectory tr = m_dataset.get(i);
					stratums.get((ordering[index]/crosssize)%k).add(tr);
					stratumlabels.get((ordering[index]/crosssize)%k).add(label);
					index++;
				}	
			}
		}
		
		/* now in turn use one class for training, rest for testing */
		double[] errorrates = 
				crossValidationImpl(classifier, k, stratums, stratumlabels, op);
		
		
		double avg = 0.0, var = 0.0;
		for (int i = 0; i < errorrates.length; i++) {
			avg += errorrates[i];
		}
		avg = avg/errorrates.length;
		for (int i = 0; i < errorrates.length; i++) {
			var += (errorrates[i] - avg) * (errorrates[i] - avg);
		}
		double samplestd = Math.sqrt(var/(errorrates.length-1));
		logger.info("error average: " + avg + 
							" sample standard deviation: " + samplestd);
		logger.info(op.toString());
		m_filehandler.flush();
	}
	
	private double[] crossValidationImpl(Classifier classifier, int realk, 
									Vector<Collection<Trajectory>> stratums,
									Vector<Collection<Integer>> stratumlabels,
									DistanceOperator op) {
		int k = realk;
		double[] errorrates = new double[k];
		for (int i = 0; i < k; i++) {
			Collection<Trajectory> trainset = stratums.get(i);
			Collection<Integer> trainlabels = stratumlabels.get(i);
			
			// train the classifier
			classifier.trainClassifier(trainset, trainlabels);
			
			// train the distance operator
			if (op.needTuning()) {
				//*
				Vector<Collection<Trajectory>> trainsplits 
										= new Vector<Collection<Trajectory>>();
				Vector<Collection<Integer>> trainsplitlabels
				 						= new Vector<Collection<Integer>>();
				
				for (int t = 0; t < 2; t++) {
					trainsplits.add(new Vector<Trajectory>());
					trainsplitlabels.add(new Vector<Integer>());
				}
				
				stratifiedSplit(
					trainset, trainlabels, trainsplits, trainsplitlabels, 0.5);
				
				if( !this.m_splitonly )
				op.tuneOperator(trainsplits.get(0), 
								trainsplitlabels.get(0), 
								new NNClassifier());
				
				//op.tuneOperator(trainset, trainlabels, new NNClassifier());
				
				/* write the training/tuning data set */
				if (this.m_splitonly) {
				Iterator<Trajectory> it1 = trainsplits.get(0).iterator();
				Iterator<Integer> it2 = trainsplitlabels.get(0).iterator();
				PrintStream ps;
				try {
					ps = new PrintStream(
							new FileOutputStream(m_datasetname + "_TUNE"));
					
					for ( ; it1.hasNext() && it2.hasNext(); ) {
						StringBuffer buffer = 
										new StringBuffer().append(it2.next());
						Trajectory tr = it1.next();
						for (int p = 0; p < tr.getNumOfPoints(); p++) 
							buffer.append(" " + tr.getPoint(p).getXPos());
						ps.println(buffer);
					}
					ps.close();
				} catch (Exception e) {
					e.printStackTrace();
				}
				}
			}
			
			if (this.m_splitonly) {	
			PrintStream ps;
			try {
				ps = new PrintStream(
						new FileOutputStream(m_datasetname + "_TEST"));
				for (int j = 0; j < k; j++) {
					if (j == i) {
						continue;
					}
					Collection<Trajectory> testset = stratums.get(j);
					Collection<Integer> testlabels = stratumlabels.get(j);
					
					Iterator<Trajectory> it1 = testset.iterator();
					Iterator<Integer> it2 = testlabels.iterator();
					for ( ; it1.hasNext() && it2.hasNext(); ) {
						Trajectory tr = it1.next();
						StringBuffer buffer = 
							new StringBuffer().append(it2.next());
						for (int p = 0; p < tr.getNumOfPoints(); p++) 
							buffer.append(" " + tr.getPoint(p).getXPos());
						ps.println(buffer);
					}
				}
				ps.close();
			} catch (Exception e) {
				e.printStackTrace();
			}
			System.out.println("get data split only");
			return new double[3];
			}
			
			
			int errors = 0, size = 0;
			for (int j = 0; j < realk; j++) {
				if (j == i) {
					continue;
				}
				
				Collection<Trajectory> testset = stratums.get(j);
				Collection<Integer> testlabels = stratumlabels.get(j);
				
				Iterator<Trajectory> it1 = testset.iterator();
				Iterator<Integer> it2 = testlabels.iterator();
				for ( ; it1.hasNext() && it2.hasNext(); ) {
					Trajectory tr = it1.next();
					int label = it2.next();
					
					if (classifier.classifyTrajectory(tr) != label) {
						errors++;
					}
					size++;					
				}
			}
			
			errorrates[i] = ((double)errors/size);
			logger.info(" Cross No." + i + " error: " + errorrates[i] + " on " +
					Calendar.getInstance().getTime().toString());
			m_filehandler.flush();
		}
		return errorrates;
	}
	
	private void stratifiedSplit(Collection<Trajectory> srcdata, 
								Collection<Integer> srclabels, 
								Vector<Collection<Trajectory>> splits, 
								Vector<Collection<Integer>> splitlabels, 
								double splitprob) {
		HashMap<Integer, Integer> distribution 
											= computeDistribution(srclabels);
		
		Iterator<Map.Entry<Integer, Integer>> it 
										= distribution.entrySet().iterator();
		for ( ; it.hasNext(); ) {
			Map.Entry<Integer, Integer> entry = it.next();
			int classlabel = entry.getKey(), count = entry.getValue();
			int index = 0;
			int cut = (int)(count * splitprob);

			//get a random ordering for trajectories of this class
			int[] ordering = randomShuffle(count);
			
			Iterator<Trajectory> it2 = srcdata.iterator();
			Iterator<Integer> it3 = srclabels.iterator();
			for ( ; it2.hasNext() && it3.hasNext(); ) {
				Trajectory tr = it2.next();
				int label = it3.next();
				if (label == classlabel) {
					if (ordering[index] < cut) {
						splits.get(0).add(tr);
						splitlabels.get(0).add(label);
					} 
					else {
						splits.get(1).add(tr);
						splitlabels.get(1).add(label);
					}
					index++;
				}
			}
		}
		return;
	}
	
	/**
	 * 
	 * @param size number of elements in the array
	 * @return the ordering of the elements, i.e., the i-th element should be
	 * placed in the return[i] position
	 */
	private int[] randomShuffle(int size) {
		int[] array = new int[size];
		Random r = new Random();
		for (int i = 0; i < size; i++) {
			array[i] = i;
		}
		
	    int n = array.length;
	    while (--n > 0) 
	    {
	        int k = r.nextInt(n + 1);  // 0 <= k <= n (!)
	        int temp = array[n];
	        array[n] = array[k];
	        array[k] = temp;
	    }
	    return array;
	}
	
	private HashMap<Integer, Integer> computeDistribution(
											Collection<Integer> classlabels) {
		HashMap<Integer, Integer> distribution 
											= new HashMap<Integer, Integer>();
		for (Iterator<Integer> it = classlabels.iterator(); it.hasNext(); ) {
			int label = it.next();
			if (distribution.containsKey(label)) {
				int count = distribution.get(label);
				count++;
				distribution.put(label, count);
			}
			else {
				distribution.put(label, 1);
			}
		}
		return distribution;
	}
	
	public static SortedMap<String, Integer> initializeDatasets() {
		TreeMap<String, Integer> datasets 
						= new TreeMap<String, Integer>(new StringComparator());
		datasets.put("50words", 5);
		datasets.put("Adiac", 5);
		datasets.put("Beef", 2);
		datasets.put("Car", 2);
		datasets.put("CBF", 16);
		datasets.put("chlorineconcentration", 9);
		datasets.put("cinc_ECG_torso", 30);
		datasets.put("Coffee", 2);
		datasets.put("diatomsizereduction", 10);
		datasets.put("ECG200", 5);
		datasets.put("ECGFiveDays", 26);
		datasets.put("FaceFour", 5);
		datasets.put("FacesUCR", 11);
		datasets.put("fish", 5);
		datasets.put("Gun_Point", 5);
		datasets.put("Haptics", 5);
		datasets.put("InlineSkate", 6);
		datasets.put("ItalyPowerDemand", 8);
		datasets.put("Lighting2", 5);
		datasets.put("Lighting7", 2);
		datasets.put("MALLAT", 20);
		datasets.put("MedicalImages", 5);
		datasets.put("Motes", 24);
		datasets.put("OliveOil", 2);
		datasets.put("OSULeaf", 5);
		datasets.put("plane", 6);
		datasets.put("SonyAIBORobotSurface", 16);
		datasets.put("SonyAIBORobotSurfaceII", 12);
		datasets.put("StarLightCurves", 9);
		datasets.put("SwedishLeaf", 5);
		datasets.put("Symbols", 30);
		datasets.put("synthetic_control", 5);
		datasets.put("Trace", 5);
		datasets.put("TwoLeadECG", 25);
		datasets.put("TwoPatterns", 5);
		datasets.put("wafer", 7);
		datasets.put("WordsSynonyms", 5);
		datasets.put("yoga", 11);
		
		return datasets;
	}
	
	/**
	 * 
	 * @param start the index of first data set, inclusive between 0 - 37
	 * @param end the index of last data set, exclusive between 1 - 38
	 * @param opname name of the similarity distance operator
	 */
	public static void run(int start, int end, String opname) {
		SortedMap<String, Integer> alldata = initializeDatasets();

		/* change distance operator HERE! */
		DistanceOperator op = null;
		try {
			op = (DistanceOperator)Class.forName(opname).newInstance();
		}
		catch (Exception e) {
			System.err.println("Incorrect name for the operator:" + opname);
			e.printStackTrace();
			return;
		}
		
		Iterator<Map.Entry<String, Integer>> it = alldata.entrySet().iterator();
		for (int i = 0 ; it.hasNext(); i++) {
			Map.Entry<String, Integer> entry = it.next();
			
			if (i < start || i >= end) {
				continue;
			}
			String name = entry.getKey(); 
			int k = entry.getValue();
			System.out.println(i + " " + entry.getKey());
			
			ClassifierManager cm = new ClassifierManager(name, op);
			cm.loadData(name);
			logger.info(name + " dataset size:" + cm.m_dataset.size());
			try {
				logger.info("on " + InetAddress.getLocalHost().getHostName());
			} catch (Exception e) {				
			}
			logger.info("starting cross validation: " + 
								Calendar.getInstance().getTime().toString());
			cm.runCrossValidation(new NNClassifier(), op, k);
			logger.info("cross validation done: " + 
								Calendar.getInstance().getTime().toString());
			// send mail
			if (!cm.m_splitonly)
			cm.sendMail(name, opname, i, start, end);
		}
	}
	
	private void sendMail(String datasetname, String opname,
								int index, int start, int end) {
		String serveraddr = "smtp.gmail.com",
				password = "lat04cruz",
				fromaddr = "smtptest37@gmail.com",
				port = "465",
				toaddr = "youremail@gmail.com",
				subject = "",
				message = "";
		try {
			Properties prop = new Properties();
			prop.put("mail.smtps.host", serveraddr);
		    prop.put("mail.smtp.user", fromaddr);
		    prop.put("mail.smtp.port", port);
		    prop.put("mail.smtp.starttls.enable","true");
		    prop.put("mail.smtps.auth", "true");
		    // props.put("mail.smtp.debug", "true");
		    prop.put("mail.smtp.socketFactory.port", port);
		    prop.put("mail.smtp.socketFactory.class", 
		    		"javax.net.ssl.SSLSocketFactory");
		    prop.put("mail.smtp.socketFactory.fallback", "false");
		 
		    Authenticator auth = new SmtpAuthenticator();
		    Session session = Session.getInstance(prop, auth);
		    // session.setDebug(true); 
		 
		    MimeMessage msg = new MimeMessage(session);
		    msg.setFrom(new InternetAddress(fromaddr));
            msg.addRecipient(Message.RecipientType.TO, 
            				new InternetAddress(toaddr));
            InetAddress addr = InetAddress.getLocalHost();
            subject = "SimCompare complete on " + addr.getHostName() + 
            			" " + datasetname + " " + 
            			opname.substring(opname.lastIndexOf('.'));
            message = subject + " on " 
            				+ Calendar.getInstance().getTime().toString()+ " ";
            if (index < end-1)
            	message += (index-start+1) + " done, " + (end-index) + "to-go";
            else
            	message += "all done!";
            
		    msg.setSubject(subject);
		    msg.setText(message);
		            
		            
		    // Create the message part 
		    BodyPart messageBodyPart = new MimeBodyPart();

		    // Fill the message
		    messageBodyPart.setText(message);

		    Multipart multipart = new MimeMultipart();
		    multipart.addBodyPart(messageBodyPart);

		    messageBodyPart = new MimeBodyPart();
		    DataSource source = new FileDataSource(m_logfilename);
		    messageBodyPart.setDataHandler(new DataHandler(source));
		    messageBodyPart.setFileName(m_logfilename);
		    multipart.addBodyPart(messageBodyPart);

		    // Put parts in message
		    msg.setContent(multipart);

		            
		    //Transport.send(msg);
		    Transport tr = session.getTransport("smtps");
		    tr.connect(serveraddr,fromaddr, password);
		    tr.sendMessage(msg, msg.getAllRecipients());
		    tr.close();
		    
		    moveFile(m_logfilename, "finished");
		} 
		catch (Exception e) {
			e.printStackTrace();
		}
	}
	
	private void moveFile(String filename, String dirname) 
								throws IOException {
		/* first we copy the content, then we delete the original file */
		File fromfile = new File(filename);
		File tofile = new File(dirname, filename);
		tofile.createNewFile();
		
		if (!fromfile.exists()) {
			throw new IOException("original file does not exist!");
		}
		if (!tofile.exists()) {
			throw new IOException("destination file does not exist!");
		}
		
		FileInputStream from = null;
		FileOutputStream to = null;
		
		try {
			from = new FileInputStream(fromfile);
			to = new FileOutputStream(tofile);
			byte[] buffer = new byte[4096];
			int bytes = 0;
			// read until EOF
			while ((bytes = from.read(buffer)) != -1) {
				to.write(buffer, 0, bytes);
			}
		}
		// always close the streams, even if exceptions were thrown
		finally {
			if (from != null) {
				try {
					from.close();
				}
				catch (IOException e) {
					e.printStackTrace();
				}
			}
			if (to != null) {
				try {
					to.close();
				}
				catch (IOException e) {
					e.printStackTrace();
				}
			}
		}
		
		fromfile.delete();
	}
	
	public static String usage() {
		return "ClassifierManager distopertor [startindex] [endindex]\n" +
				"where:\n" +
				"\'distoperator\' is the name of the distance operator\n" +
				"\'startindex\' is the index of the first dataset, inclusive\n"
				+ "\'endindex\' is the index of the last dataset, exclusive\n";
	}
	
	/*
	 * 
	 */
	public static Logger getLogger() {
		return logger;
	}
	
	/**
	 * @param args
	 */
	public static void main(String[] args) {
		if (args.length == 3) {
			String distoperator = args[0];
			int startindex = new Integer(args[1]).intValue();
			int endindex = new Integer(args[2]).intValue();
			if (startindex < 0 || startindex > 37 || endindex < startindex) {
				System.out.println(usage());
			}
			else {
				System.out.println("Start working with: " + startindex + " " 
									+ endindex + " " + distoperator);
				run(startindex, endindex, distoperator);
			}
		}
		else if (args.length == 1) {
			String distoperator = args[0];
			System.out.println("Start working with: " + 0 + " " + 38 + " " 
								+ distoperator);
			run(0, 38, distoperator);
		}
		else {
			System.out.println("Hello, World!");
			System.out.println(usage());
		}

	}
}

class SmtpAuthenticator extends javax.mail.Authenticator {
	String pass = "";
	String login = "";

	public SmtpAuthenticator() {
		super();
	}

	public SmtpAuthenticator(String login, String pass) {
		super();

		this.login = login;
		this.pass = pass;
	}

	public PasswordAuthentication getPasswordAuthentication() {
		if (pass.equals(""))
			return null;
		else
			return new PasswordAuthentication(login, pass);
	}

}

class SimpleOutputFormatter extends Formatter {
	public String format(LogRecord record) {
		return record.getMessage() + "\n";
	}
}

class StringComparator implements Comparator<String> {

	public int compare(String s1, String s2) {
		return s1.compareToIgnoreCase(s2);
	}
	
}

class Worker extends Thread {
	int workerid;
	Tester tester;
	Worker(int id, Tester t) {
		workerid = id;
		tester = t;
		start();
	}
	
	public void run() {
		try {
			int i = new Random().nextInt(5);
			System.out.println(workerid + " sleep:" + i);
			sleep(i * 1000);
		} catch (InterruptedException e) {
			throw new RuntimeException(e);
		}
		
		System.out.println(workerid + " work done!");
	}
}

class Tester extends Thread {
	int number = 5;
	Worker[] w = new Worker[number];
	
	public Tester() {	
		for (int i = 0; i < number; i++) {
			w[i] = new Worker(i, this);
		}
		start();
	}
	
	public void run() {
		while (true) { 
			for (int i = 0; i < number; i++) {
				try {
					w[i].join();
				} catch (InterruptedException e) {
					throw new RuntimeException(e);
				}
			}
			System.out.println("all done");
			break;
		}
	}
}
